#!/usr/bin/env python3
"""Emulator czujnika temperatury dla API ZAI 26Z (Python 3.9+, bez dodatkowych pakietów)."""

import argparse
import json
import logging
import math
import os
import random
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timedelta, timezone

CITIES = {
    "warszawa": (52.2297, 21.0122),
    "krakow": (50.0647, 19.9450),
    "gdansk": (54.3520, 18.6466),
    "zakopane": (49.2992, 19.9496),
    "wroclaw": (51.1079, 17.0385),
    "katowice": (50.2649, 19.0238),
}

log = logging.getLogger("generator")


def setup_logging(error_log):
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    if error_log and not any(isinstance(h, logging.FileHandler) for h in log.handlers):
        fh = logging.FileHandler(error_log, encoding="utf-8")
        fh.setLevel(logging.WARNING)
        fh.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(message)s"))
        log.addHandler(fh)


def iso(dt):
    return dt.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def parse_duration(text):
    units = {"s": 1, "m": 60, "h": 3600, "d": 86400}
    if text[-1] in units:
        return float(text[:-1]) * units[text[-1]]
    return float(text)


# ---------------------------------------------------------------- źródła wartości

class RandomSource:
    def __init__(self, a):
        self.lo, self.hi = a.min, a.max

    def value(self, ts, i):
        return round(random.uniform(self.lo, self.hi), 2)


class WalkSource:
    def __init__(self, a):
        self.lo, self.hi = a.min, a.max
        self.v = (a.min + a.max) / 2
        self.step = (a.max - a.min) * 0.05

    def value(self, ts, i):
        self.v = min(self.hi, max(self.lo, self.v + random.uniform(-self.step, self.step)))
        return round(self.v, 2)


class SineSource:
    """Przebieg dobowy: środek przedziału ± amplituda, okres --period, z niewielkim szumem."""

    def __init__(self, a):
        self.mid = (a.min + a.max) / 2
        self.amp = (a.max - a.min) / 2 * 0.8
        self.period = parse_duration(a.period)
        self.noise = a.noise

    def value(self, ts, i):
        phase = 2 * math.pi * (ts.timestamp() % self.period) / self.period
        return round(self.mid + self.amp * math.sin(phase) + random.gauss(0, self.noise), 2)


class OpenMeteoSource:
    """Rzeczywista temperatura (°C) z Open-Meteo: historia godzinowa i bieżący odczyt."""

    URL = "https://api.open-meteo.com/v1/forecast"

    def __init__(self, a):
        if a.city:
            key = a.city.lower()
            if key not in CITIES:
                sys.exit(f"Nieznane miasto '{a.city}'. Dostępne: {', '.join(CITIES)} (albo podaj --lat i --lon)")
            self.lat, self.lon = CITIES[key]
        elif a.lat is not None and a.lon is not None:
            self.lat, self.lon = a.lat, a.lon
        else:
            sys.exit("Źródło open-meteo wymaga --city albo --lat i --lon")
        self.history = None

    def _get(self, params):
        q = urllib.parse.urlencode({"latitude": self.lat, "longitude": self.lon, "timezone": "UTC", **params})
        req = urllib.request.Request(f"{self.URL}?{q}", headers={"User-Agent": "zai-generator/1.0"})
        with urllib.request.urlopen(req, timeout=20) as r:
            return json.load(r)

    def hourly_history(self, past_days):
        data = self._get({"hourly": "temperature_2m", "past_days": past_days, "forecast_days": 1})
        now = datetime.now(timezone.utc)
        out = []
        for t, v in zip(data["hourly"]["time"], data["hourly"]["temperature_2m"]):
            ts = datetime.fromisoformat(t).replace(tzinfo=timezone.utc)
            if v is not None and ts <= now:
                out.append((ts, float(v)))
        return out

    def current(self):
        """Bieżący odczyt modelu (aktualizowany co 15 min) wraz z jego znacznikiem czasu."""
        cur = self._get({"current": "temperature_2m"})["current"]
        ts = datetime.fromisoformat(cur["time"]).replace(tzinfo=timezone.utc)
        return ts, float(cur["temperature_2m"])

    def value(self, ts, i):
        return self.current()[1]


SOURCES = {"random": RandomSource, "walk": WalkSource, "sine": SineSource, "open-meteo": OpenMeteoSource}


# ---------------------------------------------------------------- wysyłanie

class Sender:
    def __init__(self, api, key):
        self.url = api.rstrip("/") + "/api/measurements"
        self.key = key
        self.ok = 0
        self.rejected = 0

    def send(self, value, ts=None):
        body = {"value": value}
        if ts is not None:
            body["timestamp"] = iso(ts)
        req = urllib.request.Request(
            self.url, data=json.dumps(body).encode(), method="POST",
            headers={"Content-Type": "application/json", "Accept": "application/json", "X-API-Key": self.key})
        when = iso(ts) if ts else "teraz"
        try:
            with urllib.request.urlopen(req, timeout=20) as r:
                m = json.load(r)
                self.ok += 1
                log.info("OK      %-20s wartość %8s -> id %s", when, value, m.get("id"))
                return True
        except urllib.error.HTTPError as e:
            self.rejected += 1
            detail = problem_detail(e)
            if e.code == 422:
                log.warning("ODRZUCONO %s wartość %s: %s", when, value, detail)
            elif e.code == 401:
                log.error("BŁĄD 401: klucz czujnika jest niepoprawny lub czujnik wyrejestrowano (%s)", detail)
                raise SystemExit(2)
            else:
                log.error("BŁĄD %s dla %s wartość %s: %s", e.code, when, value, detail)
        except urllib.error.URLError as e:
            self.rejected += 1
            log.error("Brak połączenia z API (%s)", e.reason)
        return False


def problem_detail(e):
    try:
        p = json.loads(e.read().decode("utf-8"))
        return p.get("detail") or p.get("title") or str(p)
    except Exception:
        return e.reason


# ---------------------------------------------------------------- tryby

def run_history(a, sender, source):
    step = timedelta(seconds=parse_duration(a.step))
    if isinstance(source, OpenMeteoSource):
        points = source.hourly_history(a.past_days)
        if a.count:
            points = points[-a.count:]
    else:
        end = datetime.now(timezone.utc).replace(microsecond=0)
        start = end - step * (a.count - 1)
        points = []
        for i in range(a.count):
            ts = start + step * i
            points.append((ts, source.value(ts, i)))
    log.info("Wysyłanie %d wyników historycznych do %s", len(points), sender.url)
    for ts, v in points:
        sender.send(v, ts)
        if a.delay:
            time.sleep(a.delay)


def run_live(a, sender, source):
    interval = parse_duration(a.interval)
    log.info("Tryb na żywo: odczyt co %ss do %s (Ctrl+C kończy)", interval, sender.url)
    sent = 0
    last_ts = None
    try:
        while not a.count or sent < a.count:
            try:
                if isinstance(source, OpenMeteoSource):
                    ts, v = source.current()
                    if ts == last_ts:
                        log.info("Brak nowego odczytu (ostatni z %s) - pomijam", iso(ts))
                        ts = None
                else:
                    ts, v = datetime.now(timezone.utc), source.value(datetime.now(timezone.utc), sent)
            except Exception as e:  # błąd źródła nie przerywa pracy czujnika
                log.error("Nie udało się odczytać wartości ze źródła: %s", e)
                ts = None
            if ts is not None:
                if isinstance(source, OpenMeteoSource):
                    sender.send(v, ts)
                    last_ts = ts
                else:
                    sender.send(v)
                sent += 1
            if not a.count or sent < a.count:
                time.sleep(interval)
    except KeyboardInterrupt:
        pass


def build_parser():
    p = argparse.ArgumentParser(description="Emulator czujnika - wysyła wyniki do POST /api/measurements")
    p.add_argument("--api", default=os.getenv("ZAI_API_URL"), help="adres aplikacji (lub ZAI_API_URL)")
    p.add_argument("--key", default=os.getenv("ZAI_API_KEY"), help="klucz API czujnika (lub ZAI_API_KEY)")
    p.add_argument("--mode", choices=["history", "live", "once"], default="live",
                   help="history: wyniki z przeszłości, live: bieżące w stałych odstępach, once: jedna wartość")
    p.add_argument("--source", choices=SOURCES, default="sine", help="sposób generowania wartości")
    p.add_argument("--min", type=float, default=-5.0, help="dolna granica generowanych wartości")
    p.add_argument("--max", type=float, default=25.0, help="górna granica generowanych wartości")
    p.add_argument("--count", type=int, default=0, help="liczba wyników (live: 0 = bez końca, history: domyślnie 48)")
    p.add_argument("--interval", help="live: odstęp między odczytami, np. 10s, 5m (domyślnie 60s, dla open-meteo 15m)")
    p.add_argument("--step", default="1h", help="history: odstęp między znacznikami czasu")
    p.add_argument("--past-days", type=int, default=3, help="history z open-meteo: liczba dni wstecz (maks. 92)")
    p.add_argument("--period", default="24h", help="sine: okres przebiegu")
    p.add_argument("--noise", type=float, default=0.5, help="sine: odchylenie standardowe szumu")
    p.add_argument("--city", help="open-meteo: " + ", ".join(CITIES))
    p.add_argument("--lat", type=float)
    p.add_argument("--lon", type=float)
    p.add_argument("--value", type=float, help="once: wartość do wysłania (np. spoza przedziału, by pokazać odrzucenie)")
    p.add_argument("--delay", type=float, default=0.0, help="history: przerwa między żądaniami w sekundach")
    p.add_argument("--error-log", default="generator-errors.log", help="plik z logiem odrzuceń i błędów")
    return p


def main(argv=None):
    a = build_parser().parse_args(argv)
    setup_logging(a.error_log)
    if not a.api:
        sys.exit("Podaj adres API: --api https://... albo zmienna ZAI_API_URL")
    if not a.key:
        sys.exit("Podaj klucz czujnika: --key ... albo zmienna ZAI_API_KEY")
    if a.min >= a.max:
        sys.exit("--min musi być mniejsze niż --max")

    if a.interval is None:
        a.interval = "15m" if a.source == "open-meteo" else "60s"

    sender = Sender(a.api, a.key)
    if a.mode == "once":
        if a.value is not None:
            sender.send(a.value)
        else:
            source = SOURCES[a.source](a)
            if isinstance(source, OpenMeteoSource):
                ts, v = source.current()
                sender.send(v, ts)
            else:
                sender.send(source.value(datetime.now(timezone.utc), 0))
    elif a.mode == "history":
        a.count = a.count or (0 if a.source == "open-meteo" else 48)
        run_history(a, sender, SOURCES[a.source](a))
    else:
        run_live(a, sender, SOURCES[a.source](a))

    log.info("Zakończono: przyjęte %d, odrzucone/błędy %d", sender.ok, sender.rejected)
    return 0 if sender.rejected == 0 else 1


if __name__ == "__main__":
    sys.exit(main())

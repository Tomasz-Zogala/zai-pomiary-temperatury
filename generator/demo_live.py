#!/usr/bin/env python3
"""Pokaz na żywo: dla każdej serii rejestruje tymczasowy czujnik i co kilka sekund wysyła
wynik bliski ostatniej zmierzonej temperaturze. Ctrl+C wyrejestrowuje czujniki.

Użycie:
  ZAI_ADMIN_PASSWORD='...' python3 demo_live.py --api https://moja-app.example.com
"""

import argparse
import getpass
import os
import random
import sys
import time

import generator
from seed_demo import call


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--api", default=os.getenv("ZAI_API_URL"), required=os.getenv("ZAI_API_URL") is None)
    p.add_argument("--user", default=os.getenv("ZAI_ADMIN_USER", "admin"))
    p.add_argument("--interval", type=float, default=3.0, help="odstęp między wynikami w sekundach")
    p.add_argument("--step-size", type=float, default=0.3, help="maksymalna zmiana temperatury między wynikami")
    p.add_argument("--series", nargs="*", help="nazwy serii (domyślnie wszystkie)")
    a = p.parse_args()
    password = os.getenv("ZAI_ADMIN_PASSWORD") or getpass.getpass("Hasło administratora: ")
    generator.setup_logging(None)

    token = call(a.api, "POST", "/api/auth/login", {"username": a.user, "password": password})["accessToken"]
    sensors = []
    try:
        for s in call(a.api, "GET", "/api/series"):
            if a.series and s["name"] not in a.series:
                continue
            last = call(a.api, "GET", f"/api/measurements?series={s['id']}&sort=-timestamp&limit=1")
            start = last[0]["value"] if last else (s["minValue"] + s["maxValue"]) / 2
            sensor = call(a.api, "POST", "/api/sensors", {"name": f"Demo na żywo – {s['name']}", "seriesId": s["id"]}, token)
            sensors.append({"series": s, "id": sensor["id"], "sender": generator.Sender(a.api, sensor["apiKey"]), "v": start})
            print(f"Czujnik demo dla serii {s['name']} (start {start} {s.get('unit') or ''})")
        if not sensors:
            sys.exit("Brak serii do pokazu")

        print(f"\nWysyłanie co {a.interval} s – Ctrl+C kończy i wyrejestrowuje czujniki\n")
        while True:
            for d in sensors:
                s = d["series"]
                d["v"] = round(min(s["maxValue"], max(s["minValue"], d["v"] + random.uniform(-a.step_size, a.step_size))), 2)
                d["sender"].send(d["v"])
            time.sleep(a.interval)
    except KeyboardInterrupt:
        print("\nKończenie…")
    finally:
        for d in sensors:
            try:
                call(a.api, "DELETE", f"/api/sensors/{d['id']}", token=token)
            except SystemExit:
                pass
        call(a.api, "POST", "/api/auth/logout", token=token)
        print(f"Wyrejestrowano {len(sensors)} czujników demo.")


if __name__ == "__main__":
    main()

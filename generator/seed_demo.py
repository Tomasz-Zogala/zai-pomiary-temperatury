#!/usr/bin/env python3
"""Przygotowanie danych przykładowych (F11): serie miast, czujniki i historia temperatur wysłana generatorem przez API.

Użycie:
  ZAI_ADMIN_PASSWORD='...' python3 seed_demo.py --api https://moja-app.example.com --user admin
"""

import argparse
import getpass
import json
import os
import sys
import urllib.error
import urllib.request

import generator

SERIES = [
    {"name": "Warszawa", "city": "warszawa", "color": "#1f77b4", "icon": "circle"},
    {"name": "Kraków", "city": "krakow", "color": "#d62728", "icon": "square"},
    {"name": "Gdańsk", "city": "gdansk", "color": "#2ca02c", "icon": "triangle"},
    {"name": "Zakopane", "city": "zakopane", "color": "#9467bd", "icon": "diamond"},
]


def call(api, method, path, body=None, token=None):
    headers = {"Accept": "application/json"}
    if body is not None:
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(api.rstrip("/") + path, method=method, headers=headers,
                                 data=json.dumps(body).encode() if body is not None else None)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            text = r.read().decode()
            return json.loads(text) if text else None
    except urllib.error.HTTPError as e:
        sys.exit(f"{method} {path} -> {e.code}: {generator.problem_detail(e)}")


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--api", default=os.getenv("ZAI_API_URL"), required=os.getenv("ZAI_API_URL") is None)
    p.add_argument("--user", default=os.getenv("ZAI_ADMIN_USER", "admin"))
    p.add_argument("--past-days", type=int, default=3)
    p.add_argument("--source", choices=["open-meteo", "sine"], default="open-meteo",
                   help="sine - gdy Open-Meteo jest niedostępne")
    a = p.parse_args()
    password = os.getenv("ZAI_ADMIN_PASSWORD") or getpass.getpass("Hasło administratora: ")

    token = call(a.api, "POST", "/api/auth/login", {"username": a.user, "password": password})["accessToken"]
    existing = {s["name"] for s in call(a.api, "GET", "/api/series")}

    for s in SERIES:
        if s["name"] in existing:
            print(f"Seria {s['name']} już istnieje - pomijam")
            continue
        series = call(a.api, "POST", "/api/series", {
            "name": s["name"], "minValue": -40, "maxValue": 45, "color": s["color"], "icon": s["icon"], "unit": "°C",
        }, token)
        sensor = call(a.api, "POST", "/api/sensors", {"name": f"Stacja {s['name']}", "seriesId": series["id"]}, token)
        print(f"\n== {s['name']}: seria {series['id']}, czujnik {sensor['id']}")
        # klucz trafia do generatora tylko jako parametr wywołania
        args = ["--api", a.api, "--key", sensor["apiKey"], "--mode", "history", "--source", a.source,
                "--past-days", str(a.past_days)]
        args += ["--city", s["city"]] if a.source == "open-meteo" else ["--min", "-5", "--max", "20", "--count", "72"]
        generator.main(args)

    call(a.api, "POST", "/api/auth/logout", token=token)
    print("\nGotowe.")


if __name__ == "__main__":
    main()

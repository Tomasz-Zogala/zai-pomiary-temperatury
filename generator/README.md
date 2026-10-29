# Generator danych (emulator czujnika)

Python 3.9+, bez dodatkowych pakietów. Wyniki zawsze trafiają do `POST /api/measurements` z kluczem czujnika w nagłówku `X-API-Key`.

## Parametry

| Parametr | Opis |
| --- | --- |
| `--api` / `ZAI_API_URL` | adres aplikacji, np. `https://pomiary.example.com` |
| `--key` / `ZAI_API_KEY` | klucz API czujnika (pokazywany raz przy rejestracji) |
| `--mode` | `history` – wyniki z przeszłości, `live` – bieżące w stałych odstępach, `once` – jedna wartość |
| `--source` | `open-meteo` (rzeczywista temperatura), `sine` (przebieg dobowy), `walk` (błądzenie losowe), `random` |
| `--min`, `--max` | przedział generowanych wartości (`sine`, `walk`, `random`) |
| `--count` | liczba wyników (`live`: 0 = bez końca) |
| `--interval` | `live`: odstęp między odczytami, np. `10s`, `5m` (domyślnie `60s`, dla `open-meteo` `15m`) |
| `--step` | `history`: odstęp znaczników czasu, np. `1h` |
| `--past-days` | `history` z Open-Meteo: dni wstecz (maks. 92) |
| `--city` / `--lat --lon` | lokalizacja dla Open-Meteo (`warszawa`, `krakow`, `gdansk`, `zakopane`, `wroclaw`, `katowice`) |
| `--value` | `once`: konkretna wartość (np. 999, by pokazać odrzucenie) |
| `--error-log` | plik logu odrzuceń i błędów (domyślnie `generator-errors.log`) |

## Przykłady

```bash
export ZAI_API_URL=https://pomiary.example.com
export ZAI_API_KEY=...            # klucz z rejestracji czujnika

# rzeczywiste temperatury z ostatnich 3 dni (co godzinę)
python3 generator.py --mode history --source open-meteo --city warszawa --past-days 3

# 48 wyników sinusoidalnych co godzinę wstecz od teraz
python3 generator.py --mode history --source sine --min 0 --max 20 --count 48 --step 1h

# bieżąca temperatura: Open-Meteo aktualizuje odczyt co 15 min; generator wysyła go
# ze znacznikiem czasu pomiaru i pomija powtórzone odczyty
python3 generator.py --mode live --source open-meteo --city krakow

# szybko zmieniające się dane do pokazania wykresu na żywo
python3 generator.py --mode live --source sine --min 0 --max 20 --period 2m --interval 5s

# wynik spoza przedziału serii -> komunikat ODRZUCONO i wpis w generator-errors.log
python3 generator.py --mode once --value 999
```

## Dane przykładowe (F11)

`seed_demo.py` loguje się jako administrator, tworzy serie dla czterech miast, rejestruje dla każdej czujnik i uruchamia generator w trybie historycznym:

```bash
ZAI_ADMIN_PASSWORD='...' python3 seed_demo.py --api https://pomiary.example.com --user admin
```

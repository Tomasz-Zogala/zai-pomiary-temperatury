# ZAI 26Z – Serie pomiarowe: temperatura w miastach

[![CI](https://github.com/Tomasz-Zogala/zai-pomiary-temperatury/actions/workflows/ci.yml/badge.svg)](https://github.com/Tomasz-Zogala/zai-pomiary-temperatury/actions/workflows/ci.yml)

Aplikacja zbiera i prezentuje pomiary temperatury z czujników (emulowanych generatorem pobierającym rzeczywiste dane z Open-Meteo).

| Katalog | Zawartość |
| --- | --- |
| `backend/` | REST API – Java 21, Spring Boot 3.5, Spring Security, JPA, Flyway |
| `generator/` | emulator czujnika (Python 3.9+, bez zależności) i skrypt danych przykładowych |
| `frontend/` | SPA – Angular 22, Angular Material, Chart.js; budowana do `backend/src/main/resources/static` |

Baza danych: PostgreSQL 16 z rozszerzeniem TimescaleDB (tabela `measurement` jako hypertable). Bez TimescaleDB schemat działa jako zwykła tabela.

## Struktura kodu

```
backend/src/main/java/pl/pw/zai/pomiary/
  PomiaryApplication.java      start aplikacji
  config/       ustawienia aplikacji, konto administratora tworzone przy pierwszym starcie
  domain/       encje JPA: AppUser, AuthToken, Series, Sensor, Measurement
  repo/         repozytoria Spring Data; filtrowanie wyników (MeasurementSearchImpl)
  security/     Spring Security: filtr tokenu Bearer, filtr klucza API czujnika, bcrypt, SHA-256
  web/          kontrolery REST (auth, series, sensors, measurements, health) i trasy SPA
  web/dto/      obiekty żądań i odpowiedzi API
  web/error/    błędy w formacie Problem Details (RFC 9457)
  live/         rozsyłanie nowych wyników przez Server-Sent Events
backend/src/main/resources/
  application.yml              konfiguracja (wartości ze zmiennych środowiskowych)
  db/migration/V1__schema.sql  schemat bazy (Flyway) i hypertable TimescaleDB
backend/src/test/              testy integracyjne API (Testcontainers)
frontend/src/app/
  core/         modele, klient API, sesja, interceptor tokenu, guard, strumień SSE
  dashboard/    strona główna: filtry, wykres, tabela, wydruk
  auth/         logowanie
  admin/        panel administratora: serie, czujniki, zmiana hasła
  shared/       wspólne komponenty (znacznik serii, okno potwierdzenia)
generator/      emulator czujnika, dane przykładowe, pokaz na żywo
```

Żądanie do API przechodzi przez filtry bezpieczeństwa (token administratora albo klucz czujnika), trafia do kontrolera, który waliduje dane i korzysta z repozytoriów, a odpowiedź wraca jako DTO w JSON. Frontend komunikuje się z API wyłącznie przez `ApiService`; komponenty odpowiadają za widok, serwisy z `core/` za dane i sesję.

## Uruchomienie lokalne

Wymagania: Docker; do pracy bez Dockera także JDK 21, Maven 3.9, Node.js 22, Python 3.9+.

```bash
cp .env.example .env              # uzupełnij hasła
docker compose up --build         # baza TimescaleDB + aplikacja (frontend i API) na http://localhost:8080
```

Tryb deweloperski – baza w Dockerze, backend z Mavena, frontend z serwera Angulara (przeładowanie na żywo):

```bash
docker compose up -d db
cd backend && DB_PASSWORD=... ADMIN_USERNAME=admin ADMIN_PASSWORD=... mvn spring-boot:run
cd frontend && npm install && npm start        # http://localhost:4200, /api przekierowane na :8080
```

`npm run build` w katalogu `frontend` buduje aplikację do `backend/src/main/resources/static` (katalog poza repozytorium), skąd serwuje ją Spring Boot.

Testy backendu (JUnit, MockMvc, Testcontainers z TimescaleDB – wymagają Dockera) uruchamia `mvn verify` w katalogu `backend`; w GitHub Actions uruchamiane są przy każdym pushu (`.github/workflows/ci.yml`).

Konto administratora powstaje przy pierwszym starcie z `ADMIN_USERNAME` / `ADMIN_PASSWORD` (hasło zapisywane jako bcrypt). Schemat tworzy Flyway (`backend/src/main/resources/db/migration`).

Dane przykładowe (F11) – zawsze przez API, generatorem:

```bash
cd generator
ZAI_ADMIN_PASSWORD='...' python3 seed_demo.py --api http://localhost:8080 --user admin
```

Testy prowadzącego:

```bash
node zai-tests.mjs --app http://localhost:8080 --user admin --pass '...' --stage E1
```

### Lokalny HTTPS (jak na produkcji)

```bash
docker compose --profile https up -d --build
docker compose cp proxy:/data/caddy/pki/authorities/local/root.crt ./caddy-root.crt
NODE_EXTRA_CA_CERTS=caddy-root.crt node zai-tests.mjs --app https://localhost --user admin --pass '...' --stage E2
```

Caddy wystawia certyfikat z lokalnego CA i przekierowuje `http://` na `https://`.

## Zmienne środowiskowe backendu

| Zmienna | Domyślnie | Opis |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/pomiary` | adres bazy |
| `DB_USER`, `DB_PASSWORD` | `pomiary` | dane dostępowe bazy |
| `ADMIN_USERNAME`, `ADMIN_PASSWORD` | – | konto tworzone przy pierwszym starcie |
| `TOKEN_TTL` | `PT2H` | czas ważności tokenu (ISO 8601) |
| `CORS_ORIGINS` | – | dozwolone originy frontendu, jeśli działa pod inną domeną |
| `PORT` | `8080` | port HTTP |

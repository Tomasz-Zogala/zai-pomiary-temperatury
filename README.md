# ZAI 26Z – Serie pomiarowe: temperatura w miastach

Aplikacja zbiera i prezentuje pomiary temperatury z czujników (emulowanych generatorem pobierającym rzeczywiste dane z Open-Meteo).

| Katalog | Zawartość |
| --- | --- |
| `backend/` | REST API – Java 21, Spring Boot 3.5, Spring Security, JPA, Flyway |
| `generator/` | emulator czujnika (Python 3.9+, bez zależności) i skrypt danych przykładowych |
| `frontend/` | aplikacja Angular (etap E2) |

Baza danych: PostgreSQL 16 z rozszerzeniem TimescaleDB (tabela `measurement` jako hypertable). Bez TimescaleDB schemat działa jako zwykła tabela.

## Uruchomienie lokalne

Wymagania: JDK 21, Maven 3.9, Docker (do bazy), Python 3.9+, Node.js 20+ (testy).

```bash
cp .env.example .env              # uzupełnij hasła
docker compose up --build         # baza TimescaleDB + API na http://localhost:8080
```

Albo sama baza w Dockerze i backend z IDE / Mavena:

```bash
docker compose up -d db
cd backend
DB_PASSWORD=... ADMIN_USERNAME=admin ADMIN_PASSWORD=... mvn spring-boot:run
```

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

## Wdrożenie na Azure (Azure for Students)

1. **Baza** – *Azure Database for PostgreSQL – Flexible Server*, PostgreSQL 16, warstwa *Burstable B1ms*.
   - *Server parameters*: w `azure.extensions` zaznacz `TIMESCALEDB`, w `shared_preload_libraries` dodaj `timescaledb`, zapisz i zrestartuj serwer.
   - *Networking*: publiczny dostęp + „Allow public access from any Azure service”.
   - Utwórz bazę `pomiary`.
2. **Backend** – *App Service* (Linux, Java 21, Java SE). Zbuduj `mvn -DskipTests package` i wdroż `backend/target/pomiary-1.0.0.jar` (np. rozszerzeniem Azure App Service w VS Code/IntelliJ albo `az webapp deploy`).
   - *Environment variables*: `DB_URL=jdbc:postgresql://<serwer>.postgres.database.azure.com:5432/pomiary?sslmode=require`, `DB_USER`, `DB_PASSWORD`, `ADMIN_USERNAME`, `ADMIN_PASSWORD`, `PORT=80` (App Service dla Java SE przekazuje ruch na port z `PORT`/`SERVER_PORT`).
   - *Configuration → General*: **HTTPS Only = On** (przekierowanie http → https), *Always On* = On (w planie płatnym, z kredytu studenckiego); w planie darmowym F1 aplikacja zasypia – trzeba to opisać w dokumentacji.
3. Sprawdź `https://<aplikacja>.azurewebsites.net/api/health`, uruchom `seed_demo.py` i `zai-tests.mjs` dokładnie z danymi logowania, które trafią do formularza.

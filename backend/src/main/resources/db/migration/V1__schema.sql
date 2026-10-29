CREATE TABLE app_user (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Przechowywany jest wyłącznie skrót SHA-256 tokenu
CREATE TABLE auth_token (
    token_hash CHAR(64)    PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_auth_token_expires ON auth_token (expires_at);

CREATE TABLE series (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(100)     NOT NULL,
    min_value  DOUBLE PRECISION NOT NULL,
    max_value  DOUBLE PRECISION NOT NULL,
    color      CHAR(7)          NOT NULL,
    icon       VARCHAR(50),
    unit       VARCHAR(20),
    created_at TIMESTAMPTZ      NOT NULL DEFAULT now(),
    CONSTRAINT ck_series_range CHECK (min_value < max_value),
    CONSTRAINT ck_series_color CHECK (color ~ '^#[0-9A-Fa-f]{6}$')
);

-- Wyrejestrowany czujnik zostaje w tabeli (revoked_at), żeby wyniki zachowały sensor_id
CREATE TABLE sensor (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name                VARCHAR(100) NOT NULL,
    series_id           BIGINT       NOT NULL REFERENCES series (id) ON DELETE CASCADE,
    api_key_hash        CHAR(64)     NOT NULL UNIQUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_measurement_at TIMESTAMPTZ,
    revoked_at          TIMESTAMPTZ
);
CREATE INDEX ix_sensor_series ON sensor (series_id);

-- Klucz główny zawiera ts, bo wymaga tego hypertable TimescaleDB
CREATE TABLE measurement (
    id        BIGINT GENERATED ALWAYS AS IDENTITY,
    series_id BIGINT           NOT NULL REFERENCES series (id) ON DELETE CASCADE,
    sensor_id BIGINT           REFERENCES sensor (id) ON DELETE SET NULL,
    value     DOUBLE PRECISION NOT NULL,
    ts        TIMESTAMPTZ      NOT NULL,
    PRIMARY KEY (id, ts)
);
CREATE INDEX ix_measurement_series_ts ON measurement (series_id, ts);
CREATE INDEX ix_measurement_ts ON measurement (ts);

-- TimescaleDB, jeśli jest dostępne na serwerze; bez niego działa zwykła tabela
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'timescaledb') THEN
        BEGIN
            EXECUTE 'CREATE EXTENSION IF NOT EXISTS timescaledb';
            PERFORM create_hypertable('measurement', 'ts', migrate_data => true);
        EXCEPTION WHEN OTHERS THEN
            RAISE NOTICE 'TimescaleDB niedostępne: %', SQLERRM;
        END;
    END IF;
END $$;

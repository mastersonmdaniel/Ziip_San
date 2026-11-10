-- II-2 (Onda II do PDCA-01): persistencia da fila de excepcoes da critica
-- de leitura. Ficheiro PENDENTE: sem numero Flyway (a numeracao global e
-- atribuida na integracao). Prefixo II-2 por instrucao do pacote.
-- Nenhum limiar operacional esta neste DDL: regras e limiares sao
-- parametros do tenant (chaves meter.* em CritiqueParameters) [A DEFINIR].

CREATE TABLE IF NOT EXISTS critique_exceptions (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    unit_id             UUID NOT NULL,
    reference_month     DATE NOT NULL,
    findings            JSONB NOT NULL,
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    corrected_reading   INTEGER,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_critique_exceptions_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'CORRECTED', 'REREADING_REQUESTED')),
    CONSTRAINT chk_critique_exceptions_corrected
        CHECK (status <> 'CORRECTED' OR corrected_reading IS NULL OR corrected_reading >= 0)
);

CREATE INDEX IF NOT EXISTS idx_critique_exceptions_queue
    ON critique_exceptions (tenant_id, reference_month, unit_id, status);

CREATE TABLE IF NOT EXISTS leak_proactive_signals (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    unit_id                 UUID NOT NULL,
    first_night             DATE NOT NULL,
    last_night              DATE NOT NULL,
    minimum_night_mean      NUMERIC(14, 4) NOT NULL,
    configured_min_flow     NUMERIC(14, 4) NOT NULL,
    consecutive_nights      INTEGER NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_leak_signals_nights CHECK (consecutive_nights >= 1),
    CONSTRAINT chk_leak_signals_range CHECK (last_night >= first_night)
);

CREATE INDEX IF NOT EXISTS idx_leak_proactive_signals_unit
    ON leak_proactive_signals (tenant_id, unit_id, last_night);

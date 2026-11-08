-- =====================================================================
-- RASCUNHO — NÃO APLICAR — P3.37 (hub de integrações)
--
-- O §3 do plano v3.0 (COE-15) reserva na faixa `integration` apenas a
-- V7_1 (P2.26). A linha V7_x cita a P3.37 SEM número reservado, por
-- isso esta minuta fica FORA de db/migration: ela só vira migration
-- Flyway quando a integração reservar o slot (provável V7_2) e trocar
-- o nome do arquivo. Escopo presumido: schema do tenant, como as
-- demais tabelas por tenant do módulo (conferir contra a V7_1 real na
-- integração — a V7_1 não foi relida nesta tarefa, HTTP 429).
--
-- Tabelas das portas do hub:
--   integration_failure_messages  <- FailureQueueService.FailureStore
--   integration_idempotency       <- HubDispatchService.IdempotencyStore
--   integration_contract_results  <- ContractTestService (última rodada)
-- =====================================================================

CREATE TABLE IF NOT EXISTS integration_failure_messages (
    id              uuid PRIMARY KEY,
    tenant_id       uuid NOT NULL,
    integration_name varchar(80) NOT NULL,
    operation       varchar(120) NOT NULL,
    correlation_id  varchar(120) NOT NULL,
    idempotency_key varchar(200) NOT NULL,
    payload         jsonb NOT NULL,
    attempts        integer NOT NULL,
    max_attempts    integer NOT NULL,
    status          varchar(16) NOT NULL,
    last_error      text,
    enqueued_at     timestamptz NOT NULL,
    last_attempt_at timestamptz,
    CONSTRAINT chk_integration_failure_status
        CHECK (status IN ('PENDING', 'RESOLVED', 'DEAD')),
    CONSTRAINT chk_integration_failure_attempts
        CHECK (attempts >= 0 AND max_attempts >= 1 AND attempts <= max_attempts)
);

CREATE INDEX IF NOT EXISTS idx_integration_failure_pending
    ON integration_failure_messages (tenant_id, integration_name, status, enqueued_at);

CREATE TABLE IF NOT EXISTS integration_idempotency (
    tenant_id       uuid NOT NULL,
    integration_name varchar(80) NOT NULL,
    idempotency_key varchar(200) NOT NULL,
    outcome         varchar(16) NOT NULL,
    external_id     varchar(200),
    response_payload jsonb,
    detail          text,
    completed_at    timestamptz NOT NULL,
    PRIMARY KEY (tenant_id, integration_name, idempotency_key),
    CONSTRAINT chk_integration_idempotency_outcome
        CHECK (outcome IN ('SUCCESS', 'REJECTED'))
);

CREATE TABLE IF NOT EXISTS integration_contract_results (
    integration_name varchar(80) NOT NULL,
    partner_version varchar(40) NOT NULL,
    status          varchar(16) NOT NULL,
    expected_fingerprint varchar(200),
    observed_fingerprint varchar(200),
    detail          text,
    checked_at      timestamptz NOT NULL,
    PRIMARY KEY (integration_name, partner_version),
    CONSTRAINT chk_integration_contract_status
        CHECK (status IN ('MATCH', 'DRIFT', 'NOT_RUN'))
);

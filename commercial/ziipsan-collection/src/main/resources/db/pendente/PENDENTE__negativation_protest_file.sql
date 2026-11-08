-- ED-30 Negativacao e protesto por arquivo — SQL PENDENTE, SEM NUMERO Flyway.
-- Motivo: a listagem de migrations da main (HEAD 2f77582) mostrou V6_1..V6_3
-- no modulo ziipsan-collection, mas truncada — numero livre NAO confirmado
-- (mesmo precedente da P4.1/P4.5/ED-31). Atribuir numero na integracao.
-- Valores TRAVADOS do aceite gravados como CHECK/comentario, nao como
-- parametro: envio da exclusao em ate 5 dias uteis; expiracao em 5 anos.
-- NENHUM layout de arquivo de biro/central e criado aqui: o layout e
-- [SEM FONTE] e vive atras da porta BureauFilePort.

CREATE TABLE IF NOT EXISTS negative_listings (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL, unit_id UUID NOT NULL, customer_id UUID NOT NULL,
    channel VARCHAR(80) NOT NULL, purpose VARCHAR(16) NOT NULL
        CHECK (purpose IN ('NEGATIVATION', 'PROTEST')),
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0), included_on DATE NOT NULL,
    status VARCHAR(24) NOT NULL CHECK (status IN ('ACTIVE', 'EXCLUSION_PENDING_SEND', 'EXCLUDED', 'EXPIRED')),
    exclusion_marked_on DATE, exclusion_due_on DATE, excluded_on DATE,
    exclusion_reason VARCHAR(24) CHECK (exclusion_reason IN
        ('PAID', 'ACCOUNT_CANCELLED', 'SERVICE_ORDER_REQUEST', 'EXPIRED_5_YEARS', 'RETURN_REJECTED')),
    exclusion_ref VARCHAR(200),
    -- Prazo travado: quando marcada por pagamento, o envio vence em 5 dias uteis.
    CONSTRAINT ck_neg_exclusion_due_when_paid CHECK (
        exclusion_reason IS DISTINCT FROM 'PAID' OR exclusion_due_on IS NOT NULL));

CREATE TABLE IF NOT EXISTS negative_listing_evaluations (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL, unit_id UUID NOT NULL,
    evaluated_on DATE NOT NULL, eligible BOOLEAN NOT NULL,
    refusal_reasons TEXT NOT NULL DEFAULT '', detail TEXT NOT NULL DEFAULT '');

CREATE TABLE IF NOT EXISTS negative_listing_remittances (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL, channel VARCHAR(80) NOT NULL,
    purpose VARCHAR(16) NOT NULL, file_ref VARCHAR(200) NOT NULL,
    entry_count INT NOT NULL CHECK (entry_count >= 0), generated_on DATE NOT NULL);

CREATE TABLE IF NOT EXISTS negative_listing_returns (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL, channel VARCHAR(80) NOT NULL,
    file_ref VARCHAR(200) NOT NULL, listing_id UUID NOT NULL REFERENCES negative_listings(id),
    accepted BOOLEAN NOT NULL, channel_code VARCHAR(80), channel_message TEXT);

-- Definicoes de parametro (catalogo P0.19): neg.* sao configuracao do
-- tenant — NENHUM valor de fabrica e semeado (canais, meio do aviso e
-- perfis excluidos sao do ente/canal contratado; sem fonte para padrao).
-- Os prazos travados (5 dias uteis / 5 anos) NAO sao parametros.

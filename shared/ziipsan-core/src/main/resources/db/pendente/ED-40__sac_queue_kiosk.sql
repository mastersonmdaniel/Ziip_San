-- ED-40 Totem em modo quiosque e senhas — SQL PENDENTE DE NUMERACAO FLYWAY.
-- Nenhum numero foi confirmado livre para esta frente; NAO inventar numero:
-- na integracao, atribuir o proximo slot livre da historia do modulo
-- shared/ziipsan-core, renomear o arquivo e conferir contra V8_1
-- (service_protocols) e o catalogo de parametros.
-- Os tres instantes exigidos pelo acceptance sao colunas proprias:
-- issued_at (emissao), called_at (chamada), service_started_at (inicio
-- do atendimento), com CHECKs de monotonicidade espelhando QueueTicket.
-- CORRECAO DA REVISAO (D1): a unicidade da senha e por DIA de servico,
-- nao pelo timestamp completo da emissao. service_date e a data local
-- da emissao (fuso America/Sao_Paulo, o do tenant catarinense regido
-- pela Lei SC 12.465/2002; a integracao confirma o fuso do tenant ao
-- numerar a migration), derivada de issued_at e travada por CHECK —
-- coluna gerada/expressao em indice sobre TIMESTAMPTZ dependeria de
-- funcao imutavel de conversao de fuso, o que o PostgreSQL nao garante
-- para (issued_at::date); a coluna com CHECK entrega a mesma regra de
-- forma portavel e auditavel. display_code e copia desnormalizada do
-- codigo derivado em QueueTicket.displayCode() (sequencial + perfil):
-- a UNICA fonte da derivacao e o codigo Java; a integracao grava o
-- valor a partir dele, nunca o recalcula por outra regra (D5).
-- Limites de espera NUNCA sao constantes aqui: definitions com valor de
-- fabrica da Lei estadual SC 12.465/2002 (15 min dia util comum; 30 min
-- vespera/dia seguinte a feriado prolongado), para registro no catalogo
-- de parametros na integracao (chaves em KioskParameters):
--   portal.kiosk_enabled                              (classe b, fabrica false)
--   sac.queue_wait_limit_business_day_minutes         (classe d, fabrica 15 — Lei SC 12.465/2002)
--   sac.queue_wait_limit_holiday_adjacent_minutes     (classe d, fabrica 30 — Lei SC 12.465/2002)
-- Feriados sao DADO do tenant (calendario oficial), nunca lista no codigo:
-- a fiacao entrega o conjunto de datas ao HolidayCalendar.

CREATE TABLE IF NOT EXISTS sac_queue_tickets (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    service_unit_id UUID NOT NULL,
    sequence_number INT NOT NULL CHECK (sequence_number > 0),
    display_code VARCHAR(10) NOT NULL,
    service_type VARCHAR(80) NOT NULL CHECK (NULLIF(btrim(service_type),'') IS NOT NULL),
    priority_category VARCHAR(30) NOT NULL DEFAULT 'NONE' CHECK (priority_category IN
        ('NONE','PERSON_WITH_DISABILITY','ELDERLY_60_OR_OVER','PREGNANT','NURSING','WITH_CHILD_IN_ARMS','WITH_OBESITY')),
    companion_present BOOLEAN NOT NULL DEFAULT FALSE,
    accessible_format VARCHAR(20) NOT NULL DEFAULT 'NONE' CHECK (accessible_format IN
        ('NONE','BRAILLE','LARGE_PRINT','ACCESSIBLE_PDF','AUDIO')),
    assistance_channel VARCHAR(20) NOT NULL DEFAULT 'NONE' CHECK (assistance_channel IN
        ('NONE','LIBRAS','TDD','VIDEO_SERVICE')),
    status VARCHAR(20) NOT NULL DEFAULT 'WAITING' CHECK (status IN
        ('WAITING','CALLED','IN_SERVICE','COMPLETED','NO_SHOW','CANCELLED')),
    issued_at TIMESTAMPTZ NOT NULL,
    service_date DATE NOT NULL,
    called_at TIMESTAMPTZ,
    desk_id VARCHAR(20),
    service_started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (service_date = ((issued_at AT TIME ZONE 'America/Sao_Paulo')::date)),
    CHECK (called_at IS NULL OR called_at >= issued_at),
    CHECK (called_at IS NULL OR NULLIF(btrim(desk_id),'') IS NOT NULL),
    CHECK (service_started_at IS NULL OR called_at IS NOT NULL),
    CHECK (service_started_at IS NULL OR service_started_at >= called_at),
    -- CORRECAO DA REVISAO (D2): finished_at espelha as transicoes do
    -- QueueTicket — complete exige >= inicio; markNoShow exige >=
    -- chamada; cancel exige >= emissao. Os tres CHECKs abaixo cobrem
    -- exatamente esses pisos, conforme os instantes presentes.
    CHECK (finished_at IS NULL OR finished_at >= issued_at),
    CHECK (finished_at IS NULL OR called_at IS NULL OR finished_at >= called_at),
    CHECK (finished_at IS NULL OR service_started_at IS NULL
           OR finished_at >= service_started_at),
    UNIQUE (tenant_id, service_unit_id, service_date, sequence_number)
);
CREATE INDEX IF NOT EXISTS idx_sac_queue_tickets_unit_day
    ON sac_queue_tickets (tenant_id, service_unit_id, service_date);
CREATE INDEX IF NOT EXISTS idx_sac_queue_tickets_status
    ON sac_queue_tickets (tenant_id, status, issued_at);

CREATE TABLE IF NOT EXISTS sac_kiosk_terminals (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    device_id UUID NOT NULL,
    service_unit_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, device_id)
);

SELECT register_tenant_table('sac_queue_tickets');
SELECT register_tenant_table('sac_kiosk_terminals');

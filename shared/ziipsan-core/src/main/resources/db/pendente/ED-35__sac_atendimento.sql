-- ED-35 Atendimento completo — SQL PENDENTE DE NUMERACAO FLYWAY.
-- Nenhum numero foi confirmado livre para esta frente (historias tenant/core ja
-- ocupadas ate V21/V15+ e pacotes locais pendentes disputam slots); NAO inventar
-- numero: na integracao, atribuir o proximo slot livre da historia do modulo
-- shared/ziipsan-core, renomear o arquivo e conferir FKs contra V8_1
-- (service_protocols), V3_12 (unit_essential_users) e o catalogo de parametros.
-- Correcoes pos-reprovacao (ver RELATORIO-ED-35.md, secao CORRECOES):
-- M3: sector/route NOT NULL (Area exige os tres campos no codigo).
-- M4: protocol_id UUID com FK real para service_protocols (V8_1), no padrao
--     do repo: FOREIGN KEY (tenant_id, protocol_id) REFERENCES
--     service_protocols(tenant_id, id) (mesmo padrao de V21_2 e de
--     R__service_protocol_order_fk.sql); canal como VARCHAR + CHECK com os
--     valores de ProtocolChannel da P1.36, espelhando a coluna channel de V8_1.
-- M5: sac_communication_evidences persiste reclamacao E resposta (antes so a
--     pesquisa de satisfacao tinha tabela).
-- M1: sac_external_returns ganha opened_date e closed_at (encerramento de
--     fato), com CHECK de retorno >= abertura e de encerramento so com retorno.
-- Prazos NUNCA sao constantes aqui: vem de sac.ombudsman_days (definition com
-- padrao de fabrica 30, classe c so-reduz — Lei 13.460/2017, art. 16) e de
-- sac.external_channels (JSON por origem, classe d).

CREATE TABLE IF NOT EXISTS sac_ombudsman_cases (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    protocol_id UUID NOT NULL,
    channel VARCHAR(20) NOT NULL CHECK (channel IN ('COUNTER','PHONE','PORTAL','APP','EMAIL','WHATSAPP','REGULATOR','OMBUDSMAN')),
    identity_mode VARCHAR(20) NOT NULL CHECK (identity_mode IN ('IDENTIFIED','PRESERVED','ANONYMOUS')),
    opened_date DATE NOT NULL,
    original_due_date DATE NOT NULL,
    current_due_date DATE NOT NULL,
    extended BOOLEAN NOT NULL DEFAULT FALSE,
    extension_justification VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (current_due_date >= original_due_date),
    CHECK (extended = FALSE OR NULLIF(btrim(extension_justification),'') IS NOT NULL),
    FOREIGN KEY (tenant_id, protocol_id) REFERENCES service_protocols(tenant_id, id)
);
CREATE TABLE IF NOT EXISTS sac_external_returns (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    protocol_id UUID NOT NULL,
    origin VARCHAR(30) NOT NULL CHECK (origin IN ('CONSUMER_GOV_BR','PROCON_SINDEC','REGULATOR_AGENCY','PUBLIC_PROSECUTOR','PUBLIC_DEFENDER')),
    opened_date DATE NOT NULL,
    due_date DATE NOT NULL,
    returned_at TIMESTAMPTZ,
    return_reference VARCHAR(200),
    closed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (returned_at IS NULL OR NULLIF(btrim(return_reference),'') IS NOT NULL),
    CHECK (returned_at IS NULL OR returned_at::date >= opened_date),
    CHECK (closed_at IS NULL OR returned_at IS NOT NULL),
    CHECK (closed_at IS NULL OR closed_at >= returned_at),
    FOREIGN KEY (tenant_id, protocol_id) REFERENCES service_protocols(tenant_id, id)
);
CREATE TABLE IF NOT EXISTS sac_planned_outages (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    locality VARCHAR(200) NOT NULL,
    sector VARCHAR(120) NOT NULL,
    route VARCHAR(120) NOT NULL,
    scheduled_start TIMESTAMPTZ NOT NULL,
    expected_return TIMESTAMPTZ NOT NULL,
    reason VARCHAR(500) NOT NULL,
    last_return_update_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (expected_return > scheduled_start)
);
CREATE TABLE IF NOT EXISTS sac_outage_affected_units (
    tenant_id UUID NOT NULL,
    outage_id UUID NOT NULL,
    consumer_unit_id UUID NOT NULL,
    essential_kind VARCHAR(20) CHECK (essential_kind IN ('HOSPITAL','SCHOOL','COLLECTIVE_CARE')),
    PRIMARY KEY (tenant_id, outage_id, consumer_unit_id)
);
CREATE TABLE IF NOT EXISTS sac_outage_protocols (
    tenant_id UUID NOT NULL,
    outage_id UUID NOT NULL,
    protocol_id UUID NOT NULL,
    PRIMARY KEY (tenant_id, outage_id, protocol_id),
    FOREIGN KEY (tenant_id, protocol_id) REFERENCES service_protocols(tenant_id, id)
);
CREATE TABLE IF NOT EXISTS sac_assignment_trail (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    target_type VARCHAR(20) NOT NULL CHECK (target_type IN ('PROTOCOL','SERVICE_ORDER')),
    target_id UUID NOT NULL,
    from_person_id UUID NOT NULL,
    to_person_id UUID NOT NULL,
    reason VARCHAR(500) NOT NULL CHECK (NULLIF(btrim(reason),'') IS NOT NULL),
    changed_by UUID NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (from_person_id <> to_person_id)
);
-- M5 (correcao pos-reprovacao): reclamacao e resposta como evidencia ISO
-- 9001/24510 persistidas \u2014 antes so a pesquisa de satisfacao tinha tabela.
CREATE TABLE IF NOT EXISTS sac_communication_evidences (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    protocol_id UUID NOT NULL,
    complaint_summary TEXT NOT NULL CHECK (NULLIF(btrim(complaint_summary),'') IS NOT NULL),
    response_summary TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (tenant_id, protocol_id) REFERENCES service_protocols(tenant_id, id)
);
CREATE TABLE IF NOT EXISTS sac_satisfaction_surveys (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    protocol_id UUID NOT NULL,
    score INT NOT NULL CHECK (score BETWEEN 1 AND 5),
    comment TEXT,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (tenant_id, protocol_id) REFERENCES service_protocols(tenant_id, id)
);
SELECT register_tenant_table('sac_ombudsman_cases');
SELECT register_tenant_table('sac_external_returns');
SELECT register_tenant_table('sac_planned_outages');
SELECT register_tenant_table('sac_outage_affected_units');
SELECT register_tenant_table('sac_outage_protocols');
SELECT register_tenant_table('sac_assignment_trail');
SELECT register_tenant_table('sac_communication_evidences');
SELECT register_tenant_table('sac_satisfaction_surveys');
-- Definitions dos parametros da ED-35 (core parameter_definitions; conferir o
-- formato exato das colunas contra V9_9 na integracao):
--   sac.ombudsman_days: INTEGER, classe c, padrao 30 (Lei 13.460, art. 16)
--   sac.external_channels: JSON object origem->dias, classe d, sem padrao
--   portal.outage_status_visibility: ENUM PUBLIC_FULL/PUBLIC_SUMMARY/HIDDEN, classe d, sem padrao

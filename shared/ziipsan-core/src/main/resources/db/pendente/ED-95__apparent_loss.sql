-- ============================================================================
-- ED-95 (PENDENTE DE NUMERACAO): perdas aparentes — cruzamento de
-- edificacoes com unidades e analitica de fraude (CAD-103, GES-25,
-- OPE-37; ADR-0034, ADR-0042; deps ED-24, ED-14, ED-17, ED-62).
--
-- POR QUE PENDENTE: SQL sem numero confirmado livre na serie tenant
-- do core. Regra da cascata: NENHUM numero inventado — este arquivo
-- entra como db/pendente/ com o prefixo ED-95__ e recebe o numero da
-- sequencia global na integracao (precedentes: ED-24, ED-56, ED-96).
--
-- LICAO DA C3 (aplicada): TODA tabela referenciada por FK composta
-- (tenant_id, X) tem UNIQUE (tenant_id, id) declarada NESTE arquivo:
-- loss_property_registry e referenciada pelos achados do cruzamento.
-- FKs para tabelas de outras tarefas (consumer_units da P1.3,
-- service_orders da V9_1) NAO sao declaradas aqui: a UNIQUE composta
-- delas e conferida na integracao (como na ED-24) — os ids viajam
-- como colunas com indice, sem FK presumida.
--
-- FRONTEIRA DURA DO ACEITE (ADR-0042): nenhuma tabela desta entrega
-- tem coluna de debito, corte ou infracao. Suspeitas e achados sao
-- RECOMENDACOES; a OS so existe com decisao humana registrada
-- (decided_by/decided_at NOT NULL quando work_order_id presente —
-- CHECK em loss_cross_findings e em loss_suspicions). O auto de
-- infracao e o debito sao da ED-24 (infraction_notices da ED-24,
-- recebivel INFRACTION da P2.5) — nada deles aqui.
--
-- O QUE ESTE SQL FAZ:
--   (1) loss_property_registry: cadastro imobiliario municipal
--       (CAD-103) com inscricao, area construida, uso, habite-se e
--       alvara; base_legal_basis e sharing_purpose OBRIGATORIAS
--       (NOT NULL + nao-brancas): sem elas o banco recusa o registro,
--       como o record Java. Os textos sao os DECLARADOS pelo ente —
--       nenhum texto legal e semeado ([SEM FONTE]).
--   (2) loss_cross_findings: fila de revisao do cruzamento (OPE-37),
--       com prazo (review_due_date) e acao sugerida; restrito ao
--       backoffice por desenho da aplicacao (ADR-0034 §12) — o banco
--       guarda o canal da avaliacao que gerou o lote (evaluation_channel
--       travado em BACKOFFICE pelo CHECK).
--   (3) loss_suspicions: recomendacoes das regras (GES-25), com a
--       recomendacao travada em HUMAN_INSPECTION_REVIEW e o efeito
--       automatico travado em NONE pelos CHECKs.
--   (4) loss_inspection_outcomes: desfechos agregados por regra para
--       o relatorio de acerto (taxa de confirmacao por regra).
--   (5) loss_self_reports: casos de autodenuncia com a isencao de
--       multa do programa (fine_waiver = true sempre: caso so existe
--       com o programa ligado, como no servico).
--
-- NENHUM DADO DE NEGOCIO E SEMEADO. CONVENCOES: mensagens sem
-- diacriticos; RLS via register_tenant_table.
--
-- REAPLICACAO (correcao D2 da revisao): tabelas e os 6 indices usam
-- IF NOT EXISTS — reaplicar este arquivo sobre o mesmo tenant sai
-- limpo (provado em PostgreSQL real descartavel, aplicacao x2).
-- ============================================================================

SELECT set_config('app.current_tenant', '${tenant_id}', true);

-- (1) Cadastro imobiliario municipal (CAD-103) -------------------------------
CREATE TABLE IF NOT EXISTS loss_property_registry (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    municipal_registration TEXT NOT NULL CHECK (NULLIF(btrim(municipal_registration), '') IS NOT NULL),
    built_area_square_meters NUMERIC(14,2) NOT NULL CHECK (built_area_square_meters >= 0),
    declared_use TEXT NOT NULL CHECK (declared_use IN
        ('RESIDENTIAL', 'COMMERCIAL', 'INDUSTRIAL', 'PUBLIC', 'MIXED', 'RURAL', 'OTHER')),
    habite_se_reference TEXT,
    building_permit_reference TEXT,
    legal_basis TEXT NOT NULL CHECK (NULLIF(btrim(legal_basis), '') IS NOT NULL),
    sharing_purpose TEXT NOT NULL CHECK (NULLIF(btrim(sharing_purpose), '') IS NOT NULL),
    source TEXT NOT NULL CHECK (source IN ('MUNICIPAL_CONNECTOR', 'GIS_IMPORT')),
    source_batch_reference TEXT NOT NULL CHECK (NULLIF(btrim(source_batch_reference), '') IS NOT NULL),
    imported_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_loss_property_registry_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_loss_property_registry_tenant_registration UNIQUE (tenant_id, municipal_registration)
);
SELECT register_tenant_table('loss_property_registry');
CREATE INDEX IF NOT EXISTS idx_loss_property_registry_tenant ON loss_property_registry (tenant_id, imported_at);

-- (2) Fila de revisao do cruzamento (OPE-37) ---------------------------------
CREATE TABLE IF NOT EXISTS loss_cross_findings (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    finding_kind TEXT NOT NULL CHECK (finding_kind IN
        ('BUILT_WITHOUT_CONNECTION', 'ZERO_CONSUMPTION_ACTIVE_CONNECTION',
         'AREA_DIVERGENCE', 'USE_DIVERGENCE')),
    property_id UUID,
    unit_id UUID,
    evidence TEXT NOT NULL CHECK (NULLIF(btrim(evidence), '') IS NOT NULL),
    suggested_action TEXT NOT NULL CHECK (suggested_action IN
        ('FIELD_SURVEY', 'INSPECTION', 'BATCH_RE_REGISTRATION')),
    evaluation_channel TEXT NOT NULL CHECK (evaluation_channel = 'BACKOFFICE'),
    detected_at TIMESTAMPTZ NOT NULL,
    review_due_date DATE NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('PENDING_REVIEW', 'DECIDED', 'DISMISSED')),
    decided_by UUID,
    decided_at TIMESTAMPTZ,
    work_order_id UUID,
    -- OS so com decisao humana registrada (aceite: nenhuma regra gera efeito sem decisao humana).
    CHECK (work_order_id IS NULL OR (decided_by IS NOT NULL AND decided_at IS NOT NULL)),
    CHECK (status <> 'DECIDED' OR work_order_id IS NOT NULL),
    CHECK (status <> 'PENDING_REVIEW' OR work_order_id IS NULL),
    CONSTRAINT uq_loss_cross_findings_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_loss_cross_findings_property
        FOREIGN KEY (tenant_id, property_id) REFERENCES loss_property_registry (tenant_id, id)
);
SELECT register_tenant_table('loss_cross_findings');
CREATE INDEX IF NOT EXISTS idx_loss_cross_findings_queue ON loss_cross_findings (tenant_id, status, review_due_date);
CREATE INDEX IF NOT EXISTS idx_loss_cross_findings_unit ON loss_cross_findings (tenant_id, unit_id, detected_at);

-- (3) Recomendacoes das regras de suspeita (GES-25) ---------------------------
CREATE TABLE IF NOT EXISTS loss_suspicions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    rule_code TEXT NOT NULL CHECK (rule_code IN
        ('ZERO_CONSUMPTION_ACTIVE', 'ABRUPT_DROP', 'PER_ECONOMY', 'PER_AREA',
         'STALLED_METER', 'UNDERSIZED_METER', 'METER_REPLACEMENT_ANOMALY', 'REPEATED_READING')),
    evidence TEXT NOT NULL CHECK (NULLIF(btrim(evidence), '') IS NOT NULL),
    evaluated_at TIMESTAMPTZ NOT NULL,
    recommendation TEXT NOT NULL CHECK (recommendation = 'HUMAN_INSPECTION_REVIEW'),
    automatic_effect TEXT NOT NULL CHECK (automatic_effect = 'NONE'),
    decided_by UUID,
    decided_at TIMESTAMPTZ,
    inspection_work_order_id UUID,
    CHECK (inspection_work_order_id IS NULL OR (decided_by IS NOT NULL AND decided_at IS NOT NULL)),
    CONSTRAINT uq_loss_suspicions_tenant_id UNIQUE (tenant_id, id)
);
SELECT register_tenant_table('loss_suspicions');
CREATE INDEX IF NOT EXISTS idx_loss_suspicions_unit ON loss_suspicions (tenant_id, unit_id, evaluated_at);
CREATE INDEX IF NOT EXISTS idx_loss_suspicions_rule ON loss_suspicions (tenant_id, rule_code, evaluated_at);

-- (4) Desfechos de inspecao por regra (relatorio de acerto) -------------------
CREATE TABLE IF NOT EXISTS loss_inspection_outcomes (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    rule_code TEXT NOT NULL CHECK (rule_code IN
        ('ZERO_CONSUMPTION_ACTIVE', 'ABRUPT_DROP', 'PER_ECONOMY', 'PER_AREA',
         'STALLED_METER', 'UNDERSIZED_METER', 'METER_REPLACEMENT_ANOMALY', 'REPEATED_READING')),
    inspected_count INTEGER NOT NULL CHECK (inspected_count >= 0),
    confirmed_count INTEGER NOT NULL CHECK (confirmed_count >= 0),
    CHECK (confirmed_count <= inspected_count),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_loss_inspection_outcomes_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_loss_inspection_outcomes_tenant_rule UNIQUE (tenant_id, rule_code)
);
SELECT register_tenant_table('loss_inspection_outcomes');

-- (5) Autodenuncia com isencao de multa (opcao do tenant) ---------------------
CREATE TABLE IF NOT EXISTS loss_self_reports (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    declared_irregularity TEXT NOT NULL CHECK (NULLIF(btrim(declared_irregularity), '') IS NOT NULL),
    reported_at TIMESTAMPTZ NOT NULL,
    fine_waiver BOOLEAN NOT NULL CHECK (fine_waiver = TRUE),
    status TEXT NOT NULL CHECK (status IN ('PENDING_HUMAN_REVIEW', 'DECIDED', 'DISMISSED')),
    CONSTRAINT uq_loss_self_reports_tenant_id UNIQUE (tenant_id, id)
);
SELECT register_tenant_table('loss_self_reports');
CREATE INDEX IF NOT EXISTS idx_loss_self_reports_unit ON loss_self_reports (tenant_id, unit_id, reported_at);

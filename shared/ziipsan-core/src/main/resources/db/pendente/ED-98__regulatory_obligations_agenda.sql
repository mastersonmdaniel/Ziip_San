-- ED-98 -- Agenda de obrigacoes regulatorias e de controle do tenant
-- (GES-05): itens do calendario gerado do perfil reg.* e da natureza
-- juridica, com responsavel, evidencia, situacao e lembrete (ED-07);
-- evidencia de envio da ED-97 ligada ao item (RAS-08); fiscalizacao
-- como ocorrencia da P2.25 com plano de acao (JOR-24); MTR/CDF/DMR
-- manual enquanto a ED-104 nao for liberada (RAS-17).
-- Status: PENDENTE DE NUMERACAO -- SQL sem numero global, em
-- db/pendente/ com prefixo ED-98__ (regra da cascata). Nenhuma migration
-- publicada e tocada e nenhum numero e atribuido neste arquivo.
--
-- PARAMETRO reg.obligations (aceite da ED-98): objeto, classe d -- no
-- mesmo padrao do reg.reports da ED-97. Se a chave ja existir no
-- catalogo do core, o INSERT abaixo e ignorado (ON CONFLICT DO
-- NOTHING), no precedente da ED-75/ED-06/ED-97.
--
-- TRAVAS: item DONE nao manual exige evidence_ref (RAS-08); item DONE
-- manual exige completion_note (RAS-17); item de fiscalizacao exige
-- occurrence_ref e action_plan (JOR-24); due_date nula exige due_label
-- (o rotulo [A DEFINIR] do vencimento sem fonte -- data nao se finge).
-- Correcao D7 (REVISAO-ED-98): as travas de texto exigem NAO-BRANCO
-- (btrim(...) <> ''), como no codigo -- string vazia/so espacos nao
-- passa mais em evidence_ref, completion_note, occurrence_ref,
-- action_plan e due_label (padrao btrim das tabelas do projeto, ex.
-- ED-75). Correcao D1: a mesma evidence_ref nao quita dois itens --
-- indice unico parcial sobre evidence_ref.
-- FK COMPOSTA: nenhuma FK neste arquivo (tenant_id e a particao RLS).

INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('reg.obligations', 'OBJECT', 'd', NULL,
     'ED-98 (GES-05); P3.15; ED-07; ED-97 (RAS-08); P2.25 (JOR-24); RAS-17',
     NULL,
     'Perfil de obrigacoes regulatorias do tenant (natureza juridica, data-base tarifaria, responsaveis, aplicabilidade de RREO/RGF) do qual a agenda da ED-98 gera o calendario. Sem valor de fabrica.')
ON CONFLICT (definition_key) DO NOTHING;

CREATE TABLE IF NOT EXISTS reg_obligation_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    kind VARCHAR(40) NOT NULL CHECK (kind IN (
        'SINISA_ANNUAL_CYCLE', 'SOCIAL_TARIFF_ANNUAL_REPORT',
        'ANNUAL_DISCHARGE_DECLARATION', 'WATER_LOSS_BALANCE_NR15',
        'ESFINGE_BIMONTHLY', 'SISAGUA', 'TARIFF_READJUSTMENT_PUBLICATION',
        'RREO', 'RGF', 'WASTE_MTR_CDF_DMR_MANUAL',
        'EXTERNAL_INSPECTION_FOLLOWUP')),
    title VARCHAR(200) NOT NULL,
    norm_catalog_key VARCHAR(128),
    period_ref VARCHAR(16) NOT NULL,
    due_date DATE,
    due_label VARCHAR(200),
    responsible_user_id UUID NOT NULL,
    evidence_ref VARCHAR(160),
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'EVIDENCE_LINKED', 'DONE', 'OVERDUE')),
    manual BOOLEAN NOT NULL DEFAULT FALSE,
    occurrence_ref VARCHAR(160),
    action_plan TEXT,
    reminder_ref VARCHAR(160),
    completion_note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_reg_obligation_items_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_reg_obligation_due CHECK (due_date IS NOT NULL
        OR (due_label IS NOT NULL AND btrim(due_label) <> '')),
    CONSTRAINT ck_reg_obligation_done_needs_evidence
        CHECK (status <> 'DONE' OR manual
               OR (evidence_ref IS NOT NULL AND btrim(evidence_ref) <> '')),
    CONSTRAINT ck_reg_obligation_manual_done_needs_note
        CHECK (status <> 'DONE' OR NOT manual
               OR (completion_note IS NOT NULL AND btrim(completion_note) <> '')),
    CONSTRAINT ck_reg_obligation_inspection
        CHECK (kind <> 'EXTERNAL_INSPECTION_FOLLOWUP'
               OR ((occurrence_ref IS NOT NULL AND btrim(occurrence_ref) <> '')
                   AND (action_plan IS NOT NULL AND btrim(action_plan) <> '')))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_reg_obligation_item_period
    ON reg_obligation_items(tenant_id, kind, period_ref);
CREATE UNIQUE INDEX IF NOT EXISTS uq_reg_obligation_item_evidence
    ON reg_obligation_items(evidence_ref) WHERE evidence_ref IS NOT NULL;

SELECT register_tenant_table('reg_obligation_items');

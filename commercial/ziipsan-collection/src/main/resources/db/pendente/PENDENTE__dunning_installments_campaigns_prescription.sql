-- P4.1 (R8: cobranca; negativacao e protesto na ED-30) — DDL PENDENTE DE NUMERO.
--
-- POR QUE SEM NUMERO: a faixa do modulo e a V10_x (dunning, plano v3.0 SS3),
-- mas a trilha usa a sequencia GLOBAL a partir de V100 depois da P3.12, e o
-- proximo numero livre depende da maior versao publicada no repo no momento
-- da integracao — nao confirmavel a partir dos pacotes locais (precedente:
-- P4.5). Na integracao, este arquivo vira V<nnn>__dunning_*.sql com o numero
-- da sequencia. NENHUM numero foi inventado aqui.
--
-- Conteudo:
--   * installment_agreements / installment_agreement_bills /
--     installment_schedule: o parcelamento de DAD-21 (23b SS3.16), com
--     unit_id e customer_id (titular = devedor), policy_version_id,
--     protocol_id e service_order_id (ADR-0039), included_bills com o
--     valor original, legacy_reference (carga da P2.10) e os CHECKs
--     espelhando os enums do codigo.
--   * regularization_campaigns / campaign_adherences: o programa de
--     regularizacao por ato do ente (CAD-73), com aprovacao dupla
--     (CHECK proponente <> aprovador), ato normativo citado e perda do
--     beneficio.
--   * repactuation_cases: superendividamento (CAD-75; Lei 14.181/2021).
--   * prescription_rules: tabela VERSIONADA da prescricao (RAS-15), com
--     a fonte de cada linha. Linhas sem prazo confirmado nascem com
--     period_years NULL: a consulta devolve UNDETERMINED e os portoes
--     bloqueiam — prazo nao se chuta.
--   * payment_promises / dunning_actions: promessa de pagamento da regua
--     (CAD-100) e os passos executados, base da conversao por passo.
--   * Definicoes de parametros inst.*/dun.*/ai.* em
--     public.parameter_definitions (ON CONFLICT DO NOTHING).
--
-- Dinheiro: NUMERIC(14,2). NENHUM valor de tenant e semeado, exceto as
-- linhas de fabrica da prescricao (dado legal versionado, com fonte) e os
-- padroes de fabrica que o proprio aceite fixa (interruptores desligados;
-- minimo existencial inicial do Decreto 11.567/2023 como padrao da
-- DEFINICAO — o valor vigente do tenant vive em parameter_versions).

-- ========================================================================
-- PARCELAMENTO (DAD-21)
-- ========================================================================

CREATE TABLE IF NOT EXISTS installment_agreements (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    policy_version_id UUID NOT NULL,
    protocol_id UUID NOT NULL,
    service_order_id UUID NOT NULL,
    total_debt NUMERIC(14,2) NOT NULL CHECK (total_debt > 0),
    discount_fine NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (discount_fine >= 0),
    discount_interest NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (discount_interest >= 0),
    discount_correction NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (discount_correction >= 0),
    discount_principal NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (discount_principal >= 0),
    down_payment NUMERIC(14,2) NOT NULL CHECK (down_payment >= 0),
    down_payment_due_date DATE NOT NULL,
    interest_method VARCHAR(10) NOT NULL CHECK (interest_method IN ('PRICE', 'COMPOUND', 'NONE')),
    interest_rate NUMERIC(9,6) NOT NULL DEFAULT 0 CHECK (interest_rate >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_CONFIRMATION' CHECK (status IN
        ('PENDING_CONFIRMATION', 'ACTIVE', 'UNDONE', 'CANCELED', 'REPLACED', 'CONCLUDED')),
    replaces_agreement_id UUID,
    legacy_reference TEXT,
    source VARCHAR(25) NOT NULL DEFAULT 'STANDARD' CHECK (source IN
        ('STANDARD', 'REPACTUATION_PLAN', 'REGULARIZATION_CAMPAIGN')),
    source_reference TEXT,
    statement_template_code TEXT,
    statement_template_version TEXT,
    statement_document_ref TEXT,
    term_template_code TEXT,
    term_template_version TEXT,
    term_document_ref TEXT,
    -- Permissao REGISTRADA p/ entrada abaixo do minimo (D5): quem, quando,
    -- referencia do registro e os minimos dispensados (lista de
    -- DOWN_PAYMENT_AMOUNT / DOWN_PAYMENT_DUE_DATE). Sem permissao, os
    -- quatro nascem NULL/vazio.
    down_payment_waiver_by UUID,
    down_payment_waiver_at TIMESTAMPTZ,
    down_payment_waiver_reference TEXT,
    down_payment_waived_minimums TEXT NOT NULL DEFAULT '',
    signature_level VARCHAR(10) CHECK (signature_level IN ('SIMPLE', 'ADVANCED', 'QUALIFIED')),
    signature_reference TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID,
    updated_by UUID,
    tenant_schema VARCHAR(100)
);
ALTER TABLE installment_agreements ADD CONSTRAINT uq_installment_agreements_tenant_id UNIQUE (tenant_id, id);
CREATE INDEX idx_installment_agreements_unit ON installment_agreements(tenant_id, unit_id);
CREATE INDEX idx_installment_agreements_customer ON installment_agreements(tenant_id, customer_id);
CREATE INDEX idx_installment_agreements_status ON installment_agreements(tenant_id, status);

CREATE TABLE IF NOT EXISTS installment_agreement_bills (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    agreement_id UUID NOT NULL,
    bill_id UUID NOT NULL,
    original_amount NUMERIC(14,2) NOT NULL CHECK (original_amount > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tenant_schema VARCHAR(100),
    CONSTRAINT fk_inst_bills_agreement FOREIGN KEY (tenant_id, agreement_id)
        REFERENCES installment_agreements (tenant_id, id)
);
CREATE INDEX idx_installment_agreement_bills_bill ON installment_agreement_bills(tenant_id, bill_id);

CREATE TABLE IF NOT EXISTS installment_schedule (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    agreement_id UUID NOT NULL,
    installment_number INT NOT NULL CHECK (installment_number >= 1),
    due_date DATE NOT NULL,
    amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tenant_schema VARCHAR(100),
    CONSTRAINT fk_inst_schedule_agreement FOREIGN KEY (tenant_id, agreement_id)
        REFERENCES installment_agreements (tenant_id, id),
    CONSTRAINT uq_inst_schedule_number UNIQUE (tenant_id, agreement_id, installment_number)
);

-- ========================================================================
-- CAMPANHA DE REGULARIZACAO (CAD-73)
-- ========================================================================

CREATE TABLE IF NOT EXISTS regularization_campaigns (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    name TEXT NOT NULL,
    valid_from DATE NOT NULL,
    valid_to DATE NOT NULL CHECK (valid_to >= valid_from),
    normative_act TEXT NOT NULL,
    proposed_by UUID NOT NULL,
    approved_by UUID NOT NULL,
    target_min_overdue_days INT NOT NULL CHECK (target_min_overdue_days >= 0),
    target_max_overdue_days INT NOT NULL CHECK (target_max_overdue_days >= target_min_overdue_days),
    target_categories TEXT[] NOT NULL DEFAULT '{}',
    fine_discount_pct NUMERIC(5,2) NOT NULL CHECK (fine_discount_pct BETWEEN 0 AND 100),
    interest_discount_pct NUMERIC(5,2) NOT NULL CHECK (interest_discount_pct BETWEEN 0 AND 100),
    correction_discount_pct NUMERIC(5,2) NOT NULL CHECK (correction_discount_pct BETWEEN 0 AND 100),
    principal_discount_pct NUMERIC(5,2) NOT NULL CHECK (principal_discount_pct BETWEEN 0 AND 100),
    max_installments INT NOT NULL CHECK (max_installments >= 1),
    adhesion_term_template_code TEXT NOT NULL,
    benefit_lost_on_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID,
    updated_by UUID,
    tenant_schema VARCHAR(100),
    CONSTRAINT chk_campaign_double_approval CHECK (proposed_by <> approved_by)
);
ALTER TABLE regularization_campaigns ADD CONSTRAINT uq_regularization_campaigns_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS campaign_adherences (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    campaign_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    agreement_id UUID NOT NULL,
    adhered_at DATE NOT NULL,
    recovered_amount NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (recovered_amount >= 0),
    defaulted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tenant_schema VARCHAR(100),
    CONSTRAINT fk_campaign_adherences_campaign FOREIGN KEY (tenant_id, campaign_id)
        REFERENCES regularization_campaigns (tenant_id, id),
    CONSTRAINT fk_campaign_adherences_agreement FOREIGN KEY (tenant_id, agreement_id)
        REFERENCES installment_agreements (tenant_id, id)
);

-- ========================================================================
-- SUPERENDIVIDAMENTO / REPACTUACAO (CAD-75)
-- ========================================================================

CREATE TABLE IF NOT EXISTS repactuation_cases (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    process_number TEXT NOT NULL,
    organ TEXT NOT NULL,
    hearing_date DATE NOT NULL,
    deadline DATE NOT NULL,
    homologated_plan_date DATE,
    homologated_plan_ref TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID,
    updated_by UUID,
    tenant_schema VARCHAR(100)
);
CREATE INDEX idx_repactuation_cases_unit ON repactuation_cases(tenant_id, unit_id);

-- ========================================================================
-- PRESCRICAO VERSIONADA (RAS-15) — fonte em cada linha
-- ========================================================================

CREATE TABLE IF NOT EXISTS prescription_rules (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    provider_nature VARCHAR(15) NOT NULL CHECK (provider_nature IN ('PRIVATE_LAW', 'PUBLIC_LAW')),
    claim_type VARCHAR(30) NOT NULL CHECK (claim_type IN
        ('GENERAL', 'TARIFF_EXECUTION', 'UNDUE_PAYMENT_REPETITION', 'AGAINST_PUBLIC_ENTITY', 'ARTICLE_206_UNSPECIFIED')),
    period_years INT CHECK (period_years IS NULL OR period_years >= 1),
    legal_source TEXT NOT NULL,
    requires_legal_analysis BOOLEAN NOT NULL DEFAULT FALSE,
    table_version INT NOT NULL CHECK (table_version >= 1),
    valid_from DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tenant_schema VARCHAR(100)
);
CREATE INDEX idx_prescription_rules_lookup
    ON prescription_rules(tenant_id, provider_nature, claim_type, table_version);

-- Linhas de fabrica: SO com prazo as que decorrem diretamente da fonte.
-- GENERAL: regra residual do art. 205 do CC (10 anos).
-- UNDUE_PAYMENT_REPETITION: Sumula 412 do STJ remete ao prazo do CC (art. 205).
-- TARIFF_EXECUTION (Temas 251 a 254 do STJ) e AGAINST_PUBLIC_ENTITY
-- (Decreto 20.910/1932): SEM prazo ate confirmacao/analise juridica
-- registrada em versao nova da tabela — a consulta devolve UNDETERMINED.
INSERT INTO prescription_rules
    (tenant_id, provider_nature, claim_type, period_years, legal_source,
     requires_legal_analysis, table_version, valid_from)
VALUES
    ('${tenant_id}', 'PRIVATE_LAW', 'GENERAL', 10, 'Codigo Civil, art. 205', FALSE, 1, DATE '2026-01-01'),
    ('${tenant_id}', 'PUBLIC_LAW',  'GENERAL', 10, 'Codigo Civil, art. 205', FALSE, 1, DATE '2026-01-01'),
    ('${tenant_id}', 'PRIVATE_LAW', 'UNDUE_PAYMENT_REPETITION', 10,
        'Sumula 412 do STJ; Codigo Civil, art. 205', FALSE, 1, DATE '2026-01-01'),
    ('${tenant_id}', 'PUBLIC_LAW',  'UNDUE_PAYMENT_REPETITION', 10,
        'Sumula 412 do STJ; Codigo Civil, art. 205', FALSE, 1, DATE '2026-01-01'),
    ('${tenant_id}', 'PRIVATE_LAW', 'TARIFF_EXECUTION', NULL,
        'STJ, Temas 251 a 254 (execucao de tarifa) — prazo a confirmar na fonte', FALSE, 1, DATE '2026-01-01'),
    ('${tenant_id}', 'PUBLIC_LAW',  'TARIFF_EXECUTION', NULL,
        'STJ, Temas 251 a 254 (execucao de tarifa) — prazo a confirmar na fonte', FALSE, 1, DATE '2026-01-01'),
    ('${tenant_id}', 'PUBLIC_LAW',  'AGAINST_PUBLIC_ENTITY', NULL,
        'Decreto 20.910/1932 — somente com analise juridica', TRUE, 1, DATE '2026-01-01'),
    -- Art. 206 do CC: LACUNA DECLARADA (D7) — as specs nomeiam o artigo como
    -- fonte, mas nao fixam inciso nem prazo (o artigo tem prazos por inciso);
    -- a linha nasce SEM prazo e a consulta devolve UNDETERMINED ate o
    -- juridico fixar inciso/prazo em versao nova da tabela.
    ('${tenant_id}', 'PRIVATE_LAW', 'ARTICLE_206_UNSPECIFIED', NULL,
        'Codigo Civil, art. 206 — inciso e prazo especificos nao fixados nas specs (RAS-15): lacuna declarada', FALSE, 1, DATE '2026-01-01'),
    ('${tenant_id}', 'PUBLIC_LAW',  'ARTICLE_206_UNSPECIFIED', NULL,
        'Codigo Civil, art. 206 — inciso e prazo especificos nao fixados nas specs (RAS-15): lacuna declarada', FALSE, 1, DATE '2026-01-01')
ON CONFLICT DO NOTHING;

-- ========================================================================
-- REGUA: PROMESSAS E PASSOS EXECUTADOS (CAD-100)
-- ========================================================================

CREATE TABLE IF NOT EXISTS payment_promises (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    bill_id UUID NOT NULL,
    promised_at DATE NOT NULL,
    promised_for DATE NOT NULL CHECK (promised_for >= promised_at),
    fulfilled BOOLEAN NOT NULL DEFAULT FALSE,
    fulfilled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tenant_schema VARCHAR(100)
);
CREATE INDEX idx_payment_promises_bill ON payment_promises(tenant_id, bill_id);

CREATE TABLE IF NOT EXISTS dunning_actions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    bill_id UUID NOT NULL,
    step_code TEXT NOT NULL,
    channel TEXT NOT NULL,
    template_code TEXT,
    template_version TEXT,
    pix_link TEXT,
    executed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    converted BOOLEAN NOT NULL DEFAULT FALSE,
    ruler_cycle INT NOT NULL DEFAULT 1 CHECK (ruler_cycle >= 1),
    tenant_schema VARCHAR(100),
    -- Passo de mensagem (com modelo) so existe com link Pix.
    CONSTRAINT chk_dunning_message_has_pix CHECK (template_code IS NULL OR (pix_link IS NOT NULL AND pix_link <> ''))
);
CREATE INDEX idx_dunning_actions_step ON dunning_actions(tenant_id, step_code);

-- ========================================================================
-- DEFINICOES DE PARAMETROS inst.*/dun.*/ai.* (ON CONFLICT DO NOTHING)
-- ========================================================================

INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('inst.min_down_payment_pct', 'DECIMAL', 'd', NULL,
     'Perfil de parcelamento do ente (DAD-21)', 'JUR',
     'Percentual minimo da entrada do parcelamento sobre o debito total. Sem valor aceito, nao ha perfil de parcelamento.'),
    ('inst.min_down_payment', 'DECIMAL', 'd', NULL,
     'Perfil de parcelamento do ente (DAD-21)', 'JUR',
     'Valor minimo absoluto da entrada do parcelamento.'),
    ('inst.entry_due_days', 'INT', 'd', NULL,
     'Perfil de parcelamento do ente (DAD-21)', 'JUR',
     'Prazo minimo (dias) para o vencimento da entrada do parcelamento.'),
    ('inst.max_installments', 'INT', 'd', NULL,
     'Perfil de parcelamento do ente (DAD-21)', 'JUR',
     'Numero maximo de parcelas do parcelamento.'),
    ('inst.min_installment', 'DECIMAL', 'd', NULL,
     'Perfil de parcelamento do ente (DAD-21)', 'JUR',
     'Prestacao minima do parcelamento.'),
    ('inst.max_discount_pct', 'DECIMAL', 'd', NULL,
     'Perfil de parcelamento do ente (DAD-21)', 'JUR',
     'Teto do abatimento total no parcelamento (percentual do debito).'),
    ('inst.principal_discount_limit_pct', 'DECIMAL', 'd', NULL,
     'Perfil de parcelamento do ente (DAD-21, CAD-73)', 'JUR',
     'Limite do abatimento do PRINCIPAL no parcelamento e nas campanhas de regularizacao.'),
    ('inst.income_based', 'BOOLEAN', 'd', NULL,
     'CAD-104 (acordo por capacidade de pagamento)', 'JUR',
     'Acordo por capacidade de pagamento para baixa renda fora da tarifa social. Sem valor aceito, a modalidade nao e oferecida (erro explicito).'),
    ('dun.existential_minimum', 'DECIMAL', 'c', '600.00',
     'Decreto 11.567/2023 (alteravel por ato do CMN); Lei 14.181/2021', 'JUR',
     'Minimo existencial: parametro VERSIONADO, nunca constante no codigo. O padrao de fabrica e o valor do Decreto 11.567/2023; a alteracao e por nova versao aceita do parametro.'),
    ('dun.repactuation_effects', 'TABLE', 'd', NULL,
     'Lei 14.181/2021 e o plano homologado (CAD-75, RAS-11)', 'JUR',
     'Efeitos da repactuacao por superendividamento sobre encargos, negativacao, acoes e corte: SUSPEND_CHARGES, SUSPEND_NEGATIVATION, SUSPEND_ACTIONS, SUSPEND_CUTOFF.'),
    ('dun.ruler', 'OBJECT', 'd', NULL,
     'CAD-100 (regua de cobranca)', 'OPE',
     'Regua de cobranca: passos e canais segmentados por tipo de debito, idade, categoria e classe protegida, com link Pix na mensagem.'),
    ('dun.federal_assignment_enabled', 'BOOLEAN', 'd', 'false',
     'MP 1.393/2026 (Desenrola); RAS-11', 'JUR',
     'Cessao de divida a programa federal de renegociacao. Nasce DESLIGADA e, mesmo ligada, so habilita com parecer juridico favoravel registrado.'),
    ('ai.collection_scoring_enabled', 'BOOLEAN', 'd', 'false',
     'GES-24; ADR-0042; LGPD, art. 20', 'JUR',
     'Escore de propensao de atraso: desligado por padrao; quando ligado, SO ordena o contato e adapta canal, horario e oferta — nunca alimenta corte, negativacao, protesto ou tarifa social.')
ON CONFLICT (definition_key) DO NOTHING;

-- ========================================================================
-- ISOLAMENTO (padrao register_tenant_table)
-- ========================================================================

SELECT register_tenant_table('installment_agreements');
SELECT register_tenant_table('installment_agreement_bills');
SELECT register_tenant_table('installment_schedule');
SELECT register_tenant_table('regularization_campaigns');
SELECT register_tenant_table('campaign_adherences');
SELECT register_tenant_table('repactuation_cases');
SELECT register_tenant_table('prescription_rules');
SELECT register_tenant_table('payment_promises');
SELECT register_tenant_table('dunning_actions');

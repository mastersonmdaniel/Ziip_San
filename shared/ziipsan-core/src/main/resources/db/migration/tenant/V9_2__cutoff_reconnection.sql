-- Corte e religacao: regras e bloqueios (P3.4; plano v3.0 SS3, reserva da P3.4).
-- Version: 9.2.0
--
-- Desenho (ADR-0039; RAS-12; G9 de PARAMETROS-DO-TENANT):
--   * A V8_1 (protocolo, P1.36) e a V9_1 (modelo de OS, P1.31) NAO sao
--     tocadas: corte, religacao e fiscalizacao sao OPERACOES do nucleo de
--     OS da V9_1 (service_orders/service_order_targets/efeitos). Esta
--     migration so acrescenta as tabelas proprias da P3.4, ligadas a OS
--     por FK (tenant_id, service_order_id) -> service_orders.
--   * cutoff_notices: o aviso de corte (selecao minima registrada no
--     snapshot de debitos), com a prova de entrega da P3.5 (so DELIVERED
--     e prova; BOUNCED/COMPLAINED contam como nao entregue) e o aviso
--     pos-corte (cut.post_cut_notice_hours).
--   * cutoff_non_generation_reasons: o motivo de nao geracao do aviso,
--     registrado POR UNIDADE (o relatorio e da P3.22; aqui so o registro).
--   * cutoff_orders: o vinculo OS <-> corte, com motivo, nivel de corte,
--     ordem judicial, justificativa e foto da emergencia (CHECK espelha a
--     regra: emergencia exige os dois) e o resultado da revalidacao na
--     execucao (classe a: falhou, a OS e cancelada pelo motor da V9_1).
--   * reconnection_orders: a OS de religacao gerada pelo pagamento, com a
--     condicao de liberacao aplicada, a decisao da taxa (sem prova de
--     entrega do aviso, religacao SEM taxa -- Lei 13.460/2017, art. 6o,
--     paragrafo unico) e o prazo (rec.sla_hours) com o atraso marcado.
--   * cutoff_compensations: a compensacao automatica por corte indevido
--     ou atraso de religacao, pela formula de rec.undue_cut_compensation.
--     Na formula da ARESC (20% da PRIMEIRA fatura apos a religacao) o
--     credito nasce PENDING_BASIS, sem valor inventado, ate a fatura
--     existir.
--   * cutoff_consumption_alerts: ligacao cortada que volta a registrar
--     consumo NAO muda de situacao sozinha (ADR-0039): o alerta liga a
--     unidade a OS de fiscalizacao, e a situacao so muda pelo
--     encerramento dela (NR 11, art. 42, V).
--   * Definicoes de parametros cut.*/rec.* que ainda nao estavam no
--     catalogo global sao registradas em public.parameter_definitions
--     com ON CONFLICT DO NOTHING (precedentes: V4_4 SS7 da P2.5 e V1_13
--     da P2.31). As chaves cut.min_notice_days, cut.max_debt_age_days,
--     cut.forbidden_window, rec.sla_hours e rec.undue_cut_compensation
--     ja foram registradas pela V1_13 e NAO se repetem aqui.
--   * NENHUM valor de tenant e semeado: tetos, formulas e prazos vivem em
--     parameter_versions aceitas (perfis da P2.31 ativados pela P0.19).
--
-- Depende de V9_1 (service_orders), V1_2 (register_tenant_table) e da
-- convencao de schema do tenant do provisionamento.

-- =======================================================================
-- AVISO DE CORTE
-- =======================================================================

CREATE TABLE IF NOT EXISTS cutoff_notices (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    consumer_unit_id UUID NOT NULL,
    -- Data do aviso e primeira data em que o corte pode ocorrer (aviso +
    -- cut.min_notice_days, avancada pelas vedacoes do calendario).
    notice_date DATE NOT NULL,
    earliest_cut_date DATE NOT NULL,
    -- Modelo da P2.30 usado (codigo e versao gravados: documento emitido
    -- nao muda quando o modelo muda -- regra da P2.30).
    template_code TEXT NOT NULL,
    template_version TEXT NOT NULL,
    -- Prova de entrega da P3.5: NONE (nenhuma tentativa concluida),
    -- DELIVERED (prova), NOT_DELIVERED (so SENT/BOUNCED/COMPLAINED).
    delivery_proof_status VARCHAR(20) NOT NULL DEFAULT 'NONE'
        CHECK (delivery_proof_status IN ('NONE', 'DELIVERED', 'NOT_DELIVERED')),
    delivery_channel TEXT,
    delivery_proof_at TIMESTAMPTZ,
    -- Referencia da tentativa/prova na P3.5 (id da outbox/tentativa).
    delivery_attempt_ref TEXT,
    -- Snapshot dos debitos que motivaram o aviso (Json do core).
    debts_snapshot JSONB NOT NULL,
    total_amount NUMERIC(14, 2) NOT NULL CHECK (total_amount >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'ISSUED'
        CHECK (status IN ('ISSUED', 'CONSUMED', 'EXPIRED', 'SUPERSEDED')),
    -- Aviso pos-corte (cut.post_cut_notice_hours): prazo e envio.
    post_cut_notice_due_at TIMESTAMPTZ,
    post_cut_notice_sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (earliest_cut_date >= notice_date),
    CHECK (delivery_proof_status <> 'DELIVERED' OR delivery_proof_at IS NOT NULL)
);
ALTER TABLE cutoff_notices ADD CONSTRAINT uq_cutoff_notices_tenant_id UNIQUE (tenant_id, id);
CREATE INDEX idx_cutoff_notices_unit ON cutoff_notices(tenant_id, consumer_unit_id, status);

-- =======================================================================
-- MOTIVOS DE NAO GERACAO DO AVISO (registro por unidade; relatorio: P3.22)
-- =======================================================================

CREATE TABLE IF NOT EXISTS cutoff_non_generation_reasons (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    consumer_unit_id UUID NOT NULL,
    evaluated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reason_code VARCHAR(40) NOT NULL
        CHECK (reason_code IN ('NO_OVERDUE_DEBT', 'DEBT_TOO_RECENT', 'DEBT_TOO_OLD',
                               'DEBT_BELOW_MINIMUM', 'CONNECTION_NOT_ACTIVE',
                               'PROTECTED_CLASS', 'SOCIAL_TARIFF_PROTECTED',
                               'BILL_UNDER_REVIEW_OR_CONTESTED', 'NOTICE_ALREADY_VALID')),
    detail JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE cutoff_non_generation_reasons ADD CONSTRAINT uq_cutoff_ngr_tenant_id UNIQUE (tenant_id, id);
CREATE INDEX idx_cutoff_ngr_unit ON cutoff_non_generation_reasons(tenant_id, consumer_unit_id, evaluated_at);

-- =======================================================================
-- CORTE (vinculo com a OS do nucleo da V9_1)
-- =======================================================================

CREATE TABLE IF NOT EXISTS cutoff_orders (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    service_order_id UUID NOT NULL,
    consumer_unit_id UUID NOT NULL,
    -- Aviso que sustenta o corte por inadimplencia (obrigatorio nesse
    -- motivo pela regra de negocio; o CHECK abaixo o impoe no banco).
    notice_id UUID,
    reason VARCHAR(30) NOT NULL
        CHECK (reason IN ('DEFAULT', 'FRAUD', 'RISK', 'REQUEST', 'TECHNICAL_EMERGENCY')),
    -- Nivel do corte (cut.level_model): VALVE = cavalete, BRANCH = ramal.
    cut_level VARCHAR(10) CHECK (cut_level IN ('VALVE', 'BRANCH')),
    judicial_order BOOLEAN NOT NULL DEFAULT FALSE,
    judicial_order_ref TEXT,
    -- Emergencia tecnica: justificativa E foto sao obrigatorias (a regra
    -- esta no CHECK; sem os dois, nem o banco aceita a linha).
    emergency_justification TEXT,
    emergency_photo_document_id UUID,
    revalidation_status VARCHAR(10) NOT NULL DEFAULT 'NOT_RUN'
        CHECK (revalidation_status IN ('NOT_RUN', 'PASSED', 'FAILED')),
    revalidation_failure_code VARCHAR(40)
        CHECK (revalidation_failure_code IN ('DEBT_SETTLED', 'BILL_UNDER_REVIEW_OR_CONTESTED',
                                             'TEMPORAL_BAN', 'NOTICE_NOT_DELIVERED',
                                             'NOTICE_PERIOD_NOT_ELAPSED', 'NOTICE_EXPIRED',
                                             'PROTECTED_CLASS')),
    revalidated_at TIMESTAMPTZ,
    executed_at TIMESTAMPTZ,
    -- Corte reconhecido como indevido (dispara a compensacao).
    undue BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (reason <> 'DEFAULT' OR notice_id IS NOT NULL),
    CHECK (reason <> 'TECHNICAL_EMERGENCY'
           OR (emergency_justification IS NOT NULL AND btrim(emergency_justification) <> ''
               AND emergency_photo_document_id IS NOT NULL)),
    CHECK (judicial_order = FALSE OR (judicial_order_ref IS NOT NULL AND btrim(judicial_order_ref) <> '')),
    CHECK ((revalidation_status = 'FAILED') = (revalidation_failure_code IS NOT NULL))
);
ALTER TABLE cutoff_orders ADD CONSTRAINT uq_cutoff_orders_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE cutoff_orders ADD CONSTRAINT fk_cutoff_orders_os
    FOREIGN KEY (tenant_id, service_order_id) REFERENCES service_orders(tenant_id, id);
ALTER TABLE cutoff_orders ADD CONSTRAINT fk_cutoff_orders_notice
    FOREIGN KEY (tenant_id, notice_id) REFERENCES cutoff_notices(tenant_id, id);
CREATE INDEX idx_cutoff_orders_unit ON cutoff_orders(tenant_id, consumer_unit_id);

-- =======================================================================
-- RELIGACAO (OS gerada pelo pagamento)
-- =======================================================================

CREATE TABLE IF NOT EXISTS reconnection_orders (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    service_order_id UUID NOT NULL,
    cutoff_order_id UUID NOT NULL,
    consumer_unit_id UUID NOT NULL,
    release_condition VARCHAR(40) NOT NULL
        CHECK (release_condition IN ('MOTIVATING_DEBT_PAID_OR_AGREED', 'DOWN_PAYMENT_PAID',
                                     'FULL_SETTLEMENT')),
    -- CHARGED: taxa devida. WAIVED_NO_PROOF: aviso sem prova de entrega
    -- (Lei 13.460/2017, art. 6o, p.u.). WAIVED_UNDUE_CUT: corte indevido.
    fee_decision VARCHAR(20) NOT NULL
        CHECK (fee_decision IN ('CHARGED', 'WAIVED_NO_PROOF', 'WAIVED_UNDUE_CUT')),
    urgent BOOLEAN NOT NULL DEFAULT FALSE,
    sla_due_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    late BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (completed_at IS NULL OR late = (completed_at > sla_due_at))
);
ALTER TABLE reconnection_orders ADD CONSTRAINT uq_reconnection_orders_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE reconnection_orders ADD CONSTRAINT fk_reconnection_orders_os
    FOREIGN KEY (tenant_id, service_order_id) REFERENCES service_orders(tenant_id, id);
ALTER TABLE reconnection_orders ADD CONSTRAINT fk_reconnection_orders_cutoff
    FOREIGN KEY (tenant_id, cutoff_order_id) REFERENCES cutoff_orders(tenant_id, id);
CREATE INDEX idx_reconnection_orders_open ON reconnection_orders(tenant_id, sla_due_at)
    WHERE completed_at IS NULL;

-- =======================================================================
-- COMPENSACAO AUTOMATICA (corte indevido ou atraso de religacao)
-- =======================================================================

CREATE TABLE IF NOT EXISTS cutoff_compensations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    consumer_unit_id UUID NOT NULL,
    cutoff_order_id UUID NOT NULL,
    trigger_reason VARCHAR(20) NOT NULL
        CHECK (trigger_reason IN ('UNDUE_CUT', 'LATE_RECONNECTION')),
    formula VARCHAR(60) NOT NULL
        CHECK (formula IN ('GREATER_OF_2X_URGENT_FEE_AND_20PCT_LAST_INVOICE',
                           'GREATER_OF_2X_URGENT_FEE_AND_20PCT_FIRST_INVOICE_AFTER_RECONNECTION',
                           'RECONNECTION_4H_NO_CHARGE', 'MANUAL_CREDIT_WITH_APPROVAL')),
    -- NULL somente em PENDING_BASIS (a base da formula ainda nao existe,
    -- ex.: a primeira fatura apos a religacao). Nunca valor presumido.
    credit_amount NUMERIC(14, 2),
    basis JSONB,
    status VARCHAR(20) NOT NULL
        CHECK (status IN ('PENDING_BASIS', 'PENDING_APPROVAL', 'CREDITED')),
    -- Na formula da ARIS o efeito e prazo, nao dinheiro: religacao em 4 h.
    reconnection_due_at TIMESTAMPTZ,
    approved_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (status <> 'PENDING_BASIS' OR credit_amount IS NULL),
    CHECK (status <> 'CREDITED' OR credit_amount IS NOT NULL OR formula = 'RECONNECTION_4H_NO_CHARGE'),
    CHECK (formula <> 'RECONNECTION_4H_NO_CHARGE' OR reconnection_due_at IS NOT NULL)
);
ALTER TABLE cutoff_compensations ADD CONSTRAINT uq_cutoff_compensations_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE cutoff_compensations ADD CONSTRAINT fk_cutoff_compensations_cutoff
    FOREIGN KEY (tenant_id, cutoff_order_id) REFERENCES cutoff_orders(tenant_id, id);
CREATE INDEX idx_cutoff_compensations_unit ON cutoff_compensations(tenant_id, consumer_unit_id, status);

-- =======================================================================
-- CONSUMO APOS O CORTE -> OS DE FISCALIZACAO (ADR-0039; NR 11, art. 42, V)
-- =======================================================================

CREATE TABLE IF NOT EXISTS cutoff_consumption_alerts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    consumer_unit_id UUID NOT NULL,
    -- Referencia da leitura/registro de consumo que disparou o alerta.
    reading_ref TEXT NOT NULL,
    inspection_order_id UUID NOT NULL,
    detected_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE cutoff_consumption_alerts ADD CONSTRAINT uq_cutoff_alerts_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE cutoff_consumption_alerts ADD CONSTRAINT fk_cutoff_alerts_os
    FOREIGN KEY (tenant_id, inspection_order_id) REFERENCES service_orders(tenant_id, id);
CREATE INDEX idx_cutoff_alerts_unit ON cutoff_consumption_alerts(tenant_id, consumer_unit_id, detected_at);

-- =======================================================================
-- DEFINICOES DE PARAMETROS cut.*/rec.* AINDA FORA DO CATALOGO GLOBAL
-- (classes e padroes de PARAMETROS-DO-TENANT, G9; ON CONFLICT DO NOTHING)
-- =======================================================================

INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('cut.min_overdue_days', 'INT', 'd', NULL,
     'ERI (PARAMETROS-DO-TENANT, G9: ADASA >= 60)', 'JUR',
     'Atraso minimo do debito para selecionar a unidade ao aviso de corte. Sem valor aceito, nenhum minimo se aplica alem do vencimento.'),
    ('cut.min_debt_amount', 'DECIMAL', 'd', NULL,
     'ERI (PARAMETROS-DO-TENANT, G9)', 'JUR',
     'Valor minimo do debito selecionado para gerar o aviso de corte.'),
    ('cut.notice_validity_days', 'INT', 'd', NULL,
     'ERI (PARAMETROS-DO-TENANT, G9: ARSAE-MG 90)', 'JUR',
     'Validade do aviso nao executado: vencida, o corte exige aviso novo.'),
    ('cut.post_cut_notice_hours', 'INT', NULL, NULL,
     'Aceite da P3.4 (plano v3.0); classe nao fixada no PARAMETROS-DO-TENANT -- pendencia declarada', 'OPE',
     'Horas apos o corte para o aviso pos-corte ao usuario. Sem valor aceito, nao ha prazo calculado: erro explicito em vez de prazo presumido.'),
    ('cut.protected_classes', 'TABLE', 'c',
     '["ESSENTIAL_USER", "HEALTH_DEPENDENT", "SOCIAL_TARIFF"]',
     'Lei 11.445/2007, art. 40, SS3o; PARAMETROS-DO-TENANT, G9 (lista minima travada, so acrescenta)',
     'JUR',
     'Classes protegidas do cadastro da P1.3 (CAD-80): usuario essencial, dependente de abastecimento continuo e tarifa social. A lista so acrescenta classe ou amplia protecao.'),
    ('cut.social_tariff_allowed', 'BOOLEAN', 'd', NULL,
     'ERI (PARAMETROS-DO-TENANT, G9: so se a ERI previr)', 'JUR',
     'Corte de beneficiario da tarifa social inadimplente. Sem valor aceito, o corte da classe e vedado.'),
    ('cut.sewer_allowed', 'BOOLEAN', 'd', 'false',
     'Lei 11.445/2007, art. 40, V; PARAMETROS-DO-TENANT, G9', 'JUR',
     'Interrupcao do esgoto por inadimplencia. Padrao de fabrica: nao.'),
    ('cut.reasons', 'TABLE', 'b', NULL,
     'PARAMETROS-DO-TENANT, G9 (classe b; inadimplencia sempre com a regra integral, classe a)',
     'JUR',
     'Motivos de corte e quais seguem as vedacoes temporais. Padrao de fabrica (na regra, nao em linha): todos os motivos com as vedacoes, exceto emergencia tecnica com justificativa e foto. Excluir outro motivo exige aceite formal com norma citada.'),
    ('cut.extra_forbidden_rules', 'TABLE', 'c', NULL,
     'PARAMETROS-DO-TENANT, G9 (so acrescenta as travadas)', 'JUR',
     'Vedacoes adicionais da ERI, somadas as federais e estaduais.'),
    ('cut.level_model', 'TEXT', 'd', '"EVENT_ATTRIBUTE"',
     'PARAMETROS-DO-TENANT (20a Q8)', 'OPE',
     'Situacao de corte com atributo cut_level (cavalete, ramal); supressao como situacao propria.'),
    ('cut.same_user_other_property_debt', 'BOOLEAN', 'b', 'false',
     'PARAMETROS-DO-TENANT (20f SS5, LEG-20)', 'JUR',
     'Condiciona ligacao e religacao a debito do mesmo usuario em outro imovel. Padrao de fabrica: nao.'),
    ('cut.notice_channels', 'LIST_TEXT', 'd', NULL,
     'PARAMETROS-DO-TENANT, G9 (prova obrigatoria, travada)', 'JUR',
     'Meios de aviso de corte aceitos pelo tenant.'),
    ('cut.accepted_proofs', 'LIST_TEXT', 'd', NULL,
     'PARAMETROS-DO-TENANT, G9 (tipos: ERI/G9)', 'JUR',
     'Provas de entrega aceitas; a prova da P3.5 (DELIVERED) e o piso.'),
    ('cut.business_hours', 'OBJECT', 'b', NULL,
     'PARAMETROS-DO-TENANT, G9 (padrao e limites de fabrica a definir no P0 -- pendencia declarada)',
     'JUR',
     'Janela de horario comercial por dia da semana para o corte.'),
    ('rec.auto_order_on_payment', 'BOOLEAN', 'b', 'true',
     'PARAMETROS-DO-TENANT (20d SS5, OS-10; 20c COB-12)', 'OPE',
     'Pagamento gera a OS de religacao automaticamente. Padrao de fabrica: sim.'),
    ('rec.release_condition', 'ENUM', 'b', '"MOTIVATING_DEBT_PAID_OR_AGREED"',
     'PARAMETROS-DO-TENANT, G9 (opcoes: MOTIVATING_DEBT_PAID_OR_AGREED, DOWN_PAYMENT_PAID, FULL_SETTLEMENT)',
     'OPE',
     'Condicao de liberacao da religacao apos o pagamento.'),
    ('rec.urgent_fee', 'DECIMAL', 'd', NULL,
     'ERI (PARAMETROS-DO-TENANT, G9)', 'OPE',
     'Preco da religacao de urgencia; base do 2x da formula de compensacao ARESC/AGIR.')
ON CONFLICT (definition_key) DO NOTHING;

-- =======================================================================
-- ISOLAMENTO E AUDITORIA (padrao V1_2/V9_1)
-- =======================================================================

SELECT register_tenant_table('cutoff_notices');
SELECT register_tenant_table('cutoff_non_generation_reasons');
SELECT register_tenant_table('cutoff_orders');
SELECT register_tenant_table('reconnection_orders');
SELECT register_tenant_table('cutoff_compensations');
SELECT register_tenant_table('cutoff_consumption_alerts');

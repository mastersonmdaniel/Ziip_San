-- Billing Module Migration for Tenant Schema
-- Version: 4.17.0 (P3.28 -- revisao por vazamento oculto e afericao de
-- hidrometro com laudo; ADR-0038; NR 11 art. 38; levantamento 20f
-- LEG-09/LEG-23; parametros em PARAMETROS-DO-TENANT, linhas leak.* e
-- meter.test_*)
-- Description: pedidos de revisao por vazamento ligados a revisao de conta
-- da P2.20 (bill_reviews/BILL_REVIEW) e a OS de campo LEAK_REVIEW, com
-- prazo/ancora fotografados, referencias cobertas e volumes revistos da
-- decisao; afericoes de hidrometro ligadas a OS METER_TEST, com aviso,
-- agendamento e laudo (veredito, erro medido, referencia do documento e
-- decisao de cobranca); registro das chaves leak.* e meter.test_* novas
-- no catalogo global.
--
-- NUMERACAO (sem reserva no plano -- declarado): a linha da P3.28 no
-- plano v3.0 NAO nomeia migration e o mapa do §3 NAO reserva versao para
-- ela (diferente da P2.20, que tinha a V4_8 reservada e a entregou como
-- V4_16 por inviabilidade de ordem). O modulo dono do conteudo e o
-- billing (a revisao parte da BILL_REVIEW e alimenta a retificacao de
-- conta; a afericao grava a decisao sobre a conta), cuja serie tenant vai
-- ate a V4_16 (P2.20). A primeira versao livre da serie e a V4_17,
-- adotada aqui. Nenhum numero ocupado foi reutilizado: V4_1 a V4_16
-- estao todas entregues ou reservadas no §3 (V4_5/V4_7/V4_11 sao do
-- fiscal, mesmo historico de tenant).
--
-- Depende de: V4_1 (bills), V4_16 (bill_reviews, da P2.20) e V9_1
-- (service_orders, da P1.31 -- sem FK, como em bill_reviews: o catalogo
-- de OS vive em outro modulo e os ids sao ligados pela aplicacao).
--
-- O QUE ESTA MIGRATION FAZ
-- 1. leak_reviews: o pedido de vazamento (conta, revisao da P2.20, OS
--    filha, protocolo, prazo com ancora e ultimo dia fotografados,
--    referencias cobertas em JSON, volumes medidos/medias/revistos e a
--    regra efetiva da decisao). No maximo um pedido ABERTO por conta
--    (indice unico parcial, como bill_reviews na V4_16).
-- 2. meter_tests: a afericao (hidrometro por id sem FK -- precedente da
--    V5_7 para ligacao entre modulos; unidade, OS, aviso, agendamento e,
--    com o laudo, veredito/erro/referencia/cobranca por CHECK).
-- 3. Parametros no catalogo global (public.parameter_definitions, mesmo
--    precedente da V4_4 §7 e da V4_16 §8): as chaves leak.* e
--    meter.test_* da P3.28. leak.max_discount_pct_water NAO entra aqui:
--    ja foi registrada pela V1_13 da P2.31 (ON CONFLICT a protegeria de
--    qualquer forma). Os valores de FABRICA dos perfis (70% nas ERIs
--    lidas) vivem nos perfis da P2.31 e nas versoes aceitas do tenant --
--    esta migration registra DEFINICOES, nao valores.
--
-- NENHUM DADO DE NEGOCIO E SEMEADO.

-- ========================================================================
-- 1. PEDIDOS DE REVISAO POR VAZAMENTO OCULTO
-- ========================================================================

CREATE TABLE IF NOT EXISTS leak_reviews (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    bill_id UUID NOT NULL,
    bill_reference_month DATE NOT NULL CHECK (EXTRACT(DAY FROM bill_reference_month) = 1),
    unit_id UUID NOT NULL,
    bill_review_id UUID NOT NULL,
    bill_review_order_id UUID,
    service_order_id UUID,
    protocol_id UUID,
    status VARCHAR(10) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'GRANTED', 'DENIED')),
    claim_on DATE NOT NULL,
    reading_on DATE,
    due_on DATE,
    deadline_anchor VARCHAR(20) NOT NULL CHECK (deadline_anchor IN ('READING_DATE', 'DUE_DATE')),
    deadline_on DATE NOT NULL,
    covered_references JSONB NOT NULL DEFAULT '[]'::jsonb,
    measured_water NUMERIC(14, 3),
    average_water NUMERIC(14, 3),
    measured_sewer NUMERIC(14, 3),
    average_sewer NUMERIC(14, 3),
    revised_water NUMERIC(14, 3),
    revised_sewer NUMERIC(14, 3),
    discount_rule VARCHAR(30),
    max_discount_pct_water NUMERIC(7, 4),
    decided_on DATE,
    note VARCHAR(500) NOT NULL,
    decision_note VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (char_length(btrim(note)) > 0),
    CHECK (deadline_on >= claim_on),
    CHECK (jsonb_typeof(covered_references) = 'array'),
    CHECK ((status = 'OPEN') = (decided_on IS NULL)),
    CHECK (status <> 'GRANTED' OR (revised_water IS NOT NULL AND revised_sewer IS NOT NULL
                                   AND discount_rule IS NOT NULL))
);
ALTER TABLE leak_reviews ADD CONSTRAINT uq_leak_reviews_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE leak_reviews ADD CONSTRAINT fk_leak_reviews_bill
    FOREIGN KEY (tenant_id, bill_reference_month, bill_id) REFERENCES bills(tenant_id, reference_month, id);
ALTER TABLE leak_reviews ADD CONSTRAINT fk_leak_reviews_bill_review
    FOREIGN KEY (tenant_id, bill_review_id) REFERENCES bill_reviews(tenant_id, id);
CREATE UNIQUE INDEX idx_leak_reviews_one_open ON leak_reviews(tenant_id, bill_id)
    WHERE status = 'OPEN';
CREATE INDEX idx_leak_reviews_unit_status ON leak_reviews(tenant_id, unit_id, status);
CREATE INDEX idx_leak_reviews_service_order ON leak_reviews(tenant_id, service_order_id)
    WHERE service_order_id IS NOT NULL;

-- ========================================================================
-- 2. AFERICOES DE HIDROMETRO COM LAUDO
-- ========================================================================

CREATE TABLE IF NOT EXISTS meter_tests (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    meter_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    service_order_id UUID,
    protocol_id UUID,
    status VARCHAR(12) NOT NULL DEFAULT 'REQUESTED' CHECK (status IN ('REQUESTED', 'REPORTED', 'CANCELLED')),
    notice_on DATE NOT NULL,
    scheduled_for DATE NOT NULL,
    tested_on DATE,
    verdict VARCHAR(14) CHECK (verdict IN ('NORMAL', 'IRREGULAR', 'INCONCLUSIVE')),
    measured_error_pct NUMERIC(7, 4),
    report_ref VARCHAR(200),
    chargeable BOOLEAN,
    note VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (char_length(btrim(note)) > 0),
    CHECK (scheduled_for >= notice_on),
    CHECK ((status = 'REPORTED') = (verdict IS NOT NULL)),
    CHECK (status <> 'REPORTED' OR (tested_on IS NOT NULL AND measured_error_pct IS NOT NULL
                                    AND report_ref IS NOT NULL AND chargeable IS NOT NULL))
);
ALTER TABLE meter_tests ADD CONSTRAINT uq_meter_tests_tenant_id UNIQUE (tenant_id, id);
CREATE INDEX idx_meter_tests_meter_status ON meter_tests(tenant_id, meter_id, status);
CREATE INDEX idx_meter_tests_service_order ON meter_tests(tenant_id, service_order_id)
    WHERE service_order_id IS NOT NULL;

-- ========================================================================
-- 3. PARAMETROS NO CATALOGO GLOBAL (public.parameter_definitions)
-- ========================================================================

INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('leak.discount_rule', 'ENUM', 'b', '"MOST_FAVORABLE"',
     'Bifurcacao leak.* de PARAMETROS-DO-TENANT (20f Q4; 20b §5.1)',
     'REG',
     'Regra do desconto por vazamento oculto: AVERAGE_6M (fatura pela media), PCT_OF_EXCESS (percentual sobre o volume acima da media) ou MOST_FAVORABLE (a mais favoravel ao usuario). Sem perfil de ERI, o padrao de fabrica e MOST_FAVORABLE. Regras completas: P3.28.'),
    ('leak.max_discount_pct_sewer', 'PERCENT', 'b', NULL,
     'Bifurcacao leak.* de PARAMETROS-DO-TENANT (20f Q4; 20b §5.1)',
     'REG',
     'Teto do desconto por vazamento oculto sobre o volume de esgoto acima da media, em percentual. So e lido quando leak.sewer_by_average esta desligado e a regra e por percentual; leak.max_discount_pct_water foi registrada pela V1_13 (P2.31).'),
    ('leak.claim_deadline_days', 'INT', 'b', NULL,
     'Bifurcacao leak.* de PARAMETROS-DO-TENANT (20f Q4, §5); notas dos perfis da P2.31',
     'REG',
     'Prazo em dias para reclamar o vazamento, contado da ancora de leak.claim_deadline_anchor. Valores das notas dos perfis P2.31: ARIS 45 dias; ARESC e AGIR 30 dias. Sem padrao de fabrica nesta definicao: ausencia e erro explicito.'),
    ('leak.claim_deadline_anchor', 'ENUM', 'b', NULL,
     'Bifurcacao leak.* de PARAMETROS-DO-TENANT (20f Q4, §5); notas dos perfis da P2.31',
     'REG',
     'Ancora do prazo de reclamacao do vazamento: READING_DATE (da leitura; ARIS) ou DUE_DATE (do vencimento; ARESC e AGIR). Sem padrao de fabrica nesta definicao: ausencia e erro explicito.'),
    ('leak.max_bills_per_12_months', 'INT', 'b', NULL,
     'Bifurcacao leak.* de PARAMETROS-DO-TENANT (20f Q4, §5); notas dos perfis da P2.31',
     'REG',
     'Teto de faturas beneficiadas por vazamento em 12 meses, contadas pelas referencias. Notas dos perfis P2.31: ARESC e AGIR, 2; ARIS nao registra limite. Leitura da P3.28 (pos-revisao M1): a chave e obrigatoria -- ARESC/AGIR entregam o teto e ARIS declara sem teto com nulo JSON explicito na versao do tenant; chave ausente e erro explicito de ativacao, nunca sem teto.'),
    ('leak.bills_limit_mode', 'ENUM', 'b', '"ANY_IN_12_MONTHS"',
     'Bifurcacao leak.* de PARAMETROS-DO-TENANT (20f Q4, §5); notas dos perfis da P2.31',
     'REG',
     'Como o teto de 12 meses conta: ANY_IN_12_MONTHS (quaisquer faturas na janela; ARESC) ou SEQUENTIAL_IN_12_MONTHS (so referencias consecutivas formam o bloco; AGIR). Padrao ANY_IN_12_MONTHS.'),
    ('leak.sewer_by_average', 'BOOLEAN', 'b', NULL,
     'Bifurcacao leak.* de PARAMETROS-DO-TENANT (20f Q4, §5: esgoto pela media)',
     'REG',
     'Ligado: o esgoto da fatura revisada por vazamento e cobrado pela media. Desligado: o esgoto segue o desconto percentual de leak.max_discount_pct_sewer sobre o excesso. Sem padrao de fabrica nesta definicao: ausencia e erro explicito.'),
    ('meter.test_notice_days', 'INT', 'b', NULL,
     'Parametro meter.test_* de PARAMETROS-DO-TENANT (20f Q4, §5)',
     NULL,
     'Antecedencia minima, em dias, entre o aviso ao usuario e a afericao do hidrometro. Sem padrao de fabrica nesta definicao: ausencia e erro explicito.'),
    ('meter.test_charge_only_if_normal', 'BOOLEAN', 'b', 'true',
     'Parametro meter.test_* de PARAMETROS-DO-TENANT (20f Q4, §5)',
     NULL,
     'Ligado (padrao de fabrica): a afericao so e cobrada quando o laudo conclui hidrometro normal. O valor da cobranca e da tabela de precos do tipo de OS (ED-34); esta chave decide apenas se cobra.'),
    ('meter.test_review_months', 'INT', 'b', NULL,
     'Parametro meter.test_* de PARAMETROS-DO-TENANT (20f Q4, §5); NR 11 art. 38',
     NULL,
     'Meses de faturas sujeitos a revisao quando o laudo conclui hidrometro irregular, terminando no mes do ensaio. A revisao corre pela BILL_REVIEW/retificacao da P2.20. Sem padrao de fabrica nesta definicao: ausencia e erro explicito.')
ON CONFLICT (definition_key) DO NOTHING;

-- ========================================================================
-- 4. ISOLAMENTO (RLS/auditoria, padrao register_tenant_table)
-- ========================================================================

SELECT register_tenant_table('leak_reviews');
SELECT register_tenant_table('meter_tests');

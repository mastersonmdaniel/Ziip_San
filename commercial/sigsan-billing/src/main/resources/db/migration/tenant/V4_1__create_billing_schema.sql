-- Billing Module Migration for Tenant Schema
-- Version: 4.1.0
-- Description: Tarifa (com vigencia por data, faixas e regra de tarifa social), conta e seus itens,
-- debitos a cobrar, creditos a realizar e retencao de impostos.
--
-- Depende de: V1_2 (register_tenant_table) e V3_1 (consumer_units, categories, subcategories).
--
-- NENHUM DADO E SEMEADO. Tarifa, faixa, percentual de tarifa social e aliquota de imposto sao politica
-- do prestador, regulada por agencia; qualquer valor padrao inventado seria tratado como dado real.
-- Ver docs/DIARIO-DE-BORDA.md, secao 15.4. A ausencia e erro explicito no motor de calculo.
--
-- Dinheiro: NUMERIC(14,2). Preco do m3 e tarifa minima: NUMERIC(14,4) (a tabela da agencia pode ter
-- mais de 2 casas; o valor da conta e arredondado pelo motor). Percentuais: NUMERIC(7,4) ou (5,2).

-- ========================================================================
-- TARIFA
-- ========================================================================

CREATE TABLE IF NOT EXISTS tariffs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(100) NOT NULL,
    -- BLOCKS: faixas progressivas. Outros modos (formula, bloco direto) nao estao implementados e
    -- por isso nao sao aceitos: um valor desconhecido aqui seria calculado errado em silencio.
    calc_type VARCHAR(20) NOT NULL CHECK (calc_type IN ('BLOCKS')),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE tariffs ADD CONSTRAINT uq_tariffs_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_tariffs_code ON tariffs(tenant_id, code);

-- Versao (vigencia) da tarifa. Intervalo [valid_from, valid_to] em DATAS; valid_to NULL = aberta.
-- A restricao de exclusao impede duas versoes da mesma tarifa vigentes no mesmo dia: para publicar
-- uma nova, feche a anterior (valid_to) antes. Precisa de btree_gist (criada no init do banco).
--
-- social_min_charge_discount_pct: desconto da tarifa social sobre a parcela fixa. NULL = a versao
-- NAO preve tarifa social (o motor recusa economia social nessa versao). Se informado, deve haver
-- ao menos uma faixa em tariff_social_bands; zero e valor valido e deve ser informado de forma explicita.
-- published_on/regulatory_act: rastro do ato da agencia (o controle de antecedencia de publicacao
-- do reajuste nao esta implementado).
CREATE TABLE IF NOT EXISTS tariff_versions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    tariff_id UUID NOT NULL,
    valid_from DATE NOT NULL,
    valid_to DATE,
    social_min_charge_discount_pct NUMERIC(5,2)
        CHECK (social_min_charge_discount_pct IS NULL OR social_min_charge_discount_pct BETWEEN 0 AND 100),
    regulatory_act VARCHAR(200),
    published_on DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);
ALTER TABLE tariff_versions ADD CONSTRAINT uq_tariff_versions_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE tariff_versions ADD CONSTRAINT fk_tariff_version_tariff
    FOREIGN KEY (tenant_id, tariff_id) REFERENCES tariffs(tenant_id, id);
ALTER TABLE tariff_versions ADD CONSTRAINT excl_tariff_versions_no_overlap
    EXCLUDE USING gist (tenant_id WITH =, tariff_id WITH =, daterange(valid_from, valid_to, '[]') WITH &&);

-- Regra por categoria (ou subcategoria). subcategory_id NULL = regra da categoria inteira, usada
-- como reserva para subcategorias sem regra propria.
-- min_consumption = franquia em m3 por economia (0 = tarifa basica, sem franquia); min_charge = parcela fixa.
-- economy_factor: economias equivalentes; NULL = usa as economias reais.
CREATE TABLE IF NOT EXISTS tariff_category_rules (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    version_id UUID NOT NULL,
    category_id UUID NOT NULL,
    subcategory_id UUID,
    min_consumption INT NOT NULL CHECK (min_consumption >= 0),
    min_charge NUMERIC(14,4) NOT NULL CHECK (min_charge >= 0),
    economy_factor NUMERIC(8,4) CHECK (economy_factor IS NULL OR economy_factor > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE tariff_category_rules ADD CONSTRAINT uq_tariff_category_rules_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE tariff_category_rules ADD CONSTRAINT fk_tcr_version
    FOREIGN KEY (tenant_id, version_id) REFERENCES tariff_versions(tenant_id, id);
ALTER TABLE tariff_category_rules ADD CONSTRAINT fk_tcr_category
    FOREIGN KEY (tenant_id, category_id) REFERENCES categories(tenant_id, id);
ALTER TABLE tariff_category_rules ADD CONSTRAINT fk_tcr_subcategory
    FOREIGN KEY (tenant_id, subcategory_id) REFERENCES subcategories(tenant_id, id);
-- Uma regra por (versao, categoria) e uma por (versao, categoria, subcategoria). Dois indices parciais
-- porque NULLs sao distintos num indice unico comum.
CREATE UNIQUE INDEX idx_tcr_category ON tariff_category_rules(tenant_id, version_id, category_id)
    WHERE subcategory_id IS NULL;
CREATE UNIQUE INDEX idx_tcr_subcategory ON tariff_category_rules(tenant_id, version_id, category_id, subcategory_id)
    WHERE subcategory_id IS NOT NULL;

-- Faixas progressivas de uma regra. to_m3 = ultimo m3 da faixa por economia (inclusive); NULL = faixa
-- aberta, e so uma por regra. A ordem crescente de to_m3 e validada pelo motor (CategoryRule) e
-- reforcada pelo indice unico em (regra, to_m3).
CREATE TABLE IF NOT EXISTS tariff_blocks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    rule_id UUID NOT NULL,
    block_order SMALLINT NOT NULL CHECK (block_order >= 1),
    to_m3 INT CHECK (to_m3 IS NULL OR to_m3 > 0),
    price_per_m3 NUMERIC(14,4) NOT NULL CHECK (price_per_m3 >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE tariff_blocks ADD CONSTRAINT fk_tblock_rule
    FOREIGN KEY (tenant_id, rule_id) REFERENCES tariff_category_rules(tenant_id, id);
CREATE UNIQUE INDEX idx_tblock_order ON tariff_blocks(tenant_id, rule_id, block_order);
CREATE UNIQUE INDEX idx_tblock_to ON tariff_blocks(tenant_id, rule_id, to_m3) WHERE to_m3 IS NOT NULL;
CREATE UNIQUE INDEX idx_tblock_open ON tariff_blocks(tenant_id, rule_id) WHERE to_m3 IS NULL;

-- Tarifa social: faixas de volume com desconto, por versao de tarifa. (from_m3, to_m3]: o primeiro m3
-- da faixa e from_m3 + 1; to_m3 NULL = sem limite de volume. TODOS os valores sao parametros da tabela
-- tarifaria do tenant (percentual e limite de volume): nada disto existe no codigo.
-- O enquadramento da economia (CadUnico/BPC etc.) NAO mora aqui: e cadastro e dado sensivel.
CREATE TABLE IF NOT EXISTS tariff_social_bands (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    version_id UUID NOT NULL,
    from_m3 INT NOT NULL CHECK (from_m3 >= 0),
    to_m3 INT,
    discount_pct NUMERIC(5,2) NOT NULL CHECK (discount_pct > 0 AND discount_pct <= 100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (to_m3 IS NULL OR to_m3 > from_m3)
);
ALTER TABLE tariff_social_bands ADD CONSTRAINT fk_tsb_version
    FOREIGN KEY (tenant_id, version_id) REFERENCES tariff_versions(tenant_id, id);
ALTER TABLE tariff_social_bands ADD CONSTRAINT excl_tsb_no_overlap
    EXCLUDE USING gist (tenant_id WITH =, version_id WITH =, int4range(from_m3, to_m3, '(]') WITH &&);

-- ========================================================================
-- RETENCAO DE IMPOSTOS (parametro do tenant, com vigencia)
-- ========================================================================
-- Tributo e aliquota sao do tenant (sem semente). apply_order define a ordem de calculo: o ULTIMO
-- tributo da ordem absorve o residuo de arredondamento.
CREATE TABLE IF NOT EXISTS tax_withholding_rates (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    tax_code VARCHAR(20) NOT NULL,
    rate_pct NUMERIC(7,4) NOT NULL CHECK (rate_pct > 0 AND rate_pct <= 100),
    apply_order SMALLINT NOT NULL CHECK (apply_order >= 1),
    valid_from DATE NOT NULL,
    valid_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);
ALTER TABLE tax_withholding_rates ADD CONSTRAINT excl_tax_rates_no_overlap
    EXCLUDE USING gist (tenant_id WITH =, tax_code WITH =, daterange(valid_from, valid_to, '[]') WITH &&);

-- ========================================================================
-- DEBITOS A COBRAR E CREDITOS A REALIZAR (estado entre meses)
-- ========================================================================
-- Sao o estado que atravessa as contas: quantas parcelas ja foram cobradas e, no credito, o residuo que
-- a conta nao comportou e passa ao mes seguinte.

CREATE TABLE IF NOT EXISTS debit_receivables (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    type_code VARCHAR(40) NOT NULL,
    description VARCHAR(200),
    total_amount NUMERIC(14,2) NOT NULL CHECK (total_amount > 0),
    installments INT NOT NULL CHECK (installments >= 1),
    installments_done INT NOT NULL DEFAULT 0,
    status VARCHAR(12) NOT NULL DEFAULT 'NORMAL' CHECK (status IN ('NORMAL', 'CANCELLED')),
    under_review BOOLEAN NOT NULL DEFAULT FALSE,
    -- indicador "gera conta": se o debito, sozinho, justifica emitir conta. Sem padrao: decisao do tipo de debito.
    generates_bill BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (installments_done BETWEEN 0 AND installments)
);
ALTER TABLE debit_receivables ADD CONSTRAINT uq_debit_receivables_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE debit_receivables ADD CONSTRAINT fk_drec_unit
    FOREIGN KEY (tenant_id, unit_id) REFERENCES consumer_units(tenant_id, id);
CREATE INDEX idx_drec_unit ON debit_receivables(tenant_id, unit_id);

CREATE TABLE IF NOT EXISTS credit_receivables (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    origin VARCHAR(40) NOT NULL,
    description VARCHAR(200),
    total_amount NUMERIC(14,2) NOT NULL CHECK (total_amount > 0),
    installments INT NOT NULL CHECK (installments >= 1),
    installments_done INT NOT NULL DEFAULT 0,
    -- residuo que a conta anterior nao comportou e passa ao mes seguinte
    residual_amount NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (residual_amount >= 0),
    status VARCHAR(12) NOT NULL DEFAULT 'NORMAL' CHECK (status IN ('NORMAL', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (installments_done BETWEEN 0 AND installments)
);
ALTER TABLE credit_receivables ADD CONSTRAINT uq_credit_receivables_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE credit_receivables ADD CONSTRAINT fk_crec_unit
    FOREIGN KEY (tenant_id, unit_id) REFERENCES consumer_units(tenant_id, id);
CREATE INDEX idx_crec_unit ON credit_receivables(tenant_id, unit_id);

-- ========================================================================
-- CONTA
-- ========================================================================
-- reference_month: primeiro dia do mes de referencia (competencia).
--
-- IDEMPOTENCIA: no maximo UMA conta "viva" por (unidade, referencia). Estados vivos = todos menos
-- CANCELLED, CANCELLED_BY_RECTIFICATION e PROCESSING_ERROR (uma retificacao precisa conviver com a
-- conta que ela substitui; uma conta com erro precisa poder ser reprocessada). Reexecutar o
-- faturamento da mesma unidade e referencia viola o indice e nao duplica a conta. A lista de estados
-- vivos e mantida igual a BillStatus.live() por um teste (MigrationConsistencyTest).
--
-- total_value = agua + esgoto + debitos - creditos - impostos: o banco confere a aritmetica da conta.
CREATE TABLE IF NOT EXISTS bills (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    reference_month DATE NOT NULL CHECK (EXTRACT(DAY FROM reference_month) = 1),
    status VARCHAR(30) NOT NULL CHECK (status IN (
        'NORMAL', 'RECTIFIED', 'ADDED', 'CANCELLED', 'CANCELLED_BY_RECTIFICATION', 'INSTALLMENT',
        'CARRIED_FORWARD', 'INSTALLMENT_DOWN_PAYMENT', 'PRESCRIBED_DEBT', 'PRE_BILLED', 'CREDIT_CARD',
        'PROCESSING_ERROR', 'PRESCRIBED_ADDED_BILLS', 'PAID')),
    previous_status VARCHAR(30) CHECK (previous_status IS NULL OR previous_status IN (
        'NORMAL', 'RECTIFIED', 'ADDED', 'CANCELLED', 'CANCELLED_BY_RECTIFICATION', 'INSTALLMENT',
        'CARRIED_FORWARD', 'INSTALLMENT_DOWN_PAYMENT', 'PRESCRIBED_DEBT', 'PRE_BILLED', 'CREDIT_CARD',
        'PROCESSING_ERROR', 'PRESCRIBED_ADDED_BILLS', 'PAID')),
    period_from DATE,
    period_to DATE,
    water_consumption INT CHECK (water_consumption IS NULL OR water_consumption >= 0),
    sewer_volume INT CHECK (sewer_volume IS NULL OR sewer_volume >= 0),
    sewer_pct NUMERIC(7,4),
    collection_pct NUMERIC(7,4),
    water_value NUMERIC(14,2) NOT NULL CHECK (water_value >= 0),
    sewer_value NUMERIC(14,2) NOT NULL CHECK (sewer_value >= 0),
    debits_value NUMERIC(14,2) NOT NULL CHECK (debits_value >= 0),
    credits_value NUMERIC(14,2) NOT NULL CHECK (credits_value >= 0),
    taxes_value NUMERIC(14,2) NOT NULL CHECK (taxes_value >= 0),
    total_value NUMERIC(14,2) NOT NULL CHECK (total_value >= 0),
    due_date DATE,
    original_due_date DATE,
    valid_until DATE,
    issued_on DATE,
    rectification_count INT NOT NULL DEFAULT 0 CHECK (rectification_count >= 0),
    due_change_count INT NOT NULL DEFAULT 0 CHECK (due_change_count >= 0),
    late_fee_charged BOOLEAN NOT NULL DEFAULT FALSE,
    review_reason VARCHAR(100),
    reviewed_on DATE,
    cancel_reason VARCHAR(200),
    origin_bill_id UUID,
    -- fotografia do que foi usado no calculo (versoes de tarifa, parametros de esgoto), para auditar
    -- "por que cobrou X" mesmo depois de a tarifa mudar
    tariff_snapshot JSONB,
    -- marcador de consistencia: a conta so vale depois que itens, debitos e creditos foram gravados
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (period_from IS NULL OR period_to IS NULL OR period_to >= period_from),
    CHECK (total_value = water_value + sewer_value + debits_value - credits_value - taxes_value),
    CHECK (credits_value <= water_value + sewer_value + debits_value)
);
ALTER TABLE bills ADD CONSTRAINT uq_bills_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE bills ADD CONSTRAINT fk_bill_unit
    FOREIGN KEY (tenant_id, unit_id) REFERENCES consumer_units(tenant_id, id);
ALTER TABLE bills ADD CONSTRAINT fk_bill_origin
    FOREIGN KEY (tenant_id, origin_bill_id) REFERENCES bills(tenant_id, id);
CREATE UNIQUE INDEX idx_bills_unit_reference_live ON bills(tenant_id, unit_id, reference_month)
    WHERE status NOT IN ('CANCELLED', 'CANCELLED_BY_RECTIFICATION', 'PROCESSING_ERROR');
CREATE INDEX idx_bills_unit ON bills(tenant_id, unit_id, reference_month DESC);
CREATE INDEX idx_bills_reference ON bills(tenant_id, reference_month, status);

-- Linhas da tarifa (agua e esgoto): por categoria, por versao de tarifa vigente no periodo e por
-- linha (parcela fixa ou faixa). proration_factor = fracao do periodo que a versao responde (1,0000
-- quando ha uma so versao). Reproduz o calculo sem reexecutar o motor.
CREATE TABLE IF NOT EXISTS bill_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    bill_id UUID NOT NULL,
    service_kind VARCHAR(5) NOT NULL CHECK (service_kind IN ('WATER', 'SEWER')),
    category_id UUID NOT NULL,
    subcategory_id UUID,
    tariff_version_id UUID NOT NULL,
    period_from DATE NOT NULL,
    period_to DATE NOT NULL,
    proration_factor NUMERIC(7,4) NOT NULL CHECK (proration_factor > 0 AND proration_factor <= 1),
    line_type VARCHAR(7) NOT NULL CHECK (line_type IN ('MINIMUM', 'BLOCK')),
    economies INT NOT NULL CHECK (economies >= 1),
    social_economies INT NOT NULL DEFAULT 0,
    from_m3 INT NOT NULL CHECK (from_m3 >= 0),
    to_m3 INT,
    price_per_m3 NUMERIC(14,4),
    consumption INT NOT NULL CHECK (consumption >= 0),
    gross_value NUMERIC(14,2) NOT NULL CHECK (gross_value >= 0),
    discount_value NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (discount_value >= 0),
    net_value NUMERIC(14,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (period_to >= period_from),
    CHECK (social_economies BETWEEN 0 AND economies),
    CHECK (net_value = gross_value - discount_value),
    CHECK (discount_value <= gross_value),
    CHECK ((line_type = 'BLOCK') = (price_per_m3 IS NOT NULL))
);
ALTER TABLE bill_items ADD CONSTRAINT fk_bitem_bill
    FOREIGN KEY (tenant_id, bill_id) REFERENCES bills(tenant_id, id);
ALTER TABLE bill_items ADD CONSTRAINT fk_bitem_version
    FOREIGN KEY (tenant_id, tariff_version_id) REFERENCES tariff_versions(tenant_id, id);
ALTER TABLE bill_items ADD CONSTRAINT fk_bitem_category
    FOREIGN KEY (tenant_id, category_id) REFERENCES categories(tenant_id, id);
ALTER TABLE bill_items ADD CONSTRAINT fk_bitem_subcategory
    FOREIGN KEY (tenant_id, subcategory_id) REFERENCES subcategories(tenant_id, id);
CREATE INDEX idx_bitem_bill ON bill_items(tenant_id, bill_id);

-- Parcelas de debito cobradas na conta. Um debito entra uma vez por conta.
CREATE TABLE IF NOT EXISTS bill_debits (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    bill_id UUID NOT NULL,
    receivable_id UUID NOT NULL,
    type_code VARCHAR(40) NOT NULL,
    installment_no INT NOT NULL CHECK (installment_no >= 1),
    installment_total INT NOT NULL CHECK (installment_total >= 1),
    amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (installment_no <= installment_total)
);
ALTER TABLE bill_debits ADD CONSTRAINT fk_bdebit_bill
    FOREIGN KEY (tenant_id, bill_id) REFERENCES bills(tenant_id, id);
ALTER TABLE bill_debits ADD CONSTRAINT fk_bdebit_receivable
    FOREIGN KEY (tenant_id, receivable_id) REFERENCES debit_receivables(tenant_id, id);
CREATE UNIQUE INDEX idx_bdebit_unique ON bill_debits(tenant_id, bill_id, receivable_id);

-- Creditos realizados na conta. installment_no NULL = so se realizou residuo de parcelas ja vencidas.
CREATE TABLE IF NOT EXISTS bill_credits (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    bill_id UUID NOT NULL,
    receivable_id UUID NOT NULL,
    origin VARCHAR(40) NOT NULL,
    installment_no INT CHECK (installment_no IS NULL OR installment_no >= 1),
    applied_amount NUMERIC(14,2) NOT NULL CHECK (applied_amount > 0),
    residual_after NUMERIC(14,2) NOT NULL CHECK (residual_after >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE bill_credits ADD CONSTRAINT fk_bcredit_bill
    FOREIGN KEY (tenant_id, bill_id) REFERENCES bills(tenant_id, id);
ALTER TABLE bill_credits ADD CONSTRAINT fk_bcredit_receivable
    FOREIGN KEY (tenant_id, receivable_id) REFERENCES credit_receivables(tenant_id, id);
CREATE UNIQUE INDEX idx_bcredit_unique ON bill_credits(tenant_id, bill_id, receivable_id);

-- Impostos retidos na conta (so existe se o tenant retem).
CREATE TABLE IF NOT EXISTS bill_taxes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    bill_id UUID NOT NULL,
    tax_code VARCHAR(20) NOT NULL,
    apply_order SMALLINT NOT NULL CHECK (apply_order >= 1),
    rate_pct NUMERIC(7,4) NOT NULL CHECK (rate_pct > 0 AND rate_pct <= 100),
    base_amount NUMERIC(14,2) NOT NULL CHECK (base_amount >= 0),
    amount NUMERIC(14,2) NOT NULL CHECK (amount >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE bill_taxes ADD CONSTRAINT fk_btax_bill
    FOREIGN KEY (tenant_id, bill_id) REFERENCES bills(tenant_id, id);
CREATE UNIQUE INDEX idx_btax_unique ON bill_taxes(tenant_id, bill_id, tax_code);

-- ========================================================================
-- ISOLAMENTO E AUDITORIA
-- ========================================================================

SELECT register_tenant_table('tariffs');
SELECT register_tenant_table('tariff_versions');
SELECT register_tenant_table('tariff_category_rules');
SELECT register_tenant_table('tariff_blocks');
SELECT register_tenant_table('tariff_social_bands');
SELECT register_tenant_table('tax_withholding_rates');
SELECT register_tenant_table('debit_receivables');
SELECT register_tenant_table('credit_receivables');
SELECT register_tenant_table('bills');
SELECT register_tenant_table('bill_items');
SELECT register_tenant_table('bill_debits');
SELECT register_tenant_table('bill_credits');
SELECT register_tenant_table('bill_taxes');

-- Collection Module Migration for Tenant Schema
-- Version: 6.1.0
-- Description: Arrecadacao e cobranca bancaria: convenios (com o layout do codigo de barras como
-- dado), arquivos de retorno, pagamentos e trilha de situacao, indices de correcao, feriados por
-- municipio e politica de acrescimos/tolerancias por tenant.
--
-- NENHUM dado e semeado. Convenios, indices de correcao, feriados e percentuais de multa/juros sao
-- dados do tenant (ou oficiais e com fonte propria), e um valor padrao inventado seria tratado
-- como real. Por isso as colunas de politica NAO tem DEFAULT: ausencia de regra e declarada
-- (interest_mode = 'NONE'), nunca assumida.
--
-- A faixa V6_x e deste modulo. Depende de V1_2 (register_tenant_table) e da funcao
-- current_tenant_id() do init de seguranca. Nao depende de nenhuma tabela de outro modulo:
-- unit_id e document_id de payments sao UUID sem FK (documento polimorfico: fatura, guia, etc.).

-- ========================================================================
-- CONVENIOS
-- ========================================================================

-- Um convenio liga o tenant a um banco para um tipo de documento. barcode_layout descreve, como
-- dado, onde cada informacao esta no codigo de barras (substitui "uma subclasse por empresa");
-- formato em AgreementBarcodeLayout.toJson / BankSlipLayout (campo livre em FreeFieldLayout).
CREATE TABLE IF NOT EXISTS collection_agreements (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    bank_code VARCHAR(3) NOT NULL CHECK (bank_code ~ '^[0-9]{3}$'),
    name VARCHAR(100) NOT NULL,
    -- A.03 do arquivo de retorno: "definido pelo banco", ate 20 posicoes
    agreement_code VARCHAR(20) NOT NULL,
    document_kind VARCHAR(20) NOT NULL CHECK (document_kind IN ('COLLECTION', 'BANK_SLIP')),
    barcode_layout JSONB NOT NULL,
    -- Versoes de layout (A.09) aceitas no retorno, separadas por virgula (ex.: '7,8'). NULL ate o
    -- banco confirmar: o manual FEBRABAN v08 nao informa o valor de A.09 para o layout 08.
    accepted_return_versions VARCHAR(50)
        CHECK (accepted_return_versions IS NULL OR accepted_return_versions ~ '^[0-9]{1,2}(,[0-9]{1,2})*$'),
    -- Ultimo NSA aceito; o NSA evolui de 1 em 1. NULL antes do primeiro arquivo.
    last_return_nsa BIGINT CHECK (last_return_nsa IS NULL OR last_return_nsa >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    valid_from DATE NOT NULL,
    valid_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);
ALTER TABLE collection_agreements ADD CONSTRAINT uq_collection_agreements_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_collection_agreements_key
    ON collection_agreements(tenant_id, bank_code, agreement_code, document_kind, valid_from);

-- ========================================================================
-- ARQUIVOS DE RETORNO
-- ========================================================================

CREATE TABLE IF NOT EXISTS bank_return_files (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    agreement_id UUID NOT NULL,
    layout VARCHAR(30) NOT NULL CHECK (layout IN ('FEBRABAN_150_COLLECTION')),
    file_name VARCHAR(255),
    nsa BIGINT NOT NULL CHECK (nsa >= 0),
    generated_on DATE NOT NULL,
    bank_code VARCHAR(3) NOT NULL CHECK (bank_code ~ '^[0-9]{3}$'),
    convenio_code VARCHAR(20) NOT NULL,
    layout_version INTEGER NOT NULL CHECK (layout_version BETWEEN 0 AND 99),
    -- Z.02 e Z.03 do trailer (total de registros inclui header e trailer)
    record_count INTEGER NOT NULL CHECK (record_count >= 2),
    total_amount NUMERIC(17,2) NOT NULL CHECK (total_amount >= 0),
    sha256 VARCHAR(64) NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    status VARCHAR(20) NOT NULL CHECK (status IN ('RECEIVED', 'ACCEPTED', 'REJECTED')),
    rejection_reason VARCHAR(500),
    -- Onde o arquivo cru (linhas de 150 posicoes) fica guardado como evidencia
    storage_uri VARCHAR(500),
    -- Ocorrencias da validacao (Febraban150Validator), em JSON
    issues JSONB,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (status <> 'REJECTED' OR rejection_reason IS NOT NULL)
);
ALTER TABLE bank_return_files ADD CONSTRAINT uq_bank_return_files_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE bank_return_files ADD CONSTRAINT fk_return_file_agreement
    FOREIGN KEY (tenant_id, agreement_id) REFERENCES collection_agreements(tenant_id, id);
-- O mesmo conteudo e o mesmo NSA nao entram duas vezes, mas um arquivo rejeitado pode ser reenviado.
CREATE UNIQUE INDEX idx_return_files_sha ON bank_return_files(tenant_id, sha256) WHERE status <> 'REJECTED';
CREATE UNIQUE INDEX idx_return_files_nsa ON bank_return_files(tenant_id, agreement_id, layout, nsa)
    WHERE status <> 'REJECTED';

-- ========================================================================
-- PAGAMENTOS
-- ========================================================================

CREATE TABLE IF NOT EXISTS payments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    agreement_id UUID NOT NULL,
    return_file_id UUID,
    amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    -- Excedente ja registrado quando status = TO_WRITE_DOWN (entra no total como abatimento)
    pending_excess NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (pending_excess >= 0),
    -- Excedente a devolver/creditar atribuido a este pagamento pela classificacao
    excess_amount NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (excess_amount >= 0),
    paid_on DATE NOT NULL,
    credited_on DATE,
    -- G.10 (forma de captura: 1-9, a-i, l) e G.12 (forma de pagamento: 1-6)
    capture_form VARCHAR(1),
    payment_form INTEGER CHECK (payment_form IS NULL OR payment_form BETWEEN 1 AND 6),
    barcode VARCHAR(44),
    collecting_branch VARCHAR(8),
    authentication VARCHAR(23),
    line_number INTEGER,
    status VARCHAR(30) NOT NULL CHECK (status IN (
        'CLASSIFIED', 'DUPLICATE', 'DOC_NOT_FOUND', 'EXCESS', 'TO_WRITE_DOWN', 'AMOUNT_MISMATCH',
        'OPEN', 'DUPLICATE_REFUNDED', 'TO_ACCOUNT', 'DOC_PRESCRIBED', 'DOC_PARCELED', 'DOC_CANCELED',
        'DOC_PROCESSING_ERROR', 'CREDIT_RECOVERY_CANCELED', 'CREDIT_RECOVERY_DUPLICATE')),
    previous_status VARCHAR(30),
    identified BOOLEAN NOT NULL,
    -- Documento polimorfico (fatura, guia, ...) e unidade: sem FK de proposito
    document_type VARCHAR(30),
    document_id UUID,
    unit_id UUID,
    adjustment_kind VARCHAR(20) NOT NULL DEFAULT 'NONE'
        CHECK (adjustment_kind IN ('NONE', 'CREDIT_TO_REALIZE', 'DEBIT_TO_CHARGE')),
    adjustment_amount NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (adjustment_amount >= 0),
    classified_at TIMESTAMPTZ,
    -- Chave de idempotencia da importacao (ex.: arquivo + linha): reprocessar nao duplica
    idempotency_key VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (document_type IS NULL OR document_id IS NOT NULL),
    CHECK ((adjustment_kind = 'NONE') = (adjustment_amount = 0))
);
ALTER TABLE payments ADD CONSTRAINT uq_payments_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE payments ADD CONSTRAINT fk_payment_agreement
    FOREIGN KEY (tenant_id, agreement_id) REFERENCES collection_agreements(tenant_id, id);
ALTER TABLE payments ADD CONSTRAINT fk_payment_return_file
    FOREIGN KEY (tenant_id, return_file_id) REFERENCES bank_return_files(tenant_id, id);
CREATE UNIQUE INDEX idx_payments_idempotency ON payments(tenant_id, idempotency_key);
CREATE INDEX idx_payments_document ON payments(tenant_id, document_type, document_id) WHERE document_id IS NOT NULL;
CREATE INDEX idx_payments_status ON payments(tenant_id, status, paid_on);
CREATE INDEX idx_payments_unit ON payments(tenant_id, unit_id) WHERE unit_id IS NOT NULL;

-- Trilha da classificacao: somente insercao
CREATE TABLE IF NOT EXISTS payment_status_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    payment_id UUID NOT NULL,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    reason VARCHAR(300),
    job_run_id UUID,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE payment_status_events ADD CONSTRAINT uq_payment_status_events_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE payment_status_events ADD CONSTRAINT fk_status_event_payment
    FOREIGN KEY (tenant_id, payment_id) REFERENCES payments(tenant_id, id);
CREATE INDEX idx_status_events_payment ON payment_status_events(tenant_id, payment_id, occurred_at);

-- ========================================================================
-- ACRESCIMOS POR IMPONTUALIDADE
-- ========================================================================

-- Fatores mensais de um indice de correcao monetaria, carregados pelo tenant com a fonte
-- (ex.: serie publicada pelo IBGE). A correcao entre dois meses e a razao dos fatores.
CREATE TABLE IF NOT EXISTS surcharge_indexes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    index_code VARCHAR(40) NOT NULL,
    -- AAAAMM
    reference INTEGER NOT NULL CHECK (reference BETWEEN 190001 AND 999912 AND reference % 100 BETWEEN 1 AND 12),
    factor NUMERIC(20,10) NOT NULL CHECK (factor > 0),
    source VARCHAR(300) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE surcharge_indexes ADD CONSTRAINT uq_surcharge_indexes_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_surcharge_indexes_key ON surcharge_indexes(tenant_id, index_code, reference);

-- Politica de acrescimos e de classificacao do tenant, com vigencia e norma-fonte. Sem DEFAULT
-- nas colunas de regra. A trava de multa de 2% (CDC art. 52 par. 1; Lei 9.298/1996) esta aqui
-- tambem, alem do codigo: so um teto proprio JUSTIFICADO a ultrapassa.
-- A nao sobreposicao de vigencias e verificada pela aplicacao (exigiria btree_gist).
CREATE TABLE IF NOT EXISTS collection_policies (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    valid_from DATE NOT NULL,
    valid_to DATE,
    -- Contrato ou resolucao do regulador que fixa estes parametros
    norm_source VARCHAR(300) NOT NULL,
    grace_business_days INTEGER NOT NULL CHECK (grace_business_days >= 0),
    due_date_rolls_to_business_day BOOLEAN NOT NULL,
    fine_percent NUMERIC(5,2) NOT NULL CHECK (fine_percent >= 0),
    fine_cap_percent NUMERIC(5,2) CHECK (fine_cap_percent IS NULL OR fine_cap_percent >= 0),
    fine_cap_justification VARCHAR(300),
    interest_mode VARCHAR(20) NOT NULL CHECK (interest_mode IN ('NONE', 'SIMPLE_PER_DAY')),
    interest_monthly_percent NUMERIC(7,4) CHECK (interest_monthly_percent IS NULL OR interest_monthly_percent >= 0),
    interest_month_basis_days INTEGER,
    interest_cap_percent NUMERIC(7,4) CHECK (interest_cap_percent IS NULL OR interest_cap_percent >= 0),
    correction_mode VARCHAR(20) NOT NULL CHECK (correction_mode IN ('NONE', 'BY_INDEX')),
    correction_index_code VARCHAR(40),
    correction_min_months INTEGER,
    correction_allow_negative BOOLEAN,
    classify_tolerance NUMERIC(14,2) NOT NULL CHECK (classify_tolerance >= 0),
    adjust_tolerance NUMERIC(14,2) NOT NULL,
    duplicate_winner VARCHAR(20) NOT NULL CHECK (duplicate_winner IN ('OLDEST_PAYMENT', 'NEWEST_PAYMENT')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (valid_to IS NULL OR valid_to >= valid_from),
    CHECK (adjust_tolerance >= classify_tolerance),
    CHECK (fine_percent <= COALESCE(fine_cap_percent, 2.00)),
    CHECK (fine_cap_percent IS NULL OR fine_cap_percent <= 2.00
           OR (fine_cap_justification IS NOT NULL AND length(trim(fine_cap_justification)) > 0)),
    CHECK ((interest_mode = 'NONE' AND interest_monthly_percent IS NULL
            AND interest_month_basis_days IS NULL AND interest_cap_percent IS NULL)
        OR (interest_mode = 'SIMPLE_PER_DAY' AND interest_monthly_percent IS NOT NULL
            AND interest_month_basis_days IS NOT NULL AND interest_month_basis_days BETWEEN 1 AND 31)),
    CHECK ((correction_mode = 'NONE' AND correction_index_code IS NULL
            AND correction_min_months IS NULL AND correction_allow_negative IS NULL)
        OR (correction_mode = 'BY_INDEX' AND correction_index_code IS NOT NULL
            AND correction_min_months IS NOT NULL AND correction_min_months >= 0
            AND correction_allow_negative IS NOT NULL))
);
ALTER TABLE collection_policies ADD CONSTRAINT uq_collection_policies_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_collection_policies_from ON collection_policies(tenant_id, valid_from);

-- ========================================================================
-- FERIADOS (calendario de dias uteis por municipio)
-- ========================================================================

CREATE TABLE IF NOT EXISTS holidays (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    holiday_date DATE NOT NULL,
    scope VARCHAR(10) NOT NULL CHECK (scope IN ('NATIONAL', 'STATE', 'MUNICIPAL')),
    state_code VARCHAR(2) CHECK (state_code IS NULL OR state_code ~ '^[A-Z]{2}$'),
    municipality_ibge VARCHAR(7) CHECK (municipality_ibge IS NULL OR municipality_ibge ~ '^[0-9]{7}$'),
    description VARCHAR(150) NOT NULL,
    -- Lei, decreto ou calendario oficial que institui o feriado
    source VARCHAR(300) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK ((scope = 'NATIONAL' AND state_code IS NULL AND municipality_ibge IS NULL)
        OR (scope = 'STATE' AND state_code IS NOT NULL AND municipality_ibge IS NULL)
        OR (scope = 'MUNICIPAL' AND municipality_ibge IS NOT NULL))
);
ALTER TABLE holidays ADD CONSTRAINT uq_holidays_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_holidays_key ON holidays(tenant_id, holiday_date, scope,
    COALESCE(state_code, ''), COALESCE(municipality_ibge, ''));

-- ========================================================================
-- ISOLAMENTO, AUDITORIA E COLUNAS DE ENTIDADE
-- ========================================================================

SELECT register_tenant_table('collection_agreements');
SELECT register_tenant_table('bank_return_files');
SELECT register_tenant_table('payments');
SELECT register_tenant_table('payment_status_events');
SELECT register_tenant_table('surcharge_indexes');
SELECT register_tenant_table('collection_policies');
SELECT register_tenant_table('holidays');

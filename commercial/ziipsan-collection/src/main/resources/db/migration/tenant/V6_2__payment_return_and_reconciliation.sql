-- V6_2 - P3.1 (R5a: retorno e conciliacao N3). Migration RESERVADA a esta tarefa
-- (plano v3.0, secao 3). Modulo: commercial/ziipsan-collection (schema do tenant).
--
-- O que esta migration faz:
--   1. Cria `payments` no estado final da P3.1, SEM as colunas proprias do
--      FEBRABAN 150, e com a referencia de arrecadacao (DAD-19):
--      `collection_month` (dia 1, independente da competencia de faturamento) e
--      `fiscal_document_id` com FK composta para `fiscal_documents` (V4_5,
--      modulo fiscal - a ordem global de versoes garante que ela ja existe).
--   2. Se uma tabela `payments` preexistente for encontrada (CREATE IF NOT EXISTS
--      nao a recria), MOVE o significado das colunas FEBRABAN 150 para fora dela:
--      as colunas sao removidas de `payments` (DROP COLUMN IF EXISTS) e vivem
--      apenas em `return_file_details`. O historico dessas colunas em linhas
--      antigas NAO e migrado por esta migration: nao ha arquivo de retorno
--      associado a linhas pre-V6_2 no modelo atual, e inventar essa associacao
--      seria fabricar dado. Se o tenant tiver linhas assim, a integracao deve
--      tratar a copia antes de aplicar esta migration (registrado no relatorio).
--   3. Cria `return_files` (idempotencia por tenant + NSA + hash do arquivo),
--      `return_file_details` (detalhe FEBRABAN 150), `payment_classifications`
--      (classificacao com aprovacao dupla), `withholding_credits` (credito do
--      tenant da retencao IN RFB 1.234/2012) e `payment_events` (eventos para
--      outras trilhas, ex.: recuperacao de credito da ED-32).
--
-- NENHUM DADO E SEMEADO. Dinheiro: NUMERIC(14,2). Depende de register_tenant_table
-- (V1_2) e de fiscal_documents (V4_5).
--
-- CURA IN PLACE — 10/11/2026, lote D72-V62, ao abrigo da Decisao 72 do dono
-- (Opcao A: programa completo da cadeia do provisionamento limpo; pendencia-mae
-- P-R27-A-03). Este ficheiro PUBLICADO (versao 6.2) foi editado no proprio lugar
-- porque a cadeia limpa aborta NELE: nenhuma migration posterior correria antes
-- da correccao. Tres defeitos curados, sem mudar a intencao declarada acima:
--   (a) a constraint uq_payments_tenant_id, que a V6_1 ja cria, passa a ser
--       acrescentada sob guarda de existencia (antes: 42P07 na cadeia limpa;
--       DROP+ADD esta proibido — a FK fk_status_event_payment da V6_1 depende
--       do indice unico e o DROP falha com 2BP01);
--   (b) a coluna bill_id passa a ser acrescentada (IF NOT EXISTS) ANTES do
--       indice idx_payments_bill que a usa (antes: 42703, porque na cadeia
--       limpa a tabela payments vem da V6_1, sem bill_id);
--   (c) o ADD COLUMN de collection_month foi ANTECIPADO para antes do indice
--       idx_payments_collection_month que o usa (antes: 42703, porque a coluna
--       so era acrescentada depois do indice).
-- OBRIGACAO OPERACIONAL: nas bases vivas onde a versao 6.2 ja esteja aplicada,
-- o Flyway exige repair gerido por base para realinhar o checksum deste
-- ficheiro (programa da Decisao 72; verificacao do historico vivo:
-- P-R27-A-02, do lote irmao da V4_6). Nas bases onde a 6.2 nunca se aplicou,
-- o ficheiro corrigido aplica-se directamente, sem repair.

-- ========================================================================
-- PAYMENTS (estado final; sem colunas FEBRABAN 150)
-- ========================================================================

CREATE TABLE IF NOT EXISTS payments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    bill_id UUID,
    unit_id UUID,
    channel VARCHAR(20) NOT NULL CHECK (channel IN
        ('BOLETO', 'PIX', 'CARTAO', 'DEBITO_AUTOMATICO', 'BALCAO')),
    gross_amount NUMERIC(14,2) NOT NULL CHECK (gross_amount > 0),
    segregated_tax_amount NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (segregated_tax_amount >= 0),
    withheld_amount NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (withheld_amount >= 0),
    net_amount NUMERIC(14,2) NOT NULL CHECK (net_amount >= 0),
    paid_on DATE NOT NULL,
    collection_month DATE NOT NULL
        CHECK (collection_month = DATE_TRUNC('month', collection_month)::date),
    fiscal_document_id UUID,
    payer_document VARCHAR(14),
    payer_public_nature BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'RECEBIDO' CHECK (status IN
        ('RECEBIDO', 'CLASSIFICADO', 'BAIXADO', 'DUPLICIDADE', 'NAO_IDENTIFICADO', 'TRANSFERIDO')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID,
    updated_by UUID,
    tenant_schema VARCHAR(100)
);
-- [CURA D72-V62 (a)] Guarda de existencia sobre a constraint que a V6_1 ja
-- cria: sem guarda, esta linha aborta com 42P07 na cadeia limpa. Forma da casa:
-- a mesma da V6_5 (DO $$ ... pg_constraint ...), schema-aware via conrelid.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conname = 'uq_payments_tenant_id'
                     AND conrelid = 'payments'::regclass) THEN
        ALTER TABLE payments ADD CONSTRAINT uq_payments_tenant_id UNIQUE (tenant_id, id);
    END IF;
END $$;
-- [CURA D72-V62 (b)+(c)] bill_id e collection_month tem de existir ANTES dos
-- indices que os usam: na cadeia limpa, payments vem da V6_1 (sem bill_id e
-- sem collection_month) e o CREATE TABLE acima e no-op.
ALTER TABLE payments ADD COLUMN IF NOT EXISTS bill_id UUID;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS collection_month DATE;
CREATE INDEX idx_payments_bill ON payments(tenant_id, bill_id);
CREATE INDEX idx_payments_collection_month ON payments(tenant_id, collection_month);

-- As colunas do FEBRABAN 150 saem de payments (vivem em return_file_details).
ALTER TABLE payments DROP COLUMN IF EXISTS capture_form;
ALTER TABLE payments DROP COLUMN IF EXISTS payment_form;
ALTER TABLE payments DROP COLUMN IF EXISTS collecting_branch;
ALTER TABLE payments DROP COLUMN IF EXISTS authentication;
ALTER TABLE payments DROP COLUMN IF EXISTS line_number;
ALTER TABLE payments DROP COLUMN IF EXISTS barcode;

-- Referencia de arrecadacao e documento fiscal em tabela preexistente (se houver).
-- [CURA D72-V62 (c)] O ADD COLUMN de collection_month foi ANTECIPADO para antes
-- de idx_payments_collection_month (ver acima); semantica identica (IF NOT EXISTS).
ALTER TABLE payments ADD COLUMN IF NOT EXISTS fiscal_document_id UUID;
ALTER TABLE payments DROP CONSTRAINT IF EXISTS fk_payments_fiscal_document;
ALTER TABLE payments ADD CONSTRAINT fk_payments_fiscal_document
    FOREIGN KEY (tenant_id, fiscal_document_id) REFERENCES fiscal_documents (tenant_id, id);
ALTER TABLE payments DROP CONSTRAINT IF EXISTS chk_payments_collection_month_day1;
ALTER TABLE payments ADD CONSTRAINT chk_payments_collection_month_day1
    CHECK (collection_month IS NULL OR collection_month = DATE_TRUNC('month', collection_month)::date);

-- ========================================================================
-- RETURN FILES + DETAILS (FEBRABAN 150 vive aqui)
-- ========================================================================

CREATE TABLE IF NOT EXISTS return_files (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    nsa VARCHAR(20) NOT NULL,
    file_hash VARCHAR(64) NOT NULL,
    file_name VARCHAR(200),
    receiver_document VARCHAR(14) NOT NULL,
    received_on DATE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PROCESSADO' CHECK (status IN
        ('PROCESSADO', 'REPROCESSADO_SEM_EFEITO')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID,
    updated_by UUID,
    tenant_schema VARCHAR(100)
);
ALTER TABLE return_files ADD CONSTRAINT uq_return_files_tenant_id UNIQUE (tenant_id, id);
-- Idempotencia do retorno: mesmo tenant + NSA + hash nao entra duas vezes.
CREATE UNIQUE INDEX idx_return_files_idempotency ON return_files(tenant_id, nsa, file_hash);

CREATE TABLE IF NOT EXISTS return_file_details (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    return_file_id UUID NOT NULL,
    payment_id UUID,
    line_number INT NOT NULL CHECK (line_number >= 1),
    barcode VARCHAR(60),
    capture_form VARCHAR(30),
    payment_form VARCHAR(30),
    collecting_branch VARCHAR(20),
    authentication VARCHAR(60),
    payer_document VARCHAR(14),
    paid_amount NUMERIC(14,2) NOT NULL CHECK (paid_amount > 0),
    paid_on DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID,
    updated_by UUID,
    tenant_schema VARCHAR(100),
    FOREIGN KEY (tenant_id, return_file_id) REFERENCES return_files (tenant_id, id),
    FOREIGN KEY (tenant_id, payment_id) REFERENCES payments (tenant_id, id)
);
ALTER TABLE return_file_details ADD CONSTRAINT uq_return_file_details_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_return_details_file_line ON return_file_details(tenant_id, return_file_id, line_number);

-- ========================================================================
-- CLASSIFICACOES, CREDITOS DE RETENCAO E EVENTOS
-- ========================================================================

CREATE TABLE IF NOT EXISTS payment_classifications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    payment_id UUID NOT NULL,
    situation VARCHAR(40) NOT NULL CHECK (situation IN
        ('EXACT_SETTLEMENT', 'UNDERPAID_WITHIN_TOLERANCE', 'OVERPAID_WITHIN_TOLERANCE',
         'DIFFERENCE_BEYOND_TOLERANCE', 'DUPLICATE_PAYMENT', 'PRESCRIBED_BILL_PAYMENT',
         'PAYMENT_TRANSFER', 'UNIDENTIFIED')),
    origin VARCHAR(12) NOT NULL CHECK (origin IN ('AUTOMATICA', 'MANUAL')),
    status VARCHAR(12) NOT NULL DEFAULT 'PROPOSTA' CHECK (status IN
        ('PROPOSTA', 'APROVADA', 'REJEITADA', 'APLICADA')),
    effect_type VARCHAR(30) NOT NULL CHECK (effect_type IN
        ('SETTLEMENT', 'NEXT_BILL_DEBIT', 'NEXT_BILL_CREDIT', 'NONE_PENDING_MANUAL',
         'DUPLICATE_REFUND_HANDOFF', 'CREDIT_RECOVERY', 'TRANSFER', 'UNIDENTIFIED_QUEUE')),
    effect_amount NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (effect_amount >= 0),
    target_bill_id UUID,
    service_order_id UUID,
    requested_by UUID NOT NULL,
    first_approver_id UUID,
    second_approver_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID,
    updated_by UUID,
    tenant_schema VARCHAR(100),
    FOREIGN KEY (tenant_id, payment_id) REFERENCES payments (tenant_id, id),
    CHECK (first_approver_id IS NULL OR first_approver_id <> requested_by),
    CHECK (second_approver_id IS NULL OR second_approver_id <> requested_by),
    CHECK (second_approver_id IS NULL OR first_approver_id IS NOT NULL),
    CHECK (second_approver_id IS NULL OR second_approver_id <> first_approver_id)
);
ALTER TABLE payment_classifications ADD CONSTRAINT uq_payment_classifications_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS withholding_credits (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    source_payment_id UUID NOT NULL,
    amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    reason VARCHAR(30) NOT NULL DEFAULT 'IN_RFB_1234_2012' CHECK (reason IN ('IN_RFB_1234_2012')),
    status VARCHAR(12) NOT NULL DEFAULT 'REGISTRADO' CHECK (status IN ('REGISTRADO', 'COMPENSADO')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID,
    updated_by UUID,
    tenant_schema VARCHAR(100),
    FOREIGN KEY (tenant_id, source_payment_id) REFERENCES payments (tenant_id, id)
);
ALTER TABLE withholding_credits ADD CONSTRAINT uq_withholding_credits_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS payment_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    payment_id UUID NOT NULL,
    event_type VARCHAR(40) NOT NULL CHECK (event_type IN ('CREDIT_RECOVERY')),
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID,
    updated_by UUID,
    tenant_schema VARCHAR(100),
    FOREIGN KEY (tenant_id, payment_id) REFERENCES payments (tenant_id, id)
);
ALTER TABLE payment_events ADD CONSTRAINT uq_payment_events_tenant_id UNIQUE (tenant_id, id);

-- ========================================================================
-- ISOLAMENTO (RLS) - mesmo helper das demais migrations do tenant
-- ========================================================================

SELECT register_tenant_table('payments');
SELECT register_tenant_table('return_files');
SELECT register_tenant_table('return_file_details');
SELECT register_tenant_table('payment_classifications');
SELECT register_tenant_table('withholding_credits');
SELECT register_tenant_table('payment_events');

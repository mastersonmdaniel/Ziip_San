-- V6_3 - P3.2 (R5b: Pix Cobranca e boleto hibrido). Migration RESERVADA a esta tarefa
-- (plano v3.0, secao 3). Modulo: commercial/ziipsan-collection (schema do tenant).
--
-- O que esta migration faz:
--   1. Cria `pix_webhook_routes`: o segmento aleatorio da URL do webhook resolve
--      tenant + convenio + PSP; o callback so e aceito se o convenio conferido
--      for o convenio cadastrado da rota.
--   2. Cria `pix_psp_profiles`: a matriz de comportamento por PSP (reenvio, SLA
--      do callback, entrega de certificado, segredo adicional, mecanismo
--      equivalente de confirmacao, Open Finance JSR e Pix por aproximacao),
--      lida pelo hub da P3.37. Linha ausente para um PSP = erro explicito na
--      aplicacao, nunca comportamento presumido.
--   3. Cria `pix_charges`: a cobranca Pix (com ou sem vencimento), ligada ao
--      documento pela referencia de arrecadacao da P3.1 (DAD-19):
--      `collection_month` (dia 1) e `fiscal_document_id`.
--   4. Cria `pix_inbox` PARTICIONADA por mes (`received_at`): a caixa de entrada
--      dos callbacks, idempotente por (tenant, psp, end_to_end_id). O indice
--      unico inclui `received_at` porque o PostgreSQL exige a chave de particao
--      em indice unico de tabela particionada. Correcao pos-revisao (M1): a
--      consulta previa da aplicacao NAO e atomica e esse indice NAO impede duas
--      linhas do mesmo end_to_end_id em particoes distintas; a garantia real
--      e a tabela NAO particionada `pix_inbox_dedup` (PK sem `received_at`),
--      gravada pelo adaptador na mesma transacao da inbox (protocolo na secao
--      da dedup, abaixo). O processador trata o conflito como repetido.
--      Correcao pos-revisao (M3): `route_segment` e anulavel - so CALLBACK tem
--      segmento de rota; a varredura (SWEEP) grava NULL por modelagem, com
--      CHECK garantindo segmento em toda linha CALLBACK. E `decision` nasce
--      'RECEBIDA' (DEFAULT), porque a porta de gravacao nao recebe decisao:
--      quem decide e o updateDecision posterior da regra.
--   5. Cria `hybrid_documents`: o documento hibrido (Pix Cobranca com vencimento
--      + boleto) com baixa unica - um so documento pode ser baixado uma vez.
--   6. Em `payments`: adiciona as colunas do Pix (end_to_end_id, txid,
--      hybrid_document_id) com IF NOT EXISTS e o indice unico parcial do
--      endToEndId. A PARTICAO FISICA de `payments` NAO e feita aqui: a tabela
--      ja nasce nao particionada na V6_1 e e referenciada por FKs de outras
--      tabelas do modulo; converte-la exige rebuild com janela de manutencao
--      (procedimento no relatorio da P3.2). A particao entregue nesta migration
--      e a de `pix_inbox`, que e onde o volume de callbacks cresce.
--
-- NENHUM DADO E SEMEADO. Dinheiro: NUMERIC(14,2). Depende de V6_1/V6_2
-- (payments) e de register_tenant_table (V1_2).

-- ========================================================================
-- ROTAS DE WEBHOOK (segmento aleatorio -> tenant + convenio + PSP)
-- ========================================================================

CREATE TABLE IF NOT EXISTS pix_webhook_routes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    -- Segmento aleatorio e opaco da URL do webhook; nunca sequencial nem derivado de dado.
    -- Entropia (correcao pos-revisao M2): este CHECK garante so o FORMATO. A geracao e
    -- obrigatoriamente por CSPRNG (WebhookSegmentPolicy.generate: SecureRandom, 48
    -- simbolos de um alfabeto de 64 = 288 bits) e o processador aplica o piso de
    -- entropia daquela politica a cada callback; segmento fraco/previsivel nao resolve rota.
    segment VARCHAR(64) NOT NULL CHECK (segment ~ '^[A-Za-z0-9_-]{32,64}$'),
    psp VARCHAR(40) NOT NULL,
    agreement_code VARCHAR(20) NOT NULL,
    receiver_document VARCHAR(14) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID,
    updated_by UUID,
    tenant_schema VARCHAR(100)
);
ALTER TABLE pix_webhook_routes ADD CONSTRAINT uq_pix_webhook_routes_tenant_id UNIQUE (tenant_id, id);
-- O segmento e globalmente unico: e ele que identifica o tenant no callback.
CREATE UNIQUE INDEX idx_pix_webhook_routes_segment ON pix_webhook_routes(segment);

-- ========================================================================
-- MATRIZ DE COMPORTAMENTO POR PSP (lida pelo hub da P3.37)
-- ========================================================================

CREATE TABLE IF NOT EXISTS pix_psp_profiles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    psp VARCHAR(40) NOT NULL,
    -- Politica de reenvio do callback declarada pelo PSP (texto do contrato/manual do PSP).
    callback_resend_policy VARCHAR(200) NOT NULL,
    callback_sla_seconds INT NOT NULL CHECK (callback_sla_seconds > 0),
    -- Como o certificado do PSP (mTLS) e entregue: MTLS_CHAIN, UPLOAD, ACME...
    certificate_delivery VARCHAR(40) NOT NULL,
    supports_additional_secret BOOLEAN NOT NULL DEFAULT FALSE,
    -- Mecanismo equivalente a confirmacao GET /pix/{e2eid}; NULL = nao oferece.
    equivalent_confirmation VARCHAR(80),
    open_finance_jsr BOOLEAN NOT NULL DEFAULT FALSE,
    pix_proximity BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID,
    updated_by UUID,
    tenant_schema VARCHAR(100)
);
ALTER TABLE pix_psp_profiles ADD CONSTRAINT uq_pix_psp_profiles_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_pix_psp_profiles_psp ON pix_psp_profiles(tenant_id, psp);

-- ========================================================================
-- COBRANCAS PIX
-- ========================================================================

CREATE TABLE IF NOT EXISTS pix_charges (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    psp VARCHAR(40) NOT NULL,
    txid VARCHAR(35) NOT NULL CHECK (txid ~ '^[a-zA-Z0-9]{26,35}$'),
    document_type VARCHAR(30),
    document_id UUID,
    unit_id UUID,
    amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    due_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'ATIVA' CHECK (status IN
        ('ATIVA', 'PAGA', 'CANCELADA', 'EXPIRADA', 'REMOVIDA_PELO_PSP')),
    end_to_end_id VARCHAR(32),
    hybrid_document_id UUID,
    -- Referencia de arrecadacao e vinculo fiscal da P3.1 (DAD-19).
    collection_month DATE NOT NULL
        CHECK (collection_month = DATE_TRUNC('month', collection_month)::date),
    fiscal_document_id UUID,
    payer_document VARCHAR(14),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID,
    updated_by UUID,
    tenant_schema VARCHAR(100),
    CHECK (document_type IS NULL OR document_id IS NOT NULL)
);
ALTER TABLE pix_charges ADD CONSTRAINT uq_pix_charges_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_pix_charges_txid ON pix_charges(tenant_id, psp, txid);
CREATE UNIQUE INDEX idx_pix_charges_e2eid ON pix_charges(tenant_id, end_to_end_id)
    WHERE end_to_end_id IS NOT NULL;

-- ========================================================================
-- PIX INBOX (callbacks) - PARTICIONADA POR MES
-- ========================================================================

CREATE TABLE IF NOT EXISTS pix_inbox (
    id UUID NOT NULL DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    psp VARCHAR(40) NOT NULL,
    end_to_end_id VARCHAR(32) NOT NULL,
    txid VARCHAR(35),
    amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    paid_at TIMESTAMPTZ NOT NULL,
    payer_document VARCHAR(14),
    source VARCHAR(12) NOT NULL CHECK (source IN ('CALLBACK', 'SWEEP', 'CONSULTA')),
    -- Anulavel por modelagem (M3): so CALLBACK nasce de rota de webhook. O CHECK
    -- abaixo garante segmento em CALLBACK e admite NULL nas demais origens.
    route_segment VARCHAR(64),
    mtls_verified BOOLEAN NOT NULL,
    secret_verified BOOLEAN,
    confirmation VARCHAR(24) NOT NULL DEFAULT 'NAO_CONFIRMADA' CHECK (confirmation IN
        ('CONFIRMADA_GET_PIX', 'CONFIRMADA_EQUIVALENTE', 'NAO_CONFIRMADA', 'DIVERGENTE')),
    -- Nasce RECEBIDA (M3): a porta de gravacao nao recebe decisao; o DEFAULT cobre
    -- o intervalo ate o updateDecision da regra, sem violar o NOT NULL.
    decision VARCHAR(28) NOT NULL DEFAULT 'RECEBIDA' CHECK (decision IN
        ('RECEBIDA', 'BAIXA', 'REPETIDO_SEM_EFEITO', 'PENDENTE_VINCULO', 'NAO_IDENTIFICADO',
         'DEVOLUCAO_P3_29', 'AGUARDANDO_CONFIRMACAO')),
    payload JSONB NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID,
    updated_by UUID,
    tenant_schema VARCHAR(100),
    CHECK (source <> 'CALLBACK' OR route_segment IS NOT NULL),
    PRIMARY KEY (tenant_id, id, received_at)
) PARTITION BY RANGE (received_at);
-- Particao DEFAULT: nenhum callback e recusado por falta de particao do mes.
CREATE TABLE IF NOT EXISTS pix_inbox_default PARTITION OF pix_inbox DEFAULT;
CREATE UNIQUE INDEX idx_pix_inbox_idempotency
    ON pix_inbox(tenant_id, psp, end_to_end_id, received_at);
CREATE INDEX idx_pix_inbox_txid ON pix_inbox(tenant_id, txid) WHERE txid IS NOT NULL;

-- ========================================================================
-- DEDUP DA INBOX (correcao pos-revisao M1) - NAO particionada de proposito:
-- a PK (tenant_id, psp, end_to_end_id), sem a chave de particao, e a garantia
-- de banco que a pix_inbox particionada nao pode dar. Protocolo do adaptador
-- (fiacao da integracao, contrato em PixInboxStore.saveInbox): na MESMA
-- transacao da gravacao na inbox, gravar primeiro a chave nesta tabela com
-- ON CONFLICT (tenant_id, psp, end_to_end_id) DO NOTHING; se nenhuma linha
-- foi gravada (ou se a gravacao simples acusar unicidade), o callback e
-- duplicado - lancar DuplicateEndToEndIdException e NAO gravar a inbox.
-- Equivalente aceito: lock consultivo transacional por (psp, end_to_end_id)
-- cobrindo consulta + gravacao, dispensando esta tabela por decisao da
-- integracao registrada em diario. inbox_received_at guarda o received_at
-- da linha vencedora, para localizar a particao da inbox original.
-- ========================================================================

CREATE TABLE IF NOT EXISTS pix_inbox_dedup (
    id UUID NOT NULL DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    psp VARCHAR(40) NOT NULL,
    end_to_end_id VARCHAR(32) NOT NULL,
    inbox_received_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID,
    updated_by UUID,
    tenant_schema VARCHAR(100),
    PRIMARY KEY (tenant_id, psp, end_to_end_id),
    UNIQUE (tenant_id, id)
);

-- Cria (se faltar) a particao mensal de pix_inbox do mes de `p_month`.
-- A particao DEFAULT continua existindo; mover linhas entre particoes e
-- manutencao operacional, fora desta migration. ATENCAO (registrado na
-- revisao): se a DEFAULT ja contiver linhas do mes alvo, o PostgreSQL recusa
-- criar a particao - mover essas linhas ANTES e passo operacional do ensaio
-- da integracao, nao desta migration.
CREATE OR REPLACE FUNCTION ensure_pix_inbox_partition(p_month DATE)
RETURNS TEXT AS $$
DECLARE
    v_name TEXT := 'pix_inbox_' || TO_CHAR(p_month, 'YYYY_MM');
    v_from DATE := DATE_TRUNC('month', p_month)::date;
    v_to DATE := (DATE_TRUNC('month', p_month) + INTERVAL '1 month')::date;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_class WHERE relname = v_name) THEN
        EXECUTE FORMAT('CREATE TABLE %I PARTITION OF pix_inbox FOR VALUES FROM (%L) TO (%L)',
                       v_name, v_from, v_to);
    END IF;
    RETURN v_name;
END;
$$ LANGUAGE plpgsql;

-- ========================================================================
-- DOCUMENTO HIBRIDO (Pix Cobranca com vencimento + boleto, Res. BCB 587/2026)
-- ========================================================================

CREATE TABLE IF NOT EXISTS hybrid_documents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    document_type VARCHAR(30) NOT NULL,
    document_id UUID NOT NULL,
    pix_charge_id UUID NOT NULL,
    -- Emissor do boleto: obrigatoriamente o PSP do recebedor.
    slip_issuer_psp VARCHAR(40) NOT NULL,
    slip_kind VARCHAR(16) NOT NULL CHECK (slip_kind IN ('COMUM', 'DINAMICO')),
    slip_barcode VARCHAR(44),
    amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    due_date DATE NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ABERTO' CHECK (status IN
        ('ABERTO', 'BAIXADO_PIX', 'BAIXADO_BOLETO', 'CANCELADO')),
    settled_payment_id UUID,
    collection_month DATE NOT NULL
        CHECK (collection_month = DATE_TRUNC('month', collection_month)::date),
    fiscal_document_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID,
    updated_by UUID,
    tenant_schema VARCHAR(100),
    FOREIGN KEY (tenant_id, pix_charge_id) REFERENCES pix_charges (tenant_id, id),
    -- Baixa unica: um documento so pode ter um documento hibrido aberto/baixado.
    CHECK (status <> 'BAIXADO_PIX' OR settled_payment_id IS NOT NULL),
    CHECK (status <> 'BAIXADO_BOLETO' OR settled_payment_id IS NOT NULL)
);
ALTER TABLE hybrid_documents ADD CONSTRAINT uq_hybrid_documents_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_hybrid_documents_document ON hybrid_documents(tenant_id, document_type, document_id);

-- FK composta da cobranca Pix para o documento hibrido (ciclo resolvido aqui,
-- depois que as duas tabelas existem).
ALTER TABLE pix_charges DROP CONSTRAINT IF EXISTS fk_pix_charges_hybrid_document;
ALTER TABLE pix_charges ADD CONSTRAINT fk_pix_charges_hybrid_document
    FOREIGN KEY (tenant_id, hybrid_document_id) REFERENCES hybrid_documents (tenant_id, id);

-- ========================================================================
-- PAYMENTS: colunas do Pix (aditivo; nao presume o formato da V6_1 ou da V6_2)
-- ========================================================================

ALTER TABLE payments ADD COLUMN IF NOT EXISTS end_to_end_id VARCHAR(32);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS txid VARCHAR(35);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS hybrid_document_id UUID;
CREATE UNIQUE INDEX idx_payments_e2eid ON payments(tenant_id, end_to_end_id)
    WHERE end_to_end_id IS NOT NULL;
CREATE INDEX idx_payments_hybrid ON payments(tenant_id, hybrid_document_id)
    WHERE hybrid_document_id IS NOT NULL;

-- ========================================================================
-- ISOLAMENTO (RLS) - mesmo helper das demais migrations do tenant.
-- pix_inbox particionada: o registro e sobre a tabela-mae.
-- ========================================================================

SELECT register_tenant_table('pix_webhook_routes');
SELECT register_tenant_table('pix_psp_profiles');
SELECT register_tenant_table('pix_charges');
SELECT register_tenant_table('pix_inbox');
SELECT register_tenant_table('pix_inbox_dedup');
SELECT register_tenant_table('hybrid_documents');

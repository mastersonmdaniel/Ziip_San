-- V6_5 - P3.29 (Devolucao de pagamento e fila de pagamentos nao identificados).
-- Migration RESERVADA a esta tarefa (plano v3.0, secao 3). Modulo:
-- commercial/ziipsan-collection (schema do tenant). Conferido no espelho MCP
-- em 07/11/2026: no remoto (HEAD 0fbcd22) so existem V6_1 e V6_6 neste modulo;
-- V6_2 (P3.1) e V6_3 (P3.2) sao pacotes locais; V6_5 esta LIVRE.
--
-- O que esta migration faz:
--   1. Cria `refund_requests`: a devolucao por OS REFUND_REQUEST (P1.31),
--      ligada ao pagamento de origem. Sem pagamento de origem ou com valor
--      divergente a devolucao NAO e recusada em silencio nem executada:
--      nasce PENDENTE com `pending_reason` (MISSING_ORIGIN_PAYMENT ou
--      AMOUNT_DIVERGENCE). O destino fica SOMENTE cifrado
--      (`destination_ciphertext`, FieldCipher do ziipsan-security) com
--      retencao definida em `destination_retention_until`; nenhum dado de
--      destino em claro nesta tabela.
--   2. Cria `refund_executions`: o efeito do encerramento da OS, UMA vez
--      por devolucao (UNIQUE por refund_request_id) e idempotente por OS
--      (UNIQUE por service_order_id): o reenvio da OS nao duplica credito
--      na conta seguinte nem devolucao bancaria.
--   3. Cria `unidentified_payments`: a fila de pagamentos nao identificados
--      e nao classificados, SEM cliente ficticio (nenhuma coluna de cliente;
--      `unit_id`/`document_*` nascem NULOS e so sao preenchidos pela OS
--      interna PAYMENT_IDENTIFICATION, que devolve o pagamento a
--      classificacao da P3.1).
--   4. Amplia `payment_events.event_type` com REFUND_APPLIED e
--      PAYMENT_IDENTIFIED: sao os fatos que o relatorio de devolucoes e
--      pagamentos nao identificados da P3.22 consome (a P3.29 nao faz o
--      relatorio; garante os dados/fatos). O ADD CONSTRAINT e protegido
--      por guarda de existencia (pg_constraint): reexecucao parcial nao
--      duplica a constraint nem falha em silencio.
--
-- Correcoes pos-revisao (ver RELATORIO-P3.29.md):
--   - `source` VARCHAR(40) e `unidentified_payments.status` VARCHAR(40):
--     as larguras originais (24/20) eram MENORES que literais reais
--     ('P3_2_HYBRID_SECOND_PAYMENT' = 26; 'DEVOLVIDO_CLASSIFICACAO' = 23).
--   - `base_amount`/`refund_amount` ANULAVEIS: a devolucao PENDENTE nasce
--     sem valor calculado (sem origem pode nem haver base); o CHECK
--     explicito abaixo impoe valor obrigatorio e > 0 fora de pendencia.
--
-- NENHUM DADO E SEMEADO. Dinheiro: NUMERIC(14,2). Depende de payments
-- (V6_1/V6_2), payment_events (V6_2) e register_tenant_table (V1_2).
-- A FK para service_orders (V9_1, core) NAO e criada: neste modulo o vinculo
-- com a OS e por UUID (mesmo padrao de payment_classifications.service_order_id
-- da V6_2), porque a ordem global de versoes nao garante V9_1 antes de V6_5.

-- ========================================================================
-- DEVOLUCOES (OS REFUND_REQUEST)
-- ========================================================================

CREATE TABLE IF NOT EXISTS refund_requests (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    -- OS REFUND_REQUEST que trata esta devolucao (nucleo de OS da P1.31).
    service_order_id UUID NOT NULL,
    -- Pagamento de origem; NULO somente na pendencia MISSING_ORIGIN_PAYMENT.
    origin_payment_id UUID,
    refund_type VARCHAR(24) NOT NULL CHECK (refund_type IN
        ('DUPLICATE_PAYMENT', 'UNDUE_CHARGE')),
    -- Origem do pedido: classificacao da P3.1, segundo pagamento da cobranca
    -- hibrida da P3.2 (RAS-19) ou pedido manual.
    source VARCHAR(40) NOT NULL CHECK (source IN
        ('P3_1_CLASSIFICATION', 'P3_2_HYBRID_SECOND_PAYMENT', 'MANUAL_REQUEST')),
    -- Base da devolucao (valor cobrado/pago que fundamenta o calculo) e valor
    -- final calculado (com o dobro da cobranca indevida, quando devido).
    -- ANULAVEIS de proposito: a devolucao PENDENTE nasce sem calculo (sem
    -- pagamento de origem pode nem existir base). Quem impoe valor fora de
    -- pendencia e o CHECK explicito no fim da tabela - nao um NOT NULL seco,
    -- que tornaria a pendencia impersistivel.
    base_amount NUMERIC(14,2),
    refund_amount NUMERIC(14,2),
    double_applied BOOLEAN NOT NULL DEFAULT FALSE,
    -- Justificativa que AFASTA o dobro da cobranca indevida: obrigatoria
    -- quando ha cobranca indevida, o dobro esta ligado e ele nao foi aplicado.
    double_waiver_justification VARCHAR(500),
    double_waiver_norm_citation VARCHAR(300),
    -- Modo de encerramento (pay.refund_mode): USER_CHOICE exige a escolha
    -- registrada em chosen_mode; os demais fixam o efeito.
    refund_mode VARCHAR(20) NOT NULL CHECK (refund_mode IN
        ('USER_CHOICE', 'CREDIT_NEXT_BILL', 'BANK_TRANSFER')),
    chosen_mode VARCHAR(20) CHECK (chosen_mode IN ('CREDIT_NEXT_BILL', 'BANK_TRANSFER')),
    -- Destino (pay.refund_destination, classe b). So faz sentido com
    -- BANK_TRANSFER; com CREDIT_NEXT_BILL o destino e a conta seguinte.
    refund_destination VARCHAR(24) CHECK (refund_destination IN
        ('PIX_RETURN_TO_PAYER', 'PIX_KEY', 'BANK_ACCOUNT')),
    -- Dado de destino SOMENTE cifrado (FieldCipher, contexto
    -- 'refunds.destination') e retencao definida pela agenda do tenant.
    destination_ciphertext TEXT,
    destination_retention_until DATE,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDENTE' CHECK (status IN
        ('PENDENTE', 'AGUARDANDO_APROVACAO', 'APROVADA', 'ENCERRADA', 'RECUSADA')),
    -- Motivo da pendencia; obrigatorio quando PENDENTE.
    pending_reason VARCHAR(28) CHECK (pending_reason IN
        ('MISSING_ORIGIN_PAYMENT', 'AMOUNT_DIVERGENCE')),
    requested_by UUID NOT NULL,
    first_approver_id UUID,
    second_approver_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID,
    updated_by UUID,
    tenant_schema VARCHAR(100),
    FOREIGN KEY (tenant_id, origin_payment_id) REFERENCES payments (tenant_id, id),
    -- Valores da devolucao: em PENDENCIA podem ser NULOS (nada foi
    -- calculado); fora de pendencia sao OBRIGATORIOS e > 0. Forma explicita:
    -- uma devolucao calculada (AGUARDANDO_APROVACAO em diante) sem valor
    -- e recusada por este CHECK.
    CHECK ((status = 'PENDENTE'
            AND (base_amount IS NULL OR base_amount > 0)
            AND (refund_amount IS NULL OR refund_amount > 0))
           OR (status <> 'PENDENTE'
            AND base_amount IS NOT NULL AND base_amount > 0
            AND refund_amount IS NOT NULL AND refund_amount > 0)),
    CHECK (status <> 'PENDENTE' OR pending_reason IS NOT NULL),
    CHECK (pending_reason IS NULL OR status = 'PENDENTE'),
    CHECK (pending_reason <> 'MISSING_ORIGIN_PAYMENT' OR origin_payment_id IS NULL),
    -- O afastamento do dobro NAO vira CHECK aqui de proposito: o banco nao
    -- conhece o estado de bill.undue_charge_double_refund (com o dobro
    -- DESLIGADO pelo parametro, double_applied=falso e legitimo sem
    -- justificativa). Quem exige a justificativa ao afastar o dobro LIGADO
    -- e a regra (RefundAmountCalculator.requireWaiverWhenDoubleOmitted).
    CHECK (double_waiver_justification IS NULL OR refund_type = 'UNDUE_CHARGE'),
    CHECK (destination_ciphertext IS NULL OR destination_retention_until IS NOT NULL),
    CHECK (first_approver_id IS NULL OR first_approver_id <> requested_by),
    CHECK (second_approver_id IS NULL OR second_approver_id <> requested_by),
    CHECK (second_approver_id IS NULL OR first_approver_id IS NOT NULL),
    CHECK (second_approver_id IS NULL OR second_approver_id <> first_approver_id)
);
ALTER TABLE refund_requests ADD CONSTRAINT uq_refund_requests_tenant_id UNIQUE (tenant_id, id);
-- Uma OS REFUND_REQUEST trata uma devolucao: reenvio da OS nao cria outra.
CREATE UNIQUE INDEX idx_refund_requests_os ON refund_requests(tenant_id, service_order_id);
CREATE INDEX idx_refund_requests_origin ON refund_requests(tenant_id, origin_payment_id)
    WHERE origin_payment_id IS NOT NULL;
CREATE INDEX idx_refund_requests_status ON refund_requests(tenant_id, status);

-- ========================================================================
-- EXECUCAO DA DEVOLUCAO (efeito do encerramento, uma unica vez)
-- ========================================================================

CREATE TABLE IF NOT EXISTS refund_executions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    refund_request_id UUID NOT NULL,
    -- OS cujo encerramento produziu o efeito (chave de idempotencia).
    service_order_id UUID NOT NULL,
    effect VARCHAR(24) NOT NULL CHECK (effect IN ('NEXT_BILL_CREDIT', 'BANK_REFUND')),
    amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    -- Conta seguinte creditada (efeito NEXT_BILL_CREDIT); nulo na devolucao bancaria.
    credited_bill_id UUID,
    -- Referencia devolvida pelo PSP/banco na devolucao bancaria; nulo no credito.
    bank_reference VARCHAR(80),
    executed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID,
    updated_by UUID,
    tenant_schema VARCHAR(100),
    FOREIGN KEY (tenant_id, refund_request_id) REFERENCES refund_requests (tenant_id, id),
    CHECK ((effect = 'NEXT_BILL_CREDIT') = (credited_bill_id IS NOT NULL)),
    CHECK ((effect = 'BANK_REFUND') = (bank_reference IS NOT NULL))
);
ALTER TABLE refund_executions ADD CONSTRAINT uq_refund_executions_tenant_id UNIQUE (tenant_id, id);
-- No maximo UMA execucao por devolucao e UMA por encerramento de OS.
CREATE UNIQUE INDEX idx_refund_executions_request ON refund_executions(tenant_id, refund_request_id);
CREATE UNIQUE INDEX idx_refund_executions_os ON refund_executions(tenant_id, service_order_id);

-- ========================================================================
-- FILA DE PAGAMENTOS NAO IDENTIFICADOS (sem cliente ficticio)
-- ========================================================================

CREATE TABLE IF NOT EXISTS unidentified_payments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    payment_id UUID NOT NULL,
    -- Motivo da entrada na fila: nao identificado na classificacao da P3.1
    -- ou Pix sem txid recuperado pela varredura da P3.2.
    queue_reason VARCHAR(28) NOT NULL CHECK (queue_reason IN
        ('UNIDENTIFIED_CLASSIFICATION', 'PIX_WITHOUT_TXID')),
    status VARCHAR(40) NOT NULL DEFAULT 'NA_FILA' CHECK (status IN
        ('NA_FILA', 'EM_IDENTIFICACAO', 'IDENTIFICADO', 'DEVOLVIDO_CLASSIFICACAO')),
    -- Vinculo produzido pela OS PAYMENT_IDENTIFICATION; NULO ate la.
    -- NENHUMA coluna de cliente: a fila nunca inventa um titular.
    unit_id UUID,
    document_type VARCHAR(30),
    document_id UUID,
    identification_service_order_id UUID,
    identified_by UUID,
    identified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID,
    updated_by UUID,
    tenant_schema VARCHAR(100),
    FOREIGN KEY (tenant_id, payment_id) REFERENCES payments (tenant_id, id),
    CHECK (document_type IS NULL OR document_id IS NOT NULL),
    CHECK (status NOT IN ('IDENTIFICADO', 'DEVOLVIDO_CLASSIFICACAO')
           OR (unit_id IS NOT NULL AND document_id IS NOT NULL
               AND identification_service_order_id IS NOT NULL))
);
ALTER TABLE unidentified_payments ADD CONSTRAINT uq_unidentified_payments_tenant_id UNIQUE (tenant_id, id);
-- Um pagamento entra na fila uma unica vez.
CREATE UNIQUE INDEX idx_unidentified_payments_payment ON unidentified_payments(tenant_id, payment_id);
CREATE INDEX idx_unidentified_payments_status ON unidentified_payments(tenant_id, status);

-- ========================================================================
-- FATOS PARA O RELATORIO DA P3.22 (payment_events da V6_2)
-- ========================================================================

ALTER TABLE payment_events DROP CONSTRAINT IF EXISTS payment_events_event_type_check;
-- Guarda de existencia: se uma execucao parcial ja recriou a constraint
-- (ou o DROP acima nao a removeu por divergencia de nome), o bloco nao
-- duplica a constraint nem aborta a migration em silencio.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conname = 'payment_events_event_type_check') THEN
        ALTER TABLE payment_events ADD CONSTRAINT payment_events_event_type_check
            CHECK (event_type IN ('CREDIT_RECOVERY', 'REFUND_APPLIED', 'PAYMENT_IDENTIFIED'));
    END IF;
END $$;

-- ========================================================================
-- ISOLAMENTO (RLS) - mesmo helper das demais migrations do tenant
-- ========================================================================

SELECT register_tenant_table('refund_requests');
SELECT register_tenant_table('refund_executions');
SELECT register_tenant_table('unidentified_payments');

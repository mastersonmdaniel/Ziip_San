-- ED-24 Controle de fraudes e fiscalizacao — SQL PENDENTE DE NUMERACAO FLYWAY.
-- Nenhum numero foi confirmado livre para esta frente na historia tenant do
-- modulo shared/ziipsan-core (a main tem V8_1, V9_1, V9_3, V21_1, V21_2 e
-- pacotes locais pendentes disputam slots); NAO inventar numero: na
-- integracao, atribuir o proximo slot livre da historia tenant do core,
-- renomear o arquivo e conferir as FKs contra V9_1 (service_orders /
-- service_types / service_order_targets) e o cadastro da P1.3
-- (consumer_units / unit_alternative_sources).
-- Escopo: SO o que a ED-24 cria. OS continua na V9_1 (nenhum modelo
-- redefinido); fotos e boletim sao documentos da P1.20 (aqui so os ids);
-- o documento do auto e gerado pelo motor de modelos da P2.30 (aqui so o
-- id do documento e o tipo INFRACTION_NOTICE); o debito e o recebivel de
-- fonte INFRACTION do faturamento (P2.5) — nenhuma tabela de debito aqui.
-- FORA desta entrega (aceite): cruzamento com cadastro imobiliario
-- municipal e analitica de fraude/perdas aparentes (ED-95) — nenhuma
-- tabela deles aqui.

-- Registro de fraude: tipo, fotos e boletim obrigatorios (aceite ED-24).
-- [A DEFINIR] fraud_type e o catalogo PROPOSTO em FraudRecord.FraudType;
-- conferir o catalogo definitivo do tenant/ERI na integracao.
CREATE TABLE IF NOT EXISTS fraud_records (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    consumer_unit_id UUID NOT NULL,
    inspection_order_id UUID NOT NULL,
    fraud_type VARCHAR(40) NOT NULL CHECK (fraud_type IN
        ('BYPASS', 'CLANDESTINE_CONNECTION', 'METER_TAMPERING',
         'UNAUTHORIZED_RECONNECTION', 'OTHER')),
    description TEXT NOT NULL,
    photo_document_ids UUID[] NOT NULL CHECK (array_length(photo_document_ids, 1) >= 1),
    police_report_number VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('SUSPECTED', 'CONFIRMED', 'DISMISSED')),
    registered_by UUID NOT NULL,
    registered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (tenant_id, inspection_order_id) REFERENCES service_orders(tenant_id, id)
);
ALTER TABLE fraud_records ADD CONSTRAINT uq_fraud_records_tenant_id UNIQUE (tenant_id, id);
CREATE INDEX idx_fraud_records_unit ON fraud_records(tenant_id, consumer_unit_id, registered_at);

-- Auto de infracao emitido pela OS INFRACTION_NOTICE.
-- appeal_deadline e derivado (issued_at + infraction.appeal_days) e gravado
-- para a consulta nao recalcular prazo com parametro que pode mudar.
-- charge_timing grava o valor resolvido de infraction.charge_timing na
-- emissao: mudanca posterior do parametro nao muda auto ja emitido.
CREATE TABLE IF NOT EXISTS infraction_notices (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    fraud_record_id UUID NOT NULL,
    consumer_unit_id UUID NOT NULL,
    amount NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    issued_at TIMESTAMPTZ NOT NULL,
    appeal_days INTEGER NOT NULL CHECK (appeal_days > 0),
    appeal_deadline DATE NOT NULL,
    charge_timing VARCHAR(40) NOT NULL CHECK (charge_timing IN
        ('AFTER_DECISION', 'ON_ISSUE_SUSPENDED_BY_APPEAL')),
    status VARCHAR(20) NOT NULL CHECK (status IN
        ('ISSUED', 'UNDER_APPEAL', 'DECIDED_UPHELD', 'DECIDED_ANNULLED')),
    debit_state VARCHAR(30) NOT NULL CHECK (debit_state IN
        ('NOT_LAUNCHED', 'LAUNCHED', 'SUSPENDED_BY_APPEAL',
         'CANCELLED_BY_ANNULMENT', 'NEVER_LAUNCHED_ANNULLED')),
    document_id UUID,
    document_type VARCHAR(40) NOT NULL DEFAULT 'INFRACTION_NOTICE',
    notice_order_id UUID,
    appeal_order_id UUID,
    decided_by UUID,
    decided_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (tenant_id, fraud_record_id) REFERENCES fraud_records(tenant_id, id),
    FOREIGN KEY (tenant_id, notice_order_id) REFERENCES service_orders(tenant_id, id),
    FOREIGN KEY (tenant_id, appeal_order_id) REFERENCES service_orders(tenant_id, id)
);
ALTER TABLE infraction_notices ADD CONSTRAINT uq_infraction_notices_tenant_id UNIQUE (tenant_id, id);
CREATE INDEX idx_infraction_notices_unit ON infraction_notices(tenant_id, consumer_unit_id, issued_at);
CREATE INDEX idx_infraction_notices_deadline ON infraction_notices(tenant_id, status, appeal_deadline);

-- Hits de criterio que geraram (ou foram anexados a) OS de fiscalizacao:
-- a trilha criterio -> OS que o relatorio e a auditoria consultam.
CREATE TABLE IF NOT EXISTS inspection_criterion_hits (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    consumer_unit_id UUID NOT NULL,
    criterion VARCHAR(40) NOT NULL CHECK (criterion IN
        ('CONSUMPTION_WHILE_CUT', 'ALTERNATIVE_SOURCE_DECLARED',
         'ALTERNATIVE_SOURCE_UNAUTHORIZED', 'EXTERNAL_REPORT', 'FRAUD_RECURRENCE')),
    evidence_summary TEXT NOT NULL,
    detected_at TIMESTAMPTZ NOT NULL,
    inspection_order_id UUID NOT NULL,
    created_order BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (tenant_id, inspection_order_id) REFERENCES service_orders(tenant_id, id)
);
ALTER TABLE inspection_criterion_hits ADD CONSTRAINT uq_inspection_criterion_hits_tenant_id UNIQUE (tenant_id, id);
CREATE INDEX idx_inspection_hits_unit ON inspection_criterion_hits(tenant_id, consumer_unit_id, detected_at);

-- Parametros: infraction.appeal_days, infraction.charge_timing e
-- infraction.max_retroactive_months JA estao no catalogo
-- PARAMETROS-DO-TENANT (20b 5.1, FAT-25, consumidor ED-24) — nenhuma
-- definition nova aqui. [A DEFINIR] chave de limiares dos criterios de
-- fiscalizacao nao existe no catalogo; registrar na integracao se o dono
-- decidir parametrizar janela/limiar do consumo em ligacao cortada.

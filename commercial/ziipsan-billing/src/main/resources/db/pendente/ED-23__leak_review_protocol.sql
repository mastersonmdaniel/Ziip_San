-- ED-23 -- Protocolo da revisao de consumo por vazamento (fases, vistoria, laudo).
-- SQL SEM NUMERO Flyway (regra 4 da cascata): a numeracao global e da
-- sequencia da P3.12 (V100+; ultimo usado V101) e so e lida da main ao
-- pousar. Este arquivo fica em db/pendente/ ate la -- nenhum numero
-- foi inventado.
--
-- Desenho: o protocolo se ANEXA ao pedido da P3.28 (leak_reviews, V4_17)
-- e a OS LEAK_REVIEW dela; a retificacao continua sendo da P2.20
-- (bill_maintenance_operations, V4_16). Nenhuma tabela de desconto,
-- credito ou versao de conta e criada aqui.
-- Assinaturas: os recibos guardam o codigo verificador e o nivel
-- aplicado devolvidos pela P3.34 (document_signatures, V1_14, e a
-- fonte da assinatura; aqui fica so a referencia fotografada).

CREATE TABLE leak_review_protocols (
    id                        uuid PRIMARY KEY,
    tenant_id                 uuid NOT NULL,
    bill_id                   uuid NOT NULL,
    leak_review_id            uuid NOT NULL REFERENCES leak_reviews (id),
    leak_service_order_id     uuid NOT NULL,
    phase                     varchar(20) NOT NULL DEFAULT 'OPENED'
        CHECK (phase IN ('OPENED','INSPECTION','REPORT','DECISION','RECTIFICATION',
                         'CLOSED_GRANTED','CLOSED_DENIED','CANCELLED')),
    decision_signature_code   varchar(80),
    decision_signature_level  varchar(20),
    rectification_operation_id uuid,
    result_bill_id            uuid,
    created_at                timestamptz NOT NULL DEFAULT now(),
    updated_at                timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_leak_protocol_tenant_bill
    ON leak_review_protocols (tenant_id, bill_id);

-- No maximo um protocolo nao final por pedido da P3.28.
CREATE UNIQUE INDEX uq_leak_protocol_open_per_review
    ON leak_review_protocols (tenant_id, leak_review_id)
    WHERE phase NOT IN ('CLOSED_GRANTED','CLOSED_DENIED','CANCELLED');

CREATE TABLE leak_review_phase_events (
    id           uuid PRIMARY KEY,
    tenant_id    uuid NOT NULL,
    protocol_id  uuid NOT NULL REFERENCES leak_review_protocols (id),
    from_phase   varchar(20),
    to_phase     varchar(20) NOT NULL,
    actor_id     uuid NOT NULL,
    note         varchar(500),
    created_at   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_leak_protocol_events
    ON leak_review_phase_events (tenant_id, protocol_id, created_at);

CREATE TABLE leak_review_inspections (
    id                 uuid PRIMARY KEY,
    tenant_id          uuid NOT NULL,
    protocol_id        uuid NOT NULL REFERENCES leak_review_protocols (id),
    service_order_id   uuid NOT NULL,
    technician_id      uuid NOT NULL,
    inspected_at       timestamptz NOT NULL,
    before_photo_id    uuid NOT NULL,
    after_photo_id     uuid NOT NULL,
    meter_identity_code varchar(120) NOT NULL,
    meter_reading      numeric(14,3) NOT NULL CHECK (meter_reading >= 0),
    leak_found         boolean NOT NULL,
    leak_location      varchar(300),
    finding_notes      jsonb NOT NULL DEFAULT '[]'::jsonb,
    CHECK (NOT leak_found OR leak_location IS NOT NULL)
);

CREATE TABLE leak_review_reports (
    id                    uuid PRIMARY KEY,
    tenant_id             uuid NOT NULL,
    protocol_id           uuid NOT NULL REFERENCES leak_review_protocols (id),
    inspection_order_id   uuid NOT NULL,
    conclusion            varchar(20) NOT NULL
        CHECK (conclusion IN ('LEAK_CONFIRMED','NO_LEAK_FOUND','INCONCLUSIVE')),
    inconclusive_reason   varchar(500),
    issued_by             uuid NOT NULL,
    issued_at             timestamptz NOT NULL,
    signature_code        varchar(80),
    signature_level       varchar(20),
    CHECK (conclusion <> 'INCONCLUSIVE' OR inconclusive_reason IS NOT NULL)
);

-- CORRECAO R3-B (R3-M1, Rodada 3, padrao G2 / B2-6): tabelas de tenant inscritas no
-- registro de isolamento por tenant (RLS) — uma chamada por tabela, no proprio SQL pendente.
SELECT register_tenant_table('leak_review_protocols');
SELECT register_tenant_table('leak_review_phase_events');
SELECT register_tenant_table('leak_review_inspections');
SELECT register_tenant_table('leak_review_reports');

-- NENHUMA definicao de parametro nova: o protocolo consome leak.* e
-- meter.test_* (P3.28/P2.31) e sign.min_level.* (P3.34) como estao.
-- Prazo por fase do protocolo: [A DEFINIR] -- nenhuma chave criada
-- (pendencia P-ED23-03 do RELATORIO-ED-23).

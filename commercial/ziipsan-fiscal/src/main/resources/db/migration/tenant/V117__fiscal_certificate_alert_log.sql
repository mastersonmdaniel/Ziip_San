-- PA-02 (M3, fiscal): registo de alertas de vencimento do certificado
-- (DESENHO-PA02 secao 3.8).
--
-- NUMERACAO PROVISORIA DE PACOTE STAGED: V115 (ver cabecalho da M1/V113).
-- Depende de: V1_2 (register_tenant_table) e V4_5 (fiscal_certificates).
--
-- DECISAO DE EXECUCAO (M3 = SIM, registada no relatorio de producao): o
-- registo de notificacoes existente (V1_15, notifications) deduplica por
-- source_message_id da outbox — nao garante a idempotencia por
-- (tenant, certificado, limiar, dia) que a secao 3.8 exige. Por isso a tabela
-- propria nasce, com a unicidade que torna o job diario idempotente.

CREATE TABLE IF NOT EXISTS fiscal_certificate_alert_log (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    certificate_id UUID NOT NULL,
    -- Limiar do alerta (F-21): D30, D15, D7, EXPIRED.
    threshold VARCHAR(10) NOT NULL CHECK (threshold IN ('D30', 'D15', 'D7', 'EXPIRED')),
    alerted_on DATE NOT NULL,
    channel_ref VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE fiscal_certificate_alert_log ADD CONSTRAINT uq_fiscal_cert_alert_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE fiscal_certificate_alert_log ADD CONSTRAINT fk_fiscal_cert_alert_certificate
    FOREIGN KEY (tenant_id, certificate_id) REFERENCES fiscal_certificates(tenant_id, id);
CREATE UNIQUE INDEX idx_fiscal_cert_alert_idempotency
    ON fiscal_certificate_alert_log(tenant_id, certificate_id, threshold, alerted_on);

SELECT register_tenant_table('fiscal_certificate_alert_log');

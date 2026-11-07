-- Metering Module Migration for Tenant Schema
-- Version: 5.6.0 (P2.5, correcao B1 da revisao cruzada)
-- Description: versionamento das linhas de consumo (DAD-14): consumption_records ganha
-- version_no, superseded_by_id, bill_id (+ bill_reference_month) e parameter_version_ids;
-- o indice unico de idempotencia da V5_1 passa a valer so para a versao vigente.
--
-- ORIGEM: este texto e a antiga secao 3 da V4_4 do billing, movida para ca na correcao
-- B1. O historico Flyway do tenant e unico e ordenado por versao: num tenant novo a
-- V4_4 (4.4) roda ANTES da V5_1 (5.1) que cria consumption_records, e o ALTER TABLE
-- falharia no provisionamento. Aqui, depois da V5_5, a tabela ja existe em qualquer
-- tenant. A V5_3 NAO e usada: esta reservada a P2.7 (leitura terceirizada).
--
-- Depende de: V5_1 (consumption_records) e V4_1/V4_14 (bills particionada, alvo da FK).
--
-- Retificar NAO atualiza a linha: insere a versao seguinte e liga a anterior pela
-- superseded_by_id. O historico de 120 meses (ret.billing_history_months, piso da
-- core V3) se reconstroi das proprias versoes, sem ler o audit_log.
--
-- NENHUM DADO E SEMEADO.

-- ========================================================================
-- LINHAS DE CONSUMO VERSIONADAS (DAD-14)
-- ========================================================================

-- Versao 1 e a linha original; cada retificacao insere a seguinte.
ALTER TABLE consumption_records ADD COLUMN IF NOT EXISTS version_no INT NOT NULL DEFAULT 1;
ALTER TABLE consumption_records ADD CONSTRAINT chk_consumption_records_version_no
    CHECK (version_no >= 1);

-- A versao que substituiu esta (NULL = vigente). E a UNICA coluna da linha antiga que
-- a retificacao toca: os valores faturados da versao substituida nunca mudam.
ALTER TABLE consumption_records ADD COLUMN IF NOT EXISTS superseded_by_id UUID;
ALTER TABLE consumption_records ADD CONSTRAINT fk_consumption_records_superseded
    FOREIGN KEY (tenant_id, superseded_by_id) REFERENCES consumption_records(tenant_id, id);

-- Conta que faturou esta versao. bill_reference_month e o mes da conta, exigido pela
-- FK para a bills particionada (a chave de particao entra na FK, padrao V4_14/V5_4).
ALTER TABLE consumption_records ADD COLUMN IF NOT EXISTS bill_id UUID;
ALTER TABLE consumption_records ADD COLUMN IF NOT EXISTS bill_reference_month DATE;
ALTER TABLE consumption_records ADD CONSTRAINT fk_consumption_records_bill
    FOREIGN KEY (tenant_id, bill_reference_month, bill_id) REFERENCES bills(tenant_id, reference_month, id);
ALTER TABLE consumption_records ADD CONSTRAINT chk_consumption_records_bill_pair
    CHECK ((bill_id IS NULL) = (bill_reference_month IS NULL));
ALTER TABLE consumption_records ADD CONSTRAINT chk_consumption_records_bill_month_day
    CHECK (bill_reference_month IS NULL OR EXTRACT(DAY FROM bill_reference_month) = 1);

-- Versoes de parametro lidas no calculo desta linha (mesma convencao da conta).
ALTER TABLE consumption_records ADD COLUMN IF NOT EXISTS parameter_version_ids JSONB;

-- A idempotencia da V5_1 (uma linha por unidade, servico e referencia) passa a valer
-- SO para a versao vigente: as substituidas convivem para contar a historia.
DROP INDEX IF EXISTS idx_consumption_unit_period;
CREATE UNIQUE INDEX idx_consumption_unit_period_current ON consumption_records(tenant_id, unit_id, service_kind, reference)
    WHERE superseded_by_id IS NULL;
-- Uma so linha por numero de versao dentro da mesma (unidade, servico, referencia).
CREATE UNIQUE INDEX idx_consumption_unit_period_version ON consumption_records(tenant_id, unit_id, service_kind, reference, version_no);
-- Cada versao tem NO MAXIMO uma sucessora: a cadeia de retificacao nao bifurca.
CREATE UNIQUE INDEX idx_consumption_superseded ON consumption_records(tenant_id, superseded_by_id)
    WHERE superseded_by_id IS NOT NULL;
CREATE INDEX idx_consumption_bill ON consumption_records(tenant_id, bill_id)
    WHERE bill_id IS NOT NULL;

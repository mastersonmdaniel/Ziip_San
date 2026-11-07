-- Leitura terceirizada (P2.7): fornecedor de leitura, tarefas de leitura ligadas
-- a rota e ao ciclo, e o controle de arquivos de ida e volta no leiaute do
-- fornecedor, com idempotencia em dois niveis (arquivo e registro).
-- Version: 5.3.0
--
-- Depende de V5_1 (metering: meter_installations, consumer_units via V3_1) e,
-- conceitualmente, da V4_3 do billing (billing_groups.route_code,
-- billing_references, cycle_schedule_items). NAO ha FK para as tabelas do
-- billing: os modulos nao dependem um do outro (o pom do metering so depende
-- do core), e o vinculo com o ciclo e guardado como identificadores
-- (group_id, billing_reference_id, cycle_schedule_item_id) mais a rota
-- (route_code, o mesmo identificador textual de billing_groups) e o mes de
-- referencia -- o mesmo formato de job_run_id na V4_3 e de run_id na V4_6,
-- que guardam o id sem FK por viverem em outro esquema/modulo.
--
-- NENHUM dado e semeado: fornecedor, leiaute e tarefas sao dados do tenant e
-- do contrato com o piloto. A ausencia de fornecedor ativo ou de tarefa para
-- um registro do arquivo e erro explicito no servico, nunca um padrao.
--
-- IDEMPOTENCIA (aceite da P2.7: "reenvio do mesmo arquivo ou do mesmo registro
-- nao duplica leitura"):
--   * arquivo: reading_import_batches tem UNIQUE (tenant_id, file_sha256):
--     o mesmo conteudo reenviado e o mesmo lote, e o servico devolve o lote
--     existente sem processar de novo;
--   * registro: reading_import_records tem indice unico parcial
--     (tenant_id, record_key) so para linhas IMPORTED: a mesma chave de
--     registro em outro arquivo vira uma linha DUPLICATE de auditoria, sem
--     segunda leitura. A chave tambem e gravada em
--     meter_readings.idempotency_key (coluna da V5_4, indice unico parcial
--     por tenant/mes/chave), que e a ultima trava no banco.
--   record_key = fornecedor + rota + referencia + unidade + tipo de medicao,
--   montada por VendorReadingLayout.recordKey no formato compacto (114
--   caracteres no pior caso, com os codigos nos limites das suas colunas --
--   dentro dos 120 de record_key e de meter_readings.idempotency_key).
--
-- reading_tasks.reading_id NAO tem FK de proposito: meter_readings so vira
-- particionada na V5_4, e uma FK para ela exigiria a chave de particao
-- (reference) ja nesta migration. reading_reference guarda o mes da leitura
-- para a ligacao ser reconstituivel, no mesmo espirito de
-- consumption_records.reading_reference (V5_4).

-- ========================================================================
-- FORNECEDOR DE LEITURA TERCEIRIZADA
-- ========================================================================
-- layout_code identifica o leiaute de arquivo do fornecedor (ex.:
-- PILOTO_CSV_V1, o leiaute implementado em VendorReadingLayout). E dado do
-- contrato: um fornecedor com um leiaute que o sistema nao conhece e recusado
-- na importacao/exportacao, nunca convertido "no chute".

CREATE TABLE IF NOT EXISTS reading_vendors (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(120) NOT NULL,
    document VARCHAR(20),
    layout_code VARCHAR(30) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE reading_vendors ADD CONSTRAINT uq_reading_vendors_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_reading_vendors_code ON reading_vendors(tenant_id, code);
CREATE INDEX idx_reading_vendors_active ON reading_vendors(tenant_id, active);

-- ========================================================================
-- TAREFA DE LEITURA (uma por unidade, tipo, rota e referencia)
-- ========================================================================
-- A tarefa nasce do ciclo: group_id/billing_reference_id/cycle_schedule_item_id
-- ligam a tarefa ao grupo, a referencia mensal e ao item do cronograma da
-- atividade de leitura da V4_3 (sem FK, ver cabecalho); route_code e a rota
-- de leitura do grupo; reference_month e a competencia (sempre dia 1).
-- status acompanha o fluxo: PENDING (criada), EXPORTED (saiu no arquivo de
-- ida), READ (voltou com leitura), NOT_READ (voltou sem leitura, com ou sem
-- anormalidade), CANCELLED (desistida antes da volta). Os carimbos sao
-- amarrados ao status por CHECK, no padrao da V4_3: EXPORTED exige
-- exported_at, e READ/NOT_READ exigem reading_reference (alem de reading_id,
-- que ja era exigido) -- o servico cumpre os dois lados em todos os caminhos
-- (criacao nasce PENDING sem carimbos; a exportacao carimba ao exportar; a
-- importacao grava reading_id e reading_reference ao cumprir a tarefa).

CREATE TABLE IF NOT EXISTS reading_tasks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    vendor_id UUID NOT NULL,
    route_code VARCHAR(40) NOT NULL,
    reference_month DATE NOT NULL CHECK (EXTRACT(DAY FROM reference_month) = 1),
    group_id UUID,
    billing_reference_id UUID,
    cycle_schedule_item_id UUID,
    unit_id UUID NOT NULL,
    installation_id UUID NOT NULL,
    kind VARCHAR(5) NOT NULL CHECK (kind IN ('WATER', 'WELL')),
    sequence_no INT NOT NULL CHECK (sequence_no >= 1),
    previous_reading INT CHECK (previous_reading >= 0),
    planned_on DATE,
    status VARCHAR(15) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'EXPORTED', 'READ', 'NOT_READ', 'CANCELLED')),
    exported_at TIMESTAMPTZ,
    reading_id UUID,
    reading_reference DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (status NOT IN ('READ', 'NOT_READ') OR reading_id IS NOT NULL),
    CHECK (status <> 'EXPORTED' OR exported_at IS NOT NULL),
    CHECK (status NOT IN ('READ', 'NOT_READ') OR reading_reference IS NOT NULL),
    CHECK (reading_reference IS NULL OR EXTRACT(DAY FROM reading_reference) = 1)
);
ALTER TABLE reading_tasks ADD CONSTRAINT uq_reading_tasks_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE reading_tasks ADD CONSTRAINT fk_reading_task_vendor
    FOREIGN KEY (tenant_id, vendor_id) REFERENCES reading_vendors(tenant_id, id);
ALTER TABLE reading_tasks ADD CONSTRAINT fk_reading_task_unit
    FOREIGN KEY (tenant_id, unit_id) REFERENCES consumer_units(tenant_id, id);
ALTER TABLE reading_tasks ADD CONSTRAINT fk_reading_task_installation
    FOREIGN KEY (tenant_id, installation_id) REFERENCES meter_installations(tenant_id, id);
CREATE UNIQUE INDEX idx_reading_tasks_unique ON reading_tasks(tenant_id, reference_month, route_code, unit_id, kind);
CREATE INDEX idx_reading_tasks_route ON reading_tasks(tenant_id, reference_month, route_code, sequence_no);
CREATE INDEX idx_reading_tasks_vendor ON reading_tasks(tenant_id, vendor_id, reference_month);
CREATE INDEX idx_reading_tasks_cycle_item ON reading_tasks(tenant_id, cycle_schedule_item_id)
    WHERE cycle_schedule_item_id IS NOT NULL;

-- ========================================================================
-- LOTE DE IMPORTACAO (um por arquivo recebido)
-- ========================================================================
-- file_sha256 e o hash do conteudo exato recebido: reenviar o mesmo arquivo
-- (mesmo nome ou nao) cai na UNIQUE e nao reprocessa. external_ref e a
-- referencia que o fornecedor da ao lote, quando da: unica por fornecedor
-- quando presente. Os contadores fecham com as linhas de
-- reading_import_records do lote: total = importados + duplicados + erros.

CREATE TABLE IF NOT EXISTS reading_import_batches (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    vendor_id UUID NOT NULL,
    file_name VARCHAR(200) NOT NULL,
    file_sha256 VARCHAR(64) NOT NULL,
    layout_code VARCHAR(30) NOT NULL,
    reference_month DATE CHECK (reference_month IS NULL OR EXTRACT(DAY FROM reference_month) = 1),
    route_code VARCHAR(40),
    external_ref VARCHAR(60),
    status VARCHAR(15) NOT NULL DEFAULT 'RECEIVED'
        CHECK (status IN ('RECEIVED', 'PROCESSED', 'REJECTED')),
    total_records INT NOT NULL DEFAULT 0 CHECK (total_records >= 0),
    imported_records INT NOT NULL DEFAULT 0 CHECK (imported_records >= 0),
    duplicate_records INT NOT NULL DEFAULT 0 CHECK (duplicate_records >= 0),
    error_records INT NOT NULL DEFAULT 0 CHECK (error_records >= 0),
    received_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (total_records = imported_records + duplicate_records + error_records OR status = 'RECEIVED'),
    CHECK ((status = 'PROCESSED' OR status = 'REJECTED') = (processed_at IS NOT NULL))
);
ALTER TABLE reading_import_batches ADD CONSTRAINT uq_reading_import_batches_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE reading_import_batches ADD CONSTRAINT fk_reading_import_batch_vendor
    FOREIGN KEY (tenant_id, vendor_id) REFERENCES reading_vendors(tenant_id, id);
CREATE UNIQUE INDEX idx_reading_import_batches_sha ON reading_import_batches(tenant_id, file_sha256);
CREATE UNIQUE INDEX idx_reading_import_batches_external_ref ON reading_import_batches(tenant_id, vendor_id, external_ref)
    WHERE external_ref IS NOT NULL;
CREATE INDEX idx_reading_import_batches_vendor ON reading_import_batches(tenant_id, vendor_id, received_at DESC);

-- ========================================================================
-- REGISTRO DE IMPORTACAO (uma linha por linha do arquivo, por tentativa)
-- ========================================================================
-- IMPORTED: gerou a leitura (reading_id). DUPLICATE: a mesma record_key ja
-- tinha sido importada -- auditoria do reenvio, sem leitura nova (por isso o
-- indice unico e parcial, so sobre IMPORTED). ERROR: a linha nao virou
-- leitura (sem tarefa, unidade desconhecida, valor invalido), com o motivo
-- em error_code/error_message -- a linha recusada nao e gravada como leitura,
-- como ja era a regra do retorno de leitura do modulo.

CREATE TABLE IF NOT EXISTS reading_import_records (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    batch_id UUID NOT NULL,
    line_no INT NOT NULL CHECK (line_no >= 2),
    record_key VARCHAR(120) NOT NULL,
    unit_id UUID,
    task_id UUID,
    reading_id UUID,
    status VARCHAR(15) NOT NULL
        CHECK (status IN ('IMPORTED', 'DUPLICATE', 'ERROR')),
    error_code VARCHAR(40),
    error_message VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK ((status = 'IMPORTED') = (reading_id IS NOT NULL)),
    CHECK ((status = 'ERROR') = (error_code IS NOT NULL))
);
ALTER TABLE reading_import_records ADD CONSTRAINT uq_reading_import_records_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE reading_import_records ADD CONSTRAINT fk_reading_import_record_batch
    FOREIGN KEY (tenant_id, batch_id) REFERENCES reading_import_batches(tenant_id, id);
ALTER TABLE reading_import_records ADD CONSTRAINT fk_reading_import_record_task
    FOREIGN KEY (tenant_id, task_id) REFERENCES reading_tasks(tenant_id, id);
CREATE UNIQUE INDEX idx_reading_import_records_key ON reading_import_records(tenant_id, record_key)
    WHERE status = 'IMPORTED';
CREATE INDEX idx_reading_import_records_batch ON reading_import_records(tenant_id, batch_id, line_no);
CREATE INDEX idx_reading_import_records_task ON reading_import_records(tenant_id, task_id)
    WHERE task_id IS NOT NULL;

-- ========================================================================
-- ISOLAMENTO E AUDITORIA
-- ========================================================================

SELECT register_tenant_table('reading_vendors');
SELECT register_tenant_table('reading_tasks');
SELECT register_tenant_table('reading_import_batches');
SELECT register_tenant_table('reading_import_records');

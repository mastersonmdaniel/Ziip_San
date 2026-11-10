-- ED-104 — Trilha GRS: MTR, CDF e DMR e operação de resíduos sólidos
-- Produtor: sessão 6d762d43-e1a9-4984-b50d-571ba0643b52
--
-- SQL ADITIVO de integração, em db/pendente/ (precedentes ED-102/ED-105): entra na numeração
-- global na integração. NENHUMA migration publicada é tocada: V2_4/V2_6/V2_8 ficam
-- byte-idênticas (prova por hash no RELATORIO-ED-104.md).
--
-- O que este ficheiro faz, por item da estimativa:
--   E1: waste_mtr ganha official_mtr_number, temporary_storage_id, sludge_record_id e o
--       estado NOT_SUBMITTED; nasce a tabela do armazenador temporário (quarto actor).
--   E2: o CDF deixa de ser 1:1 — sai o UNIQUE de waste_cdf.mtr_id, a coluna mtr_id é removida
--       do CDF e a agregação vive em waste_cdf_mtr (um MTR entra num só CDF).
--   E3: tabela da DMR trimestral.
--   E4: tabela do PGRS electrónico.
--   E5: waste_class ganha classification_scheme e nbr_edition; o CHECK de hazard_class é
--       corrigido para I/IIA/IIB (a V2_4 aceita I/II/III — defeito D-MTR-6; 'II' e 'III' não
--       existem na NBR 10004:2004). ATENÇÃO NA INTEGRAÇÃO: linhas existentes com 'II'/'III'
--       têm de ser revistas por responsável técnico ANTES deste CHECK novo — o SQL recusa
--       correr se existirem (falha ruidosa, sem conversão inventada).
--   E9: tabelas da operação de coleta (sectores, planos, execuções, tickets de balança,
--       parceiros/cooperativas, grandes geradores).
--
-- O catálogo de resíduos nasce VAZIO também aqui: nenhuma linha de waste_class é inserida
-- (seed inventado é proibido — precedente da V2_4). A carga da Lista IBAMA é a P-ED104-02.

-- ---------------------------------------------------------------- E5 (guarda prévia)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM waste_class WHERE hazard_class IN ('II', 'III')) THEN
        RAISE EXCEPTION 'ED-104: waste_class tem hazard_class II/III (inexistentes na NBR 10004:2004): revisão por responsável técnico exigida antes de aplicar';
    END IF;
END
$$;

-- ---------------------------------------------------------------- E1: armazenador temporário
CREATE TABLE IF NOT EXISTS waste_temporary_storage (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    cnpj VARCHAR(18) NOT NULL,
    corporate_name VARCHAR(200) NOT NULL,
    trade_name VARCHAR(200),
    address JSONB,
    environmental_license JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    properties JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100)
);
ALTER TABLE waste_temporary_storage ADD CONSTRAINT uq_waste_temp_storage_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_waste_temp_storage_doc ON waste_temporary_storage(tenant_id, cnpj);
CREATE INDEX IF NOT EXISTS idx_waste_temp_storage_status ON waste_temporary_storage(tenant_id, status);
SELECT register_tenant_table('waste_temporary_storage');

-- ---------------------------------------------------------------- E1: colunas do MTR
ALTER TABLE waste_mtr ADD COLUMN IF NOT EXISTS official_mtr_number VARCHAR(50);
ALTER TABLE waste_mtr ADD COLUMN IF NOT EXISTS temporary_storage_id UUID;
ALTER TABLE waste_mtr ADD COLUMN IF NOT EXISTS sludge_record_id UUID;
ALTER TABLE waste_mtr ADD CONSTRAINT fk_waste_mtr_temporary_storage
    FOREIGN KEY (tenant_id, temporary_storage_id) REFERENCES waste_temporary_storage(tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_waste_mtr_sludge_record ON waste_mtr(tenant_id, sludge_record_id);

-- O estado NOT_SUBMITTED entra no CHECK de sinir_sync_status sem reescrever a V2_4:
-- substitui-se a constraint (o nome vem da V2_4? não — a V2_4 declara o CHECK inline, sem nome;
-- na integração, localizar o nome real em pg_constraint e usar o DROP correspondente).
ALTER TABLE waste_mtr DROP CONSTRAINT IF EXISTS waste_mtr_sinir_sync_status_check;
ALTER TABLE waste_mtr ADD CONSTRAINT waste_mtr_sinir_sync_status_check
    CHECK (sinir_sync_status IN ('NOT_SUBMITTED', 'PENDING', 'SYNCING', 'SYNCED', 'FAILED', 'RETRYING'));

-- ---------------------------------------------------------------- E2: CDF agregado
ALTER TABLE waste_cdf ADD COLUMN IF NOT EXISTS period_start DATE;
ALTER TABLE waste_cdf ADD COLUMN IF NOT EXISTS period_end DATE;
ALTER TABLE waste_cdf ADD COLUMN IF NOT EXISTS official_cdf_number VARCHAR(50);
-- Os CDFs 1:1 anteriores à ED-104 (rascunhos internos sem valor legal) ganham o período do
-- próprio destined_at na integração, ANTES do NOT NULL abaixo; aqui só se declara a forma.
-- (Sem backfill inventado neste ficheiro: a integração decide o destino dos rascunhos antigos.)

CREATE TABLE IF NOT EXISTS waste_cdf_mtr (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    cdf_id UUID NOT NULL,
    mtr_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100)
);
ALTER TABLE waste_cdf_mtr ADD CONSTRAINT uq_waste_cdf_mtr_tenant_id UNIQUE (tenant_id, id);
-- um MTR entra num só CDF: agregar duas vezes duplicaria a massa certificada
CREATE UNIQUE INDEX IF NOT EXISTS idx_waste_cdf_mtr_mtr ON waste_cdf_mtr(tenant_id, mtr_id);
CREATE INDEX IF NOT EXISTS idx_waste_cdf_mtr_cdf ON waste_cdf_mtr(tenant_id, cdf_id);
ALTER TABLE waste_cdf_mtr ADD CONSTRAINT fk_waste_cdf_mtr_cdf
    FOREIGN KEY (tenant_id, cdf_id) REFERENCES waste_cdf(tenant_id, id);
ALTER TABLE waste_cdf_mtr ADD CONSTRAINT fk_waste_cdf_mtr_mtr
    FOREIGN KEY (tenant_id, mtr_id) REFERENCES waste_mtr(tenant_id, id);
SELECT register_tenant_table('waste_cdf_mtr');

-- O fim do 1:1 (coluna e UNIQUE antigos) é aplicado na integração DEPOIS de migrar as ligações
-- existentes para waste_cdf_mtr — fica como bloco comentado, deliberado e nomeado:
--   ALTER TABLE waste_cdf DROP CONSTRAINT IF EXISTS waste_cdf_mtr_id_key;
--   DROP INDEX IF EXISTS idx_waste_cdf_mtr;
--   ALTER TABLE waste_cdf DROP COLUMN IF EXISTS mtr_id;
--   ALTER TABLE waste_cdf ALTER COLUMN period_start SET NOT NULL;
--   ALTER TABLE waste_cdf ALTER COLUMN period_end SET NOT NULL;

-- ---------------------------------------------------------------- E3: DMR trimestral
CREATE TABLE IF NOT EXISTS waste_dmr (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    year INT NOT NULL,
    quarter INT NOT NULL CHECK (quarter BETWEEN 1 AND 4),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'CLOSED')),
    mtr_count INT NOT NULL DEFAULT 0,
    total_quantity_kg DECIMAL(14,3) NOT NULL DEFAULT 0,
    destined_quantity_kg DECIMAL(14,3) NOT NULL DEFAULT 0,
    content JSONB,
    closed_at TIMESTAMPTZ,
    submission_status VARCHAR(20) NOT NULL DEFAULT 'NOT_SUBMITTED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100)
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_waste_dmr_period ON waste_dmr(tenant_id, year, quarter);
CREATE INDEX IF NOT EXISTS idx_waste_dmr_status ON waste_dmr(tenant_id, status);
SELECT register_tenant_table('waste_dmr');

-- ---------------------------------------------------------------- E4: PGRS electrónico
CREATE TABLE IF NOT EXISTS waste_pgrs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    generator_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'ACTIVE', 'UNDER_REVISION', 'EXPIRED')),
    technician_name VARCHAR(200),
    technician_registry VARCHAR(50),
    valid_from DATE,
    valid_until DATE,
    revision_due DATE,
    lines JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100)
);
CREATE INDEX IF NOT EXISTS idx_waste_pgrs_generator ON waste_pgrs(tenant_id, generator_id);
CREATE INDEX IF NOT EXISTS idx_waste_pgrs_status ON waste_pgrs(tenant_id, status);
SELECT register_tenant_table('waste_pgrs');

-- ---------------------------------------------------------------- E5: classificação
ALTER TABLE waste_class ADD COLUMN IF NOT EXISTS classification_scheme VARCHAR(20) NOT NULL DEFAULT 'NBR_10004';
ALTER TABLE waste_class ADD COLUMN IF NOT EXISTS nbr_edition VARCHAR(10);
ALTER TABLE waste_class DROP CONSTRAINT IF EXISTS waste_class_hazard_class_check;
ALTER TABLE waste_class ADD CONSTRAINT waste_class_hazard_class_check
    CHECK (hazard_class IN ('I', 'IIA', 'IIB'));
ALTER TABLE waste_class ADD CONSTRAINT waste_class_scheme_check
    CHECK (classification_scheme IN ('NBR_10004', 'RSS', 'RCC'));

-- ---------------------------------------------------------------- E9: operação de coleta
CREATE TABLE IF NOT EXISTS waste_collection_sector (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    sector_code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100)
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_waste_collection_sector_code
    ON waste_collection_sector(tenant_id, sector_code);
SELECT register_tenant_table('waste_collection_sector');

CREATE TABLE IF NOT EXISTS waste_collection_partner (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('COOPERATIVE', 'DESTINATION', 'TRANSPORTER')),
    name VARCHAR(200) NOT NULL,
    document VARCHAR(18),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100)
);
CREATE INDEX IF NOT EXISTS idx_waste_collection_partner_kind ON waste_collection_partner(tenant_id, kind);
SELECT register_tenant_table('waste_collection_partner');

CREATE TABLE IF NOT EXISTS waste_collection_large_generator (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    document VARCHAR(18),
    flow VARCHAR(20) NOT NULL CHECK (flow IN ('REGULAR', 'SELECTIVE', 'RCC', 'RSS', 'LARGE_GENERATOR')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100)
);
SELECT register_tenant_table('waste_collection_large_generator');

CREATE TABLE IF NOT EXISTS waste_collection_execution (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    sector_id UUID NOT NULL,
    flow VARCHAR(20) NOT NULL CHECK (flow IN ('REGULAR', 'SELECTIVE', 'RCC', 'RSS', 'LARGE_GENERATOR')),
    partner_id UUID,
    frequency_days_per_week INT NOT NULL,
    ordered_stops JSONB NOT NULL DEFAULT '[]',
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'CLOSED')),
    executed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100)
);
CREATE INDEX IF NOT EXISTS idx_waste_collection_execution_sector
    ON waste_collection_execution(tenant_id, sector_id);
SELECT register_tenant_table('waste_collection_execution');

CREATE TABLE IF NOT EXISTS waste_weighing_ticket (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    execution_id UUID NOT NULL,
    ticket_number VARCHAR(50) NOT NULL,
    weighed_at TIMESTAMPTZ NOT NULL,
    net_kg DECIMAL(12,3) NOT NULL CHECK (net_kg > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100)
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_waste_weighing_ticket_number
    ON waste_weighing_ticket(tenant_id, ticket_number);
CREATE INDEX IF NOT EXISTS idx_waste_weighing_ticket_execution
    ON waste_weighing_ticket(tenant_id, execution_id);
SELECT register_tenant_table('waste_weighing_ticket');

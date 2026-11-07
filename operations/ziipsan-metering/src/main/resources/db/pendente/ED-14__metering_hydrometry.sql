-- ED-14 -- Hidrometria (Portaria Inmetro 155/2022).
--
-- *** NUMERACAO — PRECEDENTE ED-17 ***
-- O mapa Flyway do plano v3.0 (§3) NAO reserva versao para a ED-14: a faixa tenant do
-- metering (V5_1..V5_7) esta ocupada (V5_1/V5_2 existentes; V5_3 P2.7; V5_4 P2.3; V5_5 RLS;
-- V5_6/V5_7 P2.5) e a ED-14 e posterior a P3.12, fase da sequencia global V100+ com o modulo
-- no nome, cujo numero sai na ordem do merge. Este arquivo fica em db/pendente/ e NAO deve
-- ser renomeado por chute: na integracao vira V<proximo livre>__metering_hydrometry.sql e o
-- HydrometryMigrationConsistencyTest passa a ler o novo caminho. Nenhum numero inventado.
-- A Portaria 657/2025 esta SUSPENSA (nada aqui se constroi sobre ela) e a 295/2018 REVOGADA:
-- vale a 155/2022 (OPE-24 resolvido pelo catalogo).
--
-- Razoes da 155/2022 conferidas no aceite da ED-14: Q2/Q1 = 1,6; Q4/Q3 = 1,25; R = Q3/Q1 no
-- catalogo do modelo (10..800). Q1/Q2/Q4 NAO sao colunas: derivam de (Q3, R) em FlowRates.

-- ======================================================================
-- CATALOGOS METROLOGICOS (substituem brand/model/inmetro_approval livres)
-- ======================================================================
CREATE TABLE IF NOT EXISTS meter_brands (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL, code VARCHAR(30) NOT NULL, name VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID, updated_by UUID);
ALTER TABLE meter_brands ADD CONSTRAINT uq_meter_brands_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_meter_brands_code ON meter_brands(tenant_id, code);

CREATE TABLE IF NOT EXISTS meter_models (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL, brand_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL, name VARCHAR(120) NOT NULL,
    approval_ordinance VARCHAR(60) NOT NULL,
    nominal_diameter_mm NUMERIC(6,1) NOT NULL CHECK (nominal_diameter_mm > 0),
    q3_m3h NUMERIC(10,3) NOT NULL CHECK (q3_m3h > 0),
    ratio_r INT NOT NULL CHECK (ratio_r BETWEEN 10 AND 800),
    accuracy_class INT NOT NULL CHECK (accuracy_class IN (1, 2)),
    position VARCHAR(20) NOT NULL CHECK (position IN ('HORIZONTAL', 'VERTICAL', 'ANY')),
    regime VARCHAR(30) NOT NULL CHECK (regime IN ('INITIAL_VERIFICATION', 'SUBSEQUENT_VERIFICATION')),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID, updated_by UUID);
ALTER TABLE meter_models ADD CONSTRAINT uq_meter_models_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE meter_models ADD CONSTRAINT fk_meter_model_brand
    FOREIGN KEY (tenant_id, brand_id) REFERENCES meter_brands(tenant_id, id);
CREATE UNIQUE INDEX idx_meter_models_code ON meter_models(tenant_id, code);

CREATE TABLE IF NOT EXISTS meter_storage_locations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL, code VARCHAR(30) NOT NULL, name VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID, updated_by UUID);
ALTER TABLE meter_storage_locations ADD CONSTRAINT uq_meter_storage_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_meter_storage_code ON meter_storage_locations(tenant_id, code);

-- Catalogo unico de protecao e de local de instalacao (ED-14: por catalogo, nao texto livre)
CREATE TABLE IF NOT EXISTS meter_installation_catalogs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('PROTECTION', 'INSTALL_LOCATION')),
    code VARCHAR(30) NOT NULL, name VARCHAR(120) NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID, updated_by UUID);
ALTER TABLE meter_installation_catalogs ADD CONSTRAINT uq_meter_inst_catalog_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_meter_inst_catalog_code ON meter_installation_catalogs(tenant_id, kind, code);

-- ======================================================================
-- LOTE (recebimento) E ENSAIO EM BANCADA
-- ======================================================================
CREATE TABLE IF NOT EXISTS meter_lots (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL, lot_code VARCHAR(40) NOT NULL, model_id UUID NOT NULL,
    received_on DATE NOT NULL, quantity INT NOT NULL CHECK (quantity >= 0),
    storage_location_id UUID NOT NULL, bench_test_id UUID, approved BOOLEAN,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID, updated_by UUID);
ALTER TABLE meter_lots ADD CONSTRAINT uq_meter_lots_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE meter_lots ADD CONSTRAINT fk_meter_lot_model
    FOREIGN KEY (tenant_id, model_id) REFERENCES meter_models(tenant_id, id);
ALTER TABLE meter_lots ADD CONSTRAINT fk_meter_lot_storage
    FOREIGN KEY (tenant_id, storage_location_id) REFERENCES meter_storage_locations(tenant_id, id);
CREATE UNIQUE INDEX idx_meter_lots_code ON meter_lots(tenant_id, lot_code);

CREATE TABLE IF NOT EXISTS meter_bench_tests (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL, lot_id UUID NOT NULL, performed_on DATE NOT NULL,
    performed_by VARCHAR(20) NOT NULL CHECK (performed_by IN ('OWN_BENCH', 'EXTERNAL_LAB')),
    sample_size INT NOT NULL CHECK (sample_size >= 0), lot_approved BOOLEAN,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID, updated_by UUID);
ALTER TABLE meter_bench_tests ADD CONSTRAINT uq_meter_bench_tests_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE meter_bench_tests ADD CONSTRAINT fk_bench_test_lot
    FOREIGN KEY (tenant_id, lot_id) REFERENCES meter_lots(tenant_id, id);

-- Resultado por vazao (curva de erro = conjunto destas linhas por hidrometro)
CREATE TABLE IF NOT EXISTS meter_bench_test_results (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL, bench_test_id UUID NOT NULL, meter_id UUID NOT NULL,
    flow_point VARCHAR(5) NOT NULL CHECK (flow_point IN ('Q1', 'Q2', 'Q3', 'Q4')),
    reference_volume NUMERIC(12,4) NOT NULL CHECK (reference_volume > 0),
    indicated_volume NUMERIC(12,4) NOT NULL CHECK (indicated_volume >= 0),
    error_pct NUMERIC(8,4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID, updated_by UUID);
ALTER TABLE meter_bench_test_results ADD CONSTRAINT uq_bench_results_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE meter_bench_test_results ADD CONSTRAINT fk_bench_result_test
    FOREIGN KEY (tenant_id, bench_test_id) REFERENCES meter_bench_tests(tenant_id, id);
ALTER TABLE meter_bench_test_results ADD CONSTRAINT fk_bench_result_meter
    FOREIGN KEY (tenant_id, meter_id) REFERENCES meters(tenant_id, id);
CREATE UNIQUE INDEX idx_bench_result_flow ON meter_bench_test_results(tenant_id, bench_test_id, meter_id, flow_point);

-- ======================================================================
-- METERS: novas colunas + MIGRACAO DA CLASSE ANTIGA A-D
-- ======================================================================
ALTER TABLE meters ADD COLUMN IF NOT EXISTS model_id UUID;
ALTER TABLE meters ADD COLUMN IF NOT EXISTS lot_id UUID;
ALTER TABLE meters ADD COLUMN IF NOT EXISTS asset_id UUID;
ALTER TABLE meters ADD COLUMN IF NOT EXISTS storage_location_id UUID;
ALTER TABLE meters ADD COLUMN IF NOT EXISTS legacy_metrological_class VARCHAR(10);
ALTER TABLE meters ADD COLUMN IF NOT EXISTS migrated_park BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE meters ADD COLUMN IF NOT EXISTS write_off_reason VARCHAR(300);
ALTER TABLE meters ADD COLUMN IF NOT EXISTS totalized_volume_m3 BIGINT NOT NULL DEFAULT 0;

-- Migracao (teste de migracao confere): classe antiga A-D sai de metrological_class para
-- legacy_metrological_class e o hidrometro e marcado como parque migrado. Nada e apagado:
-- expand/contract do plano — metrological_class so sai na release seguinte.
UPDATE meters SET legacy_metrological_class = metrological_class, migrated_park = TRUE
 WHERE metrological_class IN ('A', 'B', 'C', 'D') AND legacy_metrological_class IS NULL;

-- A1 (revisao): trava na coluna ANTIGA — depois do UPDATE acima, classe A-D em
-- metrological_class so subsiste em linha do parque migrado; cadastro novo que grave
-- 'A'..'D' na coluna antiga e recusado pelo banco (e pelo receive() em Java).
ALTER TABLE meters ADD CONSTRAINT ck_meters_old_class_only_migrated CHECK (
    metrological_class IS NULL OR migrated_park OR metrological_class NOT IN ('A','B','C','D'));
-- migrated_park NAO e auto-declaravel pela aplicacao: o unico caminho de escrita da
-- aplicacao (MeterLifecycleService.receive) forca migrated_park = FALSE; a flag so
-- nasce TRUE neste UPDATE de migracao. (Escrita direta na coluna e fora do caminho
-- da aplicacao — ver RELATORIO, pendencia de integracao: revogar UPDATE da role.)
ALTER TABLE meters ADD CONSTRAINT fk_meter_model
    FOREIGN KEY (tenant_id, model_id) REFERENCES meter_models(tenant_id, id);
ALTER TABLE meters ADD CONSTRAINT fk_meter_lot
    FOREIGN KEY (tenant_id, lot_id) REFERENCES meter_lots(tenant_id, id);
ALTER TABLE meters ADD CONSTRAINT fk_meter_storage
    FOREIGN KEY (tenant_id, storage_location_id) REFERENCES meter_storage_locations(tenant_id, id);
-- Classe antiga so no parque migrado; cadastro novo nunca a carrega:
ALTER TABLE meters ADD CONSTRAINT ck_meters_legacy_only_migrated CHECK (
    legacy_metrological_class IS NULL OR (migrated_park AND legacy_metrological_class IN ('A','B','C','D')));
-- Armazenagem obrigatoria disponivel/em manutencao; motivo obrigatorio na baixa:
ALTER TABLE meters ADD CONSTRAINT ck_meters_storage_required CHECK (
    status NOT IN ('AVAILABLE', 'IN_MAINTENANCE') OR storage_location_id IS NOT NULL);
ALTER TABLE meters ADD CONSTRAINT ck_meters_writeoff_reason CHECK (
    status <> 'WRITTEN_OFF' OR (write_off_reason IS NOT NULL AND length(trim(write_off_reason)) > 0));
ALTER TABLE meters ADD CONSTRAINT ck_meters_totalized_nonneg CHECK (totalized_volume_m3 >= 0);
CREATE INDEX idx_meters_next_verif ON meters(tenant_id, status);
-- asset da P1.35 (V27_1): FK guardada — em tenant sem a tabela de ativos a coluna fica
-- referencia logica ate a integracao com a P1.35 (mesmo padrao to_regclass da V3_14/V9_3).
DO $$
BEGIN
    IF to_regclass('asset') IS NOT NULL THEN
        ALTER TABLE meters ADD CONSTRAINT fk_meter_asset
            FOREIGN KEY (tenant_id, asset_id) REFERENCES asset(tenant_id, id);
    END IF;
END $$;

-- ======================================================================
-- INSTALACAO: ligacao, OS, catalogos, destino da retirada
-- M4 (revisao): protection_type/location_description (texto livre da V5_1) seguem o
-- MESMO expand/contract de brand/model: o caminho novo grava so os ids de catalogo;
-- as colunas livres saem na release seguinte a que os ids virarem a unica escrita.
-- (o lacre continua SOMENTE em a coluna de lacre da V5_1, na instalacao — lugar unico)
-- ======================================================================
ALTER TABLE meter_installations ADD COLUMN IF NOT EXISTS service_connection_id UUID;
ALTER TABLE meter_installations ADD COLUMN IF NOT EXISTS install_service_order_id UUID;
ALTER TABLE meter_installations ADD COLUMN IF NOT EXISTS remove_service_order_id UUID;
ALTER TABLE meter_installations ADD COLUMN IF NOT EXISTS protection_catalog_id UUID;
ALTER TABLE meter_installations ADD COLUMN IF NOT EXISTS install_location_catalog_id UUID;
ALTER TABLE meter_installations ADD COLUMN IF NOT EXISTS removal_destination VARCHAR(20)
    CHECK (removal_destination IN ('AVAILABLE', 'SCRAP'));
DO $$
BEGIN
    IF to_regclass('service_connections') IS NOT NULL THEN
        ALTER TABLE meter_installations ADD CONSTRAINT fk_installation_connection
            FOREIGN KEY (tenant_id, service_connection_id) REFERENCES service_connections(tenant_id, id);
    END IF;
END $$;
ALTER TABLE meter_installations ADD CONSTRAINT fk_installation_protection
    FOREIGN KEY (tenant_id, protection_catalog_id) REFERENCES meter_installation_catalogs(tenant_id, id);
ALTER TABLE meter_installations ADD CONSTRAINT fk_installation_location_catalog
    FOREIGN KEY (tenant_id, install_location_catalog_id) REFERENCES meter_installation_catalogs(tenant_id, id);
ALTER TABLE meter_installations ADD CONSTRAINT ck_installation_removal CHECK (
    (removed_on IS NULL AND removal_destination IS NULL AND remove_service_order_id IS NULL)
    OR (removed_on IS NOT NULL AND removal_destination IS NOT NULL AND remove_service_order_id IS NOT NULL));

-- ======================================================================
-- MOVIMENTACOES (historico) E VERIFICACOES
-- ======================================================================
CREATE TABLE IF NOT EXISTS meter_movements (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL, meter_id UUID NOT NULL,
    movement_type VARCHAR(30) NOT NULL CHECK (movement_type IN
        ('RECEIPT','INSTALL','REMOVE','REINSTALL','MOVE_TO_MAINTENANCE','RETURN_FROM_MAINTENANCE','WRITE_OFF','STOLEN')),
    occurred_on DATE NOT NULL, service_order_id UUID, installation_id UUID,
    storage_location_id UUID,
    removal_destination VARCHAR(20) CHECK (removal_destination IN ('AVAILABLE','SCRAP')),
    write_off_reason VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID, updated_by UUID,
    -- estado muda so por instalacao/retirada/movimentacao COM OS (recebimento e o unico sem OS)
    CHECK (movement_type = 'RECEIPT' OR service_order_id IS NOT NULL),
    CHECK (movement_type <> 'REMOVE' OR removal_destination IS NOT NULL),
    CHECK (movement_type <> 'WRITE_OFF' OR (write_off_reason IS NOT NULL AND length(trim(write_off_reason)) > 0)));
ALTER TABLE meter_movements ADD CONSTRAINT uq_meter_movements_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE meter_movements ADD CONSTRAINT fk_movement_meter
    FOREIGN KEY (tenant_id, meter_id) REFERENCES meters(tenant_id, id);
ALTER TABLE meter_movements ADD CONSTRAINT fk_movement_storage
    FOREIGN KEY (tenant_id, storage_location_id) REFERENCES meter_storage_locations(tenant_id, id);
ALTER TABLE meter_movements ADD CONSTRAINT fk_movement_installation
    FOREIGN KEY (tenant_id, installation_id) REFERENCES meter_installations(tenant_id, id);
CREATE INDEX idx_movements_meter ON meter_movements(tenant_id, meter_id, occurred_on);

CREATE TABLE IF NOT EXISTS meter_verifications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL, meter_id UUID NOT NULL,
    verification_type VARCHAR(20) NOT NULL CHECK (verification_type IN ('INITIAL','SUBSEQUENT','AFTER_REPAIR')),
    performed_on DATE NOT NULL,
    performed_by VARCHAR(20) NOT NULL CHECK (performed_by IN ('OWN_BENCH','EXTERNAL_LAB')),
    external_lab_name VARCHAR(160), certificate_ref VARCHAR(80),
    result VARCHAR(20) NOT NULL CHECK (result IN ('APPROVED','REJECTED')),
    -- PROXIMA VERIFICACAO CALCULADA (ultima + meter.verification_interval_years): a coluna e
    -- projecao gravada pelo servico a partir do calculo; nenhum caminho a aceita digitada.
    next_verification_on DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID, updated_by UUID,
    CHECK (performed_by <> 'EXTERNAL_LAB' OR (external_lab_name IS NOT NULL AND certificate_ref IS NOT NULL)));
ALTER TABLE meter_verifications ADD CONSTRAINT uq_meter_verifications_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE meter_verifications ADD CONSTRAINT fk_verification_meter
    FOREIGN KEY (tenant_id, meter_id) REFERENCES meters(tenant_id, id);
CREATE INDEX idx_verifications_next ON meter_verifications(tenant_id, next_verification_on);

SELECT register_tenant_table('meter_brands');
SELECT register_tenant_table('meter_models');
SELECT register_tenant_table('meter_storage_locations');
SELECT register_tenant_table('meter_installation_catalogs');
SELECT register_tenant_table('meter_lots');
SELECT register_tenant_table('meter_bench_tests');
SELECT register_tenant_table('meter_bench_test_results');
SELECT register_tenant_table('meter_movements');
SELECT register_tenant_table('meter_verifications');

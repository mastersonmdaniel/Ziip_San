-- =====================================================================
-- ED-63 / GIS-12 — hidráulica EPANET/SWMM. SQL PENDENTE, sem número de
-- migration: a série tenant do GIS na main vai até V13_12 e nenhum
-- número livre foi confirmado (ED-53/54/63 disputam os slots seguintes).
-- Ordem de aplicação (expand/contract, GIS-CADASTRO-TECNICO §2.3 M5/M6):
--   1) aplicar a PARTE 1 (mapeamento) junto do deploy do serviço novo;
--   2) aplicar a PARTE 2 (remoção) SÓ na release seguinte à que deixou
--      de usar a view/entidade — nunca junto da troca do serviço.
-- =====================================================================

-- ---------------------------------------------------------------------
-- PARTE 1 — gis_hydraulic_mapping: tipo → elemento EPANET ou SWMM por
-- tenant (tabela prevista no GIS-CADASTRO-TECNICO §3 para a GIS-12).
-- Semente de fábrica com o mapeamento dos tipos do catálogo vigente;
-- o tenant ajusta pela mesma chave (tenant_id, model, feature_type_code).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS gis_hydraulic_mapping (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    model TEXT NOT NULL CHECK (model IN ('EPANET', 'SWMM')),
    feature_type_code TEXT NOT NULL,
    element_code TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    origin TEXT NOT NULL DEFAULT 'FACTORY' CHECK (origin IN ('FACTORY', 'TENANT')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_gis_hydraulic_mapping UNIQUE (tenant_id, model, feature_type_code)
);

INSERT INTO gis_hydraulic_mapping (tenant_id, model, feature_type_code, element_code)
VALUES
    ('${tenant_id}', 'EPANET', 'JUNCTION', 'JUNCTION'),
    ('${tenant_id}', 'EPANET', 'WELL', 'RESERVOIR'),
    ('${tenant_id}', 'EPANET', 'SPRING', 'RESERVOIR'),
    ('${tenant_id}', 'EPANET', 'SURFACE_INTAKE', 'RESERVOIR'),
    ('${tenant_id}', 'EPANET', 'RESERVOIR', 'TANK'),
    ('${tenant_id}', 'EPANET', 'ELEVATED_TANK', 'TANK'),
    ('${tenant_id}', 'EPANET', 'WATER_MAIN', 'PIPE'),
    ('${tenant_id}', 'EPANET', 'WATER_PUMP_STATION', 'PUMP'),
    ('${tenant_id}', 'SWMM', 'MANHOLE', 'JUNCTION'),
    ('${tenant_id}', 'SWMM', 'GRAVITY_SEWER', 'CONDUIT'),
    ('${tenant_id}', 'SWMM', 'FORCE_MAIN', 'CONDUIT'),
    ('${tenant_id}', 'SWMM', 'DETENTION_BASIN', 'STORAGE'),
    ('${tenant_id}', 'SWMM', 'OUTFALL', 'OUTFALL')
ON CONFLICT (tenant_id, model, feature_type_code) DO NOTHING;

SELECT register_tenant_table('gis_hydraulic_mapping');

-- Registro de execuções publicadas (portão N7 gravado: solver, versão,
-- premissas e grau de confiança são colunas NOT NULL).
CREATE TABLE IF NOT EXISTS gis_hydraulic_run (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    model TEXT NOT NULL CHECK (model IN ('EPANET', 'SWMM')),
    zone_code TEXT NOT NULL,
    as_of DATE NOT NULL,
    solver_name TEXT NOT NULL,
    solver_version TEXT NOT NULL,
    premises JSONB NOT NULL,
    confidence TEXT NOT NULL CHECK (confidence IN ('HIGH', 'MEDIUM', 'LOW')),
    scenario_kind TEXT,
    inp_content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID
);

SELECT register_tenant_table('gis_hydraulic_run');

-- ---------------------------------------------------------------------
-- PARTE 2 — REMOÇÃO (M5/M6). Executar só depois que o
-- EpanetSimulationService novo (esta entrega) estiver em produção e o
-- teste de arquitetura provar nenhum uso da view/entidade.
-- A entidade JPA GisNetworkElement sai do código na mesma release
-- (arquivo removido do módulo; ver a nota de integração da entrega).
-- ---------------------------------------------------------------------
DROP VIEW IF EXISTS gis_network_element;
DROP TABLE IF EXISTS gis_network_element_legacy;

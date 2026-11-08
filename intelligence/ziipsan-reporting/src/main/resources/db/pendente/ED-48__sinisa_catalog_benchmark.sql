-- V15_X - ED-48 (Indicadores gerenciais e SINISA: catalogo, painel e fechamento).
-- *** NUMERACAO PENDENTE - NAO INVENTADA ***
-- O plano v3.0, secao 3, reserva para a faixa reporting apenas numeros
-- nominais V15_1 (P3.22) e V15_2 (P3.41); para ED-46..ED-49 a faixa e
-- coletiva ("V15_x": "catalogo SINISA e paineis sobre o registro de
-- indicadores da V15_2"). NENHUM numero nominal foi reservado a ED-48:
-- este DDL esta PRONTO e aguarda a atribuicao do numero na integracao
-- (mesmo padrao da ED-46). Renomear para V<numero>__ed48_sinisa_catalog_benchmark.sql
-- ao integrar. Opcao do briefing: db/pendente/ — adotado o padrao ED-46
-- (V15_X no diretorio de migrations), declarado no RELATORIO-ED-48.
--
-- Conteudo (espelho das classes de br.com.ziipsan.reporting.sinisa):
--   * sinisa_indicator_links: ligacao indicador (P3.41) -> variavel do
--     mapa SINISA da P4.2, por tenant. SEM coluna de codigo oficial: o codigo do
--     Glossario NAO e coluna presumida; quando o glossario do ciclo for
--     carregado, a integracao decide a coluna — nao inventamos o formato.
--   * benchmark_references: base GLOBAL e versionada de medianas publicas
--     do SINISA (GES-29), importada de dado publico. Sem tenant_id (base
--     de produto/publica, como report_catalog_entries da ED-46): por isso
--     NAO usa register_tenant_table. NENHUMA mediana e semeada aqui.
--   * cross_tenant_opt_ins: adesao registrada do tenant a comparacao
--     entre tenants — opted_in DEFAULT FALSE (padrao desligado, GES-29)
--     e CHECK que exige a clausula contratual quando opted_in = TRUE.
-- O fechamento e o semaforo NAO ganham tabelas aqui: sao os da V15_2
-- (indicator_closings/indicator_targets da P3.41), aplicados ao catalogo.

CREATE TABLE IF NOT EXISTS sinisa_indicator_links (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    indicator_code VARCHAR(60) NOT NULL,
    sinisa_variable_key VARCHAR(80) NOT NULL,
    sinisa_form VARCHAR(20) NOT NULL
        CHECK (sinisa_form IN ('FINANCEIRO', 'TECNICO', 'BALANCO')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100),
    created_by UUID,
    updated_by UUID
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_sinisa_indicator_links_key
    ON sinisa_indicator_links(tenant_id, indicator_code);
CREATE INDEX IF NOT EXISTS idx_sinisa_indicator_links_variable
    ON sinisa_indicator_links(tenant_id, sinisa_variable_key);

CREATE TABLE IF NOT EXISTS benchmark_references (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    indicator_code VARCHAR(60) NOT NULL,
    size_class VARCHAR(80) NOT NULL,
    region VARCHAR(80) NOT NULL,
    legal_nature VARCHAR(80) NOT NULL,
    median NUMERIC(24, 10) NOT NULL,
    base_version VARCHAR(80) NOT NULL,
    source_description VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100),
    created_by UUID,
    updated_by UUID
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_benchmark_references_recorte
    ON benchmark_references(indicator_code, size_class, region, legal_nature, base_version);
CREATE INDEX IF NOT EXISTS idx_benchmark_references_base
    ON benchmark_references(base_version, indicator_code);

CREATE TABLE IF NOT EXISTS cross_tenant_opt_ins (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    opted_in BOOLEAN NOT NULL DEFAULT FALSE,
    contract_clause_reference VARCHAR(200),
    registered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100),
    created_by UUID,
    updated_by UUID,
    -- Adesao so com clausula contratual e registro (GES-29), travado no banco.
    CHECK (opted_in = FALSE OR (contract_clause_reference IS NOT NULL
            AND contract_clause_reference <> '' AND registered_at IS NOT NULL))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_cross_tenant_opt_ins_tenant
    ON cross_tenant_opt_ins(tenant_id);

SELECT register_tenant_table('sinisa_indicator_links', true, false);
SELECT register_tenant_table('cross_tenant_opt_ins', true, false);
-- benchmark_references NAO registrada: base global/publica, sem tenant
-- (mesma justificativa das linhas de fabrica da V15_1 e do catalogo da ED-46).

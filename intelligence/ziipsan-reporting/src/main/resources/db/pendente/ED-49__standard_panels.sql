-- ED-49 — Sete paineis padrao — SQL PENDENTE DE NUMERACAO FLYWAY
-- (prefixo ED-49__). Nenhum numero foi inventado: a faixa V15_x do
-- reporting e coletiva para ED-46..ED-49 e os nominais V15_1 (P3.22) e
-- V15_2 (P3.41) estao ocupados. Na integracao, atribuir o proximo slot
-- livre da faixa e renomear o arquivo. Os 14 valores da P3.9 NAO sao
-- tocados por esta entrega; nenhum valor, mediana ou ligacao e semeado.
--
-- Conteudo (espelho das classes de br.com.ziipsan.reporting.panels):
--   * panel_indicator_bindings: ligacao DECLARADA painel -> indicador
--     do registro da P3.41 (JOR-19), por tenant. Nenhuma fonte lista os
--     indicadores de cada painel, logo NENHUMA ligacao nasce pronta: a
--     implantacao declara as linhas e o StandardPanelService valida cada
--     codigo contra o registro na montagem (codigo inexistente propaga
--     INDICATOR_NOT_FOUND do registro — ligacao pendurada falha nomeada,
--     nao vira zero). Sem FK para indicator_definitions pelo mesmo
--     precedente da ED-48 (sinisa_indicator_links): o codigo e referencia
--     logica conferida pelo servico, nao chave presumida.
--   * panel_benchmark_slices: o recorte de benchmark DECLARADO do tenant
--     (porte, regiao, natureza juridica — GES-29 da ED-48), uma linha por
--     tenant. Rotulos sao os do cadastro e tem de casar com os da base
--     publica importada; nenhum rotulo nasce pronto ([A DEFINIR] por
--     tenant na implantacao).
-- Licao da C3 aplicada: as duas tabelas tem UNIQUE (tenant_id, id),
-- prontas para serem pai de FK composta (tenant_id, X) futura.
-- Valor, meta e semaforo NAO ganham tabela aqui: sao os da V15_2
-- (indicator_definitions/targets/closings da P3.41), lidos pelo painel.

CREATE TABLE IF NOT EXISTS panel_indicator_bindings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    panel VARCHAR(20) NOT NULL
        CHECK (panel IN ('ARRECADACAO', 'ATENDIMENTO', 'COBRANCA', 'FATURAMENTO',
                         'MEDICAO', 'OPERACIONAL', 'ESTRATEGICO')),
    indicator_code VARCHAR(60) NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0 CHECK (display_order >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_panel_indicator_bindings_tenant_id UNIQUE (tenant_id, id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_panel_indicator_bindings_key
    ON panel_indicator_bindings(tenant_id, panel, indicator_code);
CREATE INDEX IF NOT EXISTS idx_panel_indicator_bindings_panel
    ON panel_indicator_bindings(tenant_id, panel, display_order);

CREATE TABLE IF NOT EXISTS panel_benchmark_slices (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    size_class VARCHAR(80) NOT NULL,
    region VARCHAR(80) NOT NULL,
    legal_nature VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_panel_benchmark_slices_tenant_id UNIQUE (tenant_id, id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_panel_benchmark_slices_tenant
    ON panel_benchmark_slices(tenant_id);

SELECT register_tenant_table('panel_indicator_bindings', true, false);
SELECT register_tenant_table('panel_benchmark_slices', true, false);

-- ============================================================================
-- V114 — II-6: DMA agregado com balanco hidrico exacto, VMN e ranking de
-- DMC por retorno (Onda II do PDCA-01, decisao 64).
--
-- NUMERACAO (justificacao integral: PLANO-CICLO-4.md seccao 2, pacote
-- pdca-gis-ciclo-4; renumeracao: REVISAO-CICLO-4.md seccao 4.3 e
-- RELATORIO-COMPLEMENTO-CICLO-4.md): este SQL vivia em db/pendente/ como
-- II-6__dma_aggregation.sql, com a faixa V22_x reservada no cabecalho.
-- A faixa esta ENCERRADA: a NOTA-INTEGRACAO-NUMERACAO.md (autoritativa,
-- 08/11/2026) e a politica da P3.12 (TenantMigrationVersionPolicy)
-- mandam TODA migration nova de tenant para a sequencia global unica
-- aberta pela V100 — versao maior que a maior publicada, sem faixas de
-- modulo. Maior publicada na base desta numeracao: V112 (lista
-- migration-policy/published-tenant-migrations.txt, 113 entradas).
-- O produtor numerou este SQL V113; a revisao independente provou em BD
-- que a FK para loss_dmc_profiles (criada so pela ED-85, entao pendente)
-- tornava essa numeracao inaplicavel sozinha, e recomendou numerar
-- ED-85 e II-6 NO MESMO ACTO. E esse o acto desta numeracao final:
-- ED-85 = V113 (V113__losses_dmc_balance.sql, neste mesmo modulo) e
-- II-6 = V114, o numero imediatamente seguinte (verificado livre no
-- repo: 0 ocorrencias). A lista de publicadas e actualizada no mesmo
-- acto com as duas linhas, nesta ordem (nota autoritativa, seccao 2).
--
-- DEPENDENCIA DECLARADA E SATISFEITA NO MESMO ACTO: a FK
-- fk_ii6_dma_member_dmc referencia loss_dmc_profiles, tabela criada
-- pela ED-85, numerada V113 neste mesmo acto e aplicada imediatamente
-- antes desta migration na travessia. Os DMCs membros sao perfis da
-- ED-85, referenciados pela FK composta; nenhum DMC e recadastrado.
-- Sem a V113 aplicada, esta migration falha na FK em travessia fresca
-- (provado em BD na revisao do Ciclo 4: falha na linha 69 do SQL sem a
-- ED-85; 0 erros com ela) — declarado, nao escondido.
--
-- BASES QUE JA CORRERAM O FICHEIRO PENDENTE: nenhuma pela travessia
-- (o TenantMigrator so aplica db/migration/tenant; o historico
-- Flyway nao contem o II-6). O SQL e defensivo (CREATE TABLE IF NOT
-- EXISTS + register_tenant_table), pelo que uma aplicacao manual
-- previa fora da travessia e absorvida sem erro.
--
-- Licao da C3 aplicada: TODA FK composta (tenant_id, X) tem a UNIQUE
-- (tenant_id, id) correspondente declarada NESTA entrega.
-- NENHUM DADO SEMEADO: tarifa do ranking e parametro [A DEFINIR];
-- nenhum valor de perda "aceitavel" nasce aqui.
-- ============================================================================

SELECT set_config('app.current_tenant', '${tenant_id}', true);

-- Definicao do DMA agregado (agrupamento nomeado de DMCs do mesmo setor).
CREATE TABLE IF NOT EXISTS ii6_dma_definitions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    dma_code TEXT NOT NULL,
    sector_code TEXT NOT NULL,
    CONSTRAINT uq_ii6_dma_definitions_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_ii6_dma_definitions_tenant_code UNIQUE (tenant_id, dma_code)
);
SELECT register_tenant_table('ii6_dma_definitions');

-- Membros do DMA: DMCs da ED-85 (loss_dmc_profiles), um DMC num so DMA
-- desta tabela pela UNIQUE (tenant_id, dmc_profile_id).
CREATE TABLE IF NOT EXISTS ii6_dma_members (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    dma_definition_id UUID NOT NULL,
    dmc_profile_id UUID NOT NULL,
    CONSTRAINT uq_ii6_dma_members_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_ii6_dma_member UNIQUE (tenant_id, dmc_profile_id),
    CONSTRAINT fk_ii6_dma_member_dma FOREIGN KEY (tenant_id, dma_definition_id)
        REFERENCES ii6_dma_definitions (tenant_id, id),
    CONSTRAINT fk_ii6_dma_member_dmc FOREIGN KEY (tenant_id, dmc_profile_id)
        REFERENCES loss_dmc_profiles (tenant_id, id)
);
SELECT register_tenant_table('ii6_dma_members');

-- Snapshot do balanco agregado do DMA por periodo (aritmetica exacta em
-- NUMERIC; perda real NULL = componente ausente DECLARADA, com a lista de
-- ausentes gravada — nunca estimada).
CREATE TABLE IF NOT EXISTS ii6_dma_period_balances (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    dma_definition_id UUID NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL CHECK (period_end >= period_start),
    member_count INTEGER NOT NULL CHECK (member_count > 0),
    net_input_m3 NUMERIC NOT NULL,
    billed_total_m3 NUMERIC NOT NULL,
    authorized_consumption_m3 NUMERIC NOT NULL,
    total_losses_m3 NUMERIC NOT NULL,
    apparent_losses_m3 NUMERIC,
    real_losses_m3 NUMERIC,
    non_revenue_water_m3 NUMERIC NOT NULL,
    absent_components TEXT NOT NULL DEFAULT '',
    CONSTRAINT uq_ii6_dma_balances_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_ii6_dma_balance_period UNIQUE (tenant_id, dma_definition_id, period_start, period_end),
    CONSTRAINT fk_ii6_dma_balance_dma FOREIGN KEY (tenant_id, dma_definition_id)
        REFERENCES ii6_dma_definitions (tenant_id, id)
);
SELECT register_tenant_table('ii6_dma_period_balances');

-- ============================================================================
-- II-6 (PENDENTE DE NUMERACAO): DMA agregado com balanco hidrico exacto,
-- VMN e ranking de DMC por retorno (Onda II do PDCA-01, decisao 64).
-- SQL sem numero Flyway: prefixo II-6__ em db/pendente/ (regra do pacote).
-- Faixa do modulo losses (a mesma declarada pela ED-85: V22_x) — o numero
-- concreto so e atribuido na integracao (P-II6-CI).
-- Depende de: ED-85 (loss_dmc_profiles — os DMCs membros sao perfis da
-- ED-85, referenciados pela FK composta; nenhum DMC e recadastrado).
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

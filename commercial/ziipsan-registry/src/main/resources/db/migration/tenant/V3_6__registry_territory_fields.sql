-- Cadastro (P0.16, opcao 1): modelar no backend os campos de territorio que a tela ja enviava
-- e eram descartados em silencio (fail-on-unknown-properties=false).
-- Version: 3.6.0
--
-- Origem dos campos (frontend, a especificacao dos nomes/tipos):
--   * territoryDefinitions.ts (CatalogCrud/toPayload envia TODOS os campos declarados, com null
--     quando vazio) e services/registry/types.ts (NetworkCoverage = 'NONE' | 'PARTIAL' | 'FULL').
--   * localidade: headquarters, computerized (booleanos; a tela marca "So uma localidade pode ser
--     a sede", mas NENHUMA unicidade de sede e imposta aqui: nao ha decisao do dono sobre o que
--     acontece com a sede anterior, e inventar a regra quebraria edicoes legitimas).
--   * setor: alternative (booleano).
--   * quadra: waterNetwork/sewerNetwork (enum de 3 valores, NAO booleano), censusSector (texto,
--     ate 20, como o maxLength da tela) e autoIncrementLot (booleano).
--   * rota: localityId, sectorId (referencias; as tabelas existem desde V3_1, por isso FK
--     composta por tenant, no padrao de V3_1) e maxUnits (inteiro, minimo 1).
--
-- Rede da quadra (o ponto delicado): o backend tinha so os booleanos has_water_network /
-- has_sewer_network, e o enum tem um terceiro valor (PARTIAL) que booleano nao expressa. Por
-- isso o enum ganha coluna propria e os booleanos sao MANTIDOS (as regras de situacao de ligacao
-- os consultam). Mapeamento, documentado tambem em NetworkCoverage.java:
--   * escrita com enum: has = (enum <> 'NONE') — PARTIAL e FULL contam como "tem rede";
--   * escrita so com booleano (payload legado): true -> 'FULL', false -> 'NONE' (aproximacao:
--     booleano nao distingue parcial de total; so o enum expressa PARTIAL);
--   * backfill abaixo: linhas pre-V3_6 seguem a mesma regra (true -> 'FULL', false -> 'NONE').
-- Nenhuma coluna nova e NOT NULL sem default: localidade/setor/quadra/rota antigos continuam
-- validos, e payload sem os novos campos continua funcionando.
--
-- Independente: nao depende de tabelas de nenhuma outra frente (so V3_1). Nenhuma tabela nova,
-- portanto nenhum register_tenant_table.

-- ------------------------------------------------------------------ localidades
ALTER TABLE localities ADD COLUMN IF NOT EXISTS headquarters BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE localities ADD COLUMN IF NOT EXISTS computerized BOOLEAN NOT NULL DEFAULT FALSE;

-- ------------------------------------------------------------------ setores comerciais
ALTER TABLE commercial_sectors ADD COLUMN IF NOT EXISTS alternative BOOLEAN NOT NULL DEFAULT FALSE;

-- ------------------------------------------------------------------ quadras
ALTER TABLE blocks ADD COLUMN IF NOT EXISTS water_network VARCHAR(10) NOT NULL DEFAULT 'NONE';
ALTER TABLE blocks ADD COLUMN IF NOT EXISTS sewer_network VARCHAR(10) NOT NULL DEFAULT 'NONE';
ALTER TABLE blocks ADD COLUMN IF NOT EXISTS census_sector VARCHAR(20);
ALTER TABLE blocks ADD COLUMN IF NOT EXISTS auto_increment_lot BOOLEAN NOT NULL DEFAULT FALSE;

-- Backfill do enum a partir do booleano legado (ver mapeamento acima). O DEFAULT 'NONE' ja
-- cobre as linhas com has_* = false; aqui so as com rede passam a 'FULL'.
UPDATE blocks SET water_network = 'FULL' WHERE has_water_network AND water_network = 'NONE';
UPDATE blocks SET sewer_network = 'FULL' WHERE has_sewer_network AND sewer_network = 'NONE';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_blocks_water_network') THEN
        ALTER TABLE blocks ADD CONSTRAINT chk_blocks_water_network
            CHECK (water_network IN ('NONE', 'PARTIAL', 'FULL'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_blocks_sewer_network') THEN
        ALTER TABLE blocks ADD CONSTRAINT chk_blocks_sewer_network
            CHECK (sewer_network IN ('NONE', 'PARTIAL', 'FULL'));
    END IF;
END
$$;

-- ------------------------------------------------------------------ rotas de leitura
ALTER TABLE reading_routes ADD COLUMN IF NOT EXISTS locality_id UUID;
ALTER TABLE reading_routes ADD COLUMN IF NOT EXISTS sector_id UUID;
ALTER TABLE reading_routes ADD COLUMN IF NOT EXISTS max_units INT;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_route_locality') THEN
        ALTER TABLE reading_routes ADD CONSTRAINT fk_route_locality
            FOREIGN KEY (tenant_id, locality_id) REFERENCES localities(tenant_id, id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_route_sector') THEN
        ALTER TABLE reading_routes ADD CONSTRAINT fk_route_sector
            FOREIGN KEY (tenant_id, sector_id) REFERENCES commercial_sectors(tenant_id, id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_routes_max_units') THEN
        ALTER TABLE reading_routes ADD CONSTRAINT chk_routes_max_units
            CHECK (max_units IS NULL OR max_units >= 1);
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_routes_locality ON reading_routes(tenant_id, locality_id);
CREATE INDEX IF NOT EXISTS idx_routes_sector ON reading_routes(tenant_id, sector_id);

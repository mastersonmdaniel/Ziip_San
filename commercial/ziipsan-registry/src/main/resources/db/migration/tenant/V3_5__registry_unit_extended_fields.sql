-- Registry Module Migration for Tenant Schema
-- Version: 3.5.0
-- Description: P0.16 opcao 1 (decisao do dono, 2026-11-02): modelar no backend os campos
--   que o PropertyWizard ja envia em POST/PUT /consumer-units e que ate aqui eram
--   descartados em silencio (fail-on-unknown-properties=false). Nada aqui muda regra
--   de validacao existente e nada do que ja era opcional vira obrigatorio: todas as
--   colunas novas sao anulaveis.
--
-- Origem dos nomes/tipos: frontend/src/pages/cadastro/propertyForm.ts
--   (toConsumerUnitInput/stepPayload) e frontend/src/services/registry/types.ts
--   (ConsumerUnitInput/ConsumerUnit). O frontend e a especificacao dos nomes/tipos.
--
-- Como cada campo foi modelado:
--   * Escalar -> coluna em consumer_units (ou unit_subcategories, quando e da linha
--     de subcategoria). Numeric(10,2) segue built_area_m2 (V3_1).
--   * Referencia cuja tabela EXISTE -> coluna UUID + FK composta por tenant:
--     delivery_route_id e alt_route_id -> reading_routes; parent_unit_id ->
--     consumer_units (auto-FK). reading_routes e consumer_units ja tem a unica
--     (tenant_id, id) que a FK composta exige (V3_1).
--   * Referencia cuja tabela NAO EXISTE no backend -> coluna UUID anulavel, SEM FK,
--     e a referencia fica PENDENTE (registrada abaixo e em docs): block_face_id
--     (block-faces), delivery_mode_id (bill-delivery-modes), sewer_discharge_id
--     (sewer-discharges), profile_id (property-profiles), water_source_id
--     (water-sources), well_type_id (well-types), pavement_street_id e
--     pavement_sidewalk_id (pavements). Nenhuma tabela de dominio nova foi criada
--     fora do escopo desta frente; quando o catalogo existir, a FK podera ser
--     acrescentada e o Ref da resposta passara a trazer codigo/nome. Ate la o
--     valor e aceito como esta e devolvido como Ref(id, null, null), para o
--     round-trip (edicao) nao perder o id.
--   * physical (objeto com 4 escalares) -> 4 colunas em consumer_units, nao JSON:
--     o projeto guarda composicao em colunas proprias quando os membros sao
--     escalares (ex.: built_area_m2, due_day); JSONB fica para extensao generica
--     (properties, V1_1), que aqui nao se aplica.
--   * externalIds {iptu} (unico membro hoje) -> coluna external_iptu VARCHAR(30),
--     o maximo do formulario (textField max 30). Se externalIds ganhar outros
--     membros, a coluna vira tabela/colunas proprias na frente que os modelar.
--   * subcategories[].activity_branch_ids (lista de UUIDs) -> tabela propria
--     unit_subcategory_activity_branches, no padrao das composicoes do modulo
--     (unit_customers, unit_subcategories): linha por ramo, FK para a unidade e
--     para a subcategoria, register_tenant_table (RLS forcada + auditoria).
--     A FK para activity-branches NAO existe porque o catalogo nao existe;
--     activity_branch_id fica como UUID, referencia pendente como as acima.
--   * draftId (identificador transitorio do wizard) -> coluna anulavel, SEM FK:
--     os endpoints drafts/validate-step NAO existem no backend (404 hoje) e NAO
--     sao criados aqui (fora do escopo); a coluna so guarda a referencia que o
--     frontend enviou, e a resposta a devolve como os demais campos (o tipo
--     ConsumerUnit do frontend nao a tipa, entao a edicao a ignora, o que
--     equivale ao "rascunho descartado quando o imovel e gravado").
--   * address.coordinates (aninhado) -> NAO precisa de coluna nova: addresses
--     ja tem coordinates GEOMETRY(Point, 4674) desde V1_1 e a entidade Address
--     ja a mapeia; so faltava o DTO aceitar e o servico gravar/ler. A coluna
--     consumer_units.coordinates (V1_1) continua sem uso pelo cadastro: a
--     coordenada do frontend pertence ao endereco, nao a unidade.
--
-- Independencia: esta migration nao depende de tabelas de nenhuma outra frente
-- (V3_4/V3_6/V3_7). Reescrever migration publicada e proibido; por isso tudo
-- aqui e ADD COLUMN / CREATE TABLE novo.

-- ========================================================================
-- UNIDADE CONSUMIDORA: campos escalares e referencias
-- ========================================================================

ALTER TABLE consumer_units ADD COLUMN IF NOT EXISTS block_face_id UUID;
ALTER TABLE consumer_units ADD COLUMN IF NOT EXISTS lot_frontage_m NUMERIC(10,2) CHECK (lot_frontage_m >= 0);
ALTER TABLE consumer_units ADD COLUMN IF NOT EXISTS delivery_mode_id UUID;
ALTER TABLE consumer_units ADD COLUMN IF NOT EXISTS sewer_discharge_id UUID;
ALTER TABLE consumer_units ADD COLUMN IF NOT EXISTS profile_id UUID;
ALTER TABLE consumer_units ADD COLUMN IF NOT EXISTS water_source_id UUID;
ALTER TABLE consumer_units ADD COLUMN IF NOT EXISTS well_type_id UUID;
ALTER TABLE consumer_units ADD COLUMN IF NOT EXISTS pavement_street_id UUID;
ALTER TABLE consumer_units ADD COLUMN IF NOT EXISTS pavement_sidewalk_id UUID;
ALTER TABLE consumer_units ADD COLUMN IF NOT EXISTS physical_garden BOOLEAN;
ALTER TABLE consumer_units ADD COLUMN IF NOT EXISTS physical_lower_reservoir_m3 NUMERIC(10,2) CHECK (physical_lower_reservoir_m3 >= 0);
ALTER TABLE consumer_units ADD COLUMN IF NOT EXISTS physical_upper_reservoir_m3 NUMERIC(10,2) CHECK (physical_upper_reservoir_m3 >= 0);
ALTER TABLE consumer_units ADD COLUMN IF NOT EXISTS physical_pool_m3 NUMERIC(10,2) CHECK (physical_pool_m3 >= 0);
ALTER TABLE consumer_units ADD COLUMN IF NOT EXISTS usage_points INT CHECK (usage_points >= 0);
ALTER TABLE consumer_units ADD COLUMN IF NOT EXISTS residents INT CHECK (residents >= 0);
ALTER TABLE consumer_units ADD COLUMN IF NOT EXISTS external_iptu VARCHAR(30);
ALTER TABLE consumer_units ADD COLUMN IF NOT EXISTS delivery_route_id UUID;
ALTER TABLE consumer_units ADD COLUMN IF NOT EXISTS delivery_sequence INT CHECK (delivery_sequence IS NULL OR delivery_sequence >= 1);
ALTER TABLE consumer_units ADD COLUMN IF NOT EXISTS alt_route_id UUID;
ALTER TABLE consumer_units ADD COLUMN IF NOT EXISTS parent_unit_id UUID;
ALTER TABLE consumer_units ADD COLUMN IF NOT EXISTS draft_id UUID;

-- FKs so para tabelas que existem. As demais referencias ficam pendentes (ver
-- cabecalho): acrescentar a FK quando o catalogo existir, sem tocar nestas.
ALTER TABLE consumer_units ADD CONSTRAINT fk_unit_delivery_route
    FOREIGN KEY (tenant_id, delivery_route_id) REFERENCES reading_routes(tenant_id, id);
ALTER TABLE consumer_units ADD CONSTRAINT fk_unit_alt_route
    FOREIGN KEY (tenant_id, alt_route_id) REFERENCES reading_routes(tenant_id, id);
ALTER TABLE consumer_units ADD CONSTRAINT fk_unit_parent
    FOREIGN KEY (tenant_id, parent_unit_id) REFERENCES consumer_units(tenant_id, id);

CREATE INDEX IF NOT EXISTS idx_units_delivery_route ON consumer_units(tenant_id, delivery_route_id);
CREATE INDEX IF NOT EXISTS idx_units_alt_route ON consumer_units(tenant_id, alt_route_id);
CREATE INDEX IF NOT EXISTS idx_units_parent ON consumer_units(tenant_id, parent_unit_id);

-- ========================================================================
-- LINHA DE SUBCATEGORIA: unidades particulares e coletivas
-- ========================================================================

ALTER TABLE unit_subcategories ADD COLUMN IF NOT EXISTS private_units INT CHECK (private_units >= 0);
ALTER TABLE unit_subcategories ADD COLUMN IF NOT EXISTS collective_units INT CHECK (collective_units >= 0);

-- ========================================================================
-- RAMOS DE ATIVIDADE DA LINHA DE SUBCATEGORIA (lista de UUIDs)
-- ========================================================================

CREATE TABLE IF NOT EXISTS unit_subcategory_activity_branches (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    subcategory_id UUID NOT NULL,
    activity_branch_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE unit_subcategory_activity_branches ADD CONSTRAINT fk_usab_unit
    FOREIGN KEY (tenant_id, unit_id) REFERENCES consumer_units(tenant_id, id);
ALTER TABLE unit_subcategory_activity_branches ADD CONSTRAINT fk_usab_subcategory
    FOREIGN KEY (tenant_id, subcategory_id) REFERENCES subcategories(tenant_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_usab_unique
    ON unit_subcategory_activity_branches(tenant_id, unit_id, subcategory_id, activity_branch_id);
CREATE INDEX IF NOT EXISTS idx_usab_unit
    ON unit_subcategory_activity_branches(tenant_id, unit_id);

SELECT register_tenant_table('unit_subcategory_activity_branches');

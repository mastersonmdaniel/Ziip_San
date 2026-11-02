-- P0.16 opcao 1 (frente catalogos): modelar no backend os campos que o frontend ja envia
-- em catalogDefinitions.ts e que hoje sao descartados em silencio (fail-on-unknown=false).
-- Escopo: SOMENTE os 4 catalogos com tabela/rota (categories, subcategories, water_statuses,
-- sewer_statuses). Nenhum campo novo e obrigatorio: payloads sem os novos campos continuam
-- funcionando (colunas escalares anulaveis; flags booleanas NOT NULL DEFAULT FALSE, no padrao
-- das flags ja existentes). Nenhuma regra de validacao existente muda; `code` continua obrigatorio.
--
-- Distincoes deliberadas (nao fundir por conta propria):
--   * water_statuses/sewer_statuses.has_network e has_connection sao campos PROPRIOS do frontend
--     ("Existe rede" / "Existe ligacao"). Nao se confundem com requires_network ("so vale em
--     quadra com a rede") nem com connected (ligacao fisica ja modelada): os sentidos nao sao
--     provadamente identicos, por isso as colunas antigas ficam intactas e as novas convivem.
--   * water_statuses.minimum_billing_consumption (numerico, m3) nao e minimum_consumption
--     (flag booleana: cobra consumo minimo mesmo sem uso). Idem em sewer_statuses:
--     minimum_billing_volume (numerico) nao e minimum_consumption (flag).
--   * categories.minimum_consumption (numerico) nao tem correspondente previo em categories.
--
-- Tabelas ja registradas em V3_1 (register_tenant_table + RLS): nao ha tabela nova, logo nao
-- ha novo register_tenant_table. Migracao independente: nao depende de tabelas de outra frente.

-- Categorias: abbreviation, minimumConsumption, economiesSoftLimit, requiresSpecialPermission
ALTER TABLE categories ADD COLUMN IF NOT EXISTS abbreviation VARCHAR(5);
ALTER TABLE categories ADD COLUMN IF NOT EXISTS minimum_consumption INT;
ALTER TABLE categories ADD COLUMN IF NOT EXISTS economies_soft_limit INT;
ALTER TABLE categories ADD COLUMN IF NOT EXISTS requires_special_permission BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE categories ADD CONSTRAINT chk_categories_minimum_consumption CHECK (minimum_consumption IS NULL OR minimum_consumption >= 0);
ALTER TABLE categories ADD CONSTRAINT chk_categories_economies_soft_limit CHECK (economies_soft_limit IS NULL OR economies_soft_limit >= 1);

-- Subcategorias: abbreviation, inspectionFactor, seasonalSupply (category_id ja existe, com FK)
ALTER TABLE subcategories ADD COLUMN IF NOT EXISTS abbreviation VARCHAR(20);
ALTER TABLE subcategories ADD COLUMN IF NOT EXISTS inspection_factor INT;
ALTER TABLE subcategories ADD COLUMN IF NOT EXISTS seasonal_supply BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE subcategories ADD CONSTRAINT chk_subcategories_inspection_factor CHECK (inspection_factor IS NULL OR inspection_factor >= 0);

-- Situacoes de agua: abbreviation, minimumBillingConsumption, hasNetwork, hasConnection
ALTER TABLE water_statuses ADD COLUMN IF NOT EXISTS abbreviation VARCHAR(5);
ALTER TABLE water_statuses ADD COLUMN IF NOT EXISTS minimum_billing_consumption INT;
ALTER TABLE water_statuses ADD COLUMN IF NOT EXISTS has_network BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE water_statuses ADD COLUMN IF NOT EXISTS has_connection BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE water_statuses ADD CONSTRAINT chk_water_min_billing_consumption CHECK (minimum_billing_consumption IS NULL OR minimum_billing_consumption >= 0);

-- Situacoes de esgoto: abbreviation, minimumBillingVolume, hasNetwork, hasConnection
ALTER TABLE sewer_statuses ADD COLUMN IF NOT EXISTS abbreviation VARCHAR(5);
ALTER TABLE sewer_statuses ADD COLUMN IF NOT EXISTS minimum_billing_volume NUMERIC(10, 2);
ALTER TABLE sewer_statuses ADD COLUMN IF NOT EXISTS has_network BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE sewer_statuses ADD COLUMN IF NOT EXISTS has_connection BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE sewer_statuses ADD CONSTRAINT chk_sewer_min_billing_volume CHECK (minimum_billing_volume IS NULL OR minimum_billing_volume >= 0);

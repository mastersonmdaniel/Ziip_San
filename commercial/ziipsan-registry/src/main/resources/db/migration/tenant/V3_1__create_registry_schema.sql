-- Registry Module Migration for Tenant Schema
-- Version: 3.1.0
-- Description: Cadastro comercial: hierarquia territorial, catalogos, clientes, vinculo
-- cliente-imovel e a correcao do modelo de consumer_units criado em V1_1.
--
-- Por que alterar consumer_units (V1_1) em vez de recria-la:
--   * owner_id e responsible_id apontavam para USERS (usuarios do sistema). Proprietario e
--     responsavel de um imovel sao CLIENTES, nao usuarios. O vinculo correto e temporal (com
--     vigencia e papel) e fica em unit_customers.
--   * address JSONB duplicava o endereco da tabela addresses.
--   * category_id, water_economies e sewage_economies guardavam em duplicidade o que e derivado de
--     unit_subcategories (categoria principal e total de economias se calculam, nao se digitam).
-- Nao ha dado em producao, entao a correcao e feita agora, antes do primeiro cliente.
--
-- NENHUM catalogo e semeado. Categorias, subcategorias e situacoes de ligacao sao politica do
-- prestador (inclusive os flags de cada situacao); valores padrao inventados seriam tratados como
-- dado real. Ver docs/DIARIO-DE-BORDA.md, secao 15.4.

-- ========================================================================
-- HIERARQUIA TERRITORIAL
-- ========================================================================

CREATE TABLE IF NOT EXISTS localities (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code INT NOT NULL CHECK (code >= 0),
    name VARCHAR(100) NOT NULL,
    municipality_ibge VARCHAR(7),
    blocked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE localities ADD CONSTRAINT uq_localities_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_localities_tenant_code ON localities(tenant_id, code);

CREATE TABLE IF NOT EXISTS commercial_sectors (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    locality_id UUID NOT NULL,
    code INT NOT NULL CHECK (code >= 0),
    name VARCHAR(100) NOT NULL,
    blocked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE commercial_sectors ADD CONSTRAINT uq_commercial_sectors_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE commercial_sectors ADD CONSTRAINT fk_sector_locality
    FOREIGN KEY (tenant_id, locality_id) REFERENCES localities(tenant_id, id);
CREATE UNIQUE INDEX idx_sectors_locality_code ON commercial_sectors(tenant_id, locality_id, code);

CREATE TABLE IF NOT EXISTS reading_routes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code INT NOT NULL CHECK (code >= 0),
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE reading_routes ADD CONSTRAINT uq_reading_routes_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_reading_routes_code ON reading_routes(tenant_id, code);

-- Quadra: tem rede de agua/esgoto? e o DMC. As situacoes de ligacao so valem se a quadra tem a rede.
-- dma_id referencia dma_district (modulo losses, V2_2): este modulo so e implantado junto dele.
CREATE TABLE IF NOT EXISTS blocks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    sector_id UUID NOT NULL,
    code INT NOT NULL CHECK (code >= 0),
    reading_route_id UUID,
    dma_id UUID,
    has_water_network BOOLEAN NOT NULL DEFAULT FALSE,
    has_sewer_network BOOLEAN NOT NULL DEFAULT FALSE,
    blocked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE blocks ADD CONSTRAINT uq_blocks_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE blocks ADD CONSTRAINT fk_block_sector
    FOREIGN KEY (tenant_id, sector_id) REFERENCES commercial_sectors(tenant_id, id);
ALTER TABLE blocks ADD CONSTRAINT fk_block_route
    FOREIGN KEY (tenant_id, reading_route_id) REFERENCES reading_routes(tenant_id, id);
ALTER TABLE blocks ADD CONSTRAINT fk_block_dma
    FOREIGN KEY (tenant_id, dma_id) REFERENCES dma_district(tenant_id, id);
CREATE UNIQUE INDEX idx_blocks_sector_code ON blocks(tenant_id, sector_id, code);
CREATE INDEX idx_blocks_dma ON blocks(tenant_id, dma_id);

-- ========================================================================
-- CATALOGOS (sem semente)
-- ========================================================================

CREATE TABLE IF NOT EXISTS categories (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    public_entity BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE categories ADD CONSTRAINT uq_categories_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_categories_code ON categories(tenant_id, code);

CREATE TABLE IF NOT EXISTS subcategories (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    category_id UUID NOT NULL,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE subcategories ADD CONSTRAINT uq_subcategories_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE subcategories ADD CONSTRAINT fk_subcategory_category
    FOREIGN KEY (tenant_id, category_id) REFERENCES categories(tenant_id, id);
CREATE UNIQUE INDEX idx_subcategories_code ON subcategories(tenant_id, code);

-- Situacao de ligacao de agua e de esgoto: catalogo com flags de comportamento.
-- bills: gera cobranca; requires_network: so vale em quadra com a rede; connected: tem ligacao
-- fisica; minimum_consumption: cobra consumo minimo mesmo sem uso.
CREATE TABLE IF NOT EXISTS water_statuses (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    bills BOOLEAN NOT NULL DEFAULT FALSE,
    requires_network BOOLEAN NOT NULL DEFAULT FALSE,
    connected BOOLEAN NOT NULL DEFAULT FALSE,
    supplies BOOLEAN NOT NULL DEFAULT FALSE,
    minimum_consumption BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE water_statuses ADD CONSTRAINT uq_water_statuses_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_water_statuses_code ON water_statuses(tenant_id, code);

CREATE TABLE IF NOT EXISTS sewer_statuses (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    bills BOOLEAN NOT NULL DEFAULT FALSE,
    requires_network BOOLEAN NOT NULL DEFAULT FALSE,
    connected BOOLEAN NOT NULL DEFAULT FALSE,
    minimum_consumption BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE sewer_statuses ADD CONSTRAINT uq_sewer_statuses_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_sewer_statuses_code ON sewer_statuses(tenant_id, code);

-- ========================================================================
-- CLIENTES
-- ========================================================================
-- O documento (CPF/CNPJ) NUNCA e guardado em claro: document_ciphertext (AES-256-GCM por tenant),
-- document_hash (indice cego, HMAC-SHA-256, para busca e unicidade) e document_masked (so o miolo,
-- para listas). Quem grava calcula os tres (ver ziipsan-security e DocumentNumber).

CREATE TABLE IF NOT EXISTS customers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    person_type VARCHAR(2) NOT NULL CHECK (person_type IN ('PF', 'PJ')),
    name VARCHAR(200) NOT NULL,
    trade_name VARCHAR(200),
    document_ciphertext TEXT,
    document_hash VARCHAR(64),
    document_masked VARCHAR(30),
    birth_date DATE,
    due_day SMALLINT CHECK (due_day BETWEEN 1 AND 31),
    deleted_at TIMESTAMPTZ,
    deleted_reason VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK ((document_ciphertext IS NULL) = (document_hash IS NULL))
);
ALTER TABLE customers ADD CONSTRAINT uq_customers_tenant_id UNIQUE (tenant_id, id);
-- Um documento por cliente ativo. Excluido (logico) libera o documento.
CREATE UNIQUE INDEX idx_customers_document ON customers(tenant_id, document_hash)
    WHERE document_hash IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX idx_customers_name ON customers(tenant_id, lower(name));

-- E-mail e telefone. consent_id liga ao consentimento (lgpd_consent) quando a base legal for esse.
CREATE TABLE IF NOT EXISTS customer_contacts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    kind VARCHAR(10) NOT NULL CHECK (kind IN ('EMAIL', 'PHONE')),
    value VARCHAR(255) NOT NULL,
    label VARCHAR(50),
    primary_contact BOOLEAN NOT NULL DEFAULT FALSE,
    consent_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE customer_contacts ADD CONSTRAINT fk_contact_customer
    FOREIGN KEY (tenant_id, customer_id) REFERENCES customers(tenant_id, id);
CREATE INDEX idx_contacts_customer ON customer_contacts(tenant_id, customer_id);
-- No maximo um contato principal de cada tipo por cliente
CREATE UNIQUE INDEX idx_contacts_primary ON customer_contacts(tenant_id, customer_id, kind)
    WHERE primary_contact;

-- ========================================================================
-- ENDERECO: alinha a tabela de V1_1 ao que as entidades esperam
-- ========================================================================

ALTER TABLE addresses ADD COLUMN IF NOT EXISTS created_by UUID;
ALTER TABLE addresses ADD COLUMN IF NOT EXISTS updated_by UUID;
ALTER TABLE addresses ALTER COLUMN state TYPE VARCHAR(2);
ALTER TABLE addresses ADD CONSTRAINT uq_addresses_tenant_id UNIQUE (tenant_id, id);

-- ========================================================================
-- UNIDADE CONSUMIDORA (corrige V1_1)
-- ========================================================================

ALTER TABLE consumer_units DROP COLUMN IF EXISTS owner_id;
ALTER TABLE consumer_units DROP COLUMN IF EXISTS responsible_id;
ALTER TABLE consumer_units DROP COLUMN IF EXISTS address;
ALTER TABLE consumer_units DROP COLUMN IF EXISTS category_id;
ALTER TABLE consumer_units DROP COLUMN IF EXISTS water_economies;
ALTER TABLE consumer_units DROP COLUMN IF EXISTS sewage_economies;
ALTER TABLE consumer_units DROP COLUMN IF EXISTS water_connection_date;
ALTER TABLE consumer_units DROP COLUMN IF EXISTS sewage_connection_date;

ALTER TABLE consumer_units ALTER COLUMN dv TYPE VARCHAR(1);
ALTER TABLE consumer_units ALTER COLUMN dv SET NOT NULL;

ALTER TABLE consumer_units ADD COLUMN block_id UUID;
ALTER TABLE consumer_units ADD COLUMN lot INT CHECK (lot >= 0);
ALTER TABLE consumer_units ADD COLUMN sublot INT CHECK (sublot >= 0);
ALTER TABLE consumer_units ADD COLUMN address_id UUID;
ALTER TABLE consumer_units ADD COLUMN water_status_id UUID;
ALTER TABLE consumer_units ADD COLUMN sewer_status_id UUID;
ALTER TABLE consumer_units ADD COLUMN built_area_m2 NUMERIC(10,2) CHECK (built_area_m2 >= 0);
ALTER TABLE consumer_units ADD COLUMN due_day SMALLINT CHECK (due_day BETWEEN 1 AND 31);
ALTER TABLE consumer_units ADD COLUMN notes VARCHAR(750);
ALTER TABLE consumer_units ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE consumer_units ADD COLUMN deleted_reason VARCHAR(200);

-- status descreve o ciclo do CADASTRO (ACTIVE, PROVISIONAL); as situacoes de agua e esgoto ficam nos
-- catalogos. Um cadastro provisorio e marcado como tal, sem convencao de codigo especial de setor.
ALTER TABLE consumer_units ADD CONSTRAINT chk_consumer_units_status CHECK (status IN ('ACTIVE', 'PROVISIONAL'));
ALTER TABLE consumer_units ADD CONSTRAINT uq_consumer_units_tenant_id UNIQUE (tenant_id, id);

ALTER TABLE consumer_units ADD CONSTRAINT fk_unit_block
    FOREIGN KEY (tenant_id, block_id) REFERENCES blocks(tenant_id, id);
ALTER TABLE consumer_units ADD CONSTRAINT fk_unit_address
    FOREIGN KEY (tenant_id, address_id) REFERENCES addresses(tenant_id, id);
ALTER TABLE consumer_units ADD CONSTRAINT fk_unit_water_status
    FOREIGN KEY (tenant_id, water_status_id) REFERENCES water_statuses(tenant_id, id);
ALTER TABLE consumer_units ADD CONSTRAINT fk_unit_sewer_status
    FOREIGN KEY (tenant_id, sewer_status_id) REFERENCES sewer_statuses(tenant_id, id);
ALTER TABLE consumer_units ADD CONSTRAINT fk_unit_reading_route
    FOREIGN KEY (tenant_id, reading_route_id) REFERENCES reading_routes(tenant_id, id);

-- Unidade com quadra precisa de lote e sublote: sem isso a unicidade abaixo nao valeria (NULLs sao
-- distintos num indice unico).
ALTER TABLE consumer_units ADD CONSTRAINT chk_units_lot_with_block
    CHECK (block_id IS NULL OR (lot IS NOT NULL AND sublot IS NOT NULL));

-- A inscricao (quadra + lote + sublote) e unica entre unidades ativas. Excluida (logica) libera.
CREATE UNIQUE INDEX idx_units_inscription ON consumer_units(tenant_id, block_id, lot, sublot)
    WHERE block_id IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX idx_units_block ON consumer_units(tenant_id, block_id);
CREATE INDEX idx_units_route ON consumer_units(tenant_id, reading_route_id);

-- ========================================================================
-- VINCULO CLIENTE-IMOVEL (temporal)
-- ========================================================================

CREATE TABLE IF NOT EXISTS unit_customers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    role VARCHAR(12) NOT NULL CHECK (role IN ('OWNER', 'USER', 'RESPONSIBLE')),
    start_date DATE NOT NULL,
    end_date DATE,
    end_reason VARCHAR(200),
    show_on_bill BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (end_date IS NULL OR end_date >= start_date)
);
ALTER TABLE unit_customers ADD CONSTRAINT fk_ucust_unit
    FOREIGN KEY (tenant_id, unit_id) REFERENCES consumer_units(tenant_id, id);
ALTER TABLE unit_customers ADD CONSTRAINT fk_ucust_customer
    FOREIGN KEY (tenant_id, customer_id) REFERENCES customers(tenant_id, id);
CREATE INDEX idx_ucust_unit ON unit_customers(tenant_id, unit_id);
CREATE INDEX idx_ucust_customer ON unit_customers(tenant_id, customer_id);
-- Exatamente um nome na conta por unidade (entre os vinculos vigentes)
CREATE UNIQUE INDEX idx_ucust_bill_name ON unit_customers(tenant_id, unit_id)
    WHERE show_on_bill AND end_date IS NULL;
-- O mesmo cliente nao aparece duas vezes, com o mesmo papel, na mesma unidade, ao mesmo tempo
CREATE UNIQUE INDEX idx_ucust_current ON unit_customers(tenant_id, unit_id, customer_id, role)
    WHERE end_date IS NULL;

-- ========================================================================
-- SUBCATEGORIAS E ECONOMIAS DA UNIDADE
-- ========================================================================

CREATE TABLE IF NOT EXISTS unit_subcategories (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    subcategory_id UUID NOT NULL,
    economies INT NOT NULL CHECK (economies >= 1),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE unit_subcategories ADD CONSTRAINT fk_usub_unit
    FOREIGN KEY (tenant_id, unit_id) REFERENCES consumer_units(tenant_id, id);
ALTER TABLE unit_subcategories ADD CONSTRAINT fk_usub_subcategory
    FOREIGN KEY (tenant_id, subcategory_id) REFERENCES subcategories(tenant_id, id);
CREATE UNIQUE INDEX idx_usub_unique ON unit_subcategories(tenant_id, unit_id, subcategory_id);

-- ========================================================================
-- HISTORICO DE SITUACAO (somente insercao)
-- ========================================================================

CREATE TABLE IF NOT EXISTS unit_status_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    service_kind VARCHAR(5) NOT NULL CHECK (service_kind IN ('WATER', 'SEWER')),
    from_status_id UUID,
    to_status_id UUID NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reason VARCHAR(200),
    service_order_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE unit_status_history ADD CONSTRAINT fk_ush_unit
    FOREIGN KEY (tenant_id, unit_id) REFERENCES consumer_units(tenant_id, id);
CREATE INDEX idx_ush_unit_time ON unit_status_history(tenant_id, unit_id, changed_at DESC);

-- ========================================================================
-- ISOLAMENTO E AUDITORIA
-- ========================================================================

SELECT register_tenant_table('localities');
SELECT register_tenant_table('commercial_sectors');
SELECT register_tenant_table('reading_routes');
SELECT register_tenant_table('blocks');
SELECT register_tenant_table('categories');
SELECT register_tenant_table('subcategories');
SELECT register_tenant_table('water_statuses');
SELECT register_tenant_table('sewer_statuses');
SELECT register_tenant_table('customers');
SELECT register_tenant_table('customer_contacts');
SELECT register_tenant_table('unit_customers');
SELECT register_tenant_table('unit_subcategories');
SELECT register_tenant_table('unit_status_history');
-- Tabelas de V1_1 que passam a ter entidade: ganham version e tenant_schema
SELECT register_tenant_table('addresses');
SELECT register_tenant_table('consumer_units');

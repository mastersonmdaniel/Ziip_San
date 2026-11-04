-- ============================================================================
-- P1.3 (camada C) — R2: cadastro completo. Migration V3_12 (registry).
--
-- DESVIO DE VERSÃO (registrado, não silencioso): o plano v3.0 reserva a V3_6
-- para a P1.3, mas a V3_6 JÁ EXISTE (registry_territory_fields, P0.16) e NÃO
-- pode ser reutilizada; a V3_11 é da P1.12 e a V3_8 é da P1.41. A próxima
-- versão livre do registry é a V3_12, usada aqui. Nenhuma migration existente
-- foi renomeada, reutilizada ou reordenada. O histórico de tenant é ÚNICO e
-- ordenado por versão, então a V3_12 roda ANTES da V4_2 (billing) num fresh
-- install — o que é seguro: nada aqui depende de tabelas de billing.
--
-- CORREÇÃO R1 (gate da camada B): a R1 pedia "migration V3_6 com SET NOT NULL
-- nas 2 colunas". Como a V3_6 existe, o NOT NULL é entregue aqui e na V4_13:
--   * ESTA migration: unit_status_history.service_order_id SET NOT NULL
--     (a tabela existe desde a V3_1; a V9_1 já anunciava o campo como
--     "nullable until P1.3");
--   * V4_13 (billing, primeira livre após a V4_12 reservada): o mesmo para
--     social_tariff_enrollments.service_order_id.
-- O teste de recusa de NULL (23502) cobre AS DUAS colunas contra tenant
-- totalmente migrado (RegistryR2IT). Linhas legadas com service_order_id NULL
-- (histórico criado antes da P1.3) FAZEM ESTA MIGRATION FALHAR alto: não há
-- OS válida para atribuir retroativamente, e inventar uma seria dado falso.
-- Remediação pré-upgrade (fora desta frente): atribuir a OS real a cada
-- linha legada, ou arquivar o histórico legado, antes de rodar a V3_12.
-- Tenants novos (o caso do fresh install e dos testes) não têm linhas, e a
-- migration passa limpa.
--
-- FKs PARA TABELAS DE VERSÃO MAIOR: a V9_2 do tenant está reservada no §3 do
-- plano para a P3.4 (bloqueios de corte) e NÃO pode ser reutilizada aqui; as
-- FKs para service_orders (V9_1) e meter_installations (V5_1) chegam na
-- V9_3, primeira versão livre do tenant após a V9_1.
--
-- REGRAS DE COMPATIBILIDADE ADOTADAS (o que NÃO quebra payload antigo):
--   * CHECKs sobre linhas legadas entram como NOT VALID: valem para toda
--     escrita nova, sem reprovar o dado antigo. A validação plena (VALIDATE)
--     fica para uma frente de limpeza de dados, com decisão do dono sobre
--     cada linha reprovada — não é feita aqui às cegas.
--   * Colunas renomeadas (matricula -> registration_number, dv -> check_digit,
--     profile_id -> property_profile_id): o RENAME do PostgreSQL atualiza a
--     definição do índice único junto; nenhum valor é reescrito.
--   * Colunas removidas (tariff_type_id, meter_id, properties): nenhuma
--     entidade JPA as mapeia e nenhum outro módulo as referencia; o
--     hidrômetro passa a existir SÓ por meter_installations (metering, V5_1).
--   * O par polimórfico de addresses (entity_type/entity_id) FICA para as
--     linhas legadas; os vínculos NOVOS usam customer_addresses (cliente) e
--     consumer_units.address_id (unidade). A migração de customer_addresses
--     abaixo importa os vínculos legados de cliente.
--   * unit_customers.role FICA como espelho do system_role de role_id: o
--     serviço grava os dois juntos. Remover a coluna exigiria reescrever as
--     regras de consistência (UnitConsistency) e os testes E2E na mesma
--     frente — fora do corte desta entrega; a remoção é trabalho da P1.41.
--   * customer_types, property_profiles etc. NÃO vêm semeados: catálogo é
--     política do prestador (mesma regra da V3_1). Exceção: relation_roles é
--     semeado A PARTIR dos vínculos existentes (backfill), não inventado.
--   * Nenhum dado regulatório é inventado: ibge_municipalities nasce vazio e
--     é populado pelo tenant (importação do IBGE pela P1.40/piloto).
-- ============================================================================

-- ============================================================================
-- 1. CATÁLOGO DO IBGE (primeiro: os demais referenciam o código)
-- ============================================================================

CREATE TABLE IF NOT EXISTS ibge_municipalities (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code CHAR(7) NOT NULL CHECK (code ~ '^[0-9]{7}$'),
    name VARCHAR(100) NOT NULL,
    state CHAR(2) NOT NULL CHECK (state ~ '^[A-Z]{2}$'),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE ibge_municipalities ADD CONSTRAINT uq_ibge_municipalities_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_ibge_municipalities_code ON ibge_municipalities(tenant_id, code);

-- ============================================================================
-- 2. CATÁLOGOS DO CADASTRO (padrão V3_1: id, tenant_id, code único, name,
--    active, auditoria; nenhum seed de domínio)
-- ============================================================================

CREATE TABLE IF NOT EXISTS customer_types (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(100) NOT NULL,
    -- A que tipo de pessoa o tipo se aplica. Compatibilidade com person_type
    -- é checada no serviço (400 customerTypeId) e documentada na entidade.
    applies_to VARCHAR(4) NOT NULL DEFAULT 'BOTH' CHECK (applies_to IN ('PF', 'PJ', 'BOTH')),
    -- Ente público (para a obrigatoriedade de government_sphere).
    is_public_entity BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE customer_types ADD CONSTRAINT uq_customer_types_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_customer_types_code ON customer_types(tenant_id, code);

CREATE TABLE IF NOT EXISTS property_profiles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(100) NOT NULL,
    -- Perfil provisório: só pode estar em unidade PROVISIONAL (trigger §9).
    provisional_only BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE property_profiles ADD CONSTRAINT uq_property_profiles_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_property_profiles_code ON property_profiles(tenant_id, code);

CREATE TABLE IF NOT EXISTS relation_roles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(100) NOT NULL,
    system_role VARCHAR(12) NOT NULL CHECK (system_role IN ('OWNER', 'USER', 'RESPONSIBLE')),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE relation_roles ADD CONSTRAINT uq_relation_roles_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_relation_roles_code ON relation_roles(tenant_id, code);

CREATE TABLE IF NOT EXISTS document_exemption_reasons (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(100) NOT NULL,
    -- Lei 13.460/2017, art. 10-A: a dispensa exige permissão. Padrão protetivo:
    -- exige a permissão registry:document-exemption até o tenant decidir outro.
    requires_permission BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE document_exemption_reasons ADD CONSTRAINT uq_document_exemption_reasons_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_document_exemption_reasons_code ON document_exemption_reasons(tenant_id, code);

CREATE TABLE IF NOT EXISTS customer_delete_reasons (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE customer_delete_reasons ADD CONSTRAINT uq_customer_delete_reasons_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_customer_delete_reasons_code ON customer_delete_reasons(tenant_id, code);

CREATE TABLE IF NOT EXISTS unit_link_end_reasons (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE unit_link_end_reasons ADD CONSTRAINT uq_unit_link_end_reasons_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_unit_link_end_reasons_code ON unit_link_end_reasons(tenant_id, code);

CREATE TABLE IF NOT EXISTS sewer_discharges (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE sewer_discharges ADD CONSTRAINT uq_sewer_discharges_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_sewer_discharges_code ON sewer_discharges(tenant_id, code);

CREATE TABLE IF NOT EXISTS block_faces (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    block_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE block_faces ADD CONSTRAINT uq_block_faces_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE block_faces ADD CONSTRAINT fk_block_face_block
    FOREIGN KEY (tenant_id, block_id) REFERENCES blocks(tenant_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_block_faces_block_code ON block_faces(tenant_id, block_id, code);

CREATE TABLE IF NOT EXISTS street_names (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    municipality_ibge CHAR(7) CHECK (municipality_ibge ~ '^[0-9]{7}$'),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE street_names ADD CONSTRAINT uq_street_names_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE street_names ADD CONSTRAINT fk_street_municipality
    FOREIGN KEY (tenant_id, municipality_ibge) REFERENCES ibge_municipalities(tenant_id, code);

CREATE TABLE IF NOT EXISTS neighborhoods (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    municipality_ibge CHAR(7) CHECK (municipality_ibge ~ '^[0-9]{7}$'),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE neighborhoods ADD CONSTRAINT uq_neighborhoods_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE neighborhoods ADD CONSTRAINT fk_neighborhood_municipality
    FOREIGN KEY (tenant_id, municipality_ibge) REFERENCES ibge_municipalities(tenant_id, code);

CREATE TABLE IF NOT EXISTS consent_purposes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE consent_purposes ADD CONSTRAINT uq_consent_purposes_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_consent_purposes_code ON consent_purposes(tenant_id, code);

-- ============================================================================
-- 3. CLIENTES
-- ============================================================================

ALTER TABLE customers ADD COLUMN IF NOT EXISTS government_sphere VARCHAR(10)
    CHECK (government_sphere IN ('FEDERAL', 'STATE', 'MUNICIPAL', 'NONE'));
ALTER TABLE customers ADD COLUMN IF NOT EXISTS document_exemption_reason_id UUID;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS delete_reason_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_customer_type') THEN
        ALTER TABLE customers ADD CONSTRAINT fk_customer_type
            FOREIGN KEY (tenant_id, customer_type_id) REFERENCES customer_types(tenant_id, id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_customer_exemption_reason') THEN
        ALTER TABLE customers ADD CONSTRAINT fk_customer_exemption_reason
            FOREIGN KEY (tenant_id, document_exemption_reason_id) REFERENCES document_exemption_reasons(tenant_id, id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_customer_delete_reason') THEN
        ALTER TABLE customers ADD CONSTRAINT fk_customer_delete_reason
            FOREIGN KEY (tenant_id, delete_reason_id) REFERENCES customer_delete_reasons(tenant_id, id);
    END IF;
    -- trade_name nulo em PF. NOT VALID: linhas legadas não são reprovadas.
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_customers_trade_name_pf') THEN
        ALTER TABLE customers ADD CONSTRAINT chk_customers_trade_name_pf
            CHECK (person_type <> 'PF' OR trade_name IS NULL) NOT VALID;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_customers_government_sphere ON customers(tenant_id, government_sphere);

-- Órgão centralizador (parent_customer_id) sem ciclo: trigger, porque CHECK
-- não enxerga outras linhas. O serviço também recusa o ciclo direto (422).
CREATE OR REPLACE FUNCTION registry_no_customer_parent_cycle() RETURNS TRIGGER AS $$
DECLARE
    v_seen INT;
BEGIN
    IF NEW.parent_customer_id IS NULL THEN
        RETURN NEW;
    END IF;
    WITH RECURSIVE chain(id) AS (
        SELECT NEW.parent_customer_id
        UNION ALL
        SELECT c.parent_customer_id FROM customers c JOIN chain ch ON ch.id = c.id
        WHERE c.parent_customer_id IS NOT NULL AND c.tenant_id = NEW.tenant_id
    )
    SELECT count(*) INTO v_seen FROM chain WHERE id = NEW.id;
    IF v_seen > 0 THEN
        RAISE EXCEPTION 'customer parent cycle: % is its own ancestor', NEW.id
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_customers_parent_cycle ON customers;
CREATE TRIGGER trg_customers_parent_cycle
    BEFORE INSERT OR UPDATE OF parent_customer_id ON customers
    FOR EACH ROW EXECUTE FUNCTION registry_no_customer_parent_cycle();

-- ============================================================================
-- 4. UNIDADE CONSUMIDORA: renomes, perfil, face, esgotamento, unidade-mãe,
--    inscrição municipal, CNAE, sazonalidade, remoções
-- ============================================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = current_schema() AND table_name = 'consumer_units'
               AND column_name = 'matricula') THEN
        ALTER TABLE consumer_units RENAME COLUMN matricula TO registration_number;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = current_schema() AND table_name = 'consumer_units'
               AND column_name = 'dv') THEN
        ALTER TABLE consumer_units RENAME COLUMN dv TO check_digit;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = current_schema() AND table_name = 'consumer_units'
               AND column_name = 'profile_id') THEN
        ALTER TABLE consumer_units RENAME COLUMN profile_id TO property_profile_id;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'idx_consumer_units_tenant_matricula') THEN
        ALTER INDEX idx_consumer_units_tenant_matricula RENAME TO idx_consumer_units_tenant_registration_number;
    END IF;
END $$;

ALTER TABLE consumer_units ADD COLUMN IF NOT EXISTS parent_relation_type VARCHAR(20)
    CHECK (parent_relation_type IN ('CONDOMINIUM_MACRO', 'ANNEX'));
ALTER TABLE consumer_units ADD COLUMN IF NOT EXISTS iptu_municipality_ibge CHAR(7)
    CHECK (iptu_municipality_ibge ~ '^[0-9]{7}$');
-- Atividade econômica (CNAE) na unidade (PD-06): 7 dígitos.
ALTER TABLE consumer_units ADD COLUMN IF NOT EXISTS cnae_code VARCHAR(7)
    CHECK (cnae_code ~ '^[0-9]{7}$');
-- Marca de sazonalidade da unidade (o efeito na crítica/média é da P2.5,
-- pela chave meter.seasonal_rule, classe d).
ALTER TABLE consumer_units ADD COLUMN IF NOT EXISTS seasonal BOOLEAN NOT NULL DEFAULT FALSE;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_unit_property_profile') THEN
        ALTER TABLE consumer_units ADD CONSTRAINT fk_unit_property_profile
            FOREIGN KEY (tenant_id, property_profile_id) REFERENCES property_profiles(tenant_id, id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_unit_block_face') THEN
        ALTER TABLE consumer_units ADD CONSTRAINT fk_unit_block_face
            FOREIGN KEY (tenant_id, block_face_id) REFERENCES block_faces(tenant_id, id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_unit_sewer_discharge') THEN
        ALTER TABLE consumer_units ADD CONSTRAINT fk_unit_sewer_discharge
            FOREIGN KEY (tenant_id, sewer_discharge_id) REFERENCES sewer_discharges(tenant_id, id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_unit_iptu_municipality') THEN
        ALTER TABLE consumer_units ADD CONSTRAINT fk_unit_iptu_municipality
            FOREIGN KEY (tenant_id, iptu_municipality_ibge) REFERENCES ibge_municipalities(tenant_id, code);
    END IF;
    -- Tipo de relação exigido quando há unidade-mãe.
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_unit_parent_relation') THEN
        ALTER TABLE consumer_units ADD CONSTRAINT chk_unit_parent_relation
            CHECK ((parent_unit_id IS NULL) = (parent_relation_type IS NULL));
    END IF;
END $$;

-- Inscrição imobiliária municipal única por município (quando o município é
-- declarado; sem município declarado, a unicidade municipal não se aplica).
CREATE UNIQUE INDEX IF NOT EXISTS idx_units_municipal_iptu
    ON consumer_units(tenant_id, iptu_municipality_ibge, external_iptu)
    WHERE iptu_municipality_ibge IS NOT NULL AND external_iptu IS NOT NULL;

-- Remoções R2: hidrômetro só por meter_installations; properties (JSONB
-- genérico) sai — composição vive em colunas próprias desde a V3_5.
ALTER TABLE consumer_units DROP COLUMN IF EXISTS tariff_type_id;
ALTER TABLE consumer_units DROP COLUMN IF EXISTS meter_id;
ALTER TABLE consumer_units DROP COLUMN IF EXISTS properties;

-- Guardas da unidade-mãe e do perfil: mesma quadra, sem ciclo, perfil
-- provisório só em PROVISIONAL. Backstop do serviço (que dá 422 com o campo).
CREATE OR REPLACE FUNCTION registry_unit_guards() RETURNS TRIGGER AS $$
DECLARE
    v_parent_block UUID;
    v_seen INT;
    v_provisional BOOLEAN;
BEGIN
    IF NEW.parent_unit_id IS NOT NULL THEN
        SELECT block_id INTO v_parent_block FROM consumer_units
        WHERE tenant_id = NEW.tenant_id AND id = NEW.parent_unit_id;
        IF v_parent_block IS NULL AND NEW.block_id IS NOT NULL THEN
            RAISE EXCEPTION 'parent unit % has no block, child % has one', NEW.parent_unit_id, NEW.id
                USING ERRCODE = 'check_violation';
        END IF;
        IF v_parent_block IS NOT NULL AND NEW.block_id IS NOT NULL AND v_parent_block <> NEW.block_id THEN
            RAISE EXCEPTION 'parent unit must share the block of the child unit'
                USING ERRCODE = 'check_violation';
        END IF;
        WITH RECURSIVE chain(id) AS (
            SELECT NEW.parent_unit_id
            UNION ALL
            SELECT c.parent_unit_id FROM consumer_units c JOIN chain ch ON ch.id = c.id
            WHERE c.parent_unit_id IS NOT NULL AND c.tenant_id = NEW.tenant_id
        )
        SELECT count(*) INTO v_seen FROM chain WHERE id = NEW.id;
        IF v_seen > 0 THEN
            RAISE EXCEPTION 'consumer unit parent cycle: % is its own ancestor', NEW.id
                USING ERRCODE = 'check_violation';
        END IF;
    END IF;
    IF NEW.property_profile_id IS NOT NULL THEN
        SELECT provisional_only INTO v_provisional FROM property_profiles
        WHERE tenant_id = NEW.tenant_id AND id = NEW.property_profile_id;
        IF v_provisional AND NEW.status <> 'PROVISIONAL' THEN
            RAISE EXCEPTION 'provisional profile only allowed on PROVISIONAL units'
                USING ERRCODE = 'check_violation';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_consumer_units_guards ON consumer_units;
CREATE TRIGGER trg_consumer_units_guards
    BEFORE INSERT OR UPDATE OF parent_unit_id, block_id, property_profile_id, status ON consumer_units
    FOR EACH ROW EXECUTE FUNCTION registry_unit_guards();

-- ============================================================================
-- 5. ENDEREÇOS: IBGE, catálogos, referência, precisão, CHECKs
-- ============================================================================

ALTER TABLE addresses ADD COLUMN IF NOT EXISTS municipality_ibge CHAR(7)
    CHECK (municipality_ibge ~ '^[0-9]{7}$');
ALTER TABLE addresses ADD COLUMN IF NOT EXISTS reference_point VARCHAR(200);
-- Nível de precisão da geocodificação, gravado junto com a coordenada.
ALTER TABLE addresses ADD COLUMN IF NOT EXISTS geocode_precision VARCHAR(20)
    CHECK (geocode_precision IN ('EXACT', 'INTERPOLATED', 'CENTROID', 'MANUAL', 'UNKNOWN'));
ALTER TABLE addresses ADD COLUMN IF NOT EXISTS street_id UUID;
ALTER TABLE addresses ADD COLUMN IF NOT EXISTS neighborhood_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_address_municipality') THEN
        ALTER TABLE addresses ADD CONSTRAINT fk_address_municipality
            FOREIGN KEY (tenant_id, municipality_ibge) REFERENCES ibge_municipalities(tenant_id, code);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_address_street') THEN
        ALTER TABLE addresses ADD CONSTRAINT fk_address_street
            FOREIGN KEY (tenant_id, street_id) REFERENCES street_names(tenant_id, id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_address_neighborhood') THEN
        ALTER TABLE addresses ADD CONSTRAINT fk_address_neighborhood
            FOREIGN KEY (tenant_id, neighborhood_id) REFERENCES neighborhoods(tenant_id, id);
    END IF;
    -- CHECKs NOT VALID: valem para escrita nova, sem reprovar linhas legadas.
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_addresses_type') THEN
        ALTER TABLE addresses ADD CONSTRAINT chk_addresses_type
            CHECK (type IN ('PHYSICAL', 'BILLING', 'CORRESPONDENCE', 'ALTERNATIVE')) NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_addresses_state') THEN
        ALTER TABLE addresses ADD CONSTRAINT chk_addresses_state
            CHECK (state IS NULL OR state ~ '^[A-Z]{2}$') NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_addresses_cep') THEN
        ALTER TABLE addresses ADD CONSTRAINT chk_addresses_cep
            CHECK (cep IS NULL OR cep ~ '^[0-9]{5}-?[0-9]{3}$') NOT VALID;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_addresses_municipality ON addresses(tenant_id, municipality_ibge);

-- Ligação própria cliente-endereço (no lugar do par polimórfico, para os
-- vínculos novos): correspondência com tipo e vigência.
CREATE TABLE IF NOT EXISTS customer_addresses (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    address_id UUID NOT NULL,
    valid_from DATE NOT NULL DEFAULT CURRENT_DATE,
    valid_to DATE,
    is_correspondence BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);
ALTER TABLE customer_addresses ADD CONSTRAINT fk_custaddr_customer
    FOREIGN KEY (tenant_id, customer_id) REFERENCES customers(tenant_id, id);
ALTER TABLE customer_addresses ADD CONSTRAINT fk_custaddr_address
    FOREIGN KEY (tenant_id, address_id) REFERENCES addresses(tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_customer_addresses_customer ON customer_addresses(tenant_id, customer_id);
-- No máximo uma correspondência vigente por cliente.
CREATE UNIQUE INDEX IF NOT EXISTS idx_customer_addresses_correspondence
    ON customer_addresses(tenant_id, customer_id)
    WHERE is_correspondence AND valid_to IS NULL;

-- Importa os vínculos legados (entity_type='customers') para a tabela própria.
INSERT INTO customer_addresses (tenant_id, customer_id, address_id, valid_from, is_correspondence)
SELECT tenant_id, entity_id, id, CURRENT_DATE, (type = 'CORRESPONDENCE')
FROM addresses WHERE entity_type = 'customers'
ON CONFLICT DO NOTHING;

-- ============================================================================
-- 6. VÍNCULO: papel por catálogo, EXCLUDE, motivo de fim, OS de abertura e
--    fechamento, forma de entrega, nome na conta
-- ============================================================================

ALTER TABLE unit_customers ADD COLUMN IF NOT EXISTS role_id UUID;
ALTER TABLE unit_customers ADD COLUMN IF NOT EXISTS end_reason_id UUID;
ALTER TABLE unit_customers ADD COLUMN IF NOT EXISTS opened_by_service_order_id UUID;
ALTER TABLE unit_customers ADD COLUMN IF NOT EXISTS closed_by_service_order_id UUID;
ALTER TABLE unit_customers ADD COLUMN IF NOT EXISTS delivery_mode_id UUID;

-- Backfill do catálogo a partir dos papéis existentes (não inventado: espelha
-- o que já está nos vínculos). Depois disso, role_id é NOT NULL.
INSERT INTO relation_roles (tenant_id, code, name, system_role)
SELECT DISTINCT tenant_id, role, role, role FROM unit_customers
ON CONFLICT DO NOTHING;

UPDATE unit_customers u SET role_id = r.id
FROM relation_roles r
WHERE r.tenant_id = u.tenant_id AND r.system_role = u.role AND u.role_id IS NULL;

ALTER TABLE unit_customers ALTER COLUMN role_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_ucust_role') THEN
        ALTER TABLE unit_customers ADD CONSTRAINT fk_ucust_role
            FOREIGN KEY (tenant_id, role_id) REFERENCES relation_roles(tenant_id, id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_ucust_end_reason') THEN
        ALTER TABLE unit_customers ADD CONSTRAINT fk_ucust_end_reason
            FOREIGN KEY (tenant_id, end_reason_id) REFERENCES unit_link_end_reasons(tenant_id, id);
    END IF;
    -- SEM FK aqui para opened_by/closed_by_service_order_id: service_orders vive
    -- na V9_1, que ordena DEPOIS da V3_12 no histórico único do tenant; uma FK
    -- aqui quebraria o fresh install. As FKs são acrescentadas pela V9_3
    -- (esta frente, mesmo pacote de entrega). O mesmo vale para
    -- unit_due_days.service_order_id, cadastral_change_requests.service_order_id
    -- e unit_alternative_sources.meter_installation_id (V5_1).
    -- O nome na conta sai em nome do usuário titular (NR 11, art. 33, §2º).
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_ucust_bill_name_user') THEN
        ALTER TABLE unit_customers ADD CONSTRAINT chk_ucust_bill_name_user
            CHECK (NOT show_on_bill OR role = 'USER') NOT VALID;
    END IF;
    -- Forma de entrega obrigatória quando há responsável.
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_ucust_delivery_responsible') THEN
        ALTER TABLE unit_customers ADD CONSTRAINT chk_ucust_delivery_responsible
            CHECK (role <> 'RESPONSIBLE' OR delivery_mode_id IS NOT NULL) NOT VALID;
    END IF;
    -- Motivo de fim por catálogo obrigatório com a data de fim.
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_ucust_end_reason') THEN
        ALTER TABLE unit_customers ADD CONSTRAINT chk_ucust_end_reason
            CHECK (end_date IS NULL OR end_reason_id IS NOT NULL) NOT VALID;
    END IF;
END $$;

-- Período sem sobreposição por unidade, cliente e papel (btree_gist vem do
-- init de segurança, como nas EXCLUDEs da V4_1). NULL em end_date = aberto.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'excl_ucust_no_overlap') THEN
        ALTER TABLE unit_customers ADD CONSTRAINT excl_ucust_no_overlap
            EXCLUDE USING gist (tenant_id WITH =, unit_id WITH =, customer_id WITH =,
                                  role_id WITH =, daterange(start_date, end_date, '[]') WITH &&);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_ucust_role ON unit_customers(tenant_id, role_id);

-- ============================================================================
-- 7. ECONOMIAS COM VIGÊNCIA + CATEGORIA COM CLASSE REGULATÓRIA
-- ============================================================================

ALTER TABLE unit_subcategories ADD COLUMN IF NOT EXISTS valid_from DATE NOT NULL DEFAULT CURRENT_DATE;
ALTER TABLE unit_subcategories ADD COLUMN IF NOT EXISTS valid_to DATE;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_usub_validity') THEN
        ALTER TABLE unit_subcategories ADD CONSTRAINT chk_usub_validity
            CHECK (valid_to IS NULL OR valid_to >= valid_from);
    END IF;
    -- Uma linha vigente por unidade e subcategoria por período. A unicidade
    -- antiga (idx_usub_unique) continua valendo para a linha sem vigência
    -- explícita; a EXCLUDE é a regra R2 (competência passada usa o valor da
    -- época; o início de uma vigência nova é o primeiro dia da competência,
    -- regra do serviço, não do banco).
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'excl_usub_no_overlap') THEN
        ALTER TABLE unit_subcategories ADD CONSTRAINT excl_usub_no_overlap
            EXCLUDE USING gist (tenant_id WITH =, unit_id WITH =, subcategory_id WITH =,
                                  daterange(valid_from, valid_to, '[]') WITH &&);
    END IF;
END $$;

-- Classe regulatória da categoria (NR 13, art. 6º).
ALTER TABLE categories ADD COLUMN IF NOT EXISTS regulatory_class VARCHAR(20)
    CHECK (regulatory_class IN ('RESIDENTIAL', 'SOCIAL_RESIDENTIAL', 'COMMERCIAL',
                                 'INDUSTRIAL', 'PUBLIC', 'OTHER'));

-- ============================================================================
-- 8. CONTATOS, CONSENTIMENTO, ROTA, VENCIMENTO, FONTES E PERFIS
-- ============================================================================

ALTER TABLE customer_contacts ADD COLUMN IF NOT EXISTS phone_type VARCHAR(30);
ALTER TABLE customer_contacts ADD COLUMN IF NOT EXISTS verification_state VARCHAR(20)
    NOT NULL DEFAULT 'UNVERIFIED'
    CHECK (verification_state IN ('UNVERIFIED', 'PENDING', 'VERIFIED'));
ALTER TABLE customer_contacts ADD COLUMN IF NOT EXISTS valid_from DATE;
ALTER TABLE customer_contacts ADD COLUMN IF NOT EXISTS valid_to DATE;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_contacts_validity') THEN
        ALTER TABLE customer_contacts ADD CONSTRAINT chk_contacts_validity
            CHECK (valid_to IS NULL OR valid_to >= valid_from);
    END IF;
    -- E-mail por padrão próximo da RFC 5322 e telefone em E.164. NOT VALID:
    -- linhas legadas não são reprovadas; o serviço valida antes de gravar.
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_contacts_format') THEN
        ALTER TABLE customer_contacts ADD CONSTRAINT chk_contacts_format
            CHECK ((kind = 'EMAIL' AND value ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$')
                OR (kind = 'PHONE' AND value ~ '^\+[1-9][0-9]{7,14}$')) NOT VALID;
    END IF;
END $$;

-- Consentimento: UNIQUE (tenant_id, id) para a FK composta, e finalidade por
-- catálogo. A coluna purpose (texto) FICA: o catálogo é a referência nova.
ALTER TABLE lgpd_consent ADD CONSTRAINT uq_lgpd_consent_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE lgpd_consent ADD COLUMN IF NOT EXISTS purpose_id UUID;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_lgpd_consent_purpose') THEN
        ALTER TABLE lgpd_consent ADD CONSTRAINT fk_lgpd_consent_purpose
            FOREIGN KEY (tenant_id, purpose_id) REFERENCES consent_purposes(tenant_id, id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_contact_consent') THEN
        ALTER TABLE customer_contacts ADD CONSTRAINT fk_contact_consent
            FOREIGN KEY (tenant_id, consent_id) REFERENCES lgpd_consent(tenant_id, id);
    END IF;
END $$;

-- Rota de leitura com grupo de faturamento (referência pendente: não há
-- entidade de grupo de faturamento; o padrão é o da V3_5 para referências
-- sem catálogo — UUID aceito, FK quando o catálogo existir).
ALTER TABLE reading_routes ADD COLUMN IF NOT EXISTS billing_group_id UUID;

-- Uma só fonte do dia de vencimento, na unidade, com vigência. A coluna
-- consumer_units.due_day passa a ser o espelho do valor vigente (lido pela
-- API e pelo faturamento); a fonte é esta tabela. OS ALTERNATIVE_DUE_DAY
-- (mapa da P1.31) é o tipo da mudança, registrado em cadastral_change_requests.
CREATE TABLE IF NOT EXISTS unit_due_days (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    due_day SMALLINT NOT NULL CHECK (due_day BETWEEN 1 AND 31),
    valid_from DATE NOT NULL,
    valid_to DATE,
    service_order_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);
ALTER TABLE unit_due_days ADD CONSTRAINT fk_unit_due_day_unit
    FOREIGN KEY (tenant_id, unit_id) REFERENCES consumer_units(tenant_id, id);
-- SEM FK para service_order_id aqui (V9_1 ordena depois da V3_12): ver V9_3.
CREATE INDEX IF NOT EXISTS idx_unit_due_days_unit ON unit_due_days(tenant_id, unit_id, valid_from DESC);

-- Fonte alternativa de água por unidade, com autorização do órgão gestor.
-- O hidrômetro da fonte, quando houver, é uma meter_installations (V5_1).
CREATE TABLE IF NOT EXISTS unit_alternative_sources (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    source_type VARCHAR(20) NOT NULL CHECK (source_type IN
        ('SHALLOW_WELL', 'DEEP_WELL', 'SURFACE_CAPTURE', 'RAINWATER', 'REUSE')),
    authorization_ref VARCHAR(100),
    meter_installation_id UUID,
    captured_volume_m3 NUMERIC(12, 3) CHECK (captured_volume_m3 IS NULL OR captured_volume_m3 >= 0),
    use_description VARCHAR(200),
    valid_from DATE NOT NULL DEFAULT CURRENT_DATE,
    valid_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);
ALTER TABLE unit_alternative_sources ADD CONSTRAINT fk_alt_source_unit
    FOREIGN KEY (tenant_id, unit_id) REFERENCES consumer_units(tenant_id, id);
-- SEM FK para meter_installation_id aqui (V5_1 ordena depois da V3_12): ver V9_3.
CREATE INDEX IF NOT EXISTS idx_alt_sources_unit ON unit_alternative_sources(tenant_id, unit_id);

-- Usuário essencial por unidade (hospital, escola, internação coletiva),
-- identificado por CNES, INEP ou alvará.
CREATE TABLE IF NOT EXISTS unit_essential_users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    essential_kind VARCHAR(20) NOT NULL CHECK (essential_kind IN ('HOSPITAL', 'SCHOOL', 'COLLECTIVE_CARE')),
    identifier_type VARCHAR(10) NOT NULL CHECK (identifier_type IN ('CNES', 'INEP', 'PERMIT')),
    identifier_value VARCHAR(50) NOT NULL,
    valid_from DATE NOT NULL DEFAULT CURRENT_DATE,
    valid_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);
ALTER TABLE unit_essential_users ADD CONSTRAINT fk_essential_unit
    FOREIGN KEY (tenant_id, unit_id) REFERENCES consumer_units(tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_essential_unit ON unit_essential_users(tenant_id, unit_id);

-- Pessoa dependente de abastecimento contínuo por motivo de saúde. Dado
-- sensível (LGPD, art. 11): coletado SÓ a pedido do titular — o serviço
-- recusa a gravação sem requested_by_holder, e nenhum diagnóstico em texto
-- livre é guardado aqui (só a comprovação referenciada e a validade).
CREATE TABLE IF NOT EXISTS unit_vulnerable_persons (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    consent_id UUID,
    proof_document_id UUID,
    requested_by_holder BOOLEAN NOT NULL DEFAULT FALSE,
    valid_from DATE NOT NULL DEFAULT CURRENT_DATE,
    valid_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);
ALTER TABLE unit_vulnerable_persons ADD CONSTRAINT fk_vulnerable_unit
    FOREIGN KEY (tenant_id, unit_id) REFERENCES consumer_units(tenant_id, id);
ALTER TABLE unit_vulnerable_persons ADD CONSTRAINT fk_vulnerable_consent
    FOREIGN KEY (tenant_id, consent_id) REFERENCES lgpd_consent(tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_vulnerable_unit ON unit_vulnerable_persons(tenant_id, unit_id);

-- Perfil de atendimento prioritário, acompanhante, formato acessível de
-- documentos a pedido e canal (Lei 10.048/2000; Lei 13.146/2015, art. 62).
CREATE TABLE IF NOT EXISTS customer_accessibility (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    priority_category VARCHAR(20) CHECK (priority_category IN
        ('ELDERLY', 'DISABLED', 'PREGNANT', 'NURSING', 'INFANT_COMPANION', 'OBESE', 'REDUCED_MOBILITY')),
    companion_name VARCHAR(100),
    accessible_format VARCHAR(20) CHECK (accessible_format IN
        ('BRAILLE', 'LARGE_PRINT', 'ACCESSIBLE_PDF', 'AUDIO')),
    channel VARCHAR(20) CHECK (channel IN ('LIBRAS', 'TDD', 'VIDEO')),
    valid_from DATE NOT NULL DEFAULT CURRENT_DATE,
    valid_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);
ALTER TABLE customer_accessibility ADD CONSTRAINT fk_accessibility_customer
    FOREIGN KEY (tenant_id, customer_id) REFERENCES customers(tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_accessibility_customer ON customer_accessibility(tenant_id, customer_id);

-- ============================================================================
-- 9. RASCUNHOS + ALTERAÇÕES CAMPO A CAMPO LIGADAS À OS
-- ============================================================================

-- Rascunho do assistente no servidor (o frontend já tem o cliente em
-- drafts.ts; os endpoints não existiam). Sem trigger de auditoria: o payload
-- é a cópia de trabalho do formulário e pode conter o documento em claro
-- antes da cifra — auditar cada versão copiaria dado pessoal para o audit_log
-- (mesma justificativa de generated_files). O ciclo de vida do rascunho é
-- rastreável pela própria linha (status, updated_at, updated_by).
CREATE TABLE IF NOT EXISTS registry_drafts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    resource VARCHAR(20) NOT NULL CHECK (resource IN ('CUSTOMER', 'CONSUMER_UNIT')),
    target_id UUID,
    -- Etapa 0-based, como o cliente do frontend (drafts.ts / PropertyWizard):
    -- a etapa 0 é a primeira; o CHECK anterior (>= 1) recusaria o rascunho inicial.
    step INT NOT NULL DEFAULT 0 CHECK (step >= 0),
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'DISCARDED', 'APPLIED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
CREATE INDEX IF NOT EXISTS idx_registry_drafts_target ON registry_drafts(tenant_id, resource, target_id);

-- Alteração campo a campo ligada à OS (contrato da P1.31, mapa do 23b §3.2).
-- service_order_id é nulo até a OS existir: a criação da OS interna a partir
-- da tela/API/CSV é trabalho de integração com o motor da P1.31 (o tipo de
-- cada campo já está declarado em CadastralChangeService.FIELD_OS_TYPES).
-- SEM FK aqui (V9_1 ordena depois da V3_12): ver V9_3.
-- old_value/new_value NUNCA carregam documento em claro (só a máscara).
CREATE TABLE IF NOT EXISTS cadastral_change_requests (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    target_type VARCHAR(20) NOT NULL CHECK (target_type IN ('CUSTOMER', 'CONSUMER_UNIT')),
    target_id UUID NOT NULL,
    field VARCHAR(100) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    service_order_type VARCHAR(60) NOT NULL,
    service_order_id UUID,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPLIED', 'REVERSED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
CREATE INDEX IF NOT EXISTS idx_cadastral_changes_target ON cadastral_change_requests(tenant_id, target_type, target_id);

-- ============================================================================
-- 10. HISTÓRICOS: service_order_id OBRIGATÓRIO (R1, parte registry)
-- ============================================================================

-- unit_status_history existe desde a V3_1; a V9_1 anunciou este campo como
-- "nullable until P1.3". Chegou a P1.3. Ver o aviso sobre linhas legadas no
-- cabeçalho: tenants com NULL aqui falham alto, por desenho.
ALTER TABLE unit_status_history ALTER COLUMN service_order_id SET NOT NULL;

-- ============================================================================
-- 11. ISOLAMENTO, AUDITORIA E REDAÇÃO
-- ============================================================================

SELECT register_tenant_table('ibge_municipalities');
SELECT register_tenant_table('customer_types');
SELECT register_tenant_table('property_profiles');
SELECT register_tenant_table('relation_roles');
SELECT register_tenant_table('document_exemption_reasons');
SELECT register_tenant_table('customer_delete_reasons');
SELECT register_tenant_table('unit_link_end_reasons');
SELECT register_tenant_table('sewer_discharges');
SELECT register_tenant_table('block_faces');
SELECT register_tenant_table('street_names');
SELECT register_tenant_table('neighborhoods');
SELECT register_tenant_table('consent_purposes');
SELECT register_tenant_table('customer_addresses');
SELECT register_tenant_table('unit_due_days');
SELECT register_tenant_table('unit_alternative_sources');
SELECT register_tenant_table('unit_essential_users');
SELECT register_tenant_table('unit_vulnerable_persons');
SELECT register_tenant_table('customer_accessibility');
SELECT register_tenant_table('cadastral_change_requests');
-- Rascunhos: RLS forçada + policy, SEM trigger de auditoria (ver §9).
SELECT register_tenant_table('registry_drafts', true, false);

-- Redação das colunas novas com dado pessoal (padrão V3_2/V3_4; a R__
-- data_classification deriva a classificação destas regras).
INSERT INTO public.audit_redaction_rule (table_name, column_name, mode, reason) VALUES
    ('customer_accessibility', 'companion_name', 'REDACT', 'nome de terceiro (acompanhante)'),
    ('cadastral_change_requests', 'old_value', 'REDACT', 'valor anterior do campo, pode conter dado pessoal'),
    ('cadastral_change_requests', 'new_value', 'REDACT', 'valor novo do campo, pode conter dado pessoal'),
    ('addresses', 'reference_point', 'REDACT', 'ponto de referência, pode identificar o morador'),
    ('unit_alternative_sources', 'use_description', 'REDACT', 'texto livre sobre o uso da fonte'),
    ('unit_essential_users', 'identifier_value', 'REDACT', 'identificador da instituição (CNES/INEP/alvará)')
ON CONFLICT (table_name, column_name) DO NOTHING;

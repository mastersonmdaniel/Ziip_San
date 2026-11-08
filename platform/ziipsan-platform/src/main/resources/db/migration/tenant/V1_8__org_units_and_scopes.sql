-- P3.30 — Unidade organizacional e escopo por área (plano v3.0 §3, tenant V1_8,
-- módulo platform — reserva exata desta tarefa: "`org_units` (unidades
-- organizacionais hierárquicas por tenant), áreas de cada unidade (localidade,
-- setor, rota) e escopo do usuário por unidade e área (ADR-0040)").
--
-- O QUE MUDA
--   * org_units: a hierarquia organizacional do tenant (matriz → regional →
--     escritório local, nos nomes que o tenant der). Pai na MESMA tabela;
--     ciclo e profundidade são regra do serviço (OrgScopeService), porque o
--     banco não anda na árvore — o CHECK abaixo só veda o auto-pai.
--   * org_unit_areas: as ÁREAS de cada unidade — referências POLIMÓRFICAS às
--     tabelas de território do registry (`localities`, `commercial_sectors`,
--     `reading_routes` da V3_1/V3_6). NÃO há FK para elas, por dois motivos
--     honestos: (1) uma coluna só não pode referenciar três tabelas; (2) a
--     V1_8 ordena ANTES da V3_1 na história única do tenant. A existência da
--     área é conferida pelo serviço na ligação (porta de território, que lê o
--     registry) e a leitura de âncora que não resolve é tratada como ausência
--     de fato — nunca como área coberta. Ver OrgScopeService.linkArea.
--   * user_org_scopes: a concessão de escopo de UM usuário — exatamente UMA
--     forma por linha (CHECK): unidade organizacional (vale a subárvore e as
--     áreas dela e das descendentes), área direta (localidade/setor/rota) ou
--     irrestrito no tenant (unrestricted). Sem linha = SEM escopo = sem acesso
--     aos recursos recortados (protocolo, OS, unidade consumidora, conta e
--     caixa da P2.28): o filtro é obrigatório e falha fechado (ver ScopeRule).
--     granted_by é a trilha de quem concedeu; a regra "quem concede só concede
--     o que possui" é da aplicação (GrantRule), porque depende do escopo e das
--     permissões VIGENTES do concedente, que o banco não resolve.
--   * protocol_org_assignments: a unidade organizacional ATUAL de cada
--     protocolo (1:1). O protocolo (V8_1) não ganha coluna: a atribuição vive
--     aqui e o trâmite a atualiza; as OS do protocolo ACOMPANHAM porque a
--     unidade da OS derivada do protocolo é resolvida desta tabela.
--   * protocol_transfers: a trilha só de inserção de cada trâmite (de, para,
--     quem, motivo e os ids das OS que acompanharam, em JSON gravado pela
--     classe Json do core no serviço).
--
-- FKs CONDICIONAIS (padrão da V1_15 com a guarda da integração P1-C — UNIQUE
-- (tenant_id, id) via pg_constraint, não só to_regclass):
--   * users é da V1_9, que ordena DEPOIS desta na história única: numa
--     instalação fresca o bloco pula e quem completa as FKs de usuário é a
--     repetível R__org_scope_fks.sql; num tenant em upgrade elas nascem aqui.
--   * service_protocols é da V8_1 (também depois): idem, via repetível.
--   * As FKs ENTRE as tabelas desta migration são diretas (nascem juntas), e
--     a UNIQUE (tenant_id, id) de cada tabela nasce ANTES das FKs que a usam.
-- Sem semente: nenhuma unidade, área ou escopo nasce de fábrica — estrutura
-- organizacional é fato do tenant, e semear uma inventaria esse fato.

CREATE TABLE IF NOT EXISTS org_units (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    parent_id UUID,
    code VARCHAR(60) NOT NULL CHECK (btrim(code) <> ''),
    name VARCHAR(200) NOT NULL CHECK (btrim(name) <> ''),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (parent_id IS NULL OR parent_id <> id)
);

ALTER TABLE org_units ADD CONSTRAINT uq_org_units_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE org_units ADD CONSTRAINT uq_org_units_code UNIQUE (tenant_id, code);
ALTER TABLE org_units ADD CONSTRAINT fk_org_units_parent
    FOREIGN KEY (tenant_id, parent_id) REFERENCES org_units (tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_org_units_parent ON org_units (tenant_id, parent_id);

CREATE TABLE IF NOT EXISTS org_unit_areas (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    org_unit_id UUID NOT NULL,
    area_type VARCHAR(10) NOT NULL CHECK (area_type IN ('LOCALITY', 'SECTOR', 'ROUTE')),
    area_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_org_unit_areas_unit
        FOREIGN KEY (tenant_id, org_unit_id) REFERENCES org_units (tenant_id, id)
);

ALTER TABLE org_unit_areas ADD CONSTRAINT uq_org_unit_areas_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_org_unit_areas_link
    ON org_unit_areas (tenant_id, org_unit_id, area_type, area_id);
CREATE INDEX IF NOT EXISTS idx_org_unit_areas_area ON org_unit_areas (tenant_id, area_type, area_id);

CREATE TABLE IF NOT EXISTS user_org_scopes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    org_unit_id UUID,
    area_type VARCHAR(10) CHECK (area_type IN ('LOCALITY', 'SECTOR', 'ROUTE')),
    area_id UUID,
    unrestricted BOOLEAN NOT NULL DEFAULT FALSE,
    granted_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- Exatamente UMA forma de concessão por linha.
    CHECK ((unrestricted AND org_unit_id IS NULL AND area_type IS NULL AND area_id IS NULL)
        OR (NOT unrestricted AND org_unit_id IS NOT NULL AND area_type IS NULL AND area_id IS NULL)
        OR (NOT unrestricted AND org_unit_id IS NULL AND area_type IS NOT NULL AND area_id IS NOT NULL)),
    CONSTRAINT fk_user_org_scopes_unit
        FOREIGN KEY (tenant_id, org_unit_id) REFERENCES org_units (tenant_id, id)
);

ALTER TABLE user_org_scopes ADD CONSTRAINT uq_user_org_scopes_tenant_id UNIQUE (tenant_id, id);
-- Deduplicação por forma (UNIQUE simples não deduplica com NULLs).
CREATE UNIQUE INDEX IF NOT EXISTS uq_user_scopes_org_unit
    ON user_org_scopes (tenant_id, user_id, org_unit_id) WHERE org_unit_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_user_scopes_area
    ON user_org_scopes (tenant_id, user_id, area_type, area_id) WHERE area_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_user_scopes_unrestricted
    ON user_org_scopes (tenant_id, user_id) WHERE unrestricted;
CREATE INDEX IF NOT EXISTS idx_user_scopes_user ON user_org_scopes (tenant_id, user_id);

CREATE TABLE IF NOT EXISTS protocol_org_assignments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    protocol_id UUID NOT NULL,
    org_unit_id UUID NOT NULL,
    assigned_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_protocol_assignments_unit
        FOREIGN KEY (tenant_id, org_unit_id) REFERENCES org_units (tenant_id, id)
);

ALTER TABLE protocol_org_assignments ADD CONSTRAINT uq_protocol_assignments_tenant_id
    UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_protocol_assignments_protocol
    ON protocol_org_assignments (tenant_id, protocol_id);

CREATE TABLE IF NOT EXISTS protocol_transfers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    protocol_id UUID NOT NULL,
    from_org_unit_id UUID,
    to_org_unit_id UUID NOT NULL,
    moved_by UUID NOT NULL,
    reason VARCHAR(500) NOT NULL CHECK (btrim(reason) <> ''),
    moved_order_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (from_org_unit_id IS NULL OR from_org_unit_id <> to_org_unit_id),
    CONSTRAINT fk_protocol_transfers_from
        FOREIGN KEY (tenant_id, from_org_unit_id) REFERENCES org_units (tenant_id, id),
    CONSTRAINT fk_protocol_transfers_to
        FOREIGN KEY (tenant_id, to_org_unit_id) REFERENCES org_units (tenant_id, id)
);

ALTER TABLE protocol_transfers ADD CONSTRAINT uq_protocol_transfers_tenant_id UNIQUE (tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_protocol_transfers_protocol
    ON protocol_transfers (tenant_id, protocol_id, created_at);

-- FKs de usuário condicionais com a guarda da integração P1-C (UNIQUE
-- (tenant_id, id) via pg_constraint, como na V1_15 — não basta a tabela
-- existir). Numa instalação fresca a V1_9 ainda não rodou e o bloco pula;
-- a repetível R__org_scope_fks.sql completa depois.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint c
        WHERE c.conrelid = to_regclass('users')
          AND c.contype IN ('u', 'p')
          AND (SELECT array_agg(a.attname::text ORDER BY k.ord)
               FROM unnest(c.conkey) WITH ORDINALITY AS k(attnum, ord)
               JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k.attnum)
              = ARRAY['tenant_id', 'id']
    ) THEN
        ALTER TABLE user_org_scopes ADD CONSTRAINT fk_user_org_scopes_user
            FOREIGN KEY (tenant_id, user_id) REFERENCES users (tenant_id, id);
        ALTER TABLE user_org_scopes ADD CONSTRAINT fk_user_org_scopes_grantor
            FOREIGN KEY (tenant_id, granted_by) REFERENCES users (tenant_id, id);
        ALTER TABLE protocol_org_assignments ADD CONSTRAINT fk_protocol_assignments_actor
            FOREIGN KEY (tenant_id, assigned_by) REFERENCES users (tenant_id, id);
        ALTER TABLE protocol_transfers ADD CONSTRAINT fk_protocol_transfers_actor
            FOREIGN KEY (tenant_id, moved_by) REFERENCES users (tenant_id, id);
    END IF;
END $$;

SELECT register_tenant_table('org_units');
SELECT register_tenant_table('org_unit_areas');
SELECT register_tenant_table('user_org_scopes');
SELECT register_tenant_table('protocol_org_assignments');
SELECT register_tenant_table('protocol_transfers');

-- Classificação de dados (P0.11): os ids de usuário são identificadores de
-- operador do tenant (classe ID), como nas demais tabelas do produto.
INSERT INTO public.data_classification (table_name, column_name, class, reason) VALUES
    ('user_org_scopes', 'user_id', 'ID', 'identificador do usuario com escopo (P3.30)'),
    ('user_org_scopes', 'granted_by', 'ID', 'identificador de quem concedeu o escopo (P3.30)'),
    ('protocol_org_assignments', 'assigned_by', 'ID', 'identificador de quem atribuiu a unidade ao protocolo (P3.30)'),
    ('protocol_transfers', 'moved_by', 'ID', 'identificador de quem tramitou o protocolo (P3.30)')
ON CONFLICT (table_name, column_name) DO NOTHING;

COMMENT ON TABLE org_units IS
    'P3.30: unidades organizacionais hierárquicas do tenant (ADR-0040); pai na mesma tabela, ciclo vedado no serviço.';
COMMENT ON TABLE org_unit_areas IS
    'P3.30: áreas (localidade, setor, rota do registry) de cada unidade organizacional; referência polimórfica sem FK, existência conferida pelo serviço.';
COMMENT ON TABLE user_org_scopes IS
    'P3.30: escopo do usuário por unidade organizacional e/ou área; sem linha = sem acesso aos recursos recortados; unrestricted = tenant inteiro.';
COMMENT ON TABLE protocol_org_assignments IS
    'P3.30: unidade organizacional atual de cada protocolo; o trâmite atualiza aqui e as OS do protocolo acompanham pela derivação.';
COMMENT ON TABLE protocol_transfers IS
    'P3.30: trilha só de inserção dos trâmites de protocolo entre unidades, com as OS que acompanharam.';

-- Perfis regulatorios: colunas de origem, catalogo de fabrica, simulacao e
-- ativacao pelo envelope (P2.31). Modulo platform, sobre a V1_5 (P0.19).
-- Reserva do plano v3.0 §3: tenant V1_13 (platform) + a repetivel
-- R__regulatory_profile_catalog.sql, ambas da P2.31. A V1_13 estava livre
-- na listagem real do repo (V1_11 e V1_12 ocupadas; V1_14 e V1_15 sao das
-- P3.34/P3.36): nenhuma renumeracao foi necessaria.
--
-- O que esta versao faz:
--   1. regulatory_profiles (V1_5) ganha a identificacao por MUNICIPIO
--      (o mesmo tenant pode responder a mais de uma ERI conforme o
--      municipio -- a CASAN responde a ARESC, ARIS, AGIR e CISAM-SUL),
--      a chave do perfil de fabrica aplicado, a adocao das NR da ANA
--      (reg.ana_nr_adoption, DAD-28) e os fatos normativos do perfil.
--   2. CORRECAO do EXCLUDE da V1_5: ele era por (tenant, agencia,
--      vigencia) e impediria dois municipios da mesma agencia no mesmo
--      tenant -- justamente o caso que o acceptance da P2.31 exige.
--      O novo EXCLUDE inclui o municipio (COALESCE para o perfil sem
--      municipio, que vale para o tenant inteiro).
--   3. Tabelas do catalogo de fabrica (preenchidas pela R__ da P2.31,
--      upsert por chave estavel, precedente GIS/P1.34): perfil, valores
--      com ato/artigo/vigencia/marca de confirmacao e divergencias
--      (regra local recusada por contrariar lei federal).
--   4. regulatory_profile_activations: o envelope de ativacao do perfil
--      (proposta, aprovacao por outra pessoa e aceite formal), ligado as
--      versoes de parametro que ele cria em parameter_versions
--      (coluna regulatory_activation_id) e aos aceites da V1_5.
--   5. Chaves novas de PARAMETROS-DO-TENANT que esta carga usa e que
--      ainda nao estavam no catalogo global: entram em
--      public.parameter_definitions pelo mesmo precedente da V4_4 §7
--      (migration de tenant registra catalogo global com ON CONFLICT
--      DO NOTHING). Sem a definicao, o TenantParameterService recusa a
--      chave na proposta -- a ativacao do perfil passaria a falhar.
--
-- NENHUM valor de perfil e semeado aqui: a carga e da R__ (gerada de
-- ScRegulatoryProfiles.java, fonte unica). Nenhum dado de tenant nasce
-- nesta migration.

-- =======================================================================
-- 1. e 2. regulatory_profiles: municipio, origem e EXCLUDE corrigido
-- =======================================================================

ALTER TABLE regulatory_profiles ADD COLUMN IF NOT EXISTS municipality_ibge VARCHAR(7)
    CHECK (municipality_ibge IS NULL OR municipality_ibge ~ '^[0-9]{7}$');
ALTER TABLE regulatory_profiles ADD COLUMN IF NOT EXISTS municipality_name VARCHAR(150);
ALTER TABLE regulatory_profiles ADD COLUMN IF NOT EXISTS profile_key VARCHAR(60);
ALTER TABLE regulatory_profiles ADD COLUMN IF NOT EXISTS ana_nr_adoption JSONB;
ALTER TABLE regulatory_profiles ADD COLUMN IF NOT EXISTS normative_facts JSONB;

ALTER TABLE regulatory_profiles DROP CONSTRAINT IF EXISTS excl_regulatory_profiles_no_overlap;
ALTER TABLE regulatory_profiles ADD CONSTRAINT excl_regulatory_profiles_no_overlap
    EXCLUDE USING gist (tenant_id WITH =,
                        agency_code WITH =,
                        COALESCE(municipality_ibge, '') WITH =,
                        daterange(valid_from, valid_to, '[]') WITH &&);

CREATE INDEX IF NOT EXISTS idx_regulatory_profiles_municipality
    ON regulatory_profiles(tenant_id, agency_code, municipality_ibge, valid_from);

COMMENT ON COLUMN regulatory_profiles.municipality_ibge IS
    'Municipio (IBGE) a que o perfil se aplica (P2.31): o mesmo tenant pode ter perfis de ERI diferentes por municipio. NULL = vale para o tenant inteiro.';
COMMENT ON COLUMN regulatory_profiles.profile_key IS
    'Chave estavel do perfil de fabrica aplicado (regulatory_profile_catalog.profile_key), quando a ativacao partiu da carga da P2.31.';
COMMENT ON COLUMN regulatory_profiles.ana_nr_adoption IS
    'Valor de reg.ana_nr_adoption aplicado na ativacao: mapa NR -> estado e atos de adocao da ERI (DAD-28).';
COMMENT ON COLUMN regulatory_profiles.normative_facts IS
    'Fatos normativos do perfil (ex.: verificacao da NR 11 pela ANA a partir de 20/05/2027, Res. ANA 230/2024, art. 74, §3º).';

-- =======================================================================
-- 3. Catalogo de fabrica (a R__ da P2.31 preenche; a aplicacao so le)
-- =======================================================================

CREATE TABLE IF NOT EXISTS regulatory_profile_catalog (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    profile_key VARCHAR(60) NOT NULL,
    agency_code VARCHAR(40) NOT NULL,
    agency_name VARCHAR(200) NOT NULL,
    general_conditions_act VARCHAR(200) NOT NULL,
    valid_from DATE,
    valid_to DATE,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (valid_from IS NULL OR valid_to IS NULL OR valid_to >= valid_from)
);
ALTER TABLE regulatory_profile_catalog ADD CONSTRAINT uq_regulatory_profile_catalog_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_regulatory_profile_catalog_key
    ON regulatory_profile_catalog(tenant_id, profile_key);

CREATE TABLE IF NOT EXISTS regulatory_profile_values (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    profile_key VARCHAR(60) NOT NULL,
    definition_key TEXT NOT NULL,
    -- NULL quando confirmation = REQUIRES_TENANT_VALUE (sem valor de
    -- fabrica) ou CONFIRMED_ABSENT (ausencia confirmada da regra).
    value JSONB,
    source_act VARCHAR(200),
    source_article VARCHAR(200),
    deadline_basis VARCHAR(20) NOT NULL DEFAULT 'NOT_APPLICABLE'
        CHECK (deadline_basis IN ('CALENDAR_DAYS', 'BUSINESS_DAYS', 'NOT_DEFINED', 'NOT_APPLICABLE')),
    confirmation VARCHAR(25) NOT NULL
        CHECK (confirmation IN ('CONFIRMED', 'CONFIRMED_ABSENT', 'REQUIRES_TENANT_VALUE')),
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK ((confirmation = 'CONFIRMED' AND value IS NOT NULL)
        OR (confirmation IN ('CONFIRMED_ABSENT', 'REQUIRES_TENANT_VALUE') AND value IS NULL))
);
ALTER TABLE regulatory_profile_values ADD CONSTRAINT uq_regulatory_profile_values_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_regulatory_profile_values_key
    ON regulatory_profile_values(tenant_id, profile_key, definition_key);

CREATE TABLE IF NOT EXISTS regulatory_profile_divergences (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    profile_key VARCHAR(60) NOT NULL,
    rule_code VARCHAR(60) NOT NULL,
    local_rule TEXT NOT NULL,
    local_source VARCHAR(300) NOT NULL,
    federal_norm VARCHAR(200) NOT NULL,
    resolution VARCHAR(25) NOT NULL
        CHECK (resolution IN ('FEDERAL_FLOOR_APPLIED', 'RULE_REJECTED')),
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE regulatory_profile_divergences ADD CONSTRAINT uq_regulatory_profile_divergences_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_regulatory_profile_divergences_key
    ON regulatory_profile_divergences(tenant_id, profile_key, rule_code);

COMMENT ON TABLE regulatory_profile_catalog IS
    'Perfis regulatorios de fabrica (P2.31): um por ERI e vigencia do ato de condicoes gerais. Preenchido pela R__regulatory_profile_catalog; a aplicacao so le.';
COMMENT ON TABLE regulatory_profile_values IS
    'Valores de fabrica por chave (G1, G5, G6, G9 e bifurcacoes), com ato e artigo de origem, base de contagem do prazo e marca de confirmacao. REQUIRES_TENANT_VALUE = sem valor de fabrica, a ativacao exige o valor do tenant.';
COMMENT ON TABLE regulatory_profile_divergences IS
    'Regras locais recusadas na carga por contrariar lei federal, exibidas ao tenant como divergencia (P2.31).';

-- =======================================================================
-- 4. Envelope de ativacao do perfil (proposta/aprovacao/aceite, P0.19)
-- =======================================================================

CREATE TABLE IF NOT EXISTS regulatory_profile_activations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    profile_key VARCHAR(60) NOT NULL,
    agency_code VARCHAR(40) NOT NULL,
    municipality_ibge VARCHAR(7) CHECK (municipality_ibge IS NULL OR municipality_ibge ~ '^[0-9]{7}$'),
    municipality_name VARCHAR(150),
    valid_from DATE NOT NULL,
    valid_to DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'PROPOSED'
        CHECK (status IN ('PROPOSED', 'APPROVED', 'ACCEPTED', 'REJECTED')),
    proposed_by UUID NOT NULL,
    approved_by UUID,
    approved_at TIMESTAMPTZ,
    regulatory_profile_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (valid_to IS NULL OR valid_to >= valid_from),
    CHECK (approved_by IS NULL OR approved_by <> proposed_by),
    CHECK (status <> 'APPROVED' OR (approved_by IS NOT NULL AND approved_at IS NOT NULL))
);
ALTER TABLE regulatory_profile_activations ADD CONSTRAINT uq_regulatory_profile_activations_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE regulatory_profile_activations ADD CONSTRAINT fk_regulatory_activation_profile
    FOREIGN KEY (tenant_id, regulatory_profile_id) REFERENCES regulatory_profiles(tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_regulatory_profile_activations_profile
    ON regulatory_profile_activations(tenant_id, profile_key, status);

ALTER TABLE parameter_versions ADD COLUMN IF NOT EXISTS regulatory_activation_id UUID;
ALTER TABLE parameter_versions ADD CONSTRAINT fk_parameter_version_activation
    FOREIGN KEY (tenant_id, regulatory_activation_id)
    REFERENCES regulatory_profile_activations(tenant_id, id);

COMMENT ON TABLE regulatory_profile_activations IS
    'Ativacao de perfil regulatorio pelo envelope da P0.19 (P2.31): quem propoe nao aprova (CHECK), o aceite formal vive em parameter_acceptances e o perfil so nasce em regulatory_profiles depois do aceite.';

-- =======================================================================
-- 5. Definicoes novas no catalogo global (precedente V4_4 §7)
-- =======================================================================

INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('reg.regulator', 'ENUM', 'd', NULL,
     'Catalogo de ERIs do produto (mapa de ERIs, docs/normas/partes/04)',
     'REG',
     'ERI competente do perfil aplicado ao municipio (G1). Sem ERI: ver reg.legal_nature e o titular (NR 11, art. 52, §3º).'),
    ('reg.general_conditions_act', 'OBJECT', 'd', NULL,
     'Ato de condicoes gerais da ERI (mapa de ERIs)',
     'REG',
     'Norma de condicoes gerais vigente do perfil: ato, data e atos que o compilaram (G1).'),
    ('reg.ana_nr_adoption', 'OBJECT', 'd', NULL,
     'Mapa de ERIs, §5 (atos de adocao das NR da ANA por ERI)',
     'REG',
     'Adocao da ERI as NR da ANA: mapa NR -> {status, atos, catalogCode} (G1). A ligacao ao catalogo de normas da P1.34 (DAD-28) e pelo catalogCode = cert_standard.code (slug catalog_key do JSON de normas, ex.: NR 11 = ana-nr-11-res-230-2024), validada no aceite da ativacao.'),
    ('reg.normative_facts', 'TABLE', 'd', NULL,
     'Res. ANA 230/2024 (NR 11), art. 74, §3º',
     'REG',
     'Fatos normativos do perfil que nao sao valor de parametro: ARRAY de objetos {code, date, source} (ex.: inicio da verificacao da NR 11 pela ANA em 20/05/2027). Tipo TABLE, o mesmo de bill.auto_review_value_bands (array de objetos) -- correcao pos-revisao: estava OBJECT, mas o valor e um array.'),
    ('meter.reading_cycle_days', 'OBJECT', 'd', NULL,
     'Norma de condicoes gerais da ERI (SC: faixa de 27 a 33 dias)',
     'REG',
     'Faixa do ciclo de leitura em dias (minimo, maximo e faixa excepcional), por perfil de ERI (G1). A faixa de referencia do ajuste por dias da P2.5 e 27 a 33 (Res. ARESC 046/2016).'),
    ('fee.correction_index', 'ENUM', 'd', '"NONE"',
     'Norma de condicoes gerais da ERI (fee.correction_* de PARAMETROS-DO-TENANT, G5)',
     'JUR',
     'Indice de correcao monetaria dos encargos (IPCA, INPC, Selic ou indice do reajuste), por perfil de ERI. NONE = sem correcao (padrao de fee.correction_mode).'),
    ('cut.forbidden_window', 'OBJECT', 'a', NULL,
     'Lei 14.015/2020 (art. 6º, §4º, da Lei 8.987/1995); Lei 13.460/2017, art. 6º, p.u.',
     NULL,
     'Janela vedada para iniciar corte (G9, travado, classe a): sexta, sabado, domingo, feriado e vespera. Regra local mais frouxa e divergencia do perfil, nunca valor (P2.31).'),
    ('cut.max_debt_age_days', 'INT', 'd', NULL,
     'Norma de condicoes gerais da ERI (G9)',
     'JUR',
     'Idade maxima da divida para cortar, em dias, quando a ERI a fixa (SC: so a ARIS, 120 dias). Ausencia confirmada no perfil = sem teto.'),
    ('rec.sla_hours', 'OBJECT', 'd', NULL,
     'Norma de condicoes gerais da ERI (G9)',
     'OPE',
     'Prazos de religacao em horas (normal, urgencia e corte indevido), por perfil de ERI.'),
    ('rec.undue_cut_compensation', 'ENUM', 'b', NULL,
     'Bifurcacao de PARAMETROS-DO-TENANT (20f Q3, LEG-22)',
     'OPE+JUR',
     'Compensacao por corte indevido, por perfil de ERI: formula ARESC/AGIR (maior entre 2x a religacao de urgencia e 20% da fatura), ARIS (religacao em 4 h sem onus) ou credito manual com aprovacao.'),
    ('sac.response_days', 'OBJECT', 'd', NULL,
     'Norma de condicoes gerais da ERI (G9)',
     'REG',
     'Prazos de resposta do atendimento por perfil de ERI, com a base de contagem do perfil.'),
    ('sac.priority_min_age_years', 'INT', 'd', NULL,
     'Norma de condicoes gerais da ERI; Lei 10.741/2003 (Estatuto da Pessoa Idosa), art. 3º',
     'REG',
     'Idade do atendimento prioritario por perfil de ERI (SC: 60 na AGIR e no CISAM-SUL; 65 na ARESC).'),
    ('leak.max_discount_pct_water', 'PERCENT', 'b', NULL,
     'Bifurcacao leak.* de PARAMETROS-DO-TENANT (20f Q4; 20b §5.1)',
     'REG',
     'Teto do desconto por vazamento oculto sobre o volume acima da media, em percentual (SC: ate 70%). Regras completas de vazamento: P3.28.'),
    ('sac.deadline_counting_rule', 'ENUM', 'b', '"CALENDAR"',
     'Bifurcacao de PARAMETROS-DO-TENANT (20f §5; 20d ATD-04); mapa de ERIs §3, achado 6',
     'REG',
     'Contagem dos prazos do perfil: CALENDAR (dias corridos) ou BUSINESS (dias uteis). Sem ERI, o padrao de fabrica e CALENDAR, a contagem mais favoravel ao usuario; a AGIR nao define na RN 001/2013 e o tenant declara na ativacao do perfil.'),
    ('cut.min_notice_days', 'INT', 'c', NULL,
     'Lei 11.445/2007, art. 40, §2º (piso de 30 dias); norma de condicoes gerais da ERI (G9)',
     'JUR',
     'Aviso previo de corte por inadimplencia, em dias: piso federal de 30, so amplia (classe c); o perfil da ERI fixa o valor e a base de contagem.')
ON CONFLICT (definition_key) DO NOTHING;

-- =======================================================================
-- Isolamento e auditoria (padrao V1_2/V1_5)
-- =======================================================================

SELECT register_tenant_table('regulatory_profile_catalog');
SELECT register_tenant_table('regulatory_profile_values');
SELECT register_tenant_table('regulatory_profile_divergences');
SELECT register_tenant_table('regulatory_profile_activations');

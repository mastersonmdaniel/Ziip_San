-- II-3 Tarifa Social Lei 14.898/2024 completa + busca activa de elegiveis
-- (Onda II do PDCA-01, decisao 64 do dono): persistencia NOVA do ciclo -
-- corridas, trilha da concessao e itens de busca activa.
-- Ficheiro PENDENTE DE NUMERACAO Flyway: prefixo II-3, NENHUM numero de
-- versao e inventado e a lista de migrations publicadas NAO e alterada
-- por este pacote; a numeracao global atribui-se no pouso/integracao
-- (pendencia P-II3-CI).
-- Reusa, sem as redefinir: social_tariff_enrollments, social_base_imports
-- e social_base_records da P1.5 (V4_2) e a area de preparacao da P1.40
-- (V14_4, via esquema da ED-57). Este ficheiro so acrescenta o que essas
-- tabelas nao guardam: a corrida do ciclo, a trilha por transicao e a
-- lista de trabalho da busca activa.
-- Leiaute oficial CadUnico/BPC: [SEM FONTE] neste projecto - a importacao
-- do ciclo nasce por porta com formato DECLARADO (ver
-- DeclaredSocialListFormat); nenhum leiaute oficial e presumido aqui.
-- Intervalo de recertificacao periodica: [A DEFINIR] - nenhuma coluna de
-- prazo de recertificacao e semeada nem tem default neste ficheiro.
-- MINIMIZACAO LGPD POR DESENHO: so indices cegos (hashes) e o resultado
-- do cruzamento persistem. A trilha NAO tem coluna de indice cego: ela
-- referencia a concessao pelo id, como a V4_2 manda para a trilha de
-- auditoria ("blind indexes must never appear in the audit trail").
-- NENHUM INSERT de dados neste ficheiro: nada e semeado.

-- ========================================================================
-- CORRIDA DO CICLO (uma por importacao cruzada e concedida)
-- ========================================================================
-- status: COMPLETED (corrida terminou; recusas por linha/familia ficam
-- contadas, nao mudam o estado), REFUSED_LIST_SOURCE (trava PLT-36 antes
-- de importar - corrida registada sem contagens de cruzamento).

CREATE TABLE IF NOT EXISTS social_cycle_runs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    list_source VARCHAR(20) NOT NULL CHECK (list_source IN ('REGULATOR_LIST', 'MUNICIPAL_BASE', 'CONECTA_API')),
    layout_version VARCHAR(50) NOT NULL,
    received_on DATE NOT NULL,
    reference_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('COMPLETED', 'REFUSED_LIST_SOURCE')),
    imported_rows INT NOT NULL CHECK (imported_rows >= 0),
    duplicates_removed INT NOT NULL CHECK (duplicates_removed >= 0),
    import_rejections INT NOT NULL CHECK (import_rejections >= 0),
    matched_families INT NOT NULL CHECK (matched_families >= 0),
    unmatched_families INT NOT NULL CHECK (unmatched_families >= 0),
    ineligible_families INT NOT NULL CHECK (ineligible_families >= 0),
    grants_created INT NOT NULL CHECK (grants_created >= 0),
    grant_refusals INT NOT NULL CHECK (grant_refusals >= 0),
    active_search_entries INT NOT NULL CHECK (active_search_entries >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE social_cycle_runs ADD CONSTRAINT uq_social_cycle_runs_tenant_id UNIQUE (tenant_id, id);
CREATE INDEX idx_social_cycle_runs_reference ON social_cycle_runs(tenant_id, reference_date DESC);

-- ========================================================================
-- RESUMO DO CRUZAMENTO POR FAMILIA (chave + resultado; sem indice cego
-- de pessoa, sem a lista importada - nao e base paralela)
-- ========================================================================
-- outcome: MATCHED (casou com a unidade), UNMATCHED (elegivel sem
-- unidade: busca activa), INELIGIBLE (sem registro elegivel na
-- referencia pela regra da P1.5).

CREATE TABLE IF NOT EXISTS social_cycle_crossings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    cycle_run_id UUID NOT NULL,
    family_code_hash VARCHAR(64) NOT NULL,
    eligibility_basis VARCHAR(20) NOT NULL CHECK (eligibility_basis IN ('CADUNICO', 'BPC', 'LOCAL_PROGRAM')),
    outcome VARCHAR(20) NOT NULL CHECK (outcome IN ('MATCHED', 'UNMATCHED', 'INELIGIBLE')),
    matched_unit_id UUID,
    municipality_code VARCHAR(20),
    route_code VARCHAR(40),
    person_count INT NOT NULL CHECK (person_count >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK ((outcome = 'MATCHED') = (matched_unit_id IS NOT NULL))
);
ALTER TABLE social_cycle_crossings ADD CONSTRAINT uq_social_cycle_crossings_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE social_cycle_crossings ADD CONSTRAINT fk_social_cycle_crossing_run
    FOREIGN KEY (tenant_id, cycle_run_id) REFERENCES social_cycle_runs(tenant_id, id);
CREATE UNIQUE INDEX idx_social_cycle_crossing_family ON social_cycle_crossings(tenant_id, cycle_run_id, family_code_hash);
CREATE INDEX idx_social_cycle_crossing_unmatched ON social_cycle_crossings(tenant_id, cycle_run_id)
    WHERE outcome = 'UNMATCHED';

-- ========================================================================
-- TRILHA DA CONCESSAO (uma linha por transicao; referencia a inscricao
-- da V4_2 pelo id - sem indice cego, sem dado pessoal)
-- ========================================================================
-- from_status/to_status: ELIGIBLE (pre-concessao do ciclo), GRANTED,
-- UNDER_REVIEW, GRACE, ENDED (correspondencia com a V4_2 no Javadoc de
-- SocialGrantLifecycle). from_status e nulo apenas na leitura de trilhas
-- importadas de fora do ciclo; as transicoes do ciclo sempre o trazem.

CREATE TABLE IF NOT EXISTS social_grant_trail (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    enrollment_id UUID NOT NULL,
    sequence_no INT NOT NULL CHECK (sequence_no >= 1),
    occurred_at TIMESTAMPTZ NOT NULL,
    from_status VARCHAR(20) CHECK (from_status IN ('ELIGIBLE', 'GRANTED', 'UNDER_REVIEW', 'GRACE', 'ENDED')),
    to_status VARCHAR(20) NOT NULL CHECK (to_status IN ('ELIGIBLE', 'GRANTED', 'UNDER_REVIEW', 'GRACE', 'ENDED')),
    reason_code VARCHAR(60) NOT NULL,
    actor_id VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE social_grant_trail ADD CONSTRAINT uq_social_grant_trail_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE social_grant_trail ADD CONSTRAINT fk_social_grant_trail_enrollment
    FOREIGN KEY (tenant_id, enrollment_id) REFERENCES social_tariff_enrollments(tenant_id, id);
CREATE UNIQUE INDEX idx_social_grant_trail_sequence ON social_grant_trail(tenant_id, enrollment_id, sequence_no);

-- ========================================================================
-- BUSCA ACTIVA (lista de trabalho: elegiveis menos inscritos, por
-- municipio/rota; recalculada a cada corrida)
-- ========================================================================
-- reason: MATCHED_NOT_ENROLLED (casou com unidade, sem concessao),
-- NO_UNIT_MATCH (elegivel da lista sem unidade correspondente).
-- status: PENDING (a procurar), CONTACTED (prestador contactou/localizou
-- canal), RESOLVED (inscrita ou encerrada por verificacao), DISMISSED
-- (descartada por verificacao documentada fora do sistema).

CREATE TABLE IF NOT EXISTS social_active_search_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    cycle_run_id UUID NOT NULL,
    family_code_hash VARCHAR(64) NOT NULL,
    eligibility_basis VARCHAR(20) NOT NULL CHECK (eligibility_basis IN ('CADUNICO', 'BPC', 'LOCAL_PROGRAM')),
    municipality_code VARCHAR(20) NOT NULL,
    route_code VARCHAR(40) NOT NULL,
    reason VARCHAR(20) NOT NULL CHECK (reason IN ('MATCHED_NOT_ENROLLED', 'NO_UNIT_MATCH')),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'CONTACTED', 'RESOLVED', 'DISMISSED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE social_active_search_items ADD CONSTRAINT uq_social_active_search_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE social_active_search_items ADD CONSTRAINT fk_social_active_search_run
    FOREIGN KEY (tenant_id, cycle_run_id) REFERENCES social_cycle_runs(tenant_id, id);
CREATE UNIQUE INDEX idx_social_active_search_family ON social_active_search_items(tenant_id, cycle_run_id, family_code_hash);
CREATE INDEX idx_social_active_search_group ON social_active_search_items(tenant_id, municipality_code, route_code)
    WHERE status = 'PENDING';

-- ========================================================================
-- ISOLAMENTO E AUDITORIA (o mesmo padrao da V4_2)
-- ========================================================================

SELECT register_tenant_table('social_cycle_runs');
SELECT register_tenant_table('social_cycle_crossings');
SELECT register_tenant_table('social_grant_trail');
SELECT register_tenant_table('social_active_search_items');

-- Blind indexes must never appear in the audit trail: they would let records be linked.
INSERT INTO public.audit_redaction_rule (table_name, column_name, mode, reason) VALUES
    ('social_cycle_crossings', 'family_code_hash', 'OMIT', 'blind index of the family code: links records'),
    ('social_active_search_items', 'family_code_hash', 'OMIT', 'blind index of the family code: links records')
ON CONFLICT (table_name, column_name) DO NOTHING;

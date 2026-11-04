-- Tenant Migration — Version: 21.1
-- Description: P1.32 — nucleo do modulo Certificacoes de qualidade
-- (ADR-0041; plano v3.0 §3, tenant V21_1, reservada, P1).
--
-- O QUE ESTA VERSAO CRIA (schema do tenant; RLS forcada + auditoria via
-- register_tenant_table, como a V9_1 da P1.31):
--   * cert_standard_activation: uma linha por norma e por tenant (UNIQUE
--     tenant + standard_code). Ligar e desligar sao mudancas de estado
--     desta linha, nunca DELETE: o ciclo e PROPOSED -> APPROVED -> ACTIVE
--     -> INACTIVE, com reativacao reutilizando a mesma linha (volta a
--     PROPOSED, com novo proponente, escopo e edicao). Quem propoe nao
--     aprova, e o aceite formal (hash do conteudo aceito, no mesmo
--     padrao de parameter_acceptances da V1_5/P0.19) e registrado antes
--     de a norma ficar ACTIVE. O escopo declarado (scope_statement) e a
--     edicao em uso (edition) sao obrigatorios — o equivalente em DDL
--     ao @NotBlank e a regra do ADR-0041 itens 6 e 7.
--   * cert_clause_map: a copia, no tenant, das clausulas da edicao aceita
--     (numero + titulo em palavras proprias, congelados no aceite). E o
--     mapa que a tela exibe e que a P2.25 usara para vincular ocorrencias.
--     Ao desligar, as linhas nao sao apagadas: passam a FROZEN com
--     frozen_at (o mapa congelado do ADR-0041 item 5). Ao reativar, as
--     mesmas linhas voltam a ACTIVE — sem duplicar, sem apagar.
--   * cert_evidence_link: vinculo muitos-para-muitos entre evidencia e
--     clausula do mapa (uma evidencia serve a varias clausulas; uma
--     clausula reune varias evidencias). A evidencia e referenciada por
--     tipo + id (DOCUMENT, SERVICE_ORDER, RECORD, OTHER), sem copiar o
--     conteudo: o pacote de evidencia nao carrega dado pessoal por padrao
--     (ADR-0041 item 10, parametro travado cert.evidence_pack_personal_data
--     = false no core V3). Nao ha DELETE de vinculo nesta frente: vinculo
--     errado e corrigido por novo vinculo, como a P1.27 faz com o catalogo
--     (desativar em vez de apagar); a correcao com baixa logica e da P2.25.
--
-- TRAVAS EM BANCO (o servico as repete com mensagem legivel; o banco e a
-- ultima linha, inclusive contra escrita direta):
--   * REGULATORY nunca fica INACTIVE (ADR-0041 item 2: obrigatoria por
--     aplicabilidade, nao desativavel). A trava usa a classe copiada na
--     ativacao (standard_class), para nao depender de leitura do catalogo
--     global a cada escrita.
--   * ACTIVE exige aceite (accepted_at) e ativacao (activated_at);
--     INACTIVE exige desligamento registrado (deactivated_at/by).
--   * Mapa FROZEN exige frozen_at; mapa ACTIVE proibe frozen_at.
--   * Sem FK entre o schema do tenant e o schema platform: a ativacao
--     guarda standard_code + catalog_key + classe, e o servico valida a
--     existencia no catalogo global no momento da ativacao (o catalogo
--     nasce vazio e so a carga da P1.34 o popula; um FK quebraria o
--     provisionamento de tenants criados antes da V9_8). A clausula do
--     mapa guarda clause_id (o id global) pelo mesmo motivo.
--
-- O QUE ESTA VERSAO DELIBERADAMENTE NAO FAZ:
--   * Nenhuma linha semeada: sem norma ligada por padrao (o padrao de
--     fabrica de cert.<norma>.enabled e desligada, core V3) e sem mapa
--     copiado antes do aceite — mapa vazio ate a primeira ativacao aceita.
--   * Nenhum documento, risco, objetivo, ocorrencia ou acao: essas sao as
--     capacidades da P2.25 (V21_2) e da P3.31 (V21_3), que usam este mapa
--     e este vinculo. O que esta frente entrega e o nucleo: ativacao,
--     mapa e vinculo de evidencia.
--   * Nenhuma coluna com nome de dado pessoal (ver PersonalDataColumns):
--     scope_statement e evidence_ref sao texto livre de escopo e de
--     referencia, nao dado pessoal; activated_by/approved_by e demais
--     atores sao UUIDs. Por isso nao ha regra de redacao nem linha nova
--     em data_classification aqui — os testes de cobertura passam sem
--     excecao nova, e qualquer coluna futura com dado pessoal tera de
--     trazer a sua regra, como manda a P0.11.

-- ========================================================================
-- ACTIVATION (uma linha por norma e por tenant)
-- ========================================================================
CREATE TABLE IF NOT EXISTS cert_standard_activation (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    standard_code TEXT NOT NULL CHECK (char_length(btrim(standard_code)) > 0),
    catalog_key TEXT NOT NULL CHECK (char_length(btrim(catalog_key)) > 0),
    standard_class TEXT NOT NULL
        CHECK (standard_class IN ('REGULATORY', 'VOLUNTARY', 'REFERENCE', 'EXPORT_PROFILE')),
    edition TEXT NOT NULL CHECK (char_length(btrim(edition)) > 0),
    scope_statement TEXT NOT NULL CHECK (char_length(btrim(scope_statement)) > 0),
    status TEXT NOT NULL DEFAULT 'PROPOSED'
        CHECK (status IN ('PROPOSED', 'APPROVED', 'ACTIVE', 'INACTIVE')),
    proposed_by UUID NOT NULL,
    approved_by UUID,
    approved_at TIMESTAMPTZ,
    accepted_by UUID,
    accepted_at TIMESTAMPTZ,
    acceptance_hash TEXT,
    activated_at TIMESTAMPTZ,
    deactivated_at TIMESTAMPTZ,
    deactivated_by UUID,
    deactivation_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_cert_activation_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_cert_activation_tenant_code UNIQUE (tenant_id, standard_code),
    CONSTRAINT chk_cert_activation_active_requires_acceptance
        CHECK (status <> 'ACTIVE' OR (accepted_at IS NOT NULL AND activated_at IS NOT NULL
                                       AND acceptance_hash IS NOT NULL)),
    CONSTRAINT chk_cert_activation_inactive_requires_record
        CHECK (status <> 'INACTIVE' OR (deactivated_at IS NOT NULL AND deactivated_by IS NOT NULL)),
    CONSTRAINT chk_cert_activation_regulatory_never_inactive
        CHECK (NOT (standard_class = 'REGULATORY' AND status = 'INACTIVE')),
    CONSTRAINT chk_cert_activation_approval_consistent
        CHECK ((status = 'PROPOSED' AND approved_by IS NULL)
               OR (status <> 'PROPOSED' AND approved_by IS NOT NULL))
);
CREATE INDEX IF NOT EXISTS idx_cert_activation_tenant_status
    ON cert_standard_activation (tenant_id, status);

-- ========================================================================
-- CLAUSE MAP (copia congelavel das clausulas da edicao aceita)
-- ========================================================================
CREATE TABLE IF NOT EXISTS cert_clause_map (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    activation_id UUID NOT NULL,
    clause_id UUID NOT NULL,
    clause_number TEXT NOT NULL CHECK (char_length(btrim(clause_number)) > 0),
    title_snapshot TEXT NOT NULL CHECK (char_length(btrim(title_snapshot)) > 0),
    map_status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (map_status IN ('ACTIVE', 'FROZEN')),
    frozen_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_cert_clause_map_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_cert_clause_map_activation
        FOREIGN KEY (tenant_id, activation_id) REFERENCES cert_standard_activation (tenant_id, id),
    CONSTRAINT uq_cert_clause_map_activation_clause UNIQUE (tenant_id, activation_id, clause_id),
    CONSTRAINT chk_cert_clause_map_frozen_consistent
        CHECK ((map_status = 'ACTIVE' AND frozen_at IS NULL)
               OR (map_status = 'FROZEN' AND frozen_at IS NOT NULL))
);
CREATE INDEX IF NOT EXISTS idx_cert_clause_map_activation
    ON cert_clause_map (tenant_id, activation_id);

-- ========================================================================
-- EVIDENCE LINK (muitos-para-muitos: evidencia <-> clausula do mapa)
-- ========================================================================
CREATE TABLE IF NOT EXISTS cert_evidence_link (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    clause_map_id UUID NOT NULL,
    evidence_kind TEXT NOT NULL
        CHECK (evidence_kind IN ('DOCUMENT', 'SERVICE_ORDER', 'RECORD', 'OTHER')),
    evidence_id UUID NOT NULL,
    evidence_ref TEXT,
    linked_by UUID,
    linked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_cert_evidence_link_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_cert_evidence_link_clause_map
        FOREIGN KEY (tenant_id, clause_map_id) REFERENCES cert_clause_map (tenant_id, id),
    CONSTRAINT uq_cert_evidence_link UNIQUE (tenant_id, clause_map_id, evidence_kind, evidence_id)
);
CREATE INDEX IF NOT EXISTS idx_cert_evidence_link_evidence
    ON cert_evidence_link (tenant_id, evidence_kind, evidence_id);

-- ========================================================================
-- ISOLATION AND AUDIT
-- ========================================================================
SELECT register_tenant_table('cert_standard_activation');
SELECT register_tenant_table('cert_clause_map');
SELECT register_tenant_table('cert_evidence_link');

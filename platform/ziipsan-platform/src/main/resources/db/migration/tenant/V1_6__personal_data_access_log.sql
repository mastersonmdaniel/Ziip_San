-- Platform Migration for Tenant Schema
-- Version: 1.6.0
-- Description: P0.11 — registro de acesso a dado pessoal e cota de busca por documento.
--
-- personal_data_access_log (um registro por tenant): buscas por documento, leituras de
-- cadastro e revelações, com usuário, tenant, entidade, resultado e — no máximo — o
-- índice cego do documento, nunca o valor em claro (ADR-0008). É a base da detecção de
-- anomalia de acesso (RNF-41), cuja detecção completa é da P3.10: aqui se entrega o
-- registro e os ganchos de consulta, não a detecção.
--
-- A tabela NÃO tem trigger de auditoria (register_tenant_table com p_audit = false): o
-- log não se audita a si mesmo — auditar cada linha do log em audit_log duplicaria o
-- armazenamento e, pior, colocaria o índice cego em mais um lugar. Por isso ela entra
-- em NOT_AUDITED_BY_DESIGN no FreshInstallTest, com esta justificativa. RLS forçada e
-- policy de tenant continuam valendo, como em toda tabela de tenant.
--
-- A cota é contada sobre esta tabela (buscas concluídas por usuário na última hora);
-- não há tabela de cota separada: a janela é a própria accessed_at.
--
-- Também nesta migration, a revisão da isenção de consumer_units.coordinates (P0.11):
-- a coluna sai da allowlist NOT_PERSONAL do teste de cobertura e passa a ter regra de
-- redação (REDACT) e classificação LOC (ver R__data_classification.sql). A localização
-- da ligação vinculada a pessoa é dado pessoal de localização, não dado de rede.
-- As isenções de sampling_point.coordinates e waste_destination.coordinates (ponto
-- público e pessoa jurídica) não mudam.
--
-- O catálogo data_classification (public) é criado aqui; o seed é da migration
-- repetível R__data_classification.sql, que roda depois de todas as versionadas e por
-- isso enxerga as regras de redação de todos os módulos (inclusive as de migrations
-- posteriores a esta, como a V1_10).

CREATE TABLE IF NOT EXISTS personal_data_access_log (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    user_id UUID,
    accessed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    access_type VARCHAR(20) NOT NULL
        CHECK (access_type IN ('DOCUMENT_SEARCH', 'PERSONAL_READ', 'DOCUMENT_REVEAL')),
    entity_type VARCHAR(100),
    entity_id UUID,
    document_hash VARCHAR(64),
    purpose VARCHAR(500),
    result VARCHAR(20) NOT NULL
        CHECK (result IN ('FOUND', 'NOT_FOUND', 'DENIED', 'QUOTA_EXCEEDED'))
);
CREATE INDEX IF NOT EXISTS idx_pdal_tenant_user_time
    ON personal_data_access_log(tenant_id, user_id, accessed_at);
CREATE INDEX IF NOT EXISTS idx_pdal_tenant_result_time
    ON personal_data_access_log(tenant_id, result, accessed_at);

SELECT register_tenant_table('personal_data_access_log', false, false);

-- P0.11: fim da isenção de consumer_units.coordinates (classificação LOC).
INSERT INTO public.audit_redaction_rule (table_name, column_name, mode, reason) VALUES
    ('consumer_units', 'coordinates', 'REDACT', 'localização da ligação vinculada a pessoa')
ON CONFLICT (table_name, column_name) DO NOTHING;

-- Catálogo de classificação de dados (public, produto — não por tenant: o schema é o
-- mesmo em todos os tenants). Classes (P0.11): ID identificação, CT contato, LOC
-- localização, FIN financeiro, SOC social/demográfico, SENS sensível (LGPD art. 11).
CREATE TABLE IF NOT EXISTS public.data_classification (
    table_name VARCHAR(100) NOT NULL,
    column_name VARCHAR(100) NOT NULL,
    class VARCHAR(10) NOT NULL
        CHECK (class IN ('ID', 'CT', 'LOC', 'FIN', 'SOC', 'SENS')),
    reason TEXT NOT NULL,
    PRIMARY KEY (table_name, column_name)
);
COMMENT ON TABLE public.data_classification IS
    'Classificação de dado pessoal por tabela e coluna (P0.11). Toda coluna com nome de dado pessoal numa tabela auditada precisa de uma linha aqui; o teste DataClassificationCoverageTest recusa coluna nova sem classificação. Seed: R__data_classification.sql.';

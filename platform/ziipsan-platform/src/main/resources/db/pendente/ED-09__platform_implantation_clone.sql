-- ED-09 -- Servicos de implantacao padronizados (clone mascarado, PLT-39).
--
-- *** NUMERACAO — PRECEDENTE ED-14/ED-17 ***
-- O mapa Flyway do plano v3.0 (§3) NAO reserva versao para a ED-09: as tabelas
-- sao do schema `platform` do banco CORE (o mesmo da jornada de implantacao
-- da P1.6, core V9), e a ED-09 e posterior a P3.12 — pela regra do §3, versao
-- da sequencia global com o modulo no nome, cujo numero sai na ordem do
-- merge. Este arquivo fica em db/pendente/ e NAO deve ser renomeado por
-- chute: na integracao vira core V<proximo livre>__platform_implantation_clone.sql.
-- Nenhum numero inventado.
--
-- Conteudo (so o registro de governanca do clone — o DADO do clone vive no
-- schema do tenant clonado, nunca aqui):
--   * platform.reidentification_assessment: a avaliacao do risco de
--     reidentificacao registrada no RIPD do tenant (referencia, nao o texto
--     do RIPD, que vive no processo de privacidade do tenant — P3.10).
--   * platform.tenant_clone: cada clone gerado (origem producao, destino
--     homologacao/treinamento, finalidade, marca de sintetico travada em
--     TRUE por CHECK, resultado da varredura da P0.45 e a avaliacao que o
--     liberou). Clone sem varredura limpa nao se registra como pronto:
--     sweep_violation_count precisa ser 0 para status READY (CHECK).
--   * platform.implantation_simulation_run: o ensaio de implantacao
--     simulada (tenant sintetico, plano de 60 dias, dias decorridos e o
--     veredito) — a evidencia do ultimo item do aceite da ED-09.
--
-- Nenhum dado pessoal nestas tabelas: so ids de tenant, referencias e
-- contagens. A correspondencia matricula <-> codigo substituto NAO fica
-- aqui nem no clone: fica so no piloto (roteiro da P0.45).

CREATE SCHEMA IF NOT EXISTS platform;

CREATE TABLE IF NOT EXISTS platform.reidentification_assessment (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES public.tenant_catalog (id),
    assessed_at TIMESTAMPTZ NOT NULL,
    assessed_by UUID NOT NULL,
    viable BOOLEAN NOT NULL,
    ripd_reference VARCHAR(300) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_reident_assessment_tenant
    ON platform.reidentification_assessment (tenant_id, assessed_at DESC);

CREATE TABLE IF NOT EXISTS platform.tenant_clone (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    source_tenant_id UUID NOT NULL REFERENCES public.tenant_catalog (id),
    clone_tenant_id UUID NOT NULL REFERENCES public.tenant_catalog (id),
    purpose VARCHAR(20) NOT NULL CHECK (purpose IN ('HOMOLOGATION', 'TRAINING')),
    assessment_id UUID NOT NULL REFERENCES platform.reidentification_assessment (id),
    synthetic BOOLEAN NOT NULL DEFAULT TRUE CHECK (synthetic = TRUE),
    sweep_violation_count INT NOT NULL CHECK (sweep_violation_count >= 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('CREATED', 'READY', 'REJECTED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    CHECK (status <> 'READY' OR sweep_violation_count = 0),
    CHECK (clone_tenant_id <> source_tenant_id)
);

CREATE INDEX IF NOT EXISTS idx_tenant_clone_source
    ON platform.tenant_clone (source_tenant_id, created_at DESC);

CREATE TABLE IF NOT EXISTS platform.implantation_simulation_run (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES public.tenant_catalog (id),
    synthetic_tenant BOOLEAN NOT NULL CHECK (synthetic_tenant = TRUE),
    plan_total_days INT NOT NULL CHECK (plan_total_days = 60),
    started_on DATE NOT NULL,
    elapsed_days INT NOT NULL CHECK (elapsed_days >= 1),
    concluded_within_plan BOOLEAN NOT NULL,
    failures JSONB NOT NULL DEFAULT '[]',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

GRANT SELECT, INSERT ON platform.reidentification_assessment TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE ON platform.tenant_clone TO ziipsan_app;
GRANT SELECT, INSERT ON platform.implantation_simulation_run TO ziipsan_app;

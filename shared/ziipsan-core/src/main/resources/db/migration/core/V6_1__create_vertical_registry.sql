-- Core Migration — Version: 6.1
-- Description: P0.47 — registro de pacotes de vertical (PLATAFORMA-E-VERTICAIS §4).
--
-- platform.vertical: pacotes conhecidos (id, versão, estado).
-- platform.tenant_vertical: ativação por tenant (tenant, vertical, ativação, estado).
-- Todo tenant existente nasce com sanitation ativo (backfill abaixo, só ACTIVE).
--
-- O schema platform é criado aqui se ainda não existir: a V5_1 (P0.42) também o toca,
-- mas a P0.47 não pode depender de uma migration de outra frente ainda não aplicada.
-- CREATE SCHEMA IF NOT EXISTS não conflita com a V5_1 quando ela chegar.
--
-- Sem RLS, de propósito, como as tabelas de sessão do BFF (core V5): a ativação é do
-- console da plataforma (Central Ziip, ADR-0018), anterior a qualquer contexto de tenant
-- da requisição; a leitura pela role da aplicação é só SELECT (GRANT abaixo) e a escrita
-- chega com o console (P1.6). Nenhum outro papel recebe acesso.
-- O estado ARCHIVED é terminal para o pacote naquele tenant: pacote com dados só se
-- arquiva, nunca se apaga (PLATAFORMA-E-VERTICAIS §4).

CREATE SCHEMA IF NOT EXISTS platform;

CREATE TABLE IF NOT EXISTS platform.vertical (
    id VARCHAR(50) PRIMARY KEY,
    version VARCHAR(50) NOT NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (state IN ('ACTIVE', 'DEPRECATED', 'ARCHIVED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS platform.tenant_vertical (
    tenant_id UUID NOT NULL REFERENCES public.tenant_catalog (id),
    vertical_id VARCHAR(50) NOT NULL REFERENCES platform.vertical (id),
    state VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (state IN ('ACTIVE', 'ARCHIVED')),
    activated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    activated_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT tenant_vertical_pk PRIMARY KEY (tenant_id, vertical_id)
);

CREATE INDEX IF NOT EXISTS idx_tenant_vertical_vertical ON platform.tenant_vertical (vertical_id, state);

-- Pacote sanitation, versão do manifesto packages/sanitation/vertical.json.
INSERT INTO platform.vertical (id, version, state)
VALUES ('sanitation', '0.1.0', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

-- Backfill: todo tenant existente nasce com sanitation ativo.
INSERT INTO platform.tenant_vertical (tenant_id, vertical_id, state, activated_at)
SELECT id, 'sanitation', 'ACTIVE', NOW()
FROM public.tenant_catalog
WHERE status = 'ACTIVE'
ON CONFLICT (tenant_id, vertical_id) DO NOTHING;

GRANT USAGE ON SCHEMA platform TO ziipsan_app;
GRANT SELECT ON platform.vertical TO ziipsan_app;
GRANT SELECT ON platform.tenant_vertical TO ziipsan_app;

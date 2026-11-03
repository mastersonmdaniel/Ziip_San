-- Core Migration — Version: 5.1.0
-- Description: P0.42 — schema `platform` e `platform.deployment` (registro de implantação).
--
-- CENTRAL-ZIIP §4: o schema `platform` vive no banco do core e nenhuma tabela
-- dele guarda dado pessoal de tenant — só versão, commit, ambiente e datas.
-- `platform.deployment` é gravada pelo job `migrate` (DatabaseMigrator), uma
-- linha por execução bem-sucedida; a api só LÊ (GRANT SELECT abaixo), nunca
-- grava: o registro de implantação não é dado de runtime da api.
--
-- Entra pelo job migrate da P0.6 (CoreMigrationRunner), nunca na subida da api.
-- Versões seguintes do schema `platform` (core V9, V9_1, ...) são do P1 e não
-- entram aqui: esta migration cria só o schema e a tabela desta tarefa.

CREATE SCHEMA IF NOT EXISTS platform;

CREATE TABLE IF NOT EXISTS platform.deployment (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    version VARCHAR(100) NOT NULL,
    commit_hash VARCHAR(100),
    environment VARCHAR(50) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS deployment_created_at_idx ON platform.deployment (created_at DESC);

-- A api lê o registro mais recente em GET /api/v1/about e na lista de
-- implantações de plataforma (GET /api/v1/platform/deployments). Sem SELECT
-- aqui, a role da aplicação não enxerga a tabela (o dono é o migrador).
-- O USAGE no schema é obrigatório junto: sem ele, o SELECT falha com
-- "permission denied for schema platform" mesmo com o GRANT na tabela
-- (achado da primeira rodada de integração da P0.42).
GRANT USAGE ON SCHEMA platform TO ziipsan_app;
GRANT SELECT ON platform.deployment TO ziipsan_app;

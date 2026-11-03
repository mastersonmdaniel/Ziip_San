-- P1.14 — Rollout de migrations por tenant (17:D1; ADR-0005).
--
-- SITUAÇÃO POR TENANT, SEM TABELA NOVA: a core V7 já antecipava que a quarentena e
-- o canário da P1.14 usariam colunas do catálogo, não uma tabela nova — e é o que
-- esta migration faz. Três colunas em public.tenant_catalog, gravadas pelo job
-- migrate (TenantMigrationRollout, via DatabaseMigrator) ao fim da tentativa de
-- cada tenant, nunca pela api (a V7 já revogou INSERT/UPDATE/DELETE do catálogo
-- para a ziipsan_app; colunas novas herdam os GRANTs da tabela):
--   * migration_status: PENDING (nunca tentado), RUNNING (tentativa em curso —
--     só visível se o job morrer no meio), SUCCEEDED (última tentativa concluiu)
--     ou QUARANTINED (a tentativa falhou: o tenant fica fora da onda sem parar
--     os demais; a próxima execução do job tenta de novo).
--   * migration_error: mensagem da falha que pôs o tenant em quarentena (NULL
--     quando a última tentativa concluiu); nunca contém dado de tenant, só a
--     mensagem da exceção do Flyway.
--   * migration_attempted_at: quando a última tentativa terminou.
-- A versão Flyway aplicada continua em tenant_schema_version (V7), atualizada só
-- nos tenants que concluíram — um tenant em quarentena mantém a versão antiga,
-- o que já o distingue de um tenant atualizado sem precisar de outra fonte.
--
-- VERSÃO: core V14_1. Não há reserva desta versão no §3 do plano (a tabela de
-- core vai até a V13, da LC.15): V14_1 é a subversão livre seguinte na história
-- de core, ordenada depois de todas as migrations de core existentes. NÃO
-- confundir com a V14_1 de tenant (§3, linha da importação de legado, P1.2):
-- core e tenant vivem em tabelas de histórico separadas (public contra o schema
-- do tenant) e o §3 distingue as duas pelo prefixo "core" — não há colisão.
-- A V14 (inteira) de core também não está reservada no §3; a subversão foi
-- escolhida para deixar a V14 livre para uma eventual versão inteira futura.

ALTER TABLE public.tenant_catalog
    ADD COLUMN IF NOT EXISTS migration_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (migration_status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'QUARANTINED'));

ALTER TABLE public.tenant_catalog
    ADD COLUMN IF NOT EXISTS migration_error TEXT;

ALTER TABLE public.tenant_catalog
    ADD COLUMN IF NOT EXISTS migration_attempted_at TIMESTAMPTZ;

COMMENT ON COLUMN public.tenant_catalog.migration_status IS
    'P1.14: resultado da última tentativa do job migrate neste tenant (PENDING, RUNNING, SUCCEEDED, QUARANTINED). Quarentena não bloqueia os demais tenants.';
COMMENT ON COLUMN public.tenant_catalog.migration_error IS
    'P1.14: mensagem da falha que pôs o tenant em quarentena; NULL quando a última tentativa concluiu.';
COMMENT ON COLUMN public.tenant_catalog.migration_attempted_at IS
    'P1.14: quando a última tentativa do job migrate terminou neste tenant.';

-- P4.6 (correcção R-P46-01) — O catálogo admite N tenants SHARED por
-- (cluster, schema) sem abrir mão da unicidade dos demais modos.
--
-- O DEFEITO (achado grave R-P46-01 da revisão do pacote p4.6, 09/11/2026)
--   A core V7 trocou o UNIQUE global de schema_name por
--   UNIQUE (cluster_id, schema_name) — no máximo UM tenant por
--   (cluster, schema). Isso torna o modo SHARED do ADR-0005 impossível em
--   banco real: o schema compartilhado existe exactamente para alojar
--   VÁRIOS tenants pequenos no mesmo (cluster, schema), isolados por
--   tenant_id e RLS, mas o registo do 2.º tenant SHARED nesse schema viola
--   a constraint e o provisionamento falha em registerTenantInCatalog. O
--   próprio ADR-0005 ("o que muda no código") já previa o desenho correcto:
--   "tenant_catalog: schema_name deixa de ser UNIQUE" para os modos que
--   partilham schema — a V7 executou só metade dessa decisão.
--
-- O QUE MUDA
--   * A constraint uq_tenant_catalog_cluster_schema (UNIQUE pleno da V7)
--     é substituída por um ÍNDICE ÚNICO PARCIAL com o mesmo nome, sobre
--     (cluster_id, schema_name), restrito às linhas NÃO-SHARED
--     (WHERE isolation_mode <> 'SHARED'). Efeitos:
--       - SHARED: sem limite de tenants por (cluster, schema) — o item 3
--         da P4.6 passa a ser exequível em banco real.
--       - SCHEMA / DEDICATED: a unicidade por (cluster, schema) continua
--         EXACTAMENTE como na V7 — dois tenants de schema próprio (ou um
--         SCHEMA e um DEDICATED) no mesmo cluster+schema continuam a
--         colidir e a falhar, nunca a sobrescrever a linha alheia.
--       - A mistura SHARED + não-SHARED no mesmo (cluster, schema) NÃO é
--         barrada por este índice (um índice único parcial não exprime
--         "ou todos SHARED ou um só dono"); quem a recusa é o
--         provisionamento (TenantProvisioningService.ensureSchema, que só
--         reutiliza schema quando TODOS os ocupantes são SHARED do mesmo
--         cluster — correcção R-P46-07). O índice é a garantia dos modos
--         de schema próprio; a regra de mistura é da aplicação, como o
--         ADR-0005 a desenha ("provisionamento recusa schema existente
--         salvo no modo compartilhado explícito").
--
-- VERSÃO: core V15. Lida ao vivo no repo em 10/11/2026: a série core
-- publicada termina em V14_1 (V14 = auditoria particionada; V13_1 =
-- conta da instância dedicada, LC.15); V15 é a versão inteira livre
-- seguinte. Core e tenant vivem em históricos Flyway separados
-- (precedente do cabeçalho da V14_1), logo não há colisão com a V15_1
-- de tenant. O runner de core corre com outOfOrder(true), mas V15 é
-- posterior a todas as aplicadas, nem disso precisa.
--
-- COMPATIBILIDADE: nenhum dado existente pode violar o índice parcial —
-- até aqui valeu o UNIQUE pleno, que é mais estrito para as linhas
-- não-SHARED e igual para as SHARED (no máximo uma linha por par, logo
-- também no máximo uma não-SHARED por par). Nenhuma coluna muda; o
-- ON CONFLICT (id) do provisionamento não é afectado (alvo é a PK, não
-- este índice). A V7 NÃO é reescrita: migrations publicadas nunca se
-- reescrevem (regra do projeto; precedente da própria V7 sobre a V1).

ALTER TABLE public.tenant_catalog
    DROP CONSTRAINT IF EXISTS uq_tenant_catalog_cluster_schema;

CREATE UNIQUE INDEX IF NOT EXISTS uq_tenant_catalog_cluster_schema
    ON public.tenant_catalog (cluster_id, schema_name)
    WHERE isolation_mode <> 'SHARED';

COMMENT ON INDEX public.uq_tenant_catalog_cluster_schema IS
    'P0.21 (core V7) revisto pela P4.6 (core V15, R-P46-01): unicidade de (cluster_id, schema_name) apenas para modos de schema proprio (SCHEMA/DEDICATED); linhas SHARED partilham o schema por desenho (ADR-0005) e ficam fora do indice.';

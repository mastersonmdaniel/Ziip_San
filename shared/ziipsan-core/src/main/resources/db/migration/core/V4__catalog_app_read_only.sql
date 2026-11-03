-- P0.17: ziipsan_app só com SELECT em public.tenant_catalog.
--
-- A V1 concedia SELECT, INSERT, UPDATE, DELETE à aplicação. O catálogo é o cadastro
-- global da plataforma: a escrita dele pertence ao provisionamento, que roda com a
-- role de migração (dona da tabela, via TenantProvisioningService.registerTenantInCatalog),
-- nunca à aplicação em runtime. Sem este REVOKE, qualquer código com o DataSource da
-- aplicação (ou uma injeção que o alcance) criava, alterava ou apagava tenants.
--
-- REVOKE de privilégio não concedido não falha, então a migration é segura mesmo que a
-- V1 já tenha sido ajustada; o GRANT explícito abaixo deixa o estado final legível
-- (SELECT) em vez de depender do que restou da V1.

REVOKE INSERT, UPDATE, DELETE ON public.tenant_catalog FROM ziipsan_app;
GRANT SELECT ON public.tenant_catalog TO ziipsan_app;

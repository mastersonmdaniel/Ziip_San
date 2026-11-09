-- SEED FICTICIO - BASE FICTICIA DE TESTES DO ZIIPSAN (decisao do dono n. 34)
-- Dados 100% ficticios. PROIBIDO EM PRODUCAO.
-- Tenant ficticio dedicado: 11111111-2222-4333-8444-555555555555 (UUID inalterado
-- no complemento EMASA de 09/11/2026 — continuidade da identidade do seed).
-- COMPLEMENTO EMASA (09/11/2026): a prestadora ficticia espelha SO o perfil
-- estrutural da EMASA (autarquia, DOIS municipios, agua+esgoto) — nenhum dado
-- real copiado. Municipios ficticios: Porto das Gaivotas (maior, costeira
-- turistica) e Vale do Rio Claro (menor, vizinha), nomes e codigos inventados.
-- Fonte do dominio: public.tenant_catalog (core V1__create_core_schema.sql).
-- Carregar ANTES dos arquivos carga-NNNN.sql. Limpeza: limpeza-base-ficticia.sql.
BEGIN;
INSERT INTO public.tenant_catalog (id, schema_name, name, cnpj, email, phone, address, status, plan, settings)
VALUES ('11111111-2222-4333-8444-555555555555', 'tenant_ficticio_teste',
        'Autarquia Ficticia Intermunicipal de Aguas — Porto das Gaivotas e Vale do Rio Claro (TESTE FICTICIO)',
        NULL, NULL, NULL, NULL, 'ACTIVE', 'TESTE-FICTICIO', '{"seed_ficticio": true}'::jsonb)
ON CONFLICT (id) DO NOTHING;
COMMIT;

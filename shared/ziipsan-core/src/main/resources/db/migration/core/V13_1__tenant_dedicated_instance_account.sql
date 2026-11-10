-- LC.15 -- Instancia dedicada na AWS (18h-E4; ADR-0024; ADR-0011).
-- Produtor: sessao b3a03bf9-b502-478f-a071-9590cb482d99 (09/11/2026).
--
-- CONTEXTO E NUMERACAO: o mapa de migrations do plano v3.0 (secao 3)
-- atribui a core V13, da LC.15, o conteudo "instancia dedicada no
-- catalogo: modo de isolamento, ambiente e conta da instancia
-- (ADR-0024)". A core V13 ja publicada (renumerada pelo pacote
-- correcao-ci-tenant-v13) entregou o ambiente e reafirmou o modo de
-- isolamento (que nasceu na core V7), e declarou expressamente no seu
-- cabecalho: "A parte 'conta da instancia' e da LC.15 e NAO entra aqui".
-- Esta V13_1 e essa parte: segue o precedente da propria V13 ("lista
-- fechada so entra por migration seguinte, sem reescrever esta") e o
-- das series com sufixo (V10_2_1, V14_1).
--
-- O QUE MUDA
--   * public.tenant_catalog.dedicated_aws_account_id: ID (12 digitos) da
--     conta AWS separada onde corre a instancia dedicada do tenant
--     (LC.15). NULL para todos os tenants que nao tem instancia
--     dedicada — ou seja, todas as linhas existentes nascem NULL, sem
--     dado semeado nem presumido: a conta real de um cliente futuro e
--     [A DEFINIR] ate a ativacao (decisao 52/41 do dono) e so entao e
--     registada nesta coluna, pelo provisionamento, na ativacao.
--   * O formato e travado por CHECK (12 digitos), sem lista fechada de
--     contas: conta nao e enum.
--
-- O QUE ESTA VERSAO NAO FAZ
--   * nao toca isolation_mode (core V7) nem environment (core V13);
--   * nao regista modo de banco da P4.6: a instancia da LC.15 corre no
--     modo padrao de schema por tenant (aceite da LC.15); esta coluna
--     regista ONDE a instancia corre (a conta), nao um modo da P4.6;
--   * nao cria tabela nem linha: apenas estende o catalogo existente.
--
-- SEM DADO SEMEADO: nenhuma linha e criada, atualizada ou apagada.

ALTER TABLE public.tenant_catalog
    ADD COLUMN IF NOT EXISTS dedicated_aws_account_id VARCHAR(12);

ALTER TABLE public.tenant_catalog
    ADD CONSTRAINT chk_tenant_catalog_dedicated_account_format
    CHECK (dedicated_aws_account_id IS NULL OR dedicated_aws_account_id ~ '^[0-9]{12}$');

CREATE INDEX IF NOT EXISTS idx_tenant_catalog_dedicated_account
    ON public.tenant_catalog (dedicated_aws_account_id)
    WHERE dedicated_aws_account_id IS NOT NULL;

COMMENT ON COLUMN public.tenant_catalog.dedicated_aws_account_id IS
    'LC.15 (ADR-0024): conta AWS separada (12 digitos) da instancia dedicada do tenant; NULL quando o tenant nao tem instancia dedicada. Registada na ativacao da instancia; a instancia corre no modo padrao de schema por tenant (sem dependencia da P4.6).';

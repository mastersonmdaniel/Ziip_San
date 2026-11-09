-- ED-04 -- Pacote de reversao (18h-E4; ADR-0024; ADR-0010; COE-04).
--
-- SPEC E NUMERACAO (resolvida; renumeracao executada pelo pacote
-- correcao-ci-tenant-v13, 09/11/2026): a redacao da ED-04 na onda B pedia
-- "core V11 na parte que registra o modo de isolamento e o ambiente do tenant
-- (ADR-0024)", e a migration foi entregue nessa serie, como V11. O mapa de
-- migrations do plano v3.0 (secao 3), porem, reserva a core V11 para a ED-18
-- (platform.app_device), a V11_1 para a ED-39 e atribui este mesmo conteudo
-- (modo de isolamento e ambiente do tenant) a core V13, da LC.15. A decisao
-- registrada em DECISOES-DO-DONO.md (secao 3, item resolvido da ED-04)
-- confirmou a serie da LC.15, e o dono confirmou a execucao da renumeracao
-- em 09/11/2026. A migration NUNCA foi publicada como V11 (a ED-04 subiu sem
-- ela, pelo conflito), logo isto e correcao pre-publicacao: mudam o nome do
-- arquivo e este cabecalho, e a unica alteracao de conteudo e citar os
-- valores da lista fechada, com aspas simples (escape SQL), no texto do
-- comentario da coluna isolation_mode; nenhuma instrucao executavel muda de
-- efeito. A parte "conta da instancia"
-- e da LC.15 e NAO entra aqui (ver "O QUE ESTA VERSAO NAO FAZ").
--
-- O QUE MUDA
--   * public.tenant_catalog.environment: ambiente onde o tenant opera. As linhas
--     existentes recebem 'SAAS' (o SaaS multi-tenant e o modo de operacao atual de
--     todos os tenants do catalogo); na reversao, o runbook da ED-04 registra o
--     ambiente de destino na linha do tenant importado. A coluna e VARCHAR(50)
--     sem lista fechada, pelo precedente da core V5_1 (platform.deployment.
--     environment, VARCHAR(50) livre): o texto integral do ADR-0024 nao estava
--     disponivel nesta frente para extrair uma lista fechada dele, e inventar
--     valores de enum que o ADR nao define e proibido pela tarefa. Se o ADR-0024
--     definir a lista, ela entra em migration seguinte com CHECK, sem reescrever
--     esta (precedente da V7, que nao reescreveu a V1).
--   * Comentario da coluna isolation_mode reafirmado: o modo de isolamento JA
--     existe desde a core V7 (P0.21), com a lista fechada SCHEMA, SHARED e
--     DEDICATED. Esta migration NAO recria a coluna nem a tabela: duplicar o
--     registro quebraria o catalogo. A ED-04 apenas o consome e o documenta como
--     o registro do ADR-0024 que o pacote de reversao le.
--
-- O QUE ESTA VERSAO NAO FAZ (fica para as tarefas citadas):
--   * conta da instancia dedicada na AWS e acesso administrativo do orgao:
--     LC.15 (a separacao do COE-04 que desfaz o ciclo ED-04, LC.2, LC.1);
--   * perfil externo do regulador e do titular, somente leitura: ED-75 (GES-22);
--   * modo de banco dedicado sob demanda: P4.6 (a V7 ja o preve como valor
--     DEDICATED; nenhum comportamento novo nasce aqui).
--
-- SEM DADO SEMEADO: nenhuma linha e criada ou alterada alem do DEFAULT da
-- coluna nova nas linhas existentes do catalogo.

ALTER TABLE public.tenant_catalog
    ADD COLUMN IF NOT EXISTS environment VARCHAR(50) NOT NULL DEFAULT 'SAAS';

CREATE INDEX IF NOT EXISTS idx_tenant_catalog_environment
    ON public.tenant_catalog (environment);

COMMENT ON COLUMN public.tenant_catalog.environment IS
    'ED-04 (ADR-0024): ambiente onde o tenant opera; SAAS nas linhas existentes. Texto livre VARCHAR(50), precedente de platform.deployment.environment (core V5_1); lista fechada so entra por migration nova, se e quando o ADR-0024 a definir.';

COMMENT ON COLUMN public.tenant_catalog.isolation_mode IS
    'P0.21 (core V7): ''SCHEMA'' = schema proprio (padrao, ADR-0005); ''SHARED'' = schema compartilhado (P4.6, sob demanda); ''DEDICATED'' = banco dedicado (P4.6). Registro do ADR-0024 reafirmado pela ED-04: e o modo de isolamento que o pacote de reversao le do catalogo.';

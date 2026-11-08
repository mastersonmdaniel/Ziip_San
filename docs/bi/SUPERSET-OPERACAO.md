# BI embarcado (Superset) — operação (ED-47)

## Fluxo de habilitação de um tenant

1. Fiação roda `BiRoleProvisioningSql.sharedObjectsSql()` na réplica
   (uma vez por banco) e `provisionSql(mapping, views)` por tenant,
   via porta `BiRoleProvisioner` — as views são as `rpt_*` do registro
   da P3.22 que o tenant pode ver.
2. Fiação liga o LOGIN da role com senha gerada no cofre
   (`ALTER ROLE <role> LOGIN PASSWORD ...` fora deste pacote; a senha
   nunca vai a SQL versionado, log ou tela).
3. No Superset, registra-se UMA database por tenant: URI da réplica,
   usuário = role do tenant, senha = a do passo 2 (na configuração
   cifrada do Superset). O cadastro é conferido pela
   `ReplicaOnlyGuard` (subida e rotina): qualquer database fora da
   réplica ou no endpoint do primário derruba a habilitação e gera
   evento `DATASOURCE_REJECTED` na auditoria.
4. Usuário entra pelo SSO (Keycloak, realm `ziipsan`); a aplicação
   resolve a claim de tenant do usuário e a `BiAccessPolicy` só o deixa
   usar a database do próprio tenant; negação é auditada
   (`ACCESS_DENIED`, SG-1252).

## Isolamento — por que o `SET` não vaza

A role só tem `SELECT` nas views `bi_rpt_*` do seu schema de acesso.
Cada uma filtra `tenant_id = bi_current_tenant_id()`, e a função lê o
mapeamento `reporting_bi_role_tenant` por `current_user` (quem abriu a
conexão). `SET app.current_tenant = '<outro tenant>'` muda uma variável
de sessão que a função **não consulta** — o resultado continua sendo o
do tenant da role, em schema próprio e em schema compartilhado. Tabela
base e view `rpt_*` crua não têm grant para a role: consulta livre fora
da camada é erro de permissão do banco.

## Auditoria (SG-1252)

Eventos: `LOGIN`, `DATASET_ACCESS`, `DASHBOARD_VIEW`,
`QUERY_EXECUTED`, `EXPORT`, `ACCESS_DENIED`, `DATASOURCE_REJECTED`.
Consulta guarda só SHA-256 + tamanho do SQL — nunca o texto (filtro de
consulta livre pode conter documento de titular; LGPD). Retenção e
expurgo seguem a trilha do core (`audit_log`, P1.7).

## Desabilitação de um tenant

`deprovisionSql(...)`: revoga os grants, apaga o mapeamento (as views
passam a devolver vazio) e põe a role em `NOLOGIN`. A database do
tenant no Superset deve ser removida no mesmo ato operacional.

## O que este pacote NÃO faz (pendências declaradas)

- Painéis padrão: são da ED-49, sobre esta base.
- Projeção de receita/arrecadação/caixa por cenário: ED-100 (GES-10),
  remetida expressamente pelo aceite da ED-47.
- Cliente OIDC no Keycloak, ativos de marca da P0.34, réplica física
  (ambiente local pode usar a réplica local; RDS é da LC.3) e a
  persistência JDBC do mapeamento (reserva de migration pendente —
  faixa `V15_x` sem número fechado para a ED-47).

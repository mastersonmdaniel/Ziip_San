# Catálogo de permissões `recurso:ação` (P0.13, ADR-0040)

Fonte: `shared/ziipsan-security/.../security/auth/PermissionCatalog.java`. Este documento é
conferido contra o código no CI: toda permissão do código aparece aqui, e nenhuma linha aqui
existe sem a permissão correspondente no código.

Formato único: `recurso:ação`, sem ponto (`registry:reveal-document`, nunca
`registry.reveal-document`). Negação por padrão: papel ou permissão desconhecida não concede
nada. O Keycloak autentica e entrega identidade, tenant e perfis; a permissão fina é resolvida
na aplicação, a partir do catálogo.

## Permissões

| Permissão | Módulo | Recurso | Ação | Perfis de fábrica | Tarefa de origem |
|---|---|---|---|---|---|
| `registry:read` | cadastro | registry | read | ADMIN, MANAGER, OPERATOR, VIEWER | P0.13 |
| `registry:write` | cadastro | registry | write | ADMIN, MANAGER, OPERATOR | P0.13 |
| `registry:reveal-document` | cadastro | registry | reveal-document | (nenhum — só concessão direta) | P0.13 |
| `registry:search-document` | cadastro | registry | search-document | (nenhum — só concessão direta) | P0.11 |
| `registry:export` | cadastro | registry | export | (nenhum — só concessão direta) | P1.12 |
| `user:read` | plataforma | user | read | ADMIN, MANAGER | P0.13 |
| `user:write` | plataforma | user | write | ADMIN | P0.13 |
| `deployment:read` | plataforma | deployment | read | (nenhum perfil de tenant — só papéis de plataforma: PLATFORM_OWNER, PLATFORM_OPS) | P0.43 |
| `gis:catalog-read` | gis | gis | catalog-read | (nenhum — só concessão direta; perfis por função na P1.37) | P1.27 |
| `gis:catalog-manage` | gis | gis | catalog-manage | (nenhum — só concessão direta; perfis por função na P1.37) | P1.27 |

## Regras

- `registry:reveal-document` não está em nenhum perfil de fábrica, nem no ADMIN: revelar o
  documento em claro exige a permissão direta (papel do token com exatamente esse nome). Quem
  cadastra não precisa, em regra, ver o documento depois de gravado; o acesso fica na auditoria.
- `registry:search-document` (P0.11, ADR-0008) também não está em nenhum perfil de fábrica:
  buscar por documento completo exige a permissão direta, separada de leitura e de revelação.
  Sem ela, a busca por documento é 403; com ela, a busca é registrada em
  `personal_data_access_log` e conta na cota por usuário por hora (429 ao exceder).
- `registry:export` (P1.12) também não está em nenhum perfil de fábrica: exportar uma
  lista exige a permissão direta, separada de leitura. Sem ela, a exportação é 403 e a
  negação fica registrada em `personal_data_access_log` (resultado DENIED); com ela, cada
  exportação — síncrona ou enfileirada — é registrada ali (tipo EXPORT), com o recurso
  e a quantidade de linhas, e o arquivo traz a mesma máscara da tela (ADR-0008).
- Permissão nova entra no catálogo junto da rota que a usa; rota sem permissão declarada é
  recusada no CI. As únicas rotas de negócio sem permissão declarada são `GET /api/v1/me` (dados do
  próprio usuário autenticado, sem acesso a dado de terceiros) e `GET /api/v1/about` (P0.42:
  só metadados da implantação — versão, commit, ambiente e data — sem dado de tenant ou de usuário).
- `deployment:read` não pertence a nenhum perfil de fábrica de tenant: ela só existe via
  papel de plataforma (PLATFORM_OWNER e PLATFORM_OPS, ver `PlatformRole`). Um token de tenant
  nunca a recebe, e um token de plataforma nunca recebe permissão de tenant.
- Perfis compostos pelo ADMIN do tenant e restrição negativa ficam na ED-06, sobre este catálogo.

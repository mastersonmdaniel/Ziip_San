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
| `user:read` | plataforma | user | read | ADMIN, MANAGER | P0.13 |
| `user:write` | plataforma | user | write | ADMIN | P0.13 |

## Regras

- `registry:reveal-document` não está em nenhum perfil de fábrica, nem no ADMIN: revelar o
  documento em claro exige a permissão direta (papel do token com exatamente esse nome). Quem
  cadastra não precisa, em regra, ver o documento depois de gravado; o acesso fica na auditoria.
- Permissão nova entra no catálogo junto da rota que a usa; rota sem permissão declarada é
  recusada no CI. A única rota de negócio sem permissão declarada é `GET /api/v1/me` (dados do
  próprio usuário autenticado, sem acesso a dado de terceiros).
- Perfis compostos pelo ADMIN do tenant e restrição negativa ficam na ED-06, sobre este catálogo.

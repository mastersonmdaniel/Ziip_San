# BFF com cookie HttpOnly (P0.14)

O navegador **nunca vê token**. A sessão vive no servidor (processo `api`,
PostgreSQL — core `V5__bff_sessions.sql`): o navegador só carrega o cookie de
sessão e o anti-CSRF. Apps e integrações máquina-a-máquina continuam usando
Bearer, sem nenhuma mudança.

## Duas cadeias no mesmo processo `api` (`SecurityConfig.java`)

| Cadeia | Ordem | Casa | Sessão | CSRF |
|---|---|---|---|---|
| BFF | 1 | `/bff/**` e `/api/v1/**` **com cookie de sessão** | Servidor (Spring Session JDBC) | Ligado |
| Resource server | 2 | resto (incl. `/api/v1/**` sem cookie) | STATELESS | Desligado |

A precedência é pelo **cookie de sessão**, não pelo caminho: um app com Bearer
cai na cadeia 2 mesmo em `/api/v1/**`; um navegador sem sessão cai na cadeia 2
e recebe 401 (o frontend vai para o login BFF). Os `permitAll` antigos
(`/actuator/health`, `/v3/api-docs`) continuam só na cadeia 2, inalterados.

Endpoints BFF, **fora** de `/api/v1` (o `RoutePermissionTest` exige permissão
do catálogo em toda rota `/api/v1`, e estas são do mecanismo de login):

- `GET /bff/login?returnTo=/caminho` — guarda o caminho (saneado, sem open
  redirect) na sessão e manda ao endpoint de autorização do oauth2Login;
- `GET /bff/csrf` — força a gravação do cookie anti-CSRF antes do primeiro POST;
- `POST /bff/logout` — invalida a sessão no PostgreSQL e encerra o SSO no
  Keycloak (volta para `/login`).

## Cookie de sessão

- Nome padrão: `__Host-ziipsan-session` (exige Secure + Path=/ + sem Domain).
- HttpOnly, Secure, SameSite=**Lax** — Lax, não Strict: o retorno do Keycloak é
  um GET cross-site e o Strict perderia o cookie da sessão no callback.
- Timeout de inatividade: 30 min (`maxInactiveIntervalInSeconds=1800` em
  `@EnableJdbcHttpSession` — o Boot 4.1 NÃO tem mais SessionAutoConfiguration,
  então `spring.session.*` no yml é inerte e não aparece em `application.yml`;
  a sessão é ligada explicitamente em `SecurityConfig`, e o filtro do Spring
  Session é registrado com `SessionRepositoryFilter.DEFAULT_ORDER` para
  envolver a requisição ANTES da cadeia Spring Security — sem isto, a cadeia
  veria a sessão do container e a sessão PG nunca seria usada em produção).
- Proteção contra fixação: `migrateSession` no login.
- **Exceção explícita do perfil `local`** (`application-local.yml`): sem TLS, o
  nome perde o prefixo (`ziipsan-session`) e o Secure é desligado — o navegador
  rejeita `__Host-` sem Secure. Nunca copiar isto para produção.

## Reconstrução do JWT (`BffJwtReconstructionFilter`)

O `JwtTenantFilter` e o `MeController` só tratam `JwtAuthenticationToken`; em
vez de refatorá-los, a cadeia BFF reconstrói o JWT a partir do access token
guardado no servidor (cliente autorizado da sessão), passando pelo **mesmo**
conversor da cadeia Bearer (papéis + permissões `recurso:ação` da P0.13).
Se houver um `OAuth2AuthorizedClientManager`, ele renova o access token com o
refresh token quando venceu (rotação); sem ele, usa o cliente guardado. Token
vencido/inválido limpa o contexto (a entrada do BFF redireciona para o login),
nunca vira 500. Se o access token não trouxer `amr`, o claim é completado com
o do id token (o gate de MFA da P0.15 lê o JWT reconstruído sem distinguir
BFF de Bearer).

XHR para `/api/v1` com sessão vencida recebe **401 em problem+json** (não
redirect): o frontend trata e vai para o login. Navegação comum sem sessão é
redirecionada para `/bff/login`.

## Keycloak: client confidencial `ziipsan-bff`

- `infra/keycloak/realms/ziipsan-realm.json`: client confidencial, authorization
  code, PKCE S256, `use.refresh.tokens=true`, redirect para o callback do BFF
  (`/login/oauth2/code/ziipsan-bff`, via o proxy do Vite na origem 3000).
- **Segredo por ambiente/cofre (`ZIIPSAN_BFF_CLIENT_SECRET`), nunca no repo.**
  O realm importa sem segredo: o Keycloak gera um no console e o operador o
  copia para o ambiente/cofre. Sem o valor, a api nem sobe (sem padrão).
- `revokeRefreshToken=true` no realm (o Keycloak não tem o flag por client):
  rotação single-use. Vale também para o `ziipsan-web` — rotação é o
  comportamento padrão de clientes OIDC modernos, não uma quebra de app.
- O fluxo de login do BFF (`ziipsan-bff-browser`, com MFA condicional) é da
  P0.15; a referência a ele no client (`authenticationFlowBindingOverrides`)
  já está no JSON e só se completa na P0.15.
- **Divergência residual, ponto para o dono**: o client público `ziipsan-web`
  **não** foi desativado nem apagado. Um navegador ainda poderia obter token
  por ele (fluxo antigo), só que o SPA não o usa mais. Desativar/convertê-lo
  é decisão do dono (pode quebrar apps legados que ainda o usam) — registrada
  como pendência, não decidida aqui.

## Frontend

- `frontend/src/services/auth/oidc.ts` reescrito: sem `oidc-client-ts`, sem
  sessionStorage, sem silent renew, sem Bearer. Login/logout são navegação e
  `fetch` com credenciais; o anti-CSRF vai em `X-XSRF-TOKEN` (cookie
  `XSRF-TOKEN`, legível pelo JS).
- `frontend/src/services/api.ts`: `withCredentials`, nunca envia `Authorization`
  (e remove qualquer um que chegue), 401 em chamada de negócio volta para o
  login BFF; 401 em `GET /me` não redireciona (o guarda de rota decide).
- `useAuth`/`Login`/`AuthCallback` reescritos no mesmo sentido; `/auth/callback`
  é rota legada (o BFF volta direto para o returnTo). Os campos OIDC de
  `runtimeConfig`/`config.json` (`VITE_OIDC_*`) ficaram como legado sem uso
  pelo login; a limpeza deles é um item de higiene futuro, não desta tarefa.
- Dependência `oidc-client-ts` removida do `package.json`/`package-lock.json`.

## Provas e o que falta

- Backend (CI): `BffChainIntegrationTest` (duas cadeias lado a lado),
  `BffJwtReconstructionFilterTest` (reconstrução, amr, rotação, token vencido),
  `BffLoginSuccessHandlerTest` (returnTo seguro), `BffSessionRepositoryTest`
  (sessão ida-e-volta pelo PG; tabelas sem RLS de tenant, de propósito).
- **E2E Playwright (`frontend/e2e/`) NÃO rodado no turno 9** (sem Keycloak nem
  navegador nesta VM): nenhum token em storage, cookie HttpOnly/SameSite, POST
  sem CSRF → 403, logout invalida a sessão. O ensaio de duas réplicas com uma
  derrubada no meio da sessão está como `test.skip` no spec, aguardando o
  harness de orquestração. **Por isso a P0.14 NÃO está fechada como concluída
  neste turno**, mesmo com o CI verde: falta a prova ponta a ponta.

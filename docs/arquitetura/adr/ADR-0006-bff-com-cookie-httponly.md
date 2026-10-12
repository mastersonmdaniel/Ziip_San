# ADR-0006 — BFF com cookie HttpOnly no lugar do token no navegador

- **Status:** Aceito, 12/10/2026
- **Decisor:** dono do produto
- **Substitui:** decisão D13 do plano ("`oidc-client-ts`, PKCE, token em memória"), na parte de onde o token fica
- **Afeta:** revisão 17, achados S5 e X1

## Contexto

O frontend faz o fluxo OIDC no navegador com `oidc-client-ts` e guarda usuário, tokens e estado em `sessionStorage`
(`frontend/src/services/auth/oidc.ts:28`, usado como `userStore` e `stateStore`). A D13 previa token em memória; o
código foi para `sessionStorage`. Em ambos os casos, qualquer XSS lê o access token e o refresh token e os usa fora da
sessão. Não há CSP (S5), e a revisão encontrou dependência de navegador com XSS conhecido (S6, `maplibre-gl` 4.7.1).

O client `sigsan-web` é público com PKCE (`infra/keycloak/realms/sigsan-realm.json:297-320`). O endereço do
provedor OIDC é fixado no build (`VITE_OIDC_AUTHORITY`, `VITE_OIDC_CLIENT_ID`, `oidc.ts:16-17`), o que impede usar o
mesmo artefato em homologação e produção (X1). A API é resource server sem sessão e com CSRF desligado
(`platform/sigsan-app/src/main/java/br/com/sigsan/app/security/SecurityConfig.java:61-64`), o que é correto para
token Bearer, mas não para cookie.

## Decisão

1. O frontend passa a usar um **BFF (backend-for-frontend)** que executa o fluxo OIDC (authorization code com PKCE,
   como client confidencial) e **guarda access token e refresh token no servidor**.
2. O navegador recebe apenas um **cookie de sessão HttpOnly, Secure, SameSite** (Lax ou Strict, a definir no desenho
   do fluxo de login), sem token legível por JavaScript.
3. O BFF repassa as chamadas `/api` à API anexando o token do lado do servidor. A API continua resource server sem
   sessão, validando JWT, tenant do claim e papéis como hoje.
4. **CSRF tratado** no BFF: token sincronizador ou double-submit em cabeçalho para todo método que altera estado, além
   do SameSite e da verificação de `Origin`.
5. Logout encerra a sessão no BFF e no Keycloak; refresh com rotação acontece só no servidor.

## Consequências

### Positivas

- XSS deixa de dar acesso ao token; o dano fica restrito ao que o script consegue fazer na aba enquanto ela está
  aberta.
- Refresh token sai do navegador, e a rotação (pedida em S5) fica sob controle do servidor.
- A configuração OIDC sai do build do frontend; o mesmo artefato serve a todos os ambientes (resolve parte de X1).
- Frontend e API na mesma origem simplificam CSP e eliminam CORS.

### Negativas

- Um componente a mais em produção, com estado de sessão. Com várias réplicas, a sessão precisa de armazenamento
  compartilhado. Para não reativar Redis (ADR-0010), a recomendação é sessão no PostgreSQL (Spring Session JDBC), com
  expiração e limpeza.
- Cookie de sessão traz CSRF de volta como risco; o tratamento do item 4 é obrigatório e precisa de teste.
- Os testes de autenticação do frontend baseados em `oidc-client-ts` precisam ser reescritos.

### O que muda no código

- Novo componente BFF (Spring Security OAuth2 Client e gateway para `/api`), no mesmo repositório; forma de
  implantação (processo próprio ou papel do mesmo artefato) definida no desenho do P0.
- Keycloak: `sigsan-web` passa a client confidencial, com segredo no cofre e redirect para o BFF.
- Frontend: sai `frontend/src/services/auth/oidc.ts` e o uso de `oidc-client-ts`; `frontend/src/services/api.ts` deixa
  de enviar `Authorization` e passa a enviar o cabeçalho anti-CSRF; o login vira redirecionamento para o BFF.
- API: inalterada no modelo (Bearer, stateless); deixa de ser exposta diretamente ao navegador.
- CSP estrita e `forward-headers-strategy` passam a ser configurados na borda e no BFF.

### O que muda no roadmap

- Entra no P0, junto das correções de S5. Dispensa o passo intermediário "token em memória" previsto na seção 9 da
  revisão 17.

## Alternativas consideradas

- **Token em memória no SPA (D13 original).** Rejeitada: reduz a exposição em relação ao `sessionStorage`, mas o token
  continua acessível a script injetado durante a sessão, e o refresh no navegador mantém a superfície.
- **Manter `sessionStorage` com CSP estrita.** Rejeitada: CSP reduz a probabilidade de XSS, mas não o impacto; o token
  continua legível.
- **Service worker guardando o token.** Rejeitada: complexidade alta, suporte irregular e proteção inferior ao cookie
  HttpOnly.

## Tarefas derivadas

| Tarefa | Fase |
|---|---|
| Desenho do BFF: implantação, armazenamento de sessão, SameSite, expiração, logout | P0 |
| BFF com OIDC confidencial, proxy de `/api`, CSRF e rotação de refresh, com testes de integração | P0 |
| Frontend sem `oidc-client-ts`, com cabeçalho anti-CSRF e configuração em tempo de execução | P0 |
| Client `sigsan-web` confidencial no realm, segredo no cofre | P0 |
| CSP estrita e cabeçalhos de segurança na borda | P0 |
| Pentest cobrindo sessão, CSRF e fixação de sessão | P3 |

## Refinamento (12/10/2026)

O desenho do P0 fixou a forma de implantação deixada em aberto em "O que muda no código": o BFF roda **dentro do
processo `api`** (mesmo artefato, sem processo próprio), e a sessão fica **no PostgreSQL** (Spring Session JDBC, core
`V5`), com expiração e limpeza, sem Redis. Ver `docs/arquitetura/ARQUITETURA-ALVO.md` §1 e plano P0.14.

# E2E do BFF (P0.14)

Harness Playwright **separado** do frontend: pacote próprio (`frontend/e2e/package.json`),
para não tocar no `package-lock.json` do app nem no `ci-linux.sh`. Não é executado
pelo CI.

## Pré-requisitos

- Stack rodando: Keycloak (realm `ziipsan`, via Podman nesta VM — nunca o Docker
  daemon), api (perfil `local`) e frontend (`npm run dev`, porta 3000, com o proxy
  do Vite para a api). Sem TLS local, o cookie de sessão é `ziipsan-session`
  (perfil `local`); fora do perfil local, é `__Host-ziipsan-session` com Secure.
- Usuário no realm com `tenant_id` e papel VIEWER (ex.: criado por
  `scripts/kc-dev-user.sh`).
- Client confidencial `ziipsan-bff` com o segredo em `ZIIPSAN_BFF_CLIENT_SECRET`
  (o realm importa sem segredo; o Keycloak gera um no console).

## Rodar

```sh
cd frontend/e2e
npm install
npx playwright install chromium
E2E_USER=usuario.teste E2E_PASSWORD=... npm test
```

Variáveis: `E2E_BASE_URL` (padrão `http://localhost:3000`),
`E2E_SESSION_COOKIE` (padrão `ziipsan-session`), `E2E_EXPECT_SECURE=true` fora
do perfil local.

## Estado no turno 9

**Não rodado nesta VM**: sem Keycloak e sem navegador instalados aqui, e o
Playwright precisaria baixar o Chromium. O spec existe e está pronto; a P0.14
só fecha como concluída com ele verde (mais o ensaio manual de duas réplicas,
hoje `test.skip` no spec). O que o backend prova sem ele: cadeia BFF x Bearer
(`BffChainIntegrationTest`), reconstrução do JWT (`BffJwtReconstructionFilterTest`),
persistência da sessão em PG (`BffSessionRepositoryTest`).

# E2E do BFF (P0.14)

Harness Playwright **separado** do frontend: pacote próprio (`frontend/e2e/package.json`),
para não tocar no `package-lock.json` do app nem no `ci-linux.sh`. Os specs do BFF e do
banner de versão continuam fora do CI (exigem a stack completa e um usuário de teste); o spec da matriz de navegadores (ED-08) É executado pelo CI — ver a seção própria abaixo.

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

## Matriz de navegadores (ED-08)

Projetos em `playwright.config.ts`, um por alvo de edital: `edge-88` (Edge 88),
`chrome-100` (Chrome 100), `firefox-116` (Firefox 116), mais o `chromium` de
referência que esta suíte já usava. O spec `navegadores.spec.ts` não exige
login: prova que a tela de entrada carrega sem erro de script em cada motor,
que o motor é o da família do projeto e que as APIs do piso de linguagem dos
alvos existem; o user-agent e a versão REAL do build executado ficam anexados
ao relatório de cada projeto.

**Limite honesto (também na nota técnica em `docs/editais/`):** o Playwright
não distribui binários históricos — Edge 88 e Chrome 100 não têm build oficial
automatizável para baixar. Sem executável fixo, os projetos rodam pelo canal do
navegador instalado (`msedge`, `chrome`) ou pelo Firefox do Playwright, ou
seja, provam a família do motor e o piso de linguagem, não o binário de
2021/2022. Para rodar contra um executável específico provisionado na máquina,
defina `E2E_EDGE_EXECUTABLE`, `E2E_CHROME_EXECUTABLE` ou
`E2E_FIREFOX_EXECUTABLE` com o caminho do binário.

### No CI

O job `e2e-navegadores` de `.github/workflows/ci.yml` compila o frontend,
instala os navegadores dos canais (`chromium`, `firefox`, `chrome`, `msedge`),
serve o build com `vite preview` na porta 3000 e roda só a matriz:

```sh
cd frontend/e2e
npm run test:matriz
```

### Local, contra o build servido

```sh
cd frontend && npm run build && npx vite preview --port 3000
cd frontend/e2e && npm install && npx playwright install chromium firefox chrome msedge
npm run test:matriz
```

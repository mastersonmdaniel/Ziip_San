# Marca e temas

Desenho do pacote de marca, da marca do tenant, dos temas e da renomeação `sigsan` → `ziipsan`. Decisões em ADR-0016
e ADR-0017. Estado em 12/10/2026: nada implementado; valores de cor do §4.2 aprovados pelo dono do produto; códigos de
contrato da API em inglês decididos pelo dono do produto e incluídos na renomeação (§5, plano v2.3).

## 1. Objetivos e não objetivos

**Objetivos:** toda marca do produto visível ao usuário vem de um único pacote; cada tenant aplica nome, logo e cor
em cabeçalho, contas, PDFs, portal do cliente final e e-mails; o usuário escolhe modo e padrão de cores, guardados no
servidor; contraste WCAG 2.1 AA verificado no CI; código renomeado antes de existir dado real.

**Não objetivos:** white-label por revenda (o desenho não o impede: o pacote é resolvido num ponto só, o
`BrandProvider`, que poderia passar a resolver por host); editor de cores para o cliente; cor do tenant como primária
da interface.

## 2. Pacote de marca

### 2.1 Estrutura

```
branding/
  brand.schema.json
  ziipsan/
    brand.json          identidade, cores de marca, domínios, dados legais, atribuição, metadados de PDF
    texts/pt-BR.json    descrição, rodapé, assinatura de e-mail, texto do Sobre
    assets/             logo-light.svg, logo-dark.svg, mark.svg, favicon.svg, favicon.ico,
                        icon-192.png, icon-512.png, icon-maskable-512.png, pdf-logo.png, email-header.png
```

`brand.json`: `productName`, `shortName`, `description`; cores de marca (`brandPrimary`, `themeColor.light|dark`,
usadas em logo, login, e-mail e `theme-color`, não na paleta da interface); domínios (`app`, `tenantSubdomainBase`,
`emailFrom`, `support`); legal (`legalName`, `cnpj`, `address`, `dpoEmail`, `termsUrl`, `privacyUrl`);
`attribution.enabled` e `attribution.text` (ex.: "operado com {productName}"); `pdf.author|creator|producer`.

### 2.2 Validação

O CI valida `brand.json` contra o schema, confere existência e dimensões dos ativos e recusa SVG com `script`,
`foreignObject`, atributo `on*` ou referência externa. Falha quebra o build.

### 2.3 Consumidores

| Consumidor | Como lê |
|---|---|
| Casca do frontend (`<title>`, `description`, `theme-color`, favicon, manifest) | plugin do Vite, no build |
| Interface React (nome, rodapé, atribuição, links legais) | `GET /api/v1/public/brand`, junto do `config.json` (P0.5) |
| Login e e-mail do Keycloak | tema gerado do pacote no build da imagem; realm com `loginTheme`, `emailTheme`, `displayName` |
| PDFs, e-mails, Sobre (ADR-0021) | `BrandProvider` (Java), mais a marca do tenant (§3) |

O pacote vira um módulo de recursos (`shared/ziipsan-brand`): BFF, API, worker e gerador de tema usam a mesma versão.

### 2.4 Seleção

`ZIIPSAN_BRAND` (padrão `ziipsan`) escolhe a pasta no build; pasta inexistente quebra o build. Em execução, a
aplicação recusa subir se a marca empacotada divergir da variável. Identificadores técnicos seguem `ziipsan` com
qualquer marca, sem aparecer ao usuário.

## 3. Marca do tenant

### 3.1 Chaves

Em `public.tenant_settings` (`shared/sigsan-core/src/main/resources/db/migration/core/V1__create_core_schema.sql:107`),
classe d (PARAMETROS-DO-TENANT §2), `is_sensitive = false`: `branding.display_name` (até 60 caracteres),
`branding.short_name` (até 20), `branding.logo` e `branding.logo_dark` (id do ativo; o escuro é opcional),
`branding.color` (`#RRGGBB`), `branding.contact` (telefone, e-mail, site, endereço de atendimento) e
`branding.document_footer` (até 300). Leitura no frontend no padrão de `frontend/src/hooks/useRegistrySettings.ts`.

### 3.2 Upload de logo

- PNG e WebP, até 512 KB, de 128 a 2.048 px no maior lado. SVG fica fora até haver sanitização por lista permitida.
- Tipo conferido pelos bytes iniciais; reencode no servidor (remove metadados e arquivos poliglotas); quarentena até a
  varredura pelo ClamAV junto do `worker`, o mesmo pipeline dos anexos de chamados (exceção registrada no ADR-0010,
  substituível pelo serviço da hospedagem); troca e remoção auditadas.
- Armazenamento em `public.tenant_assets` (core `V9_2`; `bytea`, SHA-256, tipo, dimensões, estado da varredura) atrás
  de `TenantAssetStore`, que pode passar ao S3 da hospedagem decidida (ADR-0011, aceito: AWS `sa-east-1`) sem mudar
  quem a usa; MinIO segue congelado (ADR-0010).
- Entrega com o hash na URL, `Cache-Control: immutable`, `X-Content-Type-Options: nosniff` e
  `Content-Security-Policy: default-src 'none'`.

### 3.3 Antes do login

O login acontece antes de se saber o tenant. Três caminhos:

- **Subdomínio** (`<slug>.<tenantSubdomainBase>`): o BFF resolve o tenant pelo host e mostra a marca antes do login. O
  slug passa a ser público.
- **E-mail primeiro** (Keycloak Organizations): a marca só aparece depois do e-mail e exige tema que leia a
  organização.
- **Caminho** (`/t/<slug>`): funciona, com endereço menos memorizável.

Recomendação: subdomínio; `app.<domínio>` mostra só a marca do produto. Organizations é avaliado na P0.13; a marca do
tenant dentro da página do Keycloak depende dessa avaliação.

`GET /api/v1/public/tenant-brand`, resolvido pelo host, devolve só nome, logos, cor e contato. Slug desconhecido
devolve a marca do produto com o mesmo status. Sem contexto de tenant para a RLS, a leitura usa função
`SECURITY DEFINER` restrita às chaves `branding.*`. Cache curto e limite por IP.

### 3.4 Onde aparece e contraste

Cabeçalho (ao lado do produto), entrada do subdomínio, contas, PDFs, portal do cliente final (P3.3) e e-mails. A
interface de trabalho mantém o padrão do usuário.

- Texto sobre a cor do tenant: branco ou o texto escuro padrão, o que der 4,5:1 (um dos dois sempre dá).
- Cor do tenant como texto sobre branco: só com 4,5:1; abaixo disso, o sistema escurece a cor e avisa ao salvar.
- Faixa decorativa: 3:1 contra o fundo, ou ganha contorno.
- Conta para gráfica (P2.8) segue o leiaute aprovado.

## 4. Temas

### 4.1 Tokens semânticos

| Grupo | Tokens |
|---|---|
| Base | `background`, `foreground`, `surface`, `surface-raised`, `muted`, `muted-foreground`, `border`, `input`, `focus` |
| Ação | `primary`, `secondary`, `accent`, cada um com `-foreground` |
| Estado | `success`, `warning`, `danger`, `info`, cada um com `-foreground` |
| Dados e mapa | `chart-1` a `chart-6`; `map-water`, `map-sewer`, `map-boundary`, `map-selection`, `map-alert` |

`card`, `popover`, `destructive` e `ring` ficam como apelidos de `surface`, `surface-raised`, `danger` e `focus` até a
troca nos componentes. Há cores literais em `frontend/src/components/ui/Badge.tsx` e `Alert.tsx` (17 classes como
`text-red-600`); regra de lint passa a recusar classes de paleta do Tailwind, `#hex`, `rgb()` e `hsl()` numérico em
componentes.

### 4.2 Valores iniciais

**Aprovada pelo dono em 12/10/2026; ajustes finos permitidos após ver as telas, mantendo o teste de contraste.**
Contrastes calculados pela fórmula WCAG; quem aceita cada valor, inclusive o ajustado, é o teste do §4.6. Valor
ajustado é registrado nesta tabela. Comuns aos dois padrões (no escuro, os `-foreground` de estado e da primária são `222 47% 8%`; no claro, branco):

| Token | Claro | Escuro |
|---|---|---|
| `background` / `surface` | `0 0% 100%` | `222 47% 8%` / `222 47% 10%` |
| `foreground` | `222 47% 11%` | `210 40% 98%` |
| `muted` / `muted-foreground` | `210 40% 96%` / `215 19% 33%` (7,3:1) | `217 32% 15%` / `215 20% 72%` (7,6:1) |
| `border` (decorativa) / `input` | `214 20% 80%` / `215 16% 42%` (5,7:1) | `217 25% 28%` / `215 16% 60%` (6,3:1) |
| `success`, `warning` | `142 72% 26%` (6,0:1), `32 95% 24%` (8,2:1) | `142 60% 55%`, `40 95% 65%` |
| `danger`, `info` | `0 72% 40%` (7,0:1), `221 83% 40%` (8,0:1) | `0 90% 72%`, `213 94% 72%` |
| `chart-3` a `chart-6` | `32 95% 40%`, `262 52% 47%`, `340 70% 42%`, `95 60% 30%` | `38 92% 60%`, `262 80% 75%`, `340 80% 70%`, `95 55% 55%` |
| `map-water`, `map-sewer`, `map-boundary` | `210 100% 40%`, `25 75% 35%`, `280 60% 45%` | `205 95% 65%`, `25 85% 62%`, `280 70% 72%` |
| `map-selection`, `map-alert` | `38 100% 32%`, `0 80% 45%` | `48 100% 55%`, `0 85% 65%` |

| Token | Azul Água claro | Azul Água escuro | Verde Água claro | Verde Água escuro |
|---|---|---|---|---|
| `primary` = `focus` = `chart-1` | `201 96% 32%` (5,9:1) | `199 89% 58%` (8,3:1) | `176 84% 24%` (5,9:1) | `172 66% 50%` (10,1:1) |
| `secondary` | `200 60% 94%` | `205 40% 20%` | `170 45% 92%` | `175 35% 18%` |
| `accent` | `190 70% 90%` | `195 45% 22%` | `165 55% 88%` | `170 40% 21%` |
| `chart-2` | `176 84% 24%` | `172 66% 50%` | `201 96% 32%` | `199 89% 58%` |
| `theme-color` | `#0369a0` | `#0b111e` | `#0a716a` | `#0b111e` |

O `theme-color #0361a0` de `frontend/index.html:7` não coincide com a primária atual (`#0369a0`); passa a ser gerado.

### 4.3 Registro de padrões

`frontend/src/theme/palettes/<id>.json` (id, rótulo, tokens por modo, sobrescrevendo os comuns). Ids em inglês
(`aqua-blue` para Azul Água, `aqua-green` para Verde Água); rótulos em português (plano §8, Idioma). Um script gera
`palettes.css` (seletores `:root[data-palette="<id>"]` e `:root[data-mode="dark"][data-palette="<id>"]`) e
`palette-ids.json`, com que o backend valida preferências e configurações. Tailwind passa a
`darkMode: ['selector', '[data-mode="dark"]']`.

### 4.4 Preferência e padrão do tenant

Usuário: `ui.theme.mode` (`light`, `dark`, `system`) e `ui.theme.palette` em `user_preferences` (schema do tenant,
tenant `V1_7`, RLS por tenant), por
`GET/PUT /api/v1/me/preferences`; `localStorage` vira só cache. Tenant: `theme.default_mode`, `theme.default_palette`
e `theme.allowed_palettes` em `tenant_settings`. Vale a escolha do usuário, se permitida; senão a do tenant; senão
Azul Água no modo do sistema. Padrão retirado da lista do tenant cai para o padrão dele, sem apagar a escolha guardada.

### 4.5 Aplicação sem piscar

Hoje `frontend/src/hooks/useTheme.tsx:26-31` aplica o tema num `useEffect`, depois da primeira pintura. Passa a ser:

1. Script inline e estático no `<head>` lê o cache, resolve `system` por `matchMedia` e grava `data-mode`,
   `data-palette`, `color-scheme` e `theme-color` antes da pintura.
2. O build publica o SHA-256 do script para a CSP (`script-src 'self' 'sha256-…'`, P0.16); o CI recalcula e compara.
3. Ao chegar a preferência do servidor, o React corrige o tema se o cache divergir.
4. Em `system`, acompanha a mudança de `prefers-color-scheme` sem recarregar.

### 4.6 Contraste no CI

Teste Vitest percorre padrão × modo: texto e `-foreground` contra o fundo, 4,5:1; `input`, `focus`, `chart-*` e
`map-*` contra `background` e `surface`, 3:1. Os pares ficam declarados no registro. Playwright com axe roda as telas
principais em cada padrão e modo, porque axe em jsdom não mede contraste (`frontend/src/index.css:9`). Falha bloqueia
o merge.

### 4.7 Como acrescentar um padrão

1. Copiar `aqua-blue.json` para `<id>.json`; definir id (inglês, minúsculas e hífen) e rótulo.
2. Preencher os tokens dos dois modos; omitir os que seguem os comuns.
3. Rodar o gerador e o teste de contraste até passar; revisar as capturas do Playwright.
4. Aprovação do dono e PR. O padrão fica disponível aos tenants sem restrição; quem tem lista precisa incluí-lo.

Retirar um padrão exige migrar os tenants que o têm como padrão.

## 5. Renomeação `sigsan` → `ziipsan`

### 5.1 Escopo

Em 12/10/2026, 544 arquivos citam `sigsan`: 27 módulos `*/sigsan-*` e seus artifactIds; pacotes `br.com.sigsan`;
`SigsanApplication`, `SigsanProperties`; propriedades `sigsan.*`; variáveis `SIGSAN_*`; roles `sigsan_owner`,
`sigsan_app`, `sigsan_migrator`, `sigsan_ts` (e `sigsan_worker`, prevista na P1.9); contêineres e banco do
`docker-compose.yml`; realm `sigsan`, client `sigsan-web`, audiência `sigsan-api` e `sigsan-realm.json`;
`urn:sigsan:problema:` (`ProblemResponses.java:20` e testes) e os códigos de problema em português
(`ApiException.java`, `GlobalExceptionHandler.java`, `JwtTenantFilter.java`, `SecurityConfig.java` e serviços do
cadastro); tópico MQTT `sigsan/{tenant}/scada/...` (`ScadaTopic.java:8`); `spring.application.name`; contexto HKDF
(`EnvDerivedTenantKeyProvider.java:32-33`); scripts; pasta raiz e repositório.

### 5.2 Ordem

1. P0.1 concluída (clone fora do Drive: renomear em massa no Drive gera conflito de sincronização), nenhum branch
   aberto, tag `pre-rename`.
2. `docker compose down -v`: banco e Keycloak de desenvolvimento serão recriados.
3. Commit só de `git mv` (módulos, pastas de pacote, arquivos com o nome), sem mudar conteúdo, para o
   `git log --follow` achar a origem.
4. Commit de conteúdo, padrão por padrão, do mais específico ao mais genérico (`br.com.sigsan`, artifactId, classe,
   `SIGSAN_`, `sigsan.`, roles, URN, tópico, realm, client, audiência), revisando o diff de cada um. A URN passa a
   `urn:ziipsan:problem:` e os códigos de problema passam ao inglês pela tabela do plano P0.33 (`versao-desatualizada`
   → `stale-version`, `validacao` → `validation`), no mesmo commit do frontend (`errors.ts`) e dos testes; `title`,
   `detail` e mensagens continuam em português.
5. Textos visíveis com "SIGSAN" (`frontend/index.html:6,8`, `Layout.tsx:61`, `Login.tsx:40`, `AuthCallback.tsx:45`,
   `PageHeader.tsx:6`, `sigsan-realm.json:3,298`) passam a ler o pacote de marca; não viram "Ziip SAN" literal.
6. `KeycloakAdminService.java` (realm por tenant e domínios `sigsan.com.br` nas linhas 59, 107-122, 154, 186) é
   removido com a P0.13, não renomeado.
7. `name: ziipsan` explícito no compose; `.env.example`, `scripts/it-db.env` e CI atualizados; `.env` local ajustado à
   mão pelo dono; `package-lock.json` regenerado pelo `npm install`.
8. Pasta raiz, repositório no GitHub e remoto.

### 5.3 Fora da troca cega

- ADRs, diário e `docs/levantamento/` são históricos (regra 2 do README dos ADRs); os caminhos antigos valem até a tag.
- Contexto HKDF: trocado uma vez, com teste de vetor fixo.
- Menções ao Ziip Cloud, outro produto. `target/` e `node_modules/`: regenerados.
- Migrations: os `GRANT` mudam e, com eles, o checksum do Flyway. Aceito porque não há banco com dado real; depois
  disso voltam a ser imutáveis.

### 5.4 Riscos e verificação

- Propriedade `sigsan.*` esquecida cai em valor padrão em silêncio: `ignoreUnknownFields = false` e busca residual.
- Emissor OIDC muda para `/realms/ziipsan`: backend, BFF e `config.json` juntos, com login ponta a ponta.
- Volumes órfãos do compose antigo: `down -v` antes e nome de projeto fixo.
- Trabalho paralelo: nenhum branch aberto durante a execução.

Verificação: `mvn clean verify`, testes do frontend, `scripts/ci.sh`, `scripts/dev-up.sh` do zero, login, testes da
URN, dos códigos de problema (teste de contrato) e do tópico, ida e volta da cifra e busca residual sem `sigsan` fora
da lista permitida e sem `problema:` nem código de problema antigo.

### 5.5 Congelados depois

| Item | Mudança futura só por |
|---|---|
| `ziipsan/enc/v1`, `ziipsan/bidx/v1` (dado cifrado e índice cego ficariam ilegíveis) | versão `v2` com recifragem |
| `urn:ziipsan:problem:` e os códigos de problema em inglês da P0.33 (contrato da API) | nova versão da API; código novo é aditivo |
| Prefixo de tópico MQTT (equipamento de campo) | período de convivência |
| Realms (`ziipsan` e, para os consumidores do portal, `ziipsan-portal`, ADR-0022), audiência e emissor | migração planejada |

## 6. Re-brand

| Item | Prazo estimado |
|---|---|
| Nova pasta em `branding/` (ativos, textos, dados legais) | 1 a 2 dias após a arte |
| Domínio, DNS e certificados | 1 dia mais propagação |
| Remetente: SPF, DKIM, DMARC e aquecimento | 1 a 2 semanas; começa primeiro |
| Keycloak (redirects, origens), CSP, cookies | 1 dia |
| Build com `ZIIPSAN_BRAND`, homologação, implantação | 1 dia |
| Aviso aos tenants e contratos | fora do software |

`scripts/check-brand-literals.sh`, chamado pelo `ci.sh`, recusa `SIGSAN`, `Sigsan`, `Ziip SAN`, `ZiipSAN`, `Ziip San`
e `ZIIP` em `frontend/`, `infra/keycloak/`, templates de e-mail e PDF e `src/main/resources`, e `sigsan` em qualquer
caixa fora de `docs/` e do diário; exceções só em `branding/` e numa lista permitida versionada.

## 7. Tarefas

| Tarefa | Fase | Plano |
|---|---|---|
| Renomeação `sigsan` → `ziipsan`, com HKDF congelado por teste de vetor fixo e códigos de contrato da API em inglês | P0, logo após a P0.1 | P0.33 |
| Pacote de marca, schema, `BrandProvider`, plugin do Vite, endpoint público, teste de literais | P0 | P0.34 |
| Tema do Keycloak gerado do pacote | P0, entra na imagem da P0.29 | P0.35 |
| Tokens, registro, Azul Água e Verde Água, script sem piscar com hash na CSP, contraste no CI | P0, com a P0.16 | P0.36 |
| Valores do §4.2, aprovados em 12/10/2026, no registro; ajuste fino após ver as telas, com contraste verde | P0 | P0.37 |
| Avaliação de Keycloak Organizations | P0, com a P0.13 | P0.38 |
| Preferência no servidor; padrão e restrição do tenant | P1 | P1.18 |
| Marca do tenant: chaves, logo, subdomínio, endpoint pré-login, cabeçalho | P1, com a P1.6 | P1.19 |
| Marca do tenant e metadados nos PDFs | P2 | P2.17 |
| Marca do tenant no portal e nos e-mails | P3, com a P3.3 e a P3.5 | P3.21 |

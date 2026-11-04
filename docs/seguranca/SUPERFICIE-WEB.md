# Superfície web — P0.16 (parcial)

> **Status: P0.16 NÃO está fechada.** Os itens abaixo (CSP, HSTS/forwarded-headers,
> rate limit, limite de corpo, teto de página, validação das buscas e remoção de
> campos) estão implementados. O que falta para fechar a P0.16 é a decisão do dono
> sobre o mapeamento de status de validação (**400 vs 422**) — ver `docs/seguranca/`
> e o relatório do turno. Até essa decisão, o handler atual é mantido intacto:
> erros de Bean Validation continuam **422** (`validation`, com `errors[]`) e
> erros de domínio via `ApiException.BadRequest` continuam **400**.

## 1. CSP — cabeçalho nas respostas da API

As duas cadeias (`SecurityConfig`: BFF e resource server) emitem, em todas as
respostas:

```
Content-Security-Policy: default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'
```

### Inventário de cargas runtime (levantado antes de ligar a CSP)

A CSP acima vale para **respostas da API**, que não são documentos HTML e não
carregam scripts, estilos ou imagens. Por isso `default-src 'none'` é seguro
aqui. O documento SPA é outro caso — **não** há meta tag CSP em `index.html`
(uma meta estática divergiria entre dev/prod e não cobriria o host do mapa,
definido em runtime):

| Carga | Origem | Consequência para a CSP do documento |
|---|---|---|
| Mapa (`MapCanvas` / `MapPointPicker`) | `styleUrl` vindo de `VITE_MAP_STYLE_URL` (host do estilo/tiles desconhecido em build) | A CSP do documento SPA deve ser aplicada pelo **host do frontend** quando o host do mapa for fixado; congelar hoje quebraria o mapa ou o deixaria aberto demais. |
| Keycloak | Navegação top-level para o login BFF (`/bff/login` → `/oauth2/authorization/ziipsan-bff`) | Nunca embutido em iframe (`frame-ancestors 'none'` na API não afeta navegação top-level). Nenhuma diretiva de `connect-src` é necessária na API. |
| Script inline de tema (P0.36, futuro) | Injetado no documento SPA | Quando existir, a CSP do documento precisará de um **hash** do script — item registrado para a P0.36, não inventado aqui. |

## 2. HSTS + forwarded-headers

- HSTS nas duas cadeias: `max-age=31536000; includeSubDomains` (sem `preload` —
  preload é um compromisso permanente com o domínio, fora do escopo desta etapa).
- `server.forward-headers-strategy=framework` em `application.yml`: o Spring passa
  a honrar `X-Forwarded-Proto`/`X-Forwarded-Host` ao decidir se a requisição é
  segura (e, portanto, se emite HSTS).
- **Premissa documentada:** não há proxy no repositório. O proxy de produção
  **deve** enviar `X-Forwarded-Proto`; sem ele, atrás de um terminador TLS a API
  enxergaria HTTP e não emitiria HSTS. O teste (`WebSurfaceIntegrationTest`)
  prova o writer do HSTS com `request.secure(true)`; o `ForwardedHeaderFilter`
  em si não roda no MockMvc, então a cadeia proxy → header é premissa de
  implantação, não prova de teste.

## 3. Rate limit — 429

- Filtro `RateLimitFilter`, **após** o `JwtTenantFilter` nas duas cadeias (para
  ter o tenant no contexto). Isento: `/actuator/health` (o health check não pode
  ser derrubado pelo próprio limite).
- Chave, em ordem: `tenant:<uuid>` se houver tenant no contexto; senão
  `user:<sub>` se houver autenticação; senão o IP (`getRemoteAddr`).
- Limite padrão: **1000 requisições/minuto por chave**, janela fixa de 60 s
  (`ziipsan.rate-limit.limit-per-minute`, `ziipsan.rate-limit.window-seconds`;
  `ziipsan.rate-limit.enabled`, default `true`).
- Resposta: **429** `too-many-requests` em problem+json + header `Retry-After`
  (segundos até o fim da janela).
- Store compartilhado entre réplicas **sem Redis**: `JdbcRateLimitStore` sobre a
  tabela `rate_limit_buckets` (schema `public`, sem RLS de tenant — o limite é
  do servidor, anterior a qualquer tenant), com **uma única instrução SQL
  atômica** por requisição:

  ```sql
  INSERT INTO rate_limit_buckets (bucket_key, window_start, request_count)
  VALUES (?, ?, 1)
  ON CONFLICT (bucket_key) DO UPDATE
    SET window_start = CASE WHEN rate_limit_buckets.window_start < ?
                            THEN ? ELSE rate_limit_buckets.window_start END,
        request_count = CASE WHEN rate_limit_buckets.window_start < ?
                             THEN 1 ELSE rate_limit_buckets.request_count + 1 END
  RETURNING request_count;
  ```

  Uma linha por chave (a janela antiga é sobrescrita, sem limpeza periódica).
  `InMemoryRateLimitStore` existe para testes unitários e como fallback de
  construção. **Fail-open:** se o banco falhar, o filtro permite a requisição e
  registra aviso — o limite nunca pode derrubar a API sozinho.
- Testes: `AppIntegrationTestBase` registra `ziipsan.rate-limit.enabled=false`
  (a suíte inteira compartilha o tenant de IT e estouraria qualquer teto baixo);
  `RateLimitIntegrationTest` é standalone, com limite próprio (3/min) e MockMvc
  próprio.

## 4. Limite de corpo — 413

- Filtro `ContentLengthLimitFilter`, **antes da autenticação** nas duas cadeias
  (antes do `CsrfFilter` na BFF, antes do `BearerTokenAuthenticationFilter` no
  resource server): um corpo gigante não deve custar autenticação nem chegar ao
  Jackson. Cobre `/api/v1/**` e `/bff/**`, só métodos com corpo
  (POST, PUT, PATCH, DELETE).
- Ponto de corte: **filtro**, não conector. Não há `server.tomcat.max-*`
  configurado no repo e o filtro dá a resposta 413 em problem+json
  (`content-too-large`) no mesmo formato dos demais erros — um limite de
  conector devolveria um erro cru do Tomcat.
- Limite: **1 MiB** (1048576 bytes, `ziipsan.body-limit.max-bytes`). Nota da
  integração P1-C: o importador (P1.40) trouxe o primeiro upload multipart do
  produto (`POST /api/v1/registry/imports`, `MultipartFile`), e o limite cobre
  o corpo multipart inteiro — planilhas acima de ~1 MiB levam 413 até que um
  limite próprio de importação seja decidido.
- Dois caminhos: fast path por `Content-Length` acima do limite (413 imediato,
  sem ler o corpo); sem `Content-Length` (chunked), wrapper de `InputStream`
  que conta bytes e estoura em 413 ao exceder.

## 5. Teto de página

- `JpqlQuery.page`: o offset é calculado em `long` e, se
  `offset > Integer.MAX_VALUE` (ou `page < 0` / `size < 1`, defensivamente),
  lança `ApiException.BadRequest("invalid-page", ...)`.
- Antes, `spec.page() * spec.size()` em `int` estourava silenciosamente acima
  de `page ≈ 21.474.836` (com `size=100`), e `setFirstResult` recebia um valor
  enrolado. Agora: página acima do teto devolve **400**, nunca 500.
- A proteção fica em `JpqlQuery.page`, não só em `PageSpec.of`, para cobrir
  construções diretas de `PageSpec`. `PageSpec` já capa `size` em 100
  (`MAX_SIZE`) e o default é 25.

## 6. Validação das buscas

- `CustomerSearch.q` e `UnitSearch.q`: `@Size(max=200)`.
- `POST /search` dos dois controllers: `@Valid` no `@RequestBody` (sem `@Valid`,
  o `@Size` não é avaliado — os dois juntos, sempre).
- `GET` de lista dos dois controllers: classe anotada com `@Validated` e
  `@Size(max=200)` no parâmetro `q` (anotação de parâmetro exige `@Validated`
  na classe; `@Valid` em parâmetro não avalia `@Size`). Precedência: `q` com
  mais de 200 caracteres dá 422 de validação **antes** da recusa 400
  `search-in-url` da regra de domínio (que continua valendo para `q` curto).
- `GlobalExceptionHandler` ganhou handler de `ConstraintViolationException` →
  **422** no mesmo formato `validation`/`errors[]` (sem ele, violar `@Size` em
  parâmetro de GET cairia em 500 via `handleUnexpected`).
- Nada disso muda o mapeamento de status: erros de validação continuam 422
  pelo handler atual. A decisão 400 vs 422 do dono foi fechada em 2026-11-01
  (manter 422) e a fase 2 a aplica também ao corpo ilegível — o que fecha
  este item da P0.16.

## 7. Campos removidos da tela (P0.16)

O backend nunca modelou estes campos (o contrato em `docs/frontend-cadastro.md`
já os excluía); o frontend os coletava e os enviava para serem descartados em
silêncio (`fail-on-unknown-properties=false`). Removidos da tela, dos tipos,
do esquema zod, das fixtures e dos testes:

| Campo | Onde estava |
|---|---|
| `rg`, `rgState`, `rgIssueDate` | Formulário de cliente (bloco "RG"), `CustomerFormValues`, `Customer`/`CustomerInput`, detalhe |
| `motherName` | Formulário de cliente ("Nome da mãe"), `CustomerFormValues`, `Customer`/`CustomerInput`, detalhe |
| `costCenter` | Definição de campo da localidade, tipo `Locality` |

- `RegistryApiE2ETest` atualizado (fase 2): os dois corpos que cristalizavam o
  descarte (localidade e cliente com `campoDesconhecido`) agora afirmam
  primeiro a recusa 422 com o campo em `errors[]` e só depois fazem a
  criação válida que alimenta o resto do fluxo — a tentativa recusada não
  grava nada (o CPF da tentativa não é consumido).
- Comentário em `application.yml` atualizado no mesmo sentido.
- `fail-on-unknown-properties` agora é `true` (fase 2, decisão do dono
  opção 1: backend modela os campos). Propriedade desconhecida no corpo é
  recusada com 422 `validation`/`errors[]` via
  `GlobalExceptionHandler.handleHttpMessageNotReadable` (sem o override,
  cairia no handler genérico com 400); corpo malformado também é 422
  (`body`/`MALFORMED_BODY`). Cliente antigo em cache falha alto, em vez de
  perder dado em silêncio. `WebSurfaceIntegrationTest` cobre: desconhecido
  → 422, malformado → 422, payload válido → 201.

## Provas

- `WebSurfaceIntegrationTest` (CSP em 200 e em 401; HSTS com requisição segura
  e ausente sem; 413 fast path e chunked; 422 em `q` longo no POST e no GET;
  400 em página acima do teto; fase 2: 422 em propriedade desconhecida e em
  corpo malformado, 201 em payload válido).
- `RateLimitIntegrationTest` (standalone, limite 3/min: 3 passam, 4ª é 429 com
  `Retry-After`; health isento) + `RateLimitFilterTest` (chave tenant/usuário/IP,
  fail-open).
- `ContentLengthLimitFilterTest` (fast path, chunked, método sem corpo, fora
  do prefixo).
- `PageCeilingTest` (`JpqlQuery.page` com offset acima de `Integer.MAX_VALUE`
  → `BadRequest`).
- Frontend: `vitest` + typecheck + lint (remoção validada pela compilação: os
  tipos `Customer`/`CustomerInput`/`Locality` não têm mais os campos).

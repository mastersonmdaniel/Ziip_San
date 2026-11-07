# Revisão cruzada — P3.36 (Central de notificações do usuário e preferências)

Revisor: frente independente (revisão cética, sem implementação e sem push).
Objeto: `entrega/` (18 arquivos de código/SQL + `RELATORIO-P3.36.md` = 19 arquivos no total;
o relatório diz "17 criados" — contagem errada por 1, sem impacto) e `espelho/` da P3.36.
Fontes conferidas: aceite da P3.36 em `tarefas-P3.json`; plano v3.0 §3
(`docs__plano-v3__PLANO-v3.0.md`); espelho da P2.5 (mesmo HEAD declarado `2ba51f8`),
incl. `Json`, `ApiException`, V8 (outbox), V9_9/V9_10, V8_1 (tenant) e
`CoreMigrationRunner`; `TenantParameterService` (padrão do módulo platform).

Legenda: **OK** = conferido e correto · **PROBLEMA** = defeito ou lacuna real ·
**NÃO VERIFICÁVEL** = o material disponível não decide.

---

## 1. Acceptance da P3.36, item a item

### 1.1 Central única (sino, lista, lido/não lido) — **OK**
`NotificationCenterService.listForUser` / `unreadCount` / `markRead` +
`NotificationController` (`GET /api/v1/me/notifications`,
`GET /notifications/unread-count`, `POST /notifications/{id}/read`) sobre a tabela
`notifications` da V1_15. Lista filtra `channel = 'IN_APP'` e o próprio usuário;
`markRead` de aviso alheio devolve 404 (não vaza existência). Coerente.

### 1.2 Alimentada pela outbox da P1.9 — **OK como porta, com problema de idempotência (ver §6.2)**
`ingest(OutboxMessage)` existe; o laço do worker é da P1.9 e está declarado como
pendência 2 do relatório — declaração honesta. A DTO `OutboxMessage` é uma porta
simplificada (a tabela real da V8 tem também `queue`, `dedup_key`, `status`), o que é
aceitável para a fronteira declarada.

### 1.3 Catálogo da P1.38; evento fora do catálogo recusado — **OK (regra), com ressalvas (§6.4, §6.5)**
`NotificationIngestRule.draft` consulta `BusinessEventCatalog.find(type, version)` e
recusa com 409 (`event-not-in-catalog`) nome e versão não declarados; testes
`eventOutsideCatalogIsRefused` e `undeclaredVersionIsRefused` cobrem os dois casos.
A carga é lida **só** via `Json.readTree` do core (conferido: nenhum outro parser JSON
na entrega; o `payload` gravado é o texto já validado, via `CAST(? AS jsonb)`).

### 1.4 Preferências por canal e por finalidade; registro único lido por P3.5/ED-41 — **OK (registro) / PROBLEMA (aplicação, ver §6.1)**
Tabela única `notification_preferences` (canal × finalidade × sujeito) na V1_15.
A reconciliação do relatório está **correta**: o aceite cita "`user_preferences` da
P1.18", mas a V1_7 da P1.18 é só tema/idioma e o mapa §3 reserva à P3.36 a tabela
própria `notification_preferences` — a frente seguiu a autoridade certa (o mapa) e
explicou. Leitores futuros leem a mesma tabela, como manda o COE-19.

### 1.5 Digest (`notify.digest_default`, classe d) — **PROBLEMA parcial**
Parâmetro definido na V9_11 com classe `d` e fábrica `"IMMEDIATE"` + limite
`ALLOWED_VALUES [IMMEDIATE, DAILY]`; regra pura `NotificationPolicy.decide`
implementada e testada com valor calculado à mão (verificado, §3). **Porém** nenhum
código de produção chama `decide()` nem `NotificationParameters.digestDefault(...)`
(conferido por grep: só os testes chamam `decide`; `digestDefault` não tem chamador).
O relatório marca o item ✅ citando "parâmetro + decide + teste" — verdade da regra,
mas a fiação no serviço não existe; o ✅ está otimista (ver §6.1).

### 1.6 Silêncio por período — **PROBLEMA parcial (mesma raiz de 1.5)**
Par início/fim gravado na preferência, CHECK de par na migration
(`(silence_start IS NULL) = (silence_end IS NULL)`) e guarda de par no serviço;
regra de janela (inclusive cruzando meia-noite) correta e testada (§3). Mas, como o
digest, o silêncio só vive na regra pura: `ingest` nunca lê preferências (ver §6.1).

### 1.7 Categorias obrigatórias não desligáveis (`notify.mandatory_categories`, classe c) — **OK**
Piso exato do aceite — `SECURITY`, `PENDING_APPROVAL`, `LEGAL_NOTICE` — em dois
lugares coerentes: `NotificationPolicy.PLATFORM_FLOOR` e o `factory_default` da V9_11
(classe `c`). `effectiveMandatoryCategories` faz **piso ∪ tenant** (o tenant só
acrescenta; §3). `savePreference` chama `assertCanDisable` (422 nomeando o campo)
sempre que `enabled == false`. Como `decide()` devolve `NOW` para obrigatória,
digest/silêncio também não atrasam categoria obrigatória — coerente.

### 1.8 Teste de que aviso legal não é desligável — **OK**
`NotificationPolicyTest.legalNoticeCannotBeDisabled` é dedicado, exige
`ApiException.Validation` (422) e repete com o conjunto do tenant vazio; há ainda
teste irmão para `SECURITY`/`PENDING_APPROVAL` e de que categoria comum desliga.
Nenhuma asserção afrouxada em nenhum dos 3 arquivos de teste (sem `@Disabled`, sem
asserção frouxa; valores exatos com `isEqualTo`). Ressalva menor: a segunda asserção
do teste usa `Set.of()`, idêntico à constante `NO_TENANT_ADDITIONS` — redundante,
não errada.

### 1.9 Preferência do consumidor × contato verificado/consentimento (CAD-84/DAD-30) — **declaração honesta: OK; guarda existe: OK com furos (§6.3)**
O relatório declara ⚠️ parcial e a pendência 1 explica a FK física ausente sem
inventar tabela — honestidade conferida e aprovada. A guarda em runtime **existe de
fato** no código: `NotificationCenterService.savePreference` (linhas 221–231) recusa
422 sem contato verificado (`ConsentGate.isVerifiedContact`) e sem consentimento
vigente (`ConsentGate.hasActiveConsent`), e a migration tem o CHECK
`customer_id IS NULL OR (contact_id IS NOT NULL AND consent_id IS NOT NULL)`.
Furos da guarda em §6.3 — não anulam o item, mas precisam de correção.

### 1.10 Leitura registrada — **OK**
`read_at` na tabela; `markRead` grava `SET read_at = COALESCE(read_at, ?)` — a
primeira leitura nunca é sobrescrita, exatamente como o relatório afirma; a guarda
`NotificationMigrationConsistencyTest` confere a coluna.

### 1.11 Avisos de revogação e de sessão assistida (ADR-0020) — **OK (regra) / NÃO VERIFICÁVEL (nomes)**
Entram pela mesma ingestão como `SECURITY`/`LEGAL_NOTICE` (teste
`assistedAccessWarningsUseSameGate`). Os nomes (`consent.revoked`,
`access.assisted-session.started`) são ilustrativos e o relatório o declara
(pendência 3) — os oficiais dependem do catálogo real da P1.38, não espelhado.

### 1.12 Página inicial (P3.39) e alertas do tenant (ED-07) — **OK (fora de escopo, não tocados)**

---

## 2. Migrations

### 2.1 V1_15 é da P3.36? — **OK (confirmado)**
Plano v3.0 §3, tabela tenant: "`V1_15` | platform | `notifications` (central de
avisos por usuário, leitura registrada) e `notification_preferences` (canal e
finalidade; aviso legal não desligável) | **P3.36** | reservada, P3". A migration
entregue implementa exatamente esse conteúdo, nessas duas tabelas.

### 2.2 V9_11 pertence a outra tarefa? — **OK com ressalva (não pertence; não é reservada)**
- O plano §3 **não reserva** V9_11 a ninguém — aliás, não lista V9_9/V9_10/V9_11:
  a sequência core do plano vai de `V9_8` (+ `R__norm_catalog`) direto a `V10`.
  Logo, a V9_11 **não é de outra tarefa** no plano: não há o problema grave temido.
- A numeração segue o precedente real de execução: parâmetros de catálogo foram
  gravados em subversões V9_x livres — `V9_9` (P1.36, `sac.*`) e `V9_10` (P1.18,
  `ui.*`/`theme.*`), ambas presentes no espelho da P2.5. A lista core do espelho é
  completa nessa faixa (V9, V9_1–V9_10, V10, V10_1, V10_1_1, V10_2, V10_2_1, V14,
  V14_1) e **não contém V9_11**: era a primeira livre, como a frente afirma.
- Ressalva 1: `CoreMigrationRunner` do HEAD usa `outOfOrder(true)` (comentário
  P0.17/P0.19 no próprio código), embora o texto do plano diga "core nunca usa
  outOfOrder" — é o código que vale, e com ele a V9_11 aplica mesmo já existindo
  V10/V14. Sem esse `outOfOrder`, a V9_11 seria ignorada: vale registrar o fato.
- Ressalva 2: o comentário da V9_11 diz "V9_7 consta vago no plano" — no plano sim,
  mas no HEAD a V9_7 está **ocupada** (`V9_7__service_order_parameters.sql`, P1.6).
  Comentário desatualizado, sem efeito (a frente não reaproveitou a V9_7).
- Ressalva 3: busca em `~/workspace/ziipsan-producao/` acha V9_11 só na P3.36; ainda
  assim, conferir contra o HEAD real na integração (o espelho é local e parcial).

### 2.3 Colisão de V1_15/V9_11 no HEAD (espelho da P2.5) — **NÃO VERIFICÁVEL por completo; indício forte de livres**
- V9_11: ausente da lista core do espelho da P2.5 (lista aparentemente integral) —
  indício forte de livre.
- V1_15: o espelho da P2.5 **não espelhou migration tenant nenhuma do módulo
  platform** (a pasta platform só tem `src/main/java`), portanto a ausência ali não
  prova nada. A favor de livre: a reserva §3 é exclusiva da P3.36 e nenhuma outra
  tarefa reivindica V1_15 nos JSONs do plano. Risco residual baixo, a fechar na
  integração contra o HEAD real — o relatório já prevê essa conferência (pendência 5).

---

## 3. Regras do projeto

### 3.1 JSON só via classe `Json` do core — **OK**
Único uso de JSON na entrega: `Json.readTree` em `NotificationIngestRule` (linha 37).
Nenhum `ObjectMapper`/`Gson`/parse manual. O teste `invalidJsonIsRefused` espera
`IllegalStateException`, que é exatamente o que `Json.readTree` lança (conferido na
fonte do core no espelho).

### 3.2 Nenhuma asserção afrouxada — **OK**
16 testes (7 + 6 + 3, contagens do relatório conferidas) com asserções exatas de
valor, tipo de exceção e conteúdo de SQL; nenhum teste desabilitado ou vazio.

### 3.3 Valores calculados à mão (conferidos um a um, fuso America/Sao_Paulo = UTC−3) — **OK**
Recalculei os três casos centrais e todos batem com o teste e com a regra:
- `2026-03-10T02:30:00Z` = 2026-03-09 23:30 em SP → dentro do silêncio 22:00–07:00;
  fim da janela = 2026-03-10 07:00 SP = `2026-03-10T10:00:00Z` ✔ (teste
  `silenceCrossingMidnight`, ramo "antes da meia-noite" de `silenceEndsAt`).
- Digest diário às 10:00 SP (`2026-03-10T13:00:00Z`) → retido até 2026-03-11 00:00
  SP = `2026-03-11T03:00:00Z` ✔.
- Obrigatória (`LEGAL_NOTICE`) com digest diário + silêncio → `NOW` ✔.
Lacuna de cobertura (menor): o ramo "depois da meia-noite" de `silenceEndsAt`
(ex.: 02:00 local → fim hoje às 07:00) e a janela que **não** cruza meia-noite não
têm teste; a lógica lida está correta nos dois ramos.

### 3.4 Piso de categorias: tenant só acrescenta — **OK**
`NotificationPolicy.effectiveMandatoryCategories`:
```java
EnumSet<NotificationCategory> effective = EnumSet.copyOf(PLATFORM_FLOOR);
effective.addAll(tenantDeclared);
```
União pura; `tenantDeclared == null` é erro explícito (`IllegalArgumentException`),
não conjunto vazio presumido. A própria V9_11 declara honestamente que a trava
"nunca abaixo do piso" é semântica e vive na política, não no limite do console.

---

## 4. Risco nº 1 — pom do platform × Spring Web/Security — **NÃO VERIFICÁVEL; risco CONFIRMADO como aberto**
O `pom.xml` de `ziipsan-platform` **não está** em nenhum dos dois espelhos (o da
P2.5 tem pom de billing, metering, core, security e o pom raiz — platform e app,
não). Logo não dá para confirmar se platform declara spring-web/security, e o risco
declarado pela frente (pendência 4) permanece exatamente como ela descreveu.
Indício atenuante, não prova: o javadoc de `TenantParameterService` (platform)
menciona controller do console com `@PreAuthorize` no mesmo módulo, sugerindo que
platform já hospeda controllers Spring. Recomendação da frente (ajustar o pom ou
mover `NotificationController` para o módulo `app` — que existe no pom raiz como
`platform/ziipsan-app`) está correta e deve ser o **primeiro** item da 1ª rodada de
CI no PC do dono. Agravante menor não declarado: nada na entrega instancia
`NotificationCenterService` como bean (sem `@Service`/configuração) — é o mesmo
padrão de `TenantParameterService` (classe pura, fiação externa), mas a fiação do
controller + serviço + portas (`BusinessEventCatalog`, `ConsentGate`,
`NotificationParameters`) terá de nascer em algum lugar na integração.

---

## 5. Consistência migration × código

### 5.1 RLS via `register_tenant_table` — **OK**
A V1_15 termina com `register_tenant_table('notifications')` e
`register_tenant_table('notification_preferences')`, padrão das migrations tenant
(ex.: V8_1), e o teste de consistência trava as duas chamadas.

### 5.2 Índice único com COALESCE × upsert — **OK (correspondência exata)**
Índice (V1_15):
```sql
ON notification_preferences (tenant_id,
    COALESCE(user_id, '00000000-0000-0000-0000-000000000000'::uuid),
    COALESCE(customer_id, '00000000-0000-0000-0000-000000000000'::uuid),
    purpose_code, channel)
```
`ON CONFLICT` de `NotificationCenterService.savePreference`: a mesma lista, na
mesma ordem, com os mesmos casts — alvo exato do índice. Migration e serviço têm de
viajar no mesmo commit, como o relatório avisa. ✔

### 5.3 FKs condicionais — **PROBLEMA (ver §6.6)**
A intenção segue a V8_1, mas a guarda usada é a versão antiga, e há uma armadilha
de ordem não declarada para `customers`.

---

## 6. Problemas encontrados (com arquivo e trecho)

### 6.1 [médio] Preferências, digest e silêncio não são aplicados na ingestão
`NotificationCenterService.ingest` nunca lê `notification_preferences`, nunca chama
`NotificationPolicy.decide` e nunca chama `parameters.digestDefault(...)` — grava
sempre a linha `IN_APP` com `deliver_after = now`. Consequências: (a) desligar o
canal IN_APP de categoria não obrigatória não tem efeito na central (a preferência
grava, mas nada a honra); (b) `notify.digest_default` do tenant nunca é resolvido.
A justificativa "entrega externa é da P3.5" cobre e-mail/push, mas o canal
**aplicação** é da própria P3.36. O javadoc do serviço afirma que a decisão de ritmo
fica "gravada em `deliver_after` das linhas de entrega" — para a linha IN_APP isso
não acontece por decisão via política, e sim por constante. Correção esperada:
`ingest` consultar a preferência do destinatário (filtro `enabled` do IN_APP) ou,
no mínimo, o relatório rebaixar digest/silêncio de ✅ para "regra pura pronta,
fiação pendente".

### 6.
...[truncated 3079 chars]
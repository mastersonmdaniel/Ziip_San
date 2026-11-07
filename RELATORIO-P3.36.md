# P3.36 — Central de notificações do usuário e preferências — relatório de entrega

Base: HEAD `2ba51f8` de `mastersonmdaniel/Ziip_San`. Pacote LOCAL conferido, **sem push**
(conforme a tarefa). Nenhuma asserção afrouxada; nenhum dado inventado (ausência = erro explícito).

## Bloqueio honesto do espelho (ler primeiro)
A 1ª chamada de espelho fresco via GitHub MCP (`get_file_contents` de
`platform/ziipsan-platform`) devolveu **HTTP 429 (rate limit do provedor)** — parada dura:
nenhuma outra chamada ao GitHub foi feita nesta tentativa. A implementação foi construída
sobre o **espelho local da P2.5** (mesmo HEAD 2ba51f8), copiado para
`../espelho/` com proveniência registrada em `../espelho/PROVENIENCIA.md`:
`TenantParameterService` (padrão JDBC/transação/GUC do módulo platform), outbox core V8,
`Json` e `ApiException` do core, padrão de parâmetros da V9_10 (P1.18) e o mapa Flyway do
plano v3.0 §3. **Não** foram espelhados: catálogo de eventos da P1.38, `UserPreferencesService`
da P1.18 e tabelas de consentimento/contato da P1.3 — a entrega integra com eles por
**portas** (interfaces), sem inventar assinaturas deles.

## Migration (reserva conferida no plano §3)
Existe reserva exata: **tenant `V1_15` (platform)** — "`notifications` (central de avisos por
usuário, leitura registrada) e `notification_preferences` (canal e finalidade; aviso legal
não desligável)". Entregue exatamente ela:
`platform/ziipsan-platform/src/main/resources/db/migration/tenant/V1_15__notifications_and_preferences.sql`.
Mais a definição dos parâmetros no catálogo global, no padrão da V9_10:
`shared/ziipsan-core/src/main/resources/db/migration/core/V9_11__p3_36_notify_parameters.sql`
(`notify.digest_default` classe d; `notify.mandatory_categories` classe c/piso).
Nota de reconciliação: o aceite fala em registro "em `user_preferences` da P1.18"; o mapa §3
(autoridade de migrations) reserva para a P3.36 a tabela própria `notification_preferences`
na V1_15 — seguiu-se o mapa, e o registro continua único e dono P3.36 (COE-19).

## Arquivos criados (22 de código/SQL/teste; 19 na entrega original + 3 das correções pós-revisão)
- Domínio/portas (`platform/notification/`): `NotificationChannel`, `NotificationCategory`,
  `DigestMode`, `CatalogEvent`, `BusinessEventCatalog` (porta P1.38), `ConsentGate`
  (porta P1.3), `NotificationParameters` (porta notify.*), `OutboxMessage` (P1.9).
- Regras puras: `NotificationPolicy` (piso obrigatório, silêncio, resumo — casos calculados
  à mão) e `NotificationIngestRule` (catálogo + carga via `Json` do core).
- Aplicação/API: `NotificationCenterService` (JDBC, transação própria com
  `app.current_tenant`, padrão do módulo), `NotificationPreference`,
  `web/NotificationController` (`/api/v1/me/notifications`, `unread-count`, `read`,
  `notification-preferences` GET/PUT).
- Migrations: V1_15 (tenant) e V9_11 (core), acima; mais a repetível
  `R__notifications_customer_fk.sql` (tenant, correções pós-revisão).
- Testes: `NotificationPolicyTest` (7 casos), `NotificationIngestRuleTest` (7 casos),
  `NotificationMigrationConsistencyTest` (7 guardas sem banco, padrão do projeto) e, das
  correções pós-revisão, `NotificationIngestWiringTest` (6 casos, serviço real sobre JDBC
  falso) e `NotificationConsentGateTest` (5 casos). Total: 32 testes.

## Cobertura do acceptance, item a item
| Item | Estado | Como |
|---|---|---|
| Central única (sino, lista, lido/não lido) | ✅ | `notifications` + `listForUser`/`unreadCount`/`markRead` + controller |
| Alimentada pela outbox da P1.9 | ✅ (porta) | `ingest(OutboxMessage)` idempotente (dedup por `source_message_id`); o laço do worker que lê a outbox é da P1.9 — ver pendência 2 |
| Catálogo da P1.38; evento fora do catálogo recusado | ✅ | `NotificationIngestRule` + teste (nome e versão não declarada, 409) |
| Registro único de preferências por canal e finalidade, lido por P3.5/ED-41 | ✅ | `notification_preferences` (V1_15); leitores usam a mesma tabela |
| Resumo diário ou imediato (`notify.digest_default`, classe d) | ✅ | parâmetro na V9_11 + `NotificationPolicy.decide` + **fiação na ingestão** (`ingest` resolve o padrão do tenant e a preferência vence) + testes da regra e da fiação |
| Silêncio por período | ✅ | par início/fim na preferência + regra com janela cruzando meia-noite + **fiação na ingestão** (defere o `deliver_after`) + testes da regra e da fiação |
| Categorias obrigatórias não desligáveis (`notify.mandatory_categories`, classe c, piso) | ✅ | piso ∪ tenant em `effectiveMandatoryCategories`; `assertCanDisable` 422 |
| **Teste de que aviso legal não é desligável** | ✅ | `NotificationPolicyTest.legalNoticeCannotBeDisabled` (+ segurança/aprovação) |
| Preferência do consumidor ligada a contato verificado + consentimento com FK da P1.3 (CAD-84/DAD-30) | ⚠️ parcial | guarda em runtime via `ConsentGate` (recusa 422 sem contato verificado/consentimento) + CHECK no banco (consumidor exige contact_id e consent_id); a **FK física** para a tabela de consentimentos da P1.3 não foi criada — ver pendência 1 |
| Leitura registrada | ✅ | `read_at` gravado uma vez (COALESCE — primeira leitura nunca sobrescrita) |
| Avisos de revogação e de sessão de acesso assistido (ADR-0020) | ✅ (regra) | entram pela mesma ingestão como SECURITY/LEGAL_NOTICE (teste); os NOMES dos eventos dependem do catálogo real da P1.38 — pendência 3 |
| Página inicial (P3.39) e alertas do tenant (ED-07) | fora de escopo | não tocados, como manda o aceite |

## Pendências e incertezas honestas
1. **FK física do consentimento**: o nome da tabela de consentimentos da P1.3 não pôde ser
   confirmado (espelho bloqueado por 429; no material local só consta o catálogo
   `consent_purposes` da V3_12, que ordena DEPOIS da V1_15). A FK `consent_id`/`contact_id`
   precisa de uma repetível `R__` do módulo dono ou de confirmação do nome da tabela —
   **não inventei a tabela**. (As FKs para `users`/`customers` foram resolvidas nas
   correções pós-revisão — guarda P1-C via `pg_constraint` + `R__notifications_customer_fk.sql`
   para `customers` em tenant novo; ver a seção própria abaixo.)
2. **Laço outbox→central**: falta o consumidor no worker (P1.9) que chama `ingest` e marca a
   mensagem; e as linhas EMAIL/PUSH de `notifications` + o envio são da P3.5 (aqui só nasce IN_APP).
3. **Nomes de eventos ADR-0020** usados no teste (`access.assisted-session.started`,
   `consent.revoked`) são ilustrativos do contrato da porta; os nomes oficiais são os do
   catálogo da P1.38/`vertical.json`, a conferir na integração.
4. **Pom do platform + fiação de beans (PRIMEIRO item da 1ª CI no PC do dono)**: o espelho
   local não tinha o `pom.xml` de `ziipsan-platform`; o controller usa Spring Web/Security
   (`@RestController`, `@PreAuthorize`, `@AuthenticationPrincipal(expression="subject")`).
   Se o módulo não declarar essas dependências, a CI acusa e o ajuste é no pom ou mover o
   controller para o módulo `app` (onde vivem os controllers do console) — risco nº 1 de CI.
   Junto vai a fiação: nada neste pacote instancia `NotificationCenterService` como bean
   nem liga as portas (`BusinessEventCatalog`, `ConsentGate`, `NotificationParameters`)
   às implementações reais — essa cola nasce na integração e é conferida na mesma rodada.
5. **V9_11 do core**: assumi a primeira versão livre após a V9_10 na sequência V9_x
   (V9_7 consta "vago, não reaproveitado" no plano). Conferir contra o HEAD real na integração.
6. **Resumo diário**: `deliver_after` = início do dia seguinte no fuso do tenant; o horário
   de montagem/envio do resumo é do worker/P3.5 (o aceite não fixa hora — não inventei uma).
7. Sem JDK/Maven nesta VM: **nada foi compilado nem testado por execução**; a conferência foi
   por leitura contra os padrões espelhados. A 1ª rodada de CI no PC do dono é a prova real.

## Correções pós-revisão (frente de correção, sobre a REVISAO-P3.36)
A revisão cruzada deu LIBERA COM CORREÇÕES; as 5 obrigatórias foram aplicadas, sem push,
sem nenhuma asserção afrouxada (testes só foram ADICIONADOS ou ajustados à assinatura
corrigida das portas — nenhuma expectativa existente foi enfraquecida):

1. **[médio] Preferências fiadas na ingestão** — `NotificationCenterService.ingest` agora:
   lê as preferências IN_APP do destinatário para a categoria do aviso
   (`loadInAppPreferences`), resolve `parameters.digestDefault(tenantId)` e
   `tenantMandatoryCategories(tenantId)` e monta o plano pela função pura
   `planIngest` (a mesma que os testes exercitam): IN_APP desligado (categoria não
   obrigatória) SUPRIME a linha; resumo diário (preferência vence o padrão do tenant) e
   silêncio vigente RETÊM gravando o `deliver_after` calculado por `NotificationPolicy`;
   categoria obrigatória ignora tudo e entrega já (comportamento anterior preservado).
   Como a carga da outbox não declara finalidade (convenção da porta), preferências de
   várias finalidades para a mesma categoria combinam pela regra mais restritiva —
   declarado no javadoc de `planIngest`. Efeito colateral coerente: `listForUser` e
   `unreadCount` só mostram avisos com `deliver_after` vencido (retido ainda não é
   entregue). `ingest` passou a devolver `Optional<UUID>` (vazio = suprimido).
   Testes novos: `NotificationIngestWiringTest` (6) sobre o serviço real com JDBC falso —
   supressão, obrigatória, silêncio (07:00 local = 10:00Z), digest do tenant
   (dia seguinte 00:00 local = 03:00Z) e digest da preferência vencendo o do tenant.
   Com isso, os itens "Resumo diário" e "Silêncio por período" da tabela acima voltam a
   ✅ com fiação feita e testada (a revisão os tinha como regra pura sem chamador).
2. **[médio] Idempotência da ingestão** — o id da mensagem não é mais descartado:
   `NotificationIngestRule` carrega `sourceMessageId` no rascunho (teste novo na regra);
   a V1_15 ganhou a coluna `source_message_id` em `notifications` e o índice único
   parcial `uq_notifications_source_message (tenant_id, source_message_id) WHERE
   source_message_id IS NOT NULL`; `ingest` consulta a chave antes de gravar e insere com
   `ON CONFLICT ... DO NOTHING` + releitura (corrida entre reentregas simultâneas).
   Teste novo de reentrega: mesma mensagem 2× → 1 aviso, mesmo id devolvido
   (`NotificationIngestWiringTest.redeliveryDoesNotDuplicate`) + guarda da coluna/índice
   no `NotificationMigrationConsistencyTest`.
3. **[médio] FKs para `customers` em tenant novo** — conferido o precedente no espelho:
   `customers` é da V3_1, POSTERIOR à V1_15 na história única do tenant, logo o bloco
   condicional pulava em toda instalação fresca. Duas mudanças: (a) a guarda dos blocos
   condicionais da V1_15 trocou `to_regclass` puro pela verificação de UNIQUE
   `(tenant_id, id)` via `pg_constraint`, idêntica à correção P1-C da V8_1 (vale também
   para `users`); (b) criada a repetível `R__notifications_customer_fk.sql` (análoga a
   `R__service_protocol_order_fk.sql`), que roda depois das versionadas e cria
   `fk_notifications_customer` e `fk_notification_prefs_customer` com a mesma guarda e
   `duplicate_object` como no-op para tenants em upgrade. Guardas novas no teste de
   consistência (guarda P1-C na V1_15 + conteúdo da repetível). Bônus coerente: a V1_15
   ganhou o CHECK de sujeito exato (`user_id IS NULL OR customer_id IS NULL`), espelho
   do item 4 no banco.
4. **[menor] Guarda de consentimento** — `ConsentGate.hasActiveConsent` agora RECEBE o
   `consentId` e valida ESTE consentimento (assinatura e javadoc atualizados; o serviço
   passa o id gravado na preferência). `NotificationPreference.subjectKind()` não presume
   mais: os dois ids juntos, ou nenhum, é `IllegalStateException` explícita; e
   `savePreference` recusa a ambiguidade ANTES de tudo com 422 (`ambiguous-subject`),
   sem chamar o gate. Testes novos: `NotificationConsentGateTest` (5) — subjectKind nos 4
   casos, ambiguidade recusada com gate intacto (0 chamadas), gate recebendo o consentId
   alheio e recusando, consentId exato vigente passando, e USER sem passar pelo gate.
   **Declaração (caminho CONSUMER via HTTP):** o controller atual só grava preferência
   USER (dono = `sub` do token); preferência de consumidor via HTTP depende de endpoint
   PRÓPRIO do portal do consumidor (autenticação e vínculo da P1.3) — não foi inventado
   contrato aqui; até lá, consumidor só entra pelo serviço, com a guarda aplicada.
   Declarado também no javadoc do `NotificationController`.
5. **[menor] Convenções de carga** — declarado explicitamente no código
   (`OutboxMessage`, `NotificationIngestRule`) e aqui: `eventVersion`, `titleCode` e
   `bodyCode` (e os ids de destinatário) DENTRO do payload são convenção DESTA porta da
   P3.36 até o catálogo da P1.38 ser espelhado; com o catálogo real, o esquema dele
   manda e a convenção é revisada. E o `recipientPermission` do `CatalogEvent` —
   carregado e nunca usado — foi REMOVIDO da porta e dos testes (não aplicado: esta
   central não tem modelo de permissão de destinatário definido; inventar a checagem
   seria inventar contrato; volta à porta junto com o catálogo real da P1.38, se ele o
   trouxer).

**Não é correção (pendência declarada mantida e reforçada):** o pom do platform
(spring-web/security) e a fiação de beans/portas seguem NÃO conferidos — são o primeiro
item da 1ª CI no PC do dono (pendência 4). Sem JDK/Maven nesta VM, os 32 testes foram
conferidos por leitura contra os padrões espelhados, não executados — a CI é a prova.

## Riscos para a CI
- Dependências web/security no pom do platform (pendência 4) e conflito de versão Flyway
  V9_11/V1_15 se outra frente tiver usado os números entretanto.
- `SourceHygieneTest`/checagens de marca do módulo platform sobre os arquivos novos.
- O `ON CONFLICT` do upsert de preferências depende do índice único com COALESCE criado
  na V1_15 — migration e serviço viajam juntos; não separar os commits.

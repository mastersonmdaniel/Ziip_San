# P2.28 — Minhas tarefas: caixa unificada de pendências e aprovações — relatório de entrega

Base: HEAD `0fbcd22` de `mastersonmdaniel/Ziip_San`. Pacote LOCAL conferido, **sem push**
(conforme a tarefa). Nenhuma asserção afrouxada; nenhum dado inventado (ausência = erro
explícito); JSON só pela classe `Json` do core; segregação solicitante != aprovador sem bypass.

## Bloqueio honesto do espelho (ler primeiro)
O espelho fresco via GitHub MCP começou bem (raiz do `ziipsan-platform`, pom,
`TenantParameterService`, pacotes do módulo — HEAD `0fbcd22`), mas a listagem das
migrations de tenant devolveu **HTTP 429 (rate limit do provedor)** — parada dura:
nenhuma outra chamada ao GitHub foi feita. A implementação foi construída sobre os
**pacotes locais de mesmo HEAD** (`p3.36`, `p2.5`, `p2.20`, `p3.1`, `p3.14`, `p2.11`),
com proveniência em `../espelho/PROVENIENCIA.md`. **Não** foram espelhados: os esquemas
de carga reais de cada evento no `vertical.json` da P1.38, a implementação real do
catálogo de permissões da P0.13 e a hierarquia de chefias do tenant — a entrega integra
com os três por **portas** (interfaces), sem inventar assinaturas nem dados deles.

## Migration (reserva conferida no plano §3)
Existe reserva exata: **tenant `V1_11` (platform)** — "`work_items` (...) e
`work_item_delegations` (início, fim, registro)". Entregue exatamente ela:
`platform/ziipsan-platform/src/main/resources/db/migration/tenant/V1_11__work_items_and_delegations.sql`,
com as duas tabelas da reserva **mais** `approval_tasks` (a `approval_task` nomeada no
aceite) e `work_item_events` (trilha só de inserção), declaradas no cabeçalho.
Mais os parâmetros `wf.*` no catálogo global, no padrão da V9_10/V9_11:
`shared/ziipsan-core/src/main/resources/db/migration/core/V9_13__p2_28_workflow_parameters.sql`
(**V9_13 = primeira livre após a V9_12 da P2.11 nos pacotes locais; confirmar contra o
HEAD na integração**, como a P3.36 fez com a V9_11).
Detalhe de ordem conferido à mão: as UNIQUEs `(tenant_id, id)` de `work_items` e
`approval_tasks` nascem ANTES das FKs que as referenciam; as FKs de usuário usam a
guarda da integração P1-C (UNIQUE via `pg_constraint`, como na V1_15).

## Arquivos criados (33)
- Contrato/domínio (`platform/work/`): `WorkItemType` (14 tipos do aceite), `WorkItemStatus`,
  `WorkItem` (contrato completo, com invariantes), `ApprovalStatus`, `ApprovalTask`
  (invariante: decidida pelo solicitante nem se constrói), `WorkDelegation` (fim exclusivo),
  `DueFilter` (prazo na data local do tenant).
- Regras puras: `ApprovalRule` (segregação + elegibilidade + canal), `DelegationRule`
  (janela, teto, subconjunto, permissão efetiva), `EscalationRule`, `WorkItemVisibility`.
- Portas: `WorkEventCatalog`/`WorkCatalogEvent` (P1.38), `WorkParameters` (wf.*),
  `UserPermissions` (P0.13), `EscalationTargetResolver` (hierarquia do tenant).
- Ingestão por eventos: `WorkOutboxMessage` (P1.9) + `WorkItemIngestRule` (catálogo,
  efeito, carga via `Json`; convenção de carga DESTA porta, declarada no javadoc).
- Aplicação/API: `WorkInboxService` (JDBC, transação própria com `app.current_tenant`),
  `TenantWorkParameters` (leitura no padrão do console P1.6 + trava da classe a),
  `web/WorkInboxController` (`/api/v1/me/work-items`, `count`, `start`, `decision`,
  `work-delegations`), `HeldBillWorkItems` (ponte da fila P2.20).
- Migrations: V1_11 (tenant) e V9_13 (core), acima.
- Frontend: `services/work/workItems.ts` + `pages/tarefas/MinhasTarefas.tsx` (página do
  item de menu, com filtro por prazo e decisão com motivo).
- Testes (7 classes, 33 casos): `ApprovalRuleTest` (8), `DelegationRuleTest` (6),
  `EscalationRuleTest` (3), `DueFilterAndVisibilityTest` (3), `WorkItemIngestRuleTest` (5),
  `WorkMigrationConsistencyTest` (5), `WorkInboxServiceTest` (3, serviço real sobre JDBC falso).

## Cobertura do acceptance, item a item
| Item | Estado | Como |
|---|---|---|
| Item de menu "Minhas tarefas", caixa por usuário | ✅ | página `MinhasTarefas.tsx` + API `/api/v1/me/work-items` (dono = `sub` do token); contador para o menu em `/count`. O registro do item no arquivo de menu/rotas do frontend é passo de integração (ver pendência 4) |
| Reúne os 14 tipos de trabalho nomeados | ✅ | `WorkItemType` com os 14 (OS, retificação, parâmetro JOR-25, documento, protocolo a vencer, OS atrasada, conta retida JOR-09, rejeição fiscal, pacote GIS, ação corretiva, calibração, pedido de titular, obrigação regulatória, divergência de conciliação) + CHECK da V1_11 idêntico (guarda em teste) |
| Filtro por prazo | ✅ | `DueFilter` ALL/OVERDUE/DUE_TODAY/DUE_IN_7_DAYS na data LOCAL do tenant + testes com fronteira de fuso calculada à mão |
| Contrato de item de trabalho (tipo, objeto, módulo de origem, solicitante, responsável ou aprovadores elegíveis, prazo, estado, endereço do registro) | ✅ | record `WorkItem` + colunas da V1_11; item sem nenhum endereçamento nem se constrói nem grava (CHECK no banco) |
| Criação e encerramento por eventos do catálogo da P1.38 (JOR-20) | ✅ (porta) | `WorkItemIngestRule` + `ingestOpen`/`ingestClose`: fora do catálogo/versão/efeito = 409; abertura idempotente por `source_message_id`; item com aprovação pendente não encerra por evento (409 — encerra pela decisão) |
| approval_task comum com segregação pelo componente: quem solicita não aprova (wf.self_approval, classe a, sempre falso), com teste | ✅ | TRÊS camadas sem bypass: invariante do record `ApprovalTask`, `ApprovalRule` (trata `true` como catálogo adulterado) e CHECK `decided_by <> requester_id` na V1_11; V9_13 trava a definição (classe a + LOCKED + ALLOWED_VALUES [false]); testes nomeados em `ApprovalRuleTest` e no serviço |
| Delegação temporária com início, fim e registro, limitada por wf.delegation_max_days (classe d) | ✅ | `work_item_delegations` (a linha é o registro/auditoria, com revogação gravada) + `DelegationRule.assertCanCreate` (duração == teto passa; teto+1s recusa) + serviço com sobreposição recusada |
| Delegação nunca maior que a permissão do delegante, auditada | ✅ | concessão exige concedidas ⊆ delegante (422 nomeando excedentes); permissão EFETIVA = próprias ∪ (concedidas ∩ delegante ATUAL) — delegante perdeu, delegado perde junto; conjunto gravado na linha |
| Escalonamento por prazo conforme wf.escalation_hours (classe d) | ✅ | `EscalationRule` (escala em vencimento + horas; nunca antes, nunca encerrado/sem prazo/já escalonado) + rodada `escalateOverdue` com trilha; sem alvo resolvido, NÃO escala e devolve o item em `skippedWithoutTargetIds` |
| Retificação da P2.20 usa a caixa sem mudar a regra; demais aprovações usam o componente quando nascem | ✅ (ponte) | `HeldBillWorkItems`: rascunho HELD_BILL apontando para a fila (a decisão continua na P2.20) e rascunho de aprovação de retificação nascendo com approval_task pela porta comum; aprovações novas de qualquer módulo nascem pelo mesmo `openDirect`/evento com `approval=true` |
| Escopo por área é da P3.30 (depende desta) | fronteira declarada | NÃO implementado aqui, por desenho: declarado no javadoc de `WorkItemVisibility` e do serviço — a P3.30 filtra depois desta visibilidade, sem mudar o contrato |
| **Teste: delegação expira na data de fim** | ✅ | `DelegationRuleTest.delegationExpiresAtEndDate` (fim exclusivo: 23:59:59 da véspera vigente, no instante do fim morta) |
| **Teste: delegado não recebe permissão além da do delegante** | ✅ | `DelegationRuleTest.delegateCannotReceiveBeyondDelegator` + `effectivePermissionFollowsCurrentDelegator` |
| **Teste: item vencido escala** | ✅ | `EscalationRuleTest.overdueItemEscalates` (venc. 2026-03-10T15:00Z + 24h → escala de 2026-03-11T15:00Z; 14:59:59 ainda não) |
| **Teste: quem solicita não aprova** | ✅ | `ApprovalRuleTest.requesterCannotApproveOwnRequest` (+ variante com delegação não lavando segregação, + record, + serviço com 0 updates) |

## Pendências e incertezas honestas
1. **Convenção de carga dos eventos**: os campos exigidos dentro do payload
   (`eventVersion`, `workItemType`, ids, `dueAt` ISO, `approval`...) são convenção
   DESTA porta, declarada nos javadocs de `WorkOutboxMessage`/`WorkItemIngestRule` —
   os esquemas reais são declarados por cada módulo no seu `vertical.json` (P1.38) e
   não foram espelhados (429). Na integração, o esquema real manda e a convenção é
   revisada contra ele. Os NOMES de evento usados em testes/ponte
   (`billing.rectification.approval-requested` etc.) são ilustrativos do contrato.
2. **Sem padrão de fábrica para wf.delegation_max_days e wf.escalation_hours**: o plano
   não fixa os números e nenhum foi inventado — a V9_13 os define classe d com padrão
   NULL; sem valor aceito do tenant, delegar/escalar é `MissingParameterException`
   explícito. O dono/tenant precisa definir os dois pela tela de parâmetros antes de
   usar delegação e escalonamento (decisão registrada, não chute).
3. **Portas sem implementação nesta entrega**: `UserPermissions` (P0.13),
   `WorkEventCatalog` (P1.38), `EscalationTargetResolver` (hierarquia) e o laço do
   worker da outbox (P1.9) que chama `ingestOpen`/`ingestClose`, a rodada agendada de
   `escalateOverdue` e a fiação de beans do `WorkInboxService`/`TenantWorkParameters`
   nascem na integração — nada aqui instancia o serviço como bean.
4. **Frontend**: a página e o serviço TS seguem os padrões das entregas P2.22/ED-08
   (componentes `PageHeader/Card/Table/Badge/Button/Field/Input/Alert/Spinner`,
   `@services/api`, react-query), mas o REGISTRO do item "Minhas tarefas" no menu/rotas
   do app (arquivo não espelhado) e a conferência fina de props são da integração —
   risco nº 2 de CI (frontend). Declarado também no cabeçalho da página.
5. **V9_13 do core**: assumida primeira livre após a V9_12 (P2.11) encontrada nos
   pacotes locais; conferir contra o HEAD real na integração. O teste de consistência
   lê a V9_13 do classpath do core (as migrations de core viajam no jar do core, como a
   V9_10) — confirmar na 1ª CI.
6. **Volume da caixa**: `listInbox` varre no máximo 500 itens abertos do tenant e
   filtra em memória (visibilidade depende de delegações e permissões, que não são
   colunas simples). Se um tenant passar dessa ordem, a paginação entra no serviço sem
   mudar o contrato.
7. **Escalonamento é de nível único** nesta entrega (um escalonamento registrado por
   item): o aceite pede "escalonamento por prazo", sem segundo nível — não inventei
   cadeia de escalonamento.
8. Sem JDK/Maven nesta VM: **nada foi compilado nem testado por execução**; a
   conferência foi por leitura contra os padrões espelhados (assinaturas do core
   conferidas: `ApiException.Validation.of/Conflict/NotFound`, `Json`, `MissingParameterException`,
   padrão transacional do `TenantParameterService`). A 1ª rodada de CI no PC do dono é
   a prova real — risco nº 1: o `pom.xml` do platform já declara webmvc/security/test
   (conferido no espelho da P3.14), então o controller segue o mesmo risco já aceito
   na P3.36.

## Correções pós-revisão (revisão independente REPROVOU; esta seção corrige item a item)

A revisão independente reprovou o pacote com 2 itens ALTOS, 2 MÉDIOS e 4 MENORES.
Todos foram corrigidos abaixo. Duas afirmações do corpo original deste relatório
estavam **um degrau acima do código** e ficam corrigidas aqui, com todas as letras:

- a linha da tabela de cobertura *"permissão EFETIVA = próprias ∪ (concedidas ∩
  delegante ATUAL) — delegante perdeu, delegado perde junto"* descrevia a regra
  pura `DelegationRule.effectivePermissions`, que existia e tinha teste, mas era
  **código morto no caminho real**: `decideApproval` e a visibilidade usavam só as
  permissões próprias do ator e a foto `grantedPermissions`. Antes desta correção,
  aquela frase NÃO era verdade no serviço (correção A2);
- a linha *"Delegação nunca maior que a permissão do delegante, auditada ✅"* valia
  só na CRIAÇÃO (subconjunto conferido pela `DelegationRule.assertCanCreate`); no
  USO, o teto não era aplicado — nem a perda posterior de permissão do delegante,
  nem o escopo vazio para itens nomeados (correções A2/M1).

Contagem de testes corrigida: **8 classes, 60 casos** (o cabeçalho original dizia
7 classes/33 casos — era a contagem da primeira entrega). E uma diferença factual
em relação à pendência 8 original: **nesta rodada de correção os testes FORAM
executados** — foi instalado um JDK 21 nesta VM e o pacote `work` (fontes
principais + testes, sem o controller Spring, que não mudou) foi compilado contra
as classes reais do core dos espelhos locais (`Json`, `ApiException`,
`MissingParameterException`) com Jackson 3.0.0/annotations 2.20 e rodado no
JUnit: **60/60 verdes**. Isso NÃO substitui a CI total do projeto (Maven com o
pom real, Flyway, frontend) no PC do dono — ela continua sendo a prova de
integração; o que muda é que sintaxe, assinaturas e a lógica dos 60 casos estão
provados por execução, não só por leitura.

### A1 (ALTA) — Lavagem da segregação por delegação do solicitante — CORRIGIDA

O furo: no canal `DELEGATED`, `ApprovalRule.assertCanDecide` bloqueava
`actorId == requesterId`, mas não `delegatorId == requesterId`. Se o solicitante
constava como endereçado do próprio item (ex.: na lista de elegíveis), ele delegava
a um terceiro — mesmo com `grantedPermissions` vazio — e o terceiro decidia o
pedido dele, passando pelo record (`decidedBy` = terceiro ≠ solicitante) e pelo
CHECK `decided_by <> requester_id` do banco.

Correção: no canal delegado, delegação cujo **delegante é o solicitante da tarefa
ou do item é descartada como canal**; se era o único caminho do ator, a recusa sai
com o código `self-approval` (422, campo `decidedBy`), não um `not-eligible`
genérico. A guarda está na `ApprovalRule` e vale no serviço porque
`decideApproval` chama a regra **antes de qualquer UPDATE** (recusa = zero
gravação, provado em teste). Testes novos reproduzindo o cenário do revisor:
`ApprovalRuleTest.requesterDelegationDoesNotWashSegregation` (com escopo concedido
e vigente — para isolar A1 de M1/A2 —, com escopo vazio na forma literal do
cenário, e o controle positivo de que a mesma delegação vinda do aprovador
elegível habilita) e `WorkInboxServiceTest.requesterDelegationDoesNotDecideAtService`
(serviço real sobre JDBC falso, 0 updates).

### A2 (ALTA) — `effectivePermissions` era código morto — CORRIGIDA (fiada no caminho real)

Correção: novo método `DelegationRule.effectiveGrantedPermissions(delegação,
permissões atuais do delegante)` = `concedidas ∩ atuais` (delegante desconhecido
= conjunto vazio; a foto limita nos dois sentidos), usado pela `ApprovalRule` e
pela `WorkItemVisibility`. As assinaturas de `assertCanDecide` e `isVisibleTo`
ganharam o parâmetro `currentPermissionsByDelegator` (mapa delegante → permissões
atuais); a assinatura antiga foi **removida** (não restou caminho que decida pela
foto). O serviço resolve o mapa a cada decisão/leitura pela porta
`UserPermissions` que já existia (`WorkInboxService.currentPermissionsByDelegator`,
uma consulta por delegante distinto), e o injeta em `decideApproval`, `listInbox`,
`countInbox` e `startItem`. Na visibilidade, a interseção governa o caminho por
PERMISSÃO; endereçamento por nome não é permissão e continua visível ao delegado
do nomeado (a DECISÃO por ele é mais estrita — ver M1). Testes: regra —
`ApprovalRuleTest.delegatedDecisionFollowsDelegatorCurrentPermissions` (perde →
não decide; sem conjunto conhecido → não decide) e
`...DoesNotGainPermissionsDelegatorGotLater` (ganha depois → não ganha);
visibilidade — `DueFilterAndVisibilityTest.visibilityByDelegationFollowsDelegatorCurrentPermissions`
(perde → some da caixa; foto limita); serviço —
`WorkInboxServiceTest.delegatedDecisionAndVisibilityFollowDelegatorCurrentPermissions`
(decide e vê com a permissão vigente; após a perda, não decide — 0 updates — e a
lista volta vazia) e `...DoesNotGainLaterPermissionsAtService`.

### M1 (MÉDIA) — Escopo concedido ignorado para itens nomeados — CORRIGIDA (decisão do coordenador)

Semântica nova, aplicada na `ApprovalRule`: `grantedPermissions` é o teto de TODA
decisão delegada — o canal `DELEGATED` exige escopo EFETIVO (A2) **não vazio** e,
quando o item declara `requiredPermission`, exige que ela esteja no conjunto
efetivo (mesmo em item também endereçado por nome). Delegação com escopo vazio
não decide aprovação nenhuma. O teste `strangerNeedsDelegation` foi AJUSTADO
para a nova semântica — **justificativa: ele codificava exatamente o comportamento
reprovado** (delegação `Set.of()` decidindo item nomeado); a asserção positiva
passou a usar delegação com escopo efetivo (`so:approve` concedida e vigente no
delegante) e a forma de escopo vazio virou asserção NEGATIVA dentro do mesmo
teste, com a justificativa escrita no próprio código. Teste novo dedicado:
`ApprovalRuleTest.delegatedDecisionRequiresDeclaredPermissionInEffectiveScope`.
Nenhuma outra asserção existente foi alterada de sentido; as demais chamadas nos
testes só ganharam o novo parâmetro da assinatura (mapa), sem mudar o esperado.

### M2 (MÉDIA) — Métodos sem teste de serviço + corrida no `ingestOpen` — CORRIGIDA

Corrida: `ingestOpen` agora trata a perda da corrida — se a gravação viola o
índice único `uq_work_items_source_message` (SQLState 23505 nomeando a
constraint, detectado na cadeia de causas), a transação perdedora é desfeita e o
item existente é **relido numa transação nova** (no Postgres a transação que
violou unicidade está abortada e não aceita nova consulta na mesma transação) e
devolvido, como numa reentrega. Qualquer outra falha propaga como veio; sem
`sourceMessageId` não há recuperação. Testes novos de serviço (o JDBC falso foi
estendido de "só decideApproval" para um fake com estado — linhas de
`work_items`/`approval_tasks`/`work_item_delegations` em memória, INSERT/UPDATE
aplicados nelas, contadores por tabela; ele não decide regra nenhuma):
`ingestOpenCreatesItemTaskAndEvent`, `ingestOpenResendReturnsExisting` (reenvio:
mesmo id, zero INSERT novo), `ingestOpenRaceReturnsWinnerItem` (INSERT lança a
violação 23505 da constraint e a leitura passa a enxergar o item do vencedor:
devolve o id dele, 0 itens gravados por nós), `ingestCloseClosesOpenItem`,
`ingestCloseCancelledMapsToCancelled`, `ingestCloseAlreadyClosedIsNoOp`,
`ingestCloseWithPendingApprovalRefuses`, `delegateCreatesDelegation`,
`delegateOverlapRefuses`, `delegateBeyondDelegatorRefuses`,
`revokeDelegationByDelegator`, `revokeDelegationByStrangerRefuses`,
`revokeAlreadyRevokedRefuses` e `escalateOverdueEscalatesAndSkipsWithoutTarget`
(item vencido sem alvo volta em `skippedWithoutTargetIds`, sem UPDATE; item
dentro do prazo não aparece em lista nenhuma). Total da classe: 20 casos.

### m1 (MENOR) — Fronteira do `DUE_IN_7_DAYS` — CORRIGIDA (só teste e rótulo; semântica intacta)

Teste novo `DueFilterAndVisibilityTest.dueIn7DaysLocalDateBoundary`, com o agora
de referência 2026-03-10 (10:00 locais, America/Sao_Paulo): hoje+6 (2026-03-16)
dentro, hoje+7 (2026-03-17) dentro — inclusive às 23:59:59 locais de hoje+7
(2026-03-18T02:59:59Z, mais de 7×24h após agora) — e hoje+8 fora desde 00:00
locais (2026-03-18T03:00:00Z). Javadoc do `DueFilter` e rótulo do frontend
(`MinhasTarefas.tsx`: de "Próximos 7 dias" para **"Vencem até hoje + 7 dias"**)
alinhados à semântica real: janela de DATAS locais de hoje até hoje+7 inclusive
(8 datas corridas), não "agora + 7×24h". A semântica do código não mudou.

### m2 (MENOR) — Trava da V9_13 parcialmente guardada — CORRIGIDA

(a) `WorkMigrationConsistencyTest.workflowParametersDefined` agora assere também
`'wf.self_approval', 'ALLOWED_VALUES', '[false]'` (o `'LOCKED', 'false'` já era
asserido; a linha já existia na V9_13 — era o teste que não a conferia).
(b) O resolvedor `TenantWorkParameters` **não lia** `parameter_legal_limits`;
agora lê os limites da chave junto da definição e os aplica a TODO valor
resolvido (`LOCKED`: resolvido precisa igualar o valor travado;
`ALLOWED_VALUES`: precisa estar na lista; `MIN_VALUE`/`FLOOR`/`MAX_VALUE`
numéricos), como erro de estado — um `true` aceito para `wf.self_approval`, com
os limites da V9_13 presentes, explode **já na resolução**, antes de qualquer
decisão (e, sem linhas de limite, a trava restante — definição classe a +
`ApprovalRule.assertSelfApprovalForbidden` — continua explodindo com `true`).
Teste novo dedicado: `TenantWorkParametersTest` (4 casos sobre JDBC falso:
`true` aceito com limites presentes explode; `false` aceito/do padrão resolve;
`true` sem linhas de limite explode; padrão de fábrica adulterado para `true`
explode na conferência da definição).
(c) Registro com todas as letras, como pedido: **a definição no banco NÃO tem
imutabilidade física** — não há trigger nem constraint em
`parameter_definitions`/`parameter_legal_limits` que impeça um `UPDATE` direto
de quem tenha escrita no banco. A defesa da trava é **aplicação + console**: o
console (P1.6) recusa propostas fora dos limites e da classe; este resolvedor
explode com qualquer valor resolvido fora dos limites; e a `ApprovalRule`
explode com qualquer `true` resolvido antes de decidir. Quem tem escrita direta
no banco está fora do modelo de ameaça desta trava. (O mesmo texto entrou no
javadoc de `TenantWorkParameters`.)

### m3 (MENOR) — `checksMatchEnums` só conferia contenção — CORRIGIDA

`WorkMigrationConsistencyTest.checksMatchEnums` foi reescrito: extrai os literais
EXATOS de cada CHECK (`IN (...)`) da V1_11 e assere **igualdade de conjuntos** com
o enum correspondente, nos dois sentidos — `item_type` == `WorkItemType` (14),
`status` de `work_items` == `WorkItemStatus`, `status` de `approval_tasks` ==
`ApprovalStatus`, `decision_channel` == `ApprovalRule.Channel` e `event_type` ==
o conjunto de eventos gravados pelo serviço. Valor extra no CHECK agora reprova
o teste (antes passava: só se conferia que todo nome do enum estava no SQL).

### m4 (MENOR) — Convenção silenciosa de encerramento — DOCUMENTADA (comportamento inalterado)

Fica registrado explicitamente, aqui e nos javadocs de
`WorkInboxService.decideApproval`/`ingestClose`: **é convenção DESTA entrega,
pendente de decisão do plano — não é regra do aceite** — que (1) aprovar E
rejeitar encerram o item como `DONE`, com o desfecho em `close_reason`
(`APPROVED`/`REJECTED`) e no estado da tarefa; rejeição NÃO vira `CANCELLED`
(não existe estado terminal `REJECTED` para o item nesta entrega; mapear
rejeição para cancelado misturaria "o aprovador disse não" com "o trabalho foi
cancelado"); e (2) no `ingestClose`, o estado final é `CANCELLED` só para
`closeReason` exatamente `CANCELLED` — qualquer outro motivo vira `DONE` com o
motivo gravado literal. O comportamento não mudou; testes novos o fixam:
`rejectionAlsoDecides` (asserções de `DONE`/`REJECTED` acrescentadas),
`ingestCloseClosesOpenItem` (`COMPLETED` → `DONE`) e
`ingestCloseCancelledMapsToCancelled`.

### Arquivos alterados nesta correção

- `ApprovalRule.java` (A1 + A2/M1 no canal delegado; assinatura com o mapa de
  permissões atuais dos delegantes — a antiga foi removida);
- `DelegationRule.java` (novo `effectiveGrantedPermissions`, reusado por
  `effectivePermissions`, `ApprovalRule` e `WorkItemVisibility`);
- `WorkItemVisibility.java` (interseção no caminho por permissão; assinatura
  com o mapa);
- `WorkInboxService.java` (mapa de permissões dos delegantes fiado em
  decisão/lista/contagem/início; recuperação da corrida no `ingestOpen`;
  javadocs da convenção m4);
- `TenantWorkParameters.java` (leitura e aplicação de `parameter_legal_limits`
  na resolução; javadoc com o registro m2c);
- `DueFilter.java` (só javadoc) e `frontend/.../MinhasTarefas.tsx` (só o rótulo
  do filtro);
- Testes: `ApprovalRuleTest` (12 casos), `DueFilterAndVisibilityTest` (5),
  `WorkMigrationConsistencyTest` (5, fortalecidos), `WorkInboxServiceTest` (20),
  `TenantWorkParametersTest` (nova, 4) — total 60, executados nesta VM (ver o
  parágrafo de contagem acima). Nenhum push foi feito.

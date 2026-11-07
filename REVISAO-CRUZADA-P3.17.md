# Revisão cruzada INDEPENDENTE — P3.17 (Acesso assistido com consentimento e acesso de emergência)

Revisor: agente revisor independente (não é o implementador). O
`REVISAO-P3.17.md` do pacote foi tratado como documento da frente
implementadora e **não** foi usado como evidência — cada item foi relido
no código, na migration e nos testes, contra o acceptance da P3.17 em
`ziipsan-retomada/fontes/docs__plano-v3__redacao__tarefas-P3.json`, e a
coerência externa foi conferida nos espelhos locais do mesmo HEAD
(`p2.5/espelho`: `V14`/`audit_log`, `V9`/`platform.audit_event`,
`V8`/outbox, `V9_3`/`V9_4`/`support.ticket`, `Json`, `UuidV7`,
`TicketService`; pacote da P3.10 para o registro do incidente).
**Sem push e sem escrita no GitHub. Testes NÃO executados** (esta VM não
tem JDK/Maven): a revisão é estática e adversarial; a prova final é a CI
total no PC do dono.

## Veredito: LIBERA COM CORREÇÕES APLICADAS

Nenhum item do acceptance falha no código para as operações e fluxos
atuais: as negações são 403, auditadas antes de valer, nos lugares certos,
e os dois prazos têm mecanismo + teste com relógio. Havia, porém, um
**furo latente de classificação** (default permissivo para um valor futuro
do enum de operações) e **lacunas reais de teste** em caminhos de
segurança — corrigidos nesta revisão (seção 3). Os demais achados são
defeitos e pendências de integração registrados na seção 4, nenhum deles
um bypass explorável no pacote como entregue.

## 1. Acceptance × entrega, item a item

| # | Item do acceptance | Estado | Evidência conferida por esta revisão |
|---|---|---|---|
| 1 | migration única: só a core `V10_3` | ✅ COBERTO | O pacote contém exatamente 1 migration, `V10_3__assisted_and_emergency_access.sql`; nenhuma `V10_3` existe nos espelhos do HEAD e a vizinha P3.18 usa a `V10_4` — sem colisão |
| 2 | sem consentimento → acesso negado e auditado | ✅ COBERTO | `AccessPolicy.evaluateConsent` nega `consent-required`; `AccessGuard.openAssisted` grava no `audit_log` **antes** de lançar `AccessDeniedException` (403). Teste `assistedDeniedWithoutConsentIsAudited`: 403 + 1 entrada `allowed=false`, e `audit_event` vazio (trilha só da emergência) |
| 3 | revogação encerra a sessão em até 1 min | ✅ COBERTO no núcleo, com condição de integração (D3) | Mecanismo = **verificação por requisição**: a política reconsulta o consentimento a cada requisição e nega desde o instante da revogação (`consent-revoked`, imediato — dentro do teto). Persistência: `revokeConsent` grava `must_end_by = revogação + 1 min` na mesma transação e `closeDueSessions()` fecha as vencidas. Testes com relógio: negação às 10:05:00 (revogação 10:05) no guard; `mustEndBy` 10:06:00, não devido às 10:05:59 e devido às 10:06:00 no ciclo de vida — contas refeitas à mão por esta revisão |
| 4 | revelar documento e exportar bloqueados (assistido **e** emergência) | ✅ COBERTO (com correção C1 e testes C3) | `blockedOperation` roda **antes** de qualquer outra regra nos 4 métodos da política (abertura e requisição, dois modos). Testes: assistido abertura+requisição, emergência abertura. Os testes de **requisição de emergência** (revelar/exportar) faltavam — acrescentados nesta revisão. Bypass procurado: não há caminho fora do guard no pacote; o furo era **latente** — ver C1 |
| 5 | cada requisição no `audit_log` do tenant com o id do consentimento | ✅ COBERTO no guard / ⚠️ fiação fora do pacote | Guard audita abertura e cada requisição; `detailsJson` (via `Json`) carrega `consentId`; teste confere 3 permitidas + 1 negada, todas com o mesmo `consentId`. Ressalvas: a fiação HTTP que obriga toda rota a passar pelo guard **não está no pacote** (pendência declarada no relatório da frente — aceita como recorte da tarefa) e o defeito D2 |
| 6 | emergência: sem incidente declarado, sem `PLATFORM_OWNER` ou sem MFA no momento → negado e auditado | ✅ COBERTO, com ressalvas D4/D6 | Códigos por condição faltante, todos testados na política: `incident-not-found`, `incident-not-declared`, `incident-tenant-mismatch`, `platform-owner-required`, `mfa-required`, `mfa-not-current`. No guard, a negação é auditada **nos dois lugares** (testes). Papel fora do domínio da P0.43 é recusado no construtor de `PlatformActor` (nunca vira papel presumido) |
| 7 | qualquer escrita em emergência → 403 | ✅ COBERTO | `emergency-read-only` na abertura e em cada requisição (ler de novo não afrouxa: a sessão não guarda "já abriu, pode escrever"). Teste do guard confere `status() == 403` na requisição de escrita; política confere o código na abertura. Com a C1, a classificação leitura×escrita ficou **fechada**: em emergência só `READ` passa |
| 8 | aviso ao tenant pela outbox em até 24 h (teste com relógio) | ✅ COBERTO com ressalva D5 | `noticeDueAt = startedAt + 24 h` imposto no construtor do record (recusa outro valor) e no CHECK de leitura; teste com relógio: devido 08/11 10:00Z, vencido só 1 s depois, envio às 10:30 do mesmo dia cumpre, 1 s após o prazo não cumpre. O serviço grava a linha na outbox **na abertura** (t=0, muito antes do teto). A entrega final ao tenant é do worker da P1.9 e a semântica de `notice_sent_at` é a ressalva D5 |
| 9 | cada requisição da emergência no `audit_log` do tenant **e** em `audit_event`, com o id do incidente | ✅ COBERTO | O guard grava a **mesma** `AccessAuditEntry` nas duas portas, com `incidentId` no campo e no JSON; teste conta 3×3 (abertura + leitura + escrita negada) e confere o `incidentId` em todas. SQL conferido contra `V9` (colunas e grant SELECT/INSERT existem) e `V14` (colunas do INSERT existem; `action` VARCHAR(20) comporta `READ`/`UPDATE`/`EXPORT`, os três presentes no `AuditLog.AuditAction`) |
| 10 | sessão registrada no registro do incidente (P3.10) | ⚠️ PARCIAL (ressalva confirmada, não escondida) | Porta `IncidentSessionRegistry` chamada na abertura + sessão persistida com `incident_id` + `sessionsByIncident(...)`. **Verificação desta revisão**: a entrega da P3.10 é o modelo LGPD `PersonalDataIncident`, chaveado por `occurrenceId` da P2.25 — **não tem** id de incidente próprio, flag `declared`, nem tabela; logo não há FK nem escrita numa tabela de registro possível sem inventar fonte. O parcial é estrutural da dependência, declarado no relatório e no cabeçalho da `V10_3`. Ver também D4 |

**Resumo: 10 itens — 8 cobertos, 1 coberto com ressalva semântica (8),
1 parcial por dependência estrutural (10). Nenhum item ausente.**

## 2. Padrões do projeto

- **JSON só via classe `Json` do core**: ✅ conferido por busca em todo o
  pacote — `detailsJson`, `toJson` (×2) e o payload da outbox saem de
  `Json.write(...)`; nenhum JSON montado à mão, nenhum `Map.toString()`.
  `Json.write(Object)` existe no espelho com essa assinatura.
- **Nenhum dado inventado / fail-closed**: ✅ prazos só os do acceptance
  (1 min, 24 h); "MFA no momento" sem janela de tolerância inventada
  (mas ver D6); `incident_id` sem FK em vez de tabela inventada; ausência
  de consentimento/incidente/sessão sempre vira negação ou erro explícito,
  nunca presunção.
- **Testes com casos calculados à mão, não circulares**: ✅ instantes
  conferidos por esta revisão (07/11/2026 é sábado; vigência [10:00, 12:00)
  com expiração exatamente às 12:00; revogação 10:05 → teto 10:06;
  +24 h → 08/11 10:00Z). Exceção encontrada e corrigida: um loop vacuo no
  teste de migration (C2).
- **Nenhuma asserção afrouxada**: ✅ a entrega só adiciona arquivos; a
  única remoção desta revisão é o loop vacuo de C2, que não afirmava nada
  sobre o sistema (ver C2).
- **Auditoria fail-hard**: ✅ por construção — o guard audita **antes** de
  devolver sessão ou de lançar a negação; se a porta de auditoria lança, a
  exceção propaga e **nenhum acesso é concedido** (no serviço JDBC,
  `appendTenantAudit`/`appendPlatformAudit` lançam `IllegalStateException`
  em falha de SQL, sem catch que engula). Faltava o teste disso — C3.
- **Compilação provável**: ✅ saldos de chaves/parênteses = 0 em todos os
  `.java` (recontados após as correções); imports e assinaturas conferidos
  contra os espelhos: `Json.write`, `UuidV7.generate()`, colunas de
  `public.audit_log` (V14), `platform.audit_event` (V9), `public.outbox` e
  `outbox_ensure_partition(DATE)` (V8, retorna `void` — o
  `executeQuery().close()` do serviço é o mesmo padrão do `set_config` do
  `TicketService`), FKs para `support.ticket` (V9_4),
  `uuid_generate_v4()`/`current_tenant_id()` no padrão do schema `support`
  (V9_3/V9_4), `inTx` com `set_config('app.current_tenant', ?, true)`
  idêntico ao `TicketService`. O `extracting(...)` dos testes encadeia de
  `AbstractThrowableAssert`, que herda `extracting` de
  `AbstractObjectAssert` — compila no AssertJ do Boot 4.
- **SQL da `V10_3` × código**: ✅ coerente — CHECK de `mode` = `AccessMode`,
  CHECK de `actor_role` = os 4 papéis da P0.43, vínculo fechado por modo
  nos dois lados, RLS forçada com `current_tenant_id()`, sem DELETE para
  `ziipsan_app`, sem semente. Divergências menores em D7.

## 3. Correções aplicadas nesta revisão

- **C1 — `AccessPolicy.java` (defeito de segurança latente, corrigido).**
  *Era*: default permissivo — a política só **negava** operações nomeadas
  (`REVEAL_DOCUMENT`, `EXPORT` e, na emergência, `WRITE`); qualquer outro
  valor, inclusive um valor **futuro** de `AccessOperation` ainda sem
  classificação, seria **permitido** nos dois modos (na emergência, uma
  escrita disfarçada de operação nova passaria). *Ficou*: lista fechada —
  novo passo `unclassifiedOperation(...)` nos 4 métodos de avaliação
  (abertura e requisição, assistido e emergência): só `READ` e `WRITE`
  são operações ordinárias classificadas; qualquer outro valor é negado
  com o código novo `operation-not-classified`. Comportamento das 4
  operações atuais **inalterado** (conferido caminho a caminho: bloqueados
  continuam caindo antes, em `blockedOperation`; `WRITE` na emergência
  continua caindo em `emergency-read-only`). Javadoc da política e de
  `AccessOperation` atualizados para registrar a regra "enum novo nasce
  negado".
- **C2 — `AccessMigrationConsistencyTest.java` (teste vacuo removido).**
  *Era*: um loop sobre `AccessOperation.values()` cuja única asserção era
  `assertThat(operation).isNotBlank()` — não afirmava nada sobre a
  migration nem sobre o sistema, mas ocupava o lugar de uma cobertura.
  *Ficou*: loop removido e substituído por comentário honesto (operações
  não são coluna; a classificação é coberta por `AccessPolicyTest` e
  `AccessGuardTest`). Nenhuma asserção real foi removida ou afrouxada;
  os imports seguem usados pelos demais loops do método.
- **C3 — `AccessGuardTest.java` (3 testes novos + 1 dublê).**
  *Faltava* e foi acrescentado: (a) `auditFailureBlocksAccess` — porta de
  auditoria que sempre lança (`ThrowingAudit`): mesmo com consentimento
  vigente / emergência válida, o acesso é barrado pela exceção, sem
  sessão registrada e sem aviso agendado (prova do fail-hard);
  (b) `emergencyRequestRevealAndExportBlocked` — revelar e exportar em
  **requisição** de emergência (só a abertura era testada): 403 com os
  códigos certos e 3×3 entradas nas duas trilhas, todas com `incidentId`;
  (c) `assistedRequestOnEndedSessionDenied` — requisição em sessão já
  encerrada: negada `session-ended` e auditada com o `consentId`.

## 4. Defeitos e pendências (não corrigidos — registrados)

- **D1 — Abertura de emergência NÃO é atômica (defeito de integridade).**
  Em `AccessGuard.openEmergency`, auditoria do tenant, auditoria da
  plataforma, registro da sessão no incidente e gravação na outbox são
  **transações separadas** (cada método do `AccessSessionService` abre a
  sua). Se o aviso falhar depois do registro, a exceção propaga como se a
  abertura tivesse falhado, mas a sessão **já está persistida e aberta**
  no banco — sem dono (ninguém recebeu o objeto), sem aviso, e com
  auditoria dizendo `allowed=true`. Se `appendPlatformAudit` falhar, só
  a trilha do tenant fica. Não é bypass (o acesso não é concedido), mas a
  integração deve tratar: idealmente uma transação única para
  sessão+outbox, e reconciliação das sessões órfãs.
- **D2 — `authorize*` sem `tenantId`: existe negação SEM auditoria.**
  `authorizeAssistedRequest`/`authorizeEmergencyRequest` derivam o tenant
  da sessão ou do vínculo; se **ambos** forem nulos, o guard lança
  `IllegalArgumentException` **antes** de auditar. É erro de chamador (não
  há tenant onde auditar), mas num portão de segurança o desenho ideal é
  receber `tenantId` explícito para que **toda** negação seja auditável.
  A `REVISAO-P3.17.md` da frente apresenta esse ponto como defeito
  "corrigido"; esta revisão o registra como **limitação que permanece**.
  Integração: nunca chamar `authorize*` sem sessão ou vínculo.
- **D3
...[truncated 5823 chars]
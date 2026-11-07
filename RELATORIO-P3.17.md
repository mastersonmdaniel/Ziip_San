# Relatório P3.17 — Acesso assistido com consentimento e acesso de emergência

Repo `mastersonmdaniel/Ziip_San`, HEAD de referência `2ba51f8`. Entrega local
em `~/workspace/ziipsan-producao/p3.17/entrega/`, **sem push e sem escrita no
GitHub**. Migration criada: **só a core `V10_3` reservada** à tarefa.

## Limitação do espelho (honesta)

A leitura ao vivo do repo pelo conector GitHub foi **interrompida por
HTTP 429** na segunda chamada (a primeira, do caminho presumido do
ADR-0020, devolveu "arquivo inexistente" — o nome exato do arquivo do ADR
não foi confirmado). 429 é parada dura: nenhuma nova chamada foi feita.
O inventário veio dos espelhos locais do mesmo HEAD `2ba51f8`, já
conferidos em tarefas anteriores: core da P2.5 (`audit_log` + `V14`,
`platform.audit_event` da `V9`, outbox `V8`, chamados `V9_4`,
`PermissionCatalog`/papéis da P0.43, `Json`, `UuidV7`, `ApiException`) e o
pacote local da P3.10 (registro do incidente). **Não foi possível ler ao
vivo**: o texto do ADR-0020, os controladores/filtros HTTP atuais de
plataforma e a persistência real do registro do incidente da P3.10.
Consequências nas pendências.

## Desenho

- **Onde vive**: `shared/ziipsan-core`, pacote `br.com.ziipsan.core.access`
  — o mesmo módulo dos chamados da P1.23 (`core.support`), do `audit_log`
  e da outbox que a tarefa usa. Tabelas novas no schema `support`
  (linhagem da P1.23: a `V9_4` já anunciava "acesso assistido e de
  emergência: P3.17 (outras tabelas)").
- **Regra pura + portão + persistência**, como nas frentes anteriores:
  `AccessPolicy` decide (sem banco, sem relógio da máquina);
  `AccessGuard` audita TODA tentativa ANTES de a decisão valer e lança
  `AccessDeniedException` (403) já auditada nas negações;
  `AccessSessionService` (JDBC, `DataSource` + tenant + `Clock`, padrão
  `TicketService`) implementa as portas: `audit_log`, `audit_event`,
  registro da sessão no incidente e aviso pela outbox.
- **Assistido**: consentimento do tenant (`support.assisted_consent`)
  para beneficiário nomeado, com escopo e chamado (`ticketId` da P1.23)
  quando houver, vigência e revogação. Sem ele vigente → 403 auditado.
  Revelar documento e exportar bloqueados mesmo com consentimento.
  Escrita de suporte dentro do consentimento NÃO é vedada pelo aceite —
  as vedações nomeadas são revelar e exportar (registrado como leitura
  literal do acceptance).
- **Emergência**: sessão (`support.access_session`) vinculada ao incidente
  declarado, só `PLATFORM_OWNER`, só com `mfaStepUpAt` igual ao instante
  da abertura pelo relógio injetado ("no momento", sem janela inventada),
  somente leitura (escrita = 403), aviso ao tenant gravado na outbox na
  abertura (`noticeDueAt = startedAt + 24 h` imposto no construtor).

## Cobertura do acceptance, item a item

| Item | Estado | Onde |
|---|---|---|
| core `V10_3` | ✅ | migration única da entrega |
| sem consentimento → negado e auditado | ✅ | `AccessPolicy` + `AccessGuard`; teste do guard |
| revogação encerra a sessão em até 1 min | ✅ | negação desde o instante da revogação; `mustEndBy = +1 min` (record, migration, serviço); teste com relógio |
| revelar documento e exportar bloqueados | ✅ | códigos próprios, antes das demais regras, nos dois modos; testes |
| cada requisição no `audit_log` do tenant com o id do consentimento | ✅ | guard audita abertura e cada requisição; metadata via `Json` com `consentId`; teste |
| emergência sem incidente declarado / sem `PLATFORM_OWNER` / sem MFA no momento → negado e auditado | ✅ | 5 códigos de negação; auditoria nas duas trilhas; testes por motivo |
| qualquer escrita em emergência → 403 | ✅ | `emergency-read-only`; teste confere `status() == 403` |
| aviso ao tenant pela outbox em até 24 h (teste com relógio) | ✅ lógica + gravação; ⚠️ entrega final é do worker da P1.9 | `noticeDueAt`/vencimento testados com relógio; serviço grava a outbox na abertura |
| cada requisição no `audit_log` do tenant e em `audit_event` com o id do incidente | ✅ | mesma entrada nas duas portas; teste conta 3×3 |
| sessão registrada no registro do incidente | ✅ via porta + persistência por `incident_id`; ⚠️ sem FK (P3.10 sem tabela confirmada) | `IncidentSessionRegistry`, `sessionsByIncident(...)` |

**Resumo: 10 itens — 8 atendidos plenos, 2 atendidos com ressalva de
integração (aviso/outbox e registro no incidente), 0 pendentes de regra.**

## Arquivos (entrega/, caminhos do repo)

- Migration: `shared/ziipsan-core/src/main/resources/db/migration/core/V10_3__assisted_and_emergency_access.sql`
  (`support.assisted_consent`, `support.access_session`, RLS forçada,
  CHECKs de vínculo por modo, sem DELETE para `ziipsan_app`, sem semente)
- Código: `shared/ziipsan-core/src/main/java/br/com/ziipsan/core/access/`
  — `AccessMode`, `AccessOperation`, `PlatformActor`, `AssistedConsent`,
  `SecurityIncidentRef`, `AccessSession`, `AccessPolicy`, `AccessGuard`,
  `AccessAuditPort` (+ portas `SecurityIncidentLookup`,
  `IncidentSessionRegistry`, `EmergencyNoticePort`),
  `AccessDeniedException`, `AccessSessionService`
- Testes: `shared/ziipsan-core/src/test/java/br/com/ziipsan/core/access/`
  — `AccessPolicyTest` (10), `AccessGuardTest` (6),
  `AccessSessionLifecycleTest` (5), `AccessMigrationConsistencyTest` (3)
- Revisão: `REVISAO-P3.17.md` (nesta pasta)

## Verificação feita

- Contas de prazo refeitas à mão e conferidas por `date -d`
  (07/11/2026 = sábado; +24 h → 08/11 10:00Z; revogação 10:05 → 10:06).
- Conferência estática de sintaxe (saldos de chaves/parênteses) de todos
  os `.java` e do SQL sem comentários.
- **Testes NÃO executados**: esta VM não tem Java/Maven — a prova é a CI
  total (`scripts/ci.sh`) no PC do dono, na integração da camada.

## Pendências honestas

1. **ADR-0020 não lido ao vivo** (429 + nome de arquivo não confirmado):
   conferir na integração se o ADR define superfícies além do acceptance
   (ex.: telas da Central, motivos de encerramento nomeados) — o aceite da
   P3.17 foi a fonte normativa desta entrega.
2. **`SecurityIncidentLookup` sem implementação JDBC**: a P3.10 entregou
   o registro do incidente como modelo, sem tabela confirmada; a porta
   existe e o guard a consome por `SecurityIncidentRef`, mas resolver o
   id no registro real é fiação de integração. Pela mesma razão,
   `access_session.incident_id` nasce SEM FK na `V10_3` (declarado no
   cabeçalho da migration).
3. **Fiação HTTP**: não há controlador/filtro novo nesta entrega (como
   nas frentes P3.10/P3.19): ligar o `AccessGuard` às rotas de plataforma
   e ao contexto de sessão do BFF é da integração; a sessão assistida
   aberta pelo guard é persistida por quem chama
   (`AccessSessionService.saveSession`), enquanto a de emergência já se
   persiste pelo registro no incidente.
4. **Job de varredura**: `closeDueSessions()` fecha as sessões com
   `must_end_by` vencido, mas agendá-lo (cadência de 1 min) é da
   integração — o teto do aceite não depende dele, porque o portão nega
   desde a revogação.
5. **Interpretações registradas** (não escondidas): (a) "MFA no momento"
   = instante da abertura pelo relógio injetado, sem tolerância inventada;
   (b) revelar/exportar bloqueados também na emergência (exportar tratado
   como escrita de cópia para fora do tenant); (c) escrita de suporte no
   assistido não vedada, por leitura literal do aceite.
6. **Riscos para a CI**: INSERT direto em `public.audit_log` particionado
   (V14) — a partição do mês da requisição precisa existir, como no
   caminho JPA atual; `SELECT` da função `outbox_ensure_partition`
   devolvendo `void`; grants da `V10_3` pressupõem `ziipsan_app` e o
   schema `support` da `V9_3`/`V9_4`, como nas migrations vizinhas.

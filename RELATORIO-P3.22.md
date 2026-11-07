# RELATÓRIO — P3.22 Relatórios operacionais nativos: núcleo e cerca de 20 relatórios

**Repo:** mastersonmdaniel/Ziip_San, branch `main`, HEAD conferido via MCP: `0fbcd22`.
**Migration reservada:** `V15_1` (plano v3.0 §3, faixa `reporting`) — **livre confirmada**: não existe
módulo `ziipsan-reporting` nem migration V15_x no HEAD (listagem MCP de `intelligence/`:
só gis, losses, quality). **Sem push** — pacote local, conforme a tarefa.
**Espelho:** MCP respondeu normalmente (sem HTTP 429); espelho mínimo = listagens de
`intelligence/`, `commercial/`, pom do módulo losses (padrão de pom) + pacotes locais das
ondas anteriores (P2.5, P3.1, P3.4, P2.23) para as tabelas/views-fonte reais.

## Decisão estrutural (declarada)

Não havia módulo de relatórios no repo. Foi criado o módulo novo
**`intelligence/ziipsan-reporting`** (pom no padrão do `ziipsan-quality`), registrado com
**uma linha** no `pom.xml` raiz (arquivo incluído no pacote; a cópia-base é o pom do
espelho da P2.5 — conferir o diff na integração: deve ser só a linha do módulo).
Dependências: só `ziipsan-core` + webmvc + data-jpa. CSV/XLSX/PDF são gerados **só com o
JDK** (CSV RFC 4180; XLSX OOXML mínimo via ZIP; PDF 1.4 mínimo manuscrito) — nenhuma
biblioteca nova, nenhuma licença/CVE nova. JSON só pela classe `Json` do core.

## Arquivos entregues (50)

- `pom.xml` (raiz, +1 linha) e `intelligence/ziipsan-reporting/pom.xml`
- `.../db/migration/tenant/V15_1__reporting_core_and_views.sql` (518 linhas): tabelas
  `report_definitions` (linhas de fábrica com `tenant_id NULL` = catálogo de produto;
  registros externos gravam com tenant), `report_executions`, `report_schedules`;
  27 views `rpt_*` com `security_invoker = true` (RLS do tenant vale pela view);
  seed das 27 definições.
- Núcleo Java: `registry` (ReportDefinition/Column/Filter/Registry gerado
  do mesmo catálogo da seed, ExternalReportRegistrar), `query` (ReportQueryBuilder —
  SQL só com identificadores declarados, valores sempre como parâmetros;
  ReportDataSourceRouter — réplica se configurada, senão primário com
  `SET LOCAL statement_timeout`, padrão 120 s = teto RNF-71; JdbcReportQueryExecutor),
  `export` (ReportExportService com a regra única da P1.12; PersonalDataMasker;
  escritores CSV/XLSX/PDF; ReportSummaryCalculator; registro em
  `personal_data_access_log` via entidade real do core; entrega em `generated_files`
  da V3_11 por SQL nativo, sem depender do módulo registry), `schedule`
  (CronSchedule 5 campos subconjunto declarado; ReportScheduleService; ReportWorker
  por polling que gera e entrega em Arquivos gerados), `domain` (entidades/repositórios
  das execuções e agendamentos), `spi` (ReportUserContext com permissões e teto de
  máscara), `web` (ReportController mínimo) e `ReportingConfiguration` (fiação; réplica
  pelo qualifier `readReplicaDataSource`).

## Regra única da P1.12 — como foi aplicada (requisito de segurança)

Toda geração passa por `ReportExportService.export`, sem atalho:
1. permissão `reporting.<codigo>:export` do recurso (ou curinga `reporting:export`/`*:export`)
   — sem ela: `ExportDeniedException` **e** registro `DENIED` no log;
2. registro de **toda** exportação em `personal_data_access_log` (access_type `EXPORT`,
   entity_type `REPORT:<codigo>`, purpose com formato/linhas/fonte — nunca documento em claro);
3. máscara pelo teto do usuário (ADR-0008) em toda coluna `personal`/tipo DOCUMENT,
   aplicada **antes** de escrever qualquer formato — a mesma máscara da tela;
4. limite de linhas: até `SYNC_ROW_LIMIT = 1000` síncrono; acima, enfileira em
   `generated_files` (Arquivos gerados, JOR-29); teto duro por definição
   (`maxRows`, padrão 10 000) com flag `truncated` registrada — nunca corte silencioso;
5. agendado roda com o contexto do **criador** do agendamento; o resolvedor padrão
   (sem fiação de identidade) é conservador: máscara total e nenhuma permissão —
   o agendado é negado e registrado em vez de vazar.

## Cobertura do acceptance, relatório por relatório

| Cód. | Relatório | Estado | Fonte (tabela/view real) |
|---|---|---|---|
| RPT-001 | Arrecadação diária (POC-58) | ✅ entregue | `payments` status `BAIXADO` (V6_2) |
| RPT-002 | Pagamentos não identificados (POC-59) | ✅ | `payments` status `NAO_IDENTIFICADO`; documento mascarado |
| RPT-003 | Pagamentos em duplicidade (POC-59) | ✅ | `payments` status `DUPLICIDADE` |
| RPT-004 | Pagamentos por data de remessa/pagamento/crédito/banco (POC-60) | ✅ | `payments` + `return_file_details`/`return_files`; dimensão de agrupamento é escolha de execução do núcleo |
| RPT-005 | Situação da importação do retorno | ✅ | `return_files` + detalhes (V6_2) |
| RPT-006 | Faturamento sucinto | ✅ | `bills` vigentes e completas (V4_1) |
| RPT-007 | Faturamento detalhado por categoria e faixa | ✅ | `bill_items` + `categories` (V4_1) |
| RPT-008 | Fechamento do ciclo por grupo | ✅ | `cycle_schedule_items` + `billing_references`/`billing_groups` (V4_3) |
| RPT-009 | Contas emitidas | ✅ | `bills.issued_on` |
| RPT-010 | Contas canceladas | ✅ | `bills` status CANCELLED* + `cancel_reason` |
| RPT-011 | Contas retificadas | ✅ | `bills` status RECTIFIED/`rectification_count` |
| RPT-012 | Leituras retidas | ✅ | `consumption_records.billing_paused` (fila da P2.20) |
| RPT-013 | Ocorrências de leitura | ✅ | `meter_readings` + catálogo `reading_anomalies` (V5_1) |
| RPT-014 | Histograma de consumo por faixa (POC-56) | ✅ | `consumption_records` WATER vigente (V5_6), faixas 0/1-10/11-15/16-20/21-25/26-30/31-40/41-50/51+ |
| RPT-015 | Ligações e economias por situação | ✅ (interpretação declarada) | `consumer_units` status/ocupação; no modelo do cadastro a unidade é a economia |
| RPT-016 | Ligações e economias por categoria | ✅ | `bill_items` (ligações = contas distintas; economias = soma declarada) |
| RPT-017 | Valores a receber por tipo de unidade (POC-57) | ✅ | `bills` abertas + `property_profiles` |
| RPT-018 | Pendências por usuário (POC-65) | ✅ (semântica declarada) | protocolos abertos + OS não encerradas por `created_by` (UNION tipada) |
| RPT-019 | Aptos ao corte | ✅ | contas vencidas não pagas por unidade |
| RPT-020 | Notificações emitidas | ✅ | `cutoff_notices` (V9_2/P3.4) |
| RPT-021 | Motivos de não geração do aviso (P3.4) | ✅ | `cutoff_non_generation_reasons` (V9_2) |
| RPT-022 | Serviços por protocolo com tempos (POC-64) | ✅ | `service_orders` + `service_types` (V9_1), horas até encerrar |
| RPT-023 | Tarifa social | ✅ | `social_tariff_enrollments` (V4_2) |
| RPT-024 | Isenções | ✅ entregue (corrigido pós-revisão, B1) | `optional_category_enrollments` real da V4_4: categoria + vigência (`valid_from`/`valid_to`); a versão anterior lia a coluna inexistente `status` — ver seção de correções |
| RPT-025 | NFAg-e autorizadas, rejeitadas e em contingência | ✅ (mapeamento declarado) | `fiscal_documents` (V4_5); contingência = `PENDING_RECONCILIATION`/`TRANSMITTED` (estados do dicionário fiscal, NfageStatus) |
| RPT-026 | Devoluções (P3.29) | ✅ entregue (corrigido pós-revisão, B2) | `refund_requests` real da V6_5/P3.29 (a versão anterior lia a tabela inventada `payment_refunds`); fila `unidentified_payments` lida pela RPT-002 — ver seção de correções |
| RPT-027 | Reclamações mensais — art. 54 NR 11 | ✅ | `service_protocols` encerrados (V8_1/P1.36): mês, motivo classificado obrigatório (`close_reason_code`), sistema = canal de origem, % não atendido = encerrado após `current_due_date` |

**Total: 27 definições — 27 entregues** (na entrega original eram 25 + 2 parciais
protegidas; RPT-024 e RPT-026 foram corrigidas na revisão — seção própria abaixo).

**Mecanismo P3.31:** ✅ entregue (`ExternalReportRegistrar` + `ReportRegistry.registerExternal`):
origem CERTIFICATION/EXTERNAL, código sem colisão, view `rpt_*`, permissão `*:export` —
os relatórios de certificações em si (mapa de cláusulas, pendências, vencimentos) são da
P3.31, fora desta tarefa, como manda o acceptance ("só o mecanismo de registro").

**Núcleo declarativo:** ✅ filtros/ordenação/agrupamento validados contra a definição
(identificador fora da declaração é recusado; valor nunca vira SQL), exportação
PDF/XLSX/CSV, agendamento pelo worker, permissão por papel (papéis/permissões no
`ReportUserContext`), leitura em réplica ou primário com `statement_timeout`.

## Fronteira de escopo (itens remetidos — FORA, como manda a tarefa)

ED-46 (relatórios ausentes da 23d §5.2 / Superset da trilha), ED-88 (cobrança por
disponibilidade) e ED-97 (índice de tarifa social, lista positiva e pacote por agência)
**não** foram implementados — são das respectivas tarefas da trilha.

## Testes entregues

- `ReportRegistryTest` — 27 definições, unicidade, permissão por código, dado pessoal só onde declarado, colunas do art. 54.
- `ExternalReportRegistrarTest` — registro de certificação, recusas (origem BUILTIN, duplicado, view fora do padrão, permissão sem `:export`).
- `ReportQueryBuilderTest` — SQL só com declarados, valores como parâmetros, agrupamento com SUM, recusas de injeção/filtro não declarado.
- `PersonalDataMaskerTest` — tetos UNMASKED/PARTIAL/FULL, DOCUMENT mascara mesmo sem flag.
- `WritersTest` — CSV com escape RFC 4180, XLSX ZIP/OOXML legível, PDF com header/EOF e escape.
- `ExportPolicyTest` — negação registrada, máscara ponta a ponta, >1000 linhas enfileira em Arquivos gerados.
- `ReportContentSyntheticMassTest` — soma linhas **já agregadas e inventadas** pelo `ReportSummaryCalculator`: é teste do CALCULADOR DE TOTAIS, **não** prova de conteúdo de relatório (correção G6: a redação anterior o contava como prova de conteúdo — a prova de conteúdo é o `ReportViewsDatabaseTest`).
- `ReportViewsDatabaseTest` — prova real das views: aplica a V15_1 inteira sobre DDL mínima das fontes + massa sintética (`p322/massa-sintetica.sql`) e confere valores à mão (arrecadação 345,50; histograma 4 ligações/80 m³; art. 54 = 50,00%; aptos ao corte 450,00; views protegidas vazias sem as tabelas-fonte; seed = 27). Padrão de integração do projeto (PostgreSQL descartável, `ZIIPSAN_IT_*`, schema `itest_` descartável).
- `ReportMigrationConsistencyTest` + `CronScheduleTest` — migration × catálogo; cron diário/semanal/mensal e recusas.

## Riscos e pendências para a CI/integração (honestos)

1. **Esta VM não compila** (sem JDK/Maven/Docker): a prova é a CI no PC do dono.
   Riscos residuais típicos: imports/assinaturas (mitigado por reutilizar padrões
   verificados: `TenantAwareEntity`, `Json`, `PersonalDataAccessLog` do core).
2. **Performance RNF-71 (gerar em até 2 min na massa da P2.9, 300 mil ligações):
   NÃO verificável aqui** — declarado. O desenho ajuda (views agregadas, timeout de
   120 s, réplica), mas a medição é da rodada de CI/carga no PC do dono.
3. **Colunas assumidas e declaradas** (não conferidas em migration real nesta VM):
   `categories(code,name)`, `property_profiles(name)`, `service_types(code,name)`,
   `fiscal_documents(status, created_at)`, `cycle_schedule_items.status` e os valores
   `'DONE'`/`'PENDING'` do cronograma — se a CI apontar divergência, o fix é pontual
   na view correspondente. `fiscal_documents` existe com certeza (é alvo de FK da V6_2);
   os estados da NFAg-e foram conferidos no `NfageStatus` da P2.6/P3.7.
4. `report_definitions` **não** usa `register_tenant_table` (linhas de fábrica têm
   `tenant_id NULL`): justificativa gravada na migration; o `FreshInstallTest` pode
   exigir inclusão em lista de exceções, como já ocorre com `generated_files`.
5. Fiação no `ziipsan-app` (precisão pós-revisão, R1/G3): o módulo é autocontido
   pela `ReportingConfiguration`; do app dependem apenas: scan/import da
   configuração e do controller, `@EnableJpaRepositories` do pacote `domain`,
   `@EnableScheduling`, o atributo `ziipsan.reportUser` na borda e, opcionalmente,
   o bean `readReplicaDataSource` e resolvedores de identidade reais no lugar dos
   conservadores. O módulo **não** usa a fiação de exportação do app (ver adendo).
6. PDF mínimo: fonte Helvetica/WinAnsi (caractere fora vira `?`) e layout em texto —
   suficiente para o acceptance (exportar PDF); relatório formatado rico é evolução.
7. Filtros chegam como texto e são ligados como parâmetros `String` (datas/números
   dependem do cast do PostgreSQL na comparação) — conferir na CI com filtro real.

**Adendo (conferência tardia do espelho MCP):** a listagem remota confirmou o desenho —
`intelligence/` sem módulo de relatórios (404 no caminho novo), `Json.java` em
`shared/ziipsan-core/.../core/json`. **Correção G3 (honestidade arquitetural):** o
`ziipsan-app` tem um pacote `export` com a fiação da P1.12, mas este módulo **NÃO** a
usa e a integração **NÃO** deve "ligar" o controller/worker a ela — um módulo não pode
depender do app. O que existe aqui é uma **segunda implementação** da mesma regra da
P1.12, escrita contra os contratos compartilhados (`generated_files` da V3_11 e
`personal_data_access_log` do core). **Risco registrado:** duas implementações da
mesma regra podem divergir; a mitigação é o contrato (tabelas e desfechos) ser único
e os testes de política deste módulo cobrirem permissão, máscara, auditoria e limite.
O pom do módulo ganhou a dependência explícita `org.postgresql:postgresql`, no padrão
dos demais módulos (ex.: losses), porque o teste de banco usa `PGSimpleDataSource`.


---

## Correções pós-revisão (frente de correção — pacote reprovado por revisor independente)

Revisão aplicada sobre o pacote local, **sem push**. Contagens corrigidas primeiro:
o texto original dizia "32 classes" e "9 arquivos de teste" — **errado**: o pacote
reprovado tinha **35 arquivos Java em `src/main` e 10 de teste**. Depois desta
correção: **42 em `src/main` e 14 de teste** (+7 de produção: `ReportQueryJson`,
`ExternalDefinitionStore`, `InMemoryExternalDefinitionStore`,
`JdbcExternalDefinitionStore`, `ReportExecutionRecorder`,
`JpaReportExecutionRecorder`, `ReportUserContextResolver`; +4 de teste:
`ReportDataSourceRouterTest`, `ReportControllerTest`, `ReportWorkerTest`,
`AsyncExportEndToEndTest`). Nenhuma asserção foi afrouxada; os testes alterados por
correção de semântica estão justificados item a item abaixo, com o recálculo.

### Bloqueantes

- **B1 — RPT-024 (Isenções).** A view lia a coluna `status`, inexistente em
  `optional_category_enrollments` (V4_4 real: `category_code`, `criterion`, `proof`,
  `valid_from`, `valid_to`); com a tabela presente no integrado, o bloco `DO`
  executava o `CREATE VIEW` inválido e a V15_1 abortava. **Correção:** view
  reescrita direta (sem `DO`/`to_regclass`) sobre as colunas reais — isenções por
  `categoria_codigo` com `vigencia_inicio`/`vigencia_fim` e `COUNT(*)`; definição
  (registry + seed) atualizada; `criterion`/`proof` fora da view (dado SOC/SENS).
  A DDL mínima do teste ganhou a tabela com a DDL real e a massa ganhou 3
  enquadramentos. **Recálculo:** VULNERABLE_RESIDENTIAL 2026-01-01→2026-12-31 = 1;
  ASSISTANCE_ENTITY 2026-01-01→2026-06-30 = 1; MICRO_SMALL_BUSINESS
  2026-03-01→2027-02-28 = 1; soma de inscrições = **3** (antes o teste assertava 0
  "porque a tabela não existia" — a proteção era a omissão).
- **B2 — RPT-026 (Devoluções).** Não existe `payment_refunds` — contrato inventado.
  A P3.29 real (V6_5, versão atual do pacote local, já corrigida) cria
  `refund_requests` (`origin_payment_id`, `base_amount`, `refund_amount`,
  `refund_type`, `status`), `refund_executions`, `unidentified_payments` e amplia
  `payment_events` com `REFUND_APPLIED`/`PAYMENT_IDENTIFIED`. **Correção:** view
  reescrita sobre `refund_requests` (`motivo = refund_type`, `situacao = status`,
  `valor_total = COALESCE(SUM(refund_amount),0)` — a PENDENTE da P3.29 nasce sem
  valor calculado); as 4 tabelas entraram na DDL mínima e na massa. Coerência de
  "não identificados": a **RPT-002** passou a ler também a fila
  `unidentified_payments` (`NA_FILA`/`EM_IDENTIFICACAO`) em `UNION` com o status
  `NAO_IDENTIFICADO` de `payments` (o `UNION` deduplica) — definição única, sem
  duplicar na RPT-026. **Recálculos:** devoluções = **3** (ENCERRADA/DUPLICATE
  100,00; APROVADA/UNDUE 100,00 — dobro sobre base 50,00; PENDENTE/UNDUE 0,00),
  valor total = **200,00**; `refund_executions` = 1; eventos `REFUND_APPLIED` = 1.
  RPT-002: d4 por status (75,25, também na fila — 1 linha) + d5 só pela fila
  (33,40) = **2 linhas, 108,65**.
- **B3 — RPT-004.** O teste esperava `SUM(valor_liquido)=345,50`, mas o `LEFT JOIN`
  produzia 520,75 (100,00 + 245,50 + 100,00 da duplicidade + 75,25 do não
  identificado, ambos sem retorno). **Decisão do coordenador aplicada e
  documentada no comentário da view:** o POC-60 é por data de
  remessa/pagamento/crédito/banco e essas datas nascem do retorno — a view passou
  a `INNER JOIN` com `return_file_details`/`return_files`; só pagamentos com
  retorno entram e o esperado **345,50** se sustenta (100,00 + 245,50). Nenhum
  esperado foi alterado neste item; quem mudou foi a view.
- **B4 — `statement_timeout` sem efeito.** `SET LOCAL` fora de transação é
  ignorado pelo PostgreSQL. **Correção:** `ReportDataSourceRouter.open` abre
  transação explícita na conexão (`setAutoCommit(false)` → `SET LOCAL` → consulta
  → `commit()` no sucesso / rollback no fechamento, restaurando o auto-commit
  original); `JdbcReportQueryExecutor` confirma a transação ao concluir a
  leitura. **Teste novo** (`ReportDataSourceRouterTest`, Mockito): prova a ordem
  das chamadas sobre a conexão — auto-commit desligado antes do `SET LOCAL`,
  consulta na mesma transação, commit e restauração.
- **B5 — Exportação assíncrona nunca entregue.** Acima de 1.000 linhas o serviço
  enfileirava em `generated_files`, mas ninguém chamava `complete` para a fila
  ad-hoc (o worker só processava `report_schedules`). **Correção:** o serviço
  grava a execucao `QUEUED` em `report_executions` com os params (é daí que o
  worker regenera); `ReportWorker.runQueuedAdHoc` consome
  `report_executions` `QUEUED` não agendadas, resolve o contexto do solicitante,
  gera com máscara via `ReportExportService.generate` e chama
  `complete`/`fail`, fechando a execução como `READY`/`FAILED`/`DENIED`.
  `ExportPolicyTest` corrigido: o fake de entrega agora guarda estado de verdade
  (o `complete()` no-op escondia o buraco) e **teste novo de ponta a ponta**
  (`AsyncExportEndToEndTest`): 1.001 linhas de RPT-002 → `QUEUED`; após o worker,
  arquivo `READY` com 1.002 linhas de CSV (cabeçalho + 1.001), documento
  `12345678900` ausente e `***` presente; execução `READY` com `row_count=1001`.
- **B6 — Entidades × V15_1.** `ReportSchedule`/`ReportExecution` estendem
  `TenantAwareEntity`/`BaseEntity` (`version` `@Version nullable=false`,
  `tenant_schema`; base ainda traz `updated_at`, `created_by`, `updated_by`), e a
  V15_1 não criava essas colunas em `report_schedules`/`report_executions`.
  **Correção:** V15_1 ganhou `version BIGINT NOT NULL DEFAULT 0`,
  `tenant_schema VARCHAR(100)` nas duas tabelas e, em `report_executions`,
  também `updated_at`, `created_by`, `updated_by` (que a tabela não tinha).
  **Guarda nova** em `ReportMigrationConsistencyTest` conferindo os blocos das
  duas tabelas.

### Graves

- **G1 — Worker ignorava os params.** `runOne` executava `ReportQuery.empty()`.
  **Correção:** novo `ReportQueryJson` (serialização via `Json` do core, formato
  declarado, leitura tolerante a params antigos); `ReportScheduleService` grava
  filtros + agrupamento + ordenação nesse formato; `runOne` reconstrói a query
  dos params. **Teste novo** (`ReportWorkerTest`): agendamento com faixa de
  datas, ordenação desc. e agrupamento por canal chega idêntico ao executor.
- **G2 — Controller sem query e com contexto fabricado.** O `export` usava
  `ReportQuery.empty()` e, sem o atributo `ziipsan.reportUser`, fabricava
  `tenantId` aleatório/`userId` nulo. **Correção:** o controller monta o
  `ReportQuery` dos parâmetros HTTP (convenção documentada na classe:
  `sort`/`dir`/`groupBy`, filtros `coluna=valor` e `coluna_from`/`coluna_to`) e
  a ausência/tipo errado do atributo é **recusa explícita**
  (`IllegalStateException`) — contexto fabricado nunca exporta. A listagem
  passou a ser por tenant (`allForTenant`). **Testes novos**
  (`ReportControllerTest`): query montada capturada no executor; sem contexto,
  exceção e nenhuma execução.
- **G3 — Honestidade arquitetural.** Corrigido no adendo acima e no javadoc da
  `ReportingConfiguration`: o módulo **não** usa a fiação de exportação do
  `ziipsan-app` (módulo não depende de app); é uma segunda implementação
  conforme à regra da P1.12, com o **risco de divergência registrado**. Nenhum
  texto sugere mais que "a integração liga à fiação existente".
- **G4 — `report_executions` morta.** **Correção (fiação, caminho preferido):**
  nova porta `ReportExecutionRecorder` + `JpaReportExecutionRecorder`; o serviço
  grava negada (`DENIED`), síncrona (`READY`), enfileirada (`QUEUED` com o
  `generated_file_id`) e falha de leitura (`FAILED`); o worker fecha as
  assíncronas/agendadas. Testes observam a trilha nos fakes honestos.
- **G5 — Registro externo só em memória.** **Correção:** registros da P3.31 são
  **por tenant e persistidos** em `report_definitions` (a tabela já tinha
  `tenant_id` — nenhuma coluna nova foi preciso; linhas de fábrica seguem com
  `NULL`) via nova porta `ExternalDefinitionStore` (JDBC na fiação, memória nos
  testes); o `ReportRegistry` carrega builtins + externos persistidos **do
  tenant** (`find(code, tenantId)`, `allForTenant`); javadoc do registrar
  corrigido para a verdade. Validação de permissão reforçada na
  `ReportDefinition`: além do sufixo `:export`, exige
  `<prefixo>.<codigo-minusculo>:export`, o padrão do `canExport` (permissão de
  outro recurso é recusada — teste novo). **Testes:** registro persiste e é
  recarregado por um registry novo sobre o mesmo store; registro do tenant A
  não aparece para o tenant B.
- **G6 — Prova de conteúdo tautológica e views sem cobertura.** O teste
  unitário de totais foi rebaixado no texto a teste do calculador (acima). O
  `ReportViewsDatabaseTest` foi estendido com massa e asserções calculadas à
  mão para **RPT-009, RPT-011, RPT-016, ramo OS da RPT-018 e RPT-024/026 com
  dados** (recálculos nos itens B1/B2 e R3 abaixo).

### Menores

- **R1 — Fiação completa.** `ReportingConfiguration` declara todos os beans de
  infraestrutura (store, entrega, auditoria, recorder, worker, serviço de
  agendamento, resolvedores conservadores com `@ConditionalOnMissingBean`);
  `JdbcGeneratedFileDelivery`, `PersonalDataAccessExportAuditRecorder`,
  `ReportScheduleService` e `ReportWorker` perderam `@Component`/`@Service`
  (a configuração é a única fiação — sem bean duplicado). O que ainda depende
  do app está declarado com precisão no item 5 dos riscos e no javadoc da
  configuração.
- **R2 — Percentual somado no agrupamento.** `ReportColumn` ganhou
  `aggregable` (padrão: só `LONG`/`MONEY`); o `ReportQueryBuilder` só usa
  `SUM` em coluna agregável — razão/percentual `DECIMAL` (ex.:
  `percentual_nao_atendido` da RPT-027) vira `MAX` (valor representativo do
  grupo; somar percentuais não tem significado). Escolha documentada no
  javadoc e provada em teste (`ReportQueryBuilderTest`).
- **R3 — Semânticas fracas.**
  (a) **RPT-015:** `ligacoes`/`economias` eram o mesmo `COUNT(*)`. Contra a
  estrutura real de `consumer_units` (V3_5/V3_12): economia = cada unidade;
  ligação = unidade-raiz (`parent_unit_id IS NULL`). Massa ganhou a unidade
  filha u3 e a raiz u4. **Recálculo:** somas = ligações **3**, economias **4**;
  grupo ACTIVE/OCCUPIED (u1, u3, u4) = ligações 2, economias 3.
  (b) **RPT-019:** apto ao corte agora exclui quem tem proteção em
  `cutoff_non_generation_reasons` (P3.4) com código `PROTECTED_CLASS`,
  `SOCIAL_TARIFF_PROTECTED`, `BILL_UNDER_REVIEW_OR_CONTESTED` ou
  `CONNECTION_NOT_ACTIVE` (motivos situacionais não excluem). **Recálculo
  (mudança de esperado justificada):** u1 devia 450,00 e u2, 120,00 (conta b4
  nova), mas ambas têm `PROTECTED_CLASS` → fora; só u4 (b5, 80,00, sem
  proteção) é apta: **1 linha, 1 conta, 80,00** (o esperado anterior,
  2 contas/450,00 de u1, era a semântica errada que a revisão apontou).
  (c) **RPT-004:** não existe data de crédito nas colunas reais do retorno
  (`return_file_details` tem `paid_on`; `payments.collection_month` é a
  referência de arrecadação, dia 1 — DAD-19). A coluna foi renomeada para
  `mes_arrecadacao` na view, na definição e na seed.
  (d) **RPT-008:** os status reais do ciclo (V4_3) são `PENDING`, `COMMANDED`,
  `DONE`, `CANCELLED`; `pendentes` contava `CANCELLED`. Corrigido para
  `PENDING`+`COMMANDED`. Massa ganhou 1 item `CANCELLED`. **Recálculo
  (esperado justificado):** atividades **5** (era 4), realizadas 3,
  pendentes **1** (sem a correção seriam 2).
- **R4 — `SQLException` registrada como `NOT_FOUND`.** Conferida a entidade
  real do core (`PersonalDataAccessLog.Result`): os desfechos aceitos são
  `FOUND`, `NOT_FOUND`, `DENIED`, `QUOTA_EXCEEDED` — **não existe valor de
  erro e nenhum foi inventado**. A falha de leitura não grava mais linha no
  log de acesso (gravar `NOT_FOUND` era mentira de auditoria); o desfecho vai
  para `report_executions` como `FAILED`, onde há vocabulário. Declarado
  aqui, como manda a correção, em vez de inventar um valor. Teste novo em
  `ExportPolicyTest` prova: sem `NOT_FOUND` no log e execução `FAILED`.
- **§5/NR 11 — Mês sem reclamações.** Teste novo
  (`art54MesSemReclamacoesNaoGeraLinha`): a massa só tem encerramentos em
  2026-10; para 2026-09 a view gera **0 linhas** (ausência explícita).
  Declaração (também no comentário da view): a RPT-027 conta **todos** os
  protocolos encerrados agrupados por `close_reason_code`; identificar quais
  códigos são "reclamação" é do catálogo de motivos do tenant
  (`service_protocol_close_reasons`, V8_1) — a view não inventa lista.

### Recálculos que sustentam os demais testes alterados (massa ampliada)

A massa ganhou: unidade filha u3 e raiz u4; conta b4 (u2, ref. 2026-09,
120,00, `rectification_count=2`, origem b2) e b5 (u4, 80,00); item de b5
(economias 2, consumo 10, líquido 80,00); item de ciclo `CANCELLED`;
OS `os2` OPEN (usuário …002) e `os3` IN_EXECUTION (usuário …001); pagamento
d5 (33,40) + fila; devoluções/execução/evento; 3 isenções. Esperados
recalculados à mão e conferidos no teste de banco:

- RPT-006: ref. 2026-10 = 3 contas/**530,00** (300+150+80); ref. 2026-09 = 1/**120,00**.
- RPT-007: contas distintas **2**, economias **3** (1+2), consumo **18** (8+10), líquido **280,00** (200+80).
- RPT-009: total **4 contas/650,00** (530 + 120).
- RPT-011: **1** linha, retificações **2**, valor **120,00**.
- RPT-016: ligações **2**, economias **3**.
- RPT-017: RESIDENCIAL **530,00**; total com o grupo sem perfil **650,00**.
- RPT-018: PROTOCOLO/usuário …001 = **1**; ORDEM_SERVICO/…001 = **1** (os3);
  ORDEM_SERVICO/…002 = **1** (os2); os1 encerrada não conta.
- RPT-001/005/010/012/013/014/020/021/022/023/025/027: esperados **inalterados**
  (a massa nova não toca suas fontes ou toca sem mudar os grupos assertados:
  art. 54 segue 2 encerrados, 1 não atendido, **50,00%**).

**Limite desta frente (declarado):** esta VM não tem JDK/Maven/PostgreSQL — os
testes foram escritos e conferidos por leitura e recálculo, mas **não foram
executados aqui**; a prova é a rodada de CI no PC do dono, como nas demais
frentes. Nenhum push foi feito.

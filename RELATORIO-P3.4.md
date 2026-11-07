# P3.4 — Corte e religação: regras e bloqueios — relatório de entrega

Execução local, 07/11/2026. Base: HEAD `0fbcd22` de
`mastersonmdaniel/Ziip_San` (a P2.5 está na main). **Nenhum push foi
feito** — trabalho local apenas, conforme o encargo. Sem JDK/Maven nesta
VM: **nada compilou nem executou** — a prova é a CI total no PC do dono.
Nenhuma asserção afrouxada; nenhum dado ou regra inventado além do que
a spec cita; ausência de dado = erro explícito.

## Espelho (fluxo obrigatório, passo 2) — com parada 429 declarada

Via MCP foram lidos do HEAD `0fbcd22`: `ServiceOrderService` (73.343
bytes), `ServiceOrderEnums`, `ServiceOrderStatus`,
`ServiceOrderParameters` e `TargetType` do núcleo de OS — **idênticos**
(diff vazio) aos do espelho local da P2.5. Na 6ª chamada o GitHub MCP
devolveu **HTTP 429** e a parada dura foi respeitada: os demais insumos
vieram dos espelhos/pacotes locais da mesma linhagem (V9_1, V8_1,
`DeadlineCalculator`/`HolidayScope`, `Json`, `ApiException`,
`ParameterLimits` — espelho da P2.5; formatos de `cut.*`/`rec.*` e
PARAMETROS-DO-TENANT — pacote/espelho da P2.31; `DeliveryProofRule` —
pacote da P3.5). Proveniência completa em `../espelho/PROVENIENCIA.md`.
**Não espelhados** (sem cópia local acessível): as migrations V3_x do
registry (situação da ligação e classes protegidas da P1.3) e o motor
de modelos da P2.30 — integrados por **portas** e por identidade
gravada (código/versão do modelo), no precedente das P3.36/P3.5, sem
assinatura inventada.

## Onde vive e por quê

Módulo **`shared/ziipsan-core`**, pacote novo
`br.com.ziipsan.core.cutoff`, ao lado dos pacotes `serviceorder`
(P1.31) e `protocol` (P1.36) — é onde vivem a V9_1 e a V8_1 *tenant*,
logo é onde a reserva do plano (§3: `V9_2 | field | bloqueios de corte;
seleção mínima do aviso, prova de entrega e motivos de não geração |
P3.4`) se materializa: **tenant `V9_2` no core**, confirmada também
pelo relatório da P3.5 ("P3.4 = tenant V9_2"). A V8_1 e a V9_1 **não
foram tocadas** (teste de consistência guarda a fronteira).

## Pacote (12 de produção + 9 de teste + migration + este relatório e a revisão)

| Arquivo | Papel |
|---|---|
| `.../migration/tenant/V9_2__cutoff_reconnection.sql` | 6 tabelas (`cutoff_notices`, `cutoff_non_generation_reasons`, `cutoff_orders`, `reconnection_orders`, `cutoff_compensations`, `cutoff_consumption_alerts`), FKs para `service_orders`, CHECKs espelhando os enums (emergência exige justificativa E foto também no banco), RLS via `register_tenant_table`, e 17 definições `cut.*`/`rec.*` novas em `public.parameter_definitions` (ON CONFLICT DO NOTHING; as 5 da V1_13/P2.31 não se repetem) |
| `cutoff/CutoffEnums.java` | Motivos, nível de corte, classes protegidas, motivos de não geração, falhas de revalidação, prova, condição de liberação, fórmulas, gatilhos e taxas |
| `cutoff/CutoffParameters.java` | Chaves, classes e padrões de PARAMETROS-DO-TENANT (G9/20d), parsers/validadores dos formatos reais dos perfis da P2.31 |
| `cutoff/CutoffCalendar.java` | Vedações federal (Lei 14.015/2020) e SC (Lei 11.959/2001 c/ redação da 17.780/2019), janelas das 8h |
| `cutoff/NoticeSelectionRule.java` | Seleção mínima do aviso e do documento de cobrança |
| `cutoff/CutoffNoticeRule.java` | Aviso: primeira data, validade, prova, aviso pós-corte |
| `cutoff/CutoffExecutionRule.java` | Portão de execução (todos os motivos) |
| `cutoff/CutoffRevalidationRule.java` | Revalidação na execução, pura/offline (classe a) |
| `cutoff/ReconnectionRule.java` | OS de religação pelo pagamento, condição de liberação, taxa e SLA |
| `cutoff/UndueCutCompensationRule.java` | Compensação automática pelas 4 fórmulas |
| `cutoff/CutoffOrderTypes.java` | Os 3 tipos de OS declarados AO núcleo (`WATER_CUTOFF`, `WATER_RECONNECTION`, `CONNECTION_INSPECTION`) e o motivo de encerramento da revalidação |
| `cutoff/CutoffPorts.java` | Portas: prova de entrega (P3.5) e efeito de situação da ligação (registry/P1.3) |
| `cutoff/CutoffService.java` | JDBC no padrão do `ServiceOrderService`: seleção+registro, aviso, OS de corte pelo motor, revalidação que cancela pelo motor, pagamento→religação, consumo pós-corte→fiscalização, compensação, aviso pós-corte |
| 9 classes de teste | 61 casos (abaixo) |

## Cobertura do acceptance, item a item

1. **Migration tenant `V9_2`** com bloqueios, seleção mínima, prova de
   entrega e motivos de não geração ✔ (reserva conferida no §3 do
   plano; V8_1/V9_1 intactas).
2. **Corte e religação como operações do núcleo de OS da P1.31** ✔ —
   tipos declarados via `ServiceOrderService.declareType`, ordens por
   `createOrder/addTarget/submit`, cancelamento por
   `closeNotExecuted`, efeitos pelo `EffectHandlerRegistry` do motor
   (sem handler ligado, o motor recusa executar — nunca marca efeito
   não ocorrido). Nenhum motor paralelo.
3. **Casos da v1 como testes** ✔ — aviso 15/10/2026 + 30 dias = sáb
   14/11 → corte a partir de **seg 16/11**; **19/11** (véspera) e
   **20/11** (feriado nacional, sexta) bloqueados nas camadas federal e
   SC; **ADASA** (mínimo 60 / teto 120 dias: 59 cedo demais, 60 e 120
   selecionam, 121 preterito); **sem prova → religação sem taxa**.
   Datas conferidas com `date -d` (2026 é o ano em que o caso fecha:
   20/11/2026 é sexta e feriado).
4. **Vedações para TODOS os motivos** ✔ — padrão de fábrica de
   `cut.reasons` (classe b): todos seguem as vedações; excluir motivo
   que não a inadimplência exige aceite formal com norma citada
   (parser recusa sem a citação); inadimplência nunca sai (classe a).
   **Emergência exige justificativa E foto** ✔ — na regra, no CHECK da
   V9_2; com os dois, e só com os dois, dispensa as vedações temporais.
5. **Seleção mínima** ✔ — idade e valor do débito
   (`cut.min_overdue_days`, `cut.max_debt_age_days`,
   `cut.min_debt_amount`), situação da ligação (só ACTIVE), classes
   protegidas da P1.3/CAD-80 (essencial e dependente de saúde: nunca;
   tarifa social: só com `cut.social_tariff_allowed` da ERI), **sem
   conta em revisão ou contestada** (NR 11, arts. 37–38). A mesma
   regra alimenta o documento de cobrança (javadoc da regra).
6. **Aviso pelo modelo da P2.30, com prova da P3.5** ✔ — o aviso grava
   `template_code/template_version` (identidade do motor da P2.30; a
   renderização é dele) e a prova vem da porta da P3.5 com a semântica
   dela (só DELIVERED é prova; bounced/complained = não entregue).
   **Motivo de não geração registrado por unidade** ✔ em
   `cutoff_non_generation_reasons` (o relatório é da P3.22 — só o
   registro aqui), inclusive `NOTICE_ALREADY_VALID`.
7. **Aviso pós-corte (`cut.post_cut_notice_hours`)** ✔ —
   `postCutNoticeDueAt` = execução + horas; **sem valor aceito, erro
   explícito** (a chave não tem padrão de fábrica em fonte alguma; foi
   registrada na V9_2 com classe NULL = "não fixada", pendência
   declarada na própria definição).
8. **Teto de idade por agência dos perfis da P2.31** ✔ —
   `cut.max_debt_age_days` lido das `parameter_versions` aceitas (a
   ativação dos perfis); ARIS 120 dias é o teste do teto. Os formatos
   reais dos perfis (SLA `{normalHours,urgentHours,undueHours}`,
   fórmulas como string JSON) são os fixtures dos testes de parâmetros.
9. **Compensação automática** ✔ — fórmulas ARESC (maior entre 2×
   urgência e 20% da **primeira fatura após a religação**; sem a
   fatura, `PENDING_BASIS` sem valor), AGIR (20% da **última** fatura),
   ARIS (religação em 4 h sem ônus, crédito 0), manual com aprovação de
   outra pessoa. Sem `rec.urgent_fee`, erro explícito — nunca crédito
   zero presumido. Atraso de religação usa o mesmo motor
   (`trigger_reason = LATE_RECONNECTION`).
10. **Revalidação na execução, inclusive offline** ✔ — regra pura
    sobre snapshot de fatos (quitação/parcelamento → contestação →
    vedações → aviso válido → classe protegida, na ordem do aceite);
    falhando, `CutoffService.revalidate` grava a falha e **cancela a
    OS pelo motor** (`closeNotExecuted` com `CUT_REVALIDATION_FAILED`,
    criado no catálogo por `ensureRevalidationCloseReason`). Execução
    em campo é da ED-20 (fora de escopo) — a revalidação roda ANTES de
    `execute()` do motor, que é o desenho dela.
11. **OS de religação gerada pelo pagamento** ✔ —
    `rec.auto_order_on_payment` (padrão sim) e as **três opções** de
    `rec.release_condition`, uma por teste; SLA de `rec.sla_hours` por
    tipo (normal/urgência/indevida) gravado em `sla_due_at`.
12. **Consumo após o corte** ✔ — `onConsumptionAfterCut` cria a OS de
    **fiscalização** (`CONNECTION_INSPECTION`, operacional, sem efeito
    comercial) e o alerta; **nenhuma escrita de situação** acontece —
    a situação só muda pelo encerramento dessa OS (ADR-0039; NR 11,
    art. 42, V).
13. **Vedações temporais** ✔ — federal: sem iniciar suspensão sex–dom,
    feriado ou véspera (dia-granular); SC: janelas das 8h semanais e de
    feriado/**ponto facultativo** (`cal.holidays` kind OPTIONAL_POINT
    conta na camada SC, não na federal), **ressalvada ordem judicial**
    (que levanta só a camada temporal — nunca classe protegida nem a
    evidência da emergência); fuso = o do calendário operacional do
    tenant (`tenant.timezone`, PLT-14): a API recebe `ZonedDateTime`
    já nesse fuso e as janelas das 8h são por UF (camada SC só com
    `stateCode = SC`). Feriados de **todas as esferas** contam na
    vedação (regra travada de PARAMETROS-DO-TENANT, distinta de
    `sac.holiday_scope`). **Testes obrigatórios presentes**: corte às
    7h de segunda recusado (8h liberado) e corte em ponto facultativo
    recusado. **Um teste por regra e por opção de parâmetro** ✔
    (matriz abaixo).
    Fora de escopo respeitado: prazo/prorrogação do protocolo (P1.36),
    cobrança completa (P4.1), medida excepcional por área (ED-91) —
    nada implementado deles.

## Dependências dos pacotes locais (declaradas)

- **P2.31 (pacote local `p2.31/entrega`)**: os valores de agência são
  lidos das `parameter_versions` aceitas que a ativação da P2.31 gera —
  em especial `cut.max_debt_age_days` (ARIS 120, art. 153 §2º),
  `cut.min_notice_days`, `rec.sla_hours` e
  `rec.undue_cut_compensation` nos formatos exatos que
  `ScRegulatoryProfiles` carrega (fixtures dos testes). O formato não
  discriminado da AGR-Tubarão (`{reconnectionHours:24}`) **não** é
  adivinhado: `SlaHours.hoursFor` dá erro explícito, como a P2.31
  desenhou ("o tenant declara o mapeamento na ativação"). A P3.4
  depende de a P2.31 ser pushada/ativada para os valores existirem;
  sem perfil, valem os padrões de PARAMETROS-DO-TENANT.
- **P3.5 (pacote local `p3.5/entrega`)**: a prova de entrega é
  consumida pela porta `DeliveryProofPort`, com a semântica da
  `DeliveryProofRule` dela (só DELIVERED prova). A P3.5 declarou a
  persistência dela como porta sem migration própria e que "a prova do
  corte da P3.4 (V9_2) e esta porta precisam pousar na mesma história
  de tenant" — a V9_2 grava o estado da prova em `cutoff_notices`
  (`delivery_proof_status/at/channel/attempt_ref`), que é o pouso
  combinado; a fiação da porta à implementação da P3.5 é da integração.
- **P2.5 (na main)**: débitos/contas são os fatos de entrada das regras
  (`NoticeSelectionRule.Debt`, revisão/contestação) — sem dependência
  de módulo invertida (o core não depende do billing).
- **Interpretação declarada**: sem perfil de agência, o padrão de
  `rec.undue_cut_compensation` é a família ARESC/AGIR; entre as duas
  bases, adotou-se a da **última fatura** (a única cuja base já existe
  no momento do corte indevido). Se o dono preferir a outra base como
  padrão, é uma linha em `CutoffParameters`.

## Testes (9 classes, 61 casos — contagem por método)

- `CutoffCalendarTest` (6): federais por dia; seg 7h recusado/8h
  liberado; janela SC do feriado; ponto facultativo recusado; primeira
  data (caso v1); primeiro instante.
- `NoticeSelectionRuleTest` (9): seleção feliz, um teste por motivo de
  não geração (7), caso ADASA (4 fronteiras num teste), teto ARIS com
  exclusão parcial.
- `CutoffNoticeRuleTest` (5): caso v1 15/10→16/11, prova (3 estados),
  período não decorrido, validade (dentro/fora/sem validade), pós-corte
  (com e sem o parâmetro).
- `CutoffExecutionRuleTest` (8): válido; vedação p/ todos os motivos;
  exclusão com citação; ordem judicial (e seu limite); emergência
  (sem justificativa / sem foto / completa); classe protegida p/ todos
  os motivos; esgoto (sem/com parâmetro); inadimplência sem aviso e
  aviso sem prova.
- `CutoffParametersTest` (8): piso de 30 dias; classes protegidas só
  acrescentam; `cut.reasons` (classe a, citação, padrões); SLA nos
  formatos ARIS e AGR; fórmulas nos valores reais dos 3 perfis +
  padrão; 3 opções de `release_condition` + inválida;
  `auto_order_on_payment`; booleanos ausentes explícitos.
- `CutoffRevalidationRuleTest` (7): passa; DEBT_SETTLED (com
  precedência); contestada; TEMPORAL_BAN (+ judicial); prova; período
  e expiração; classe protegida.
- `ReconnectionRuleTest` (8): condição padrão; DOWN_PAYMENT;
  FULL_SETTLEMENT; auto desligado; sem prova sem taxa (2 estados);
  indevida e urgência (SLA 4 h); SLA ausente = erro; atraso.
- `UndueCutCompensationRuleTest` (5): AGIR nos dois ramos; ARESC
  pendente/com fatura; ARIS; sem taxa de urgência = erro; manual com
  aprovação separada.
- `CutoffMigrationConsistencyTest` (5): tabelas+RLS; CHECKs × enums;
  definições novas × as da V1_13; fronteira V9_1/V8_1; códigos dos tipos.

## Riscos para a CI (honestos)

1. **Sem compilação local**: conferência estática contra os espelhos
   (assinaturas de `ServiceOrderService`/`EffectHandlerRegistry`/
   `ApiException`/`Json` copiadas dos arquivos reais do HEAD
   `0fbcd22`). Erro de API fora do espelho só aparece no `mvn`.
2. **Datas locais no JDBC**: `setObject(LocalDate)` e
   `getObject(col, LocalDate.class)` (JDBC 4.2/pgjdbc) nas colunas
   `DATE` do aviso — o motor da P1.31 não usa `LocalDate`, sem
   precedente local; primeiro ponto a olhar se a rodada quebrar aqui.
3. **Tipos de OS e motivo de encerramento são por tenant**: a
   integração precisa chamar `CutoffService.declareTypes` e
   `ensureRevalidationCloseReason` uma vez por tenant (ou semear na
   P0.19/provisionamento); sem isso, `createOrder`/`closeNotExecuted`
   recusam nomeando o tipo/motivo ausente (comportamento desenhado).
4. **`CutoffService` lê parâmetros por `CURRENT_DATE`** (mesmo SQL do
   motor da P1.31): vigência por data, não por instante — coerente com
   o padrão existente, declarado.
5. **Fiação das portas** (prova da P3.5, efeito de situação do
   registry, renderização da P2.30, evento de pagamento do billing,
   evento de leitura do metering) nasce na integração, como nas demais
   tarefas; sem a porta de efeito, o motor recusa a execução do corte
   em vez de fingir o efeito.
6. **`SourceHygieneTest`/checagens de marca** do core sobre os arquivos
   novos, e o teste de RLS do platform/app sobre as 6 tabelas novas
   (registradas via `register_tenant_table`, como a V9_1 manda).
7. **Revisão cruzada** foi feita nesta mesma frente (profundidade 2/2,
   sem subagente disponível) — `REVISAO-P3.4.md`; recomenda-se a
   revisão do agente principal antes do push, como nas anteriores.

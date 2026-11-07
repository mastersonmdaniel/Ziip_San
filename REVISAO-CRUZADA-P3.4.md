# Revisão cruzada independente — P3.4 (07/11/2026)

Revisor independente (não é o autor da entrega nem da autorrevisão).
Conferência regra a regra contra o acceptance integral da P3.4
(`tarefas-P3.json`), o §3 do plano v3.0, os perfis reais da P2.31
(`ScRegulatoryProfiles` + `V1_13`), a `DeliveryProofRule` da P3.5 e os
5 arquivos frescos do núcleo de OS (espelho da P2.5, idêntico ao HEAD
`0fbcd22` segundo a proveniência — as assinaturas usadas conferem
campo a campo com esse espelho, ver item 5). Datas recalculadas com
`date -d` e aritmética de dias em Python. **Sem JDK nesta VM: nada
compilou** — a prova de compilação/execução continua sendo a CI total
no PC do dono. Nenhum push feito.

## Vereditos por item

### 1. Migration V9_2 — **OK**
- É a reservada à P3.4: §3 linha 431 (`V9_2 | field | bloqueios de
  corte… | P3.4`). A segunda coluna do §3 é a ÁREA (V8_1 = "portal",
  V9_1 = "OS"), não a location do Flyway; duas notas do próprio plano
  (linhas 398 e 430, P1.3) dizem textualmente "a `V9_2` do tenant segue
  reservada para a P3.4", e o relatório da P3.5 concorda. A entrega está
  em `db/migration/tenant/V9_2__cutoff_reconnection.sql`, junto das
  `V8_1`/`V9_1` tenant do core, sem tocá-las (a entrega não contém
  nenhum arquivo delas; o teste de fronteira guarda isso).
- 6 tabelas, todas com `register_tenant_table` (RLS) e FKs
  `(tenant_id, …) → service_orders(tenant_id, id)` onde há OS.
- CHECKs × enums conferidos por IGUALDADE DE CONJUNTOS à mão (o teste
  da entrega só afirma `contains`, que é mais fraco): reason_code (9),
  reason (5), cut_level (2), revalidation_failure_code (7),
  delivery_proof_status (3), release_condition (3), fee_decision (3),
  trigger_reason (2), formula (4), status da compensação (3) — todos
  idênticos aos enums Java, sem valor órfão em nenhum dos lados.
- Emergência exige justificativa E foto também no banco
  (`reason <> 'TECHNICAL_EMERGENCY' OR (btrim(justificativa) <> '' AND
  emergency_photo_document_id IS NOT NULL)`), e inadimplência exige
  `notice_id` no banco. Bônus coerente: ordem judicial exige
  `judicial_order_ref` no CHECK (ver defeito corrigido C3).
- As 17 definições novas são DISJUNTAS das 5 da V1_13/P2.31
  (`cut.min_notice_days`, `cut.max_debt_age_days`, `cut.forbidden_window`,
  `rec.sla_hours`, `rec.undue_cut_compensation`) — conferido por diff
  de conjuntos sobre os dois SQLs, não só pelo teste.

### 2. Casos da v1 recalculados à mão — **OK**
- 15/10/2026 é quinta; +30 dias corridos = sábado 14/11/2026; primeira
  data livre = **segunda 16/11/2026** ✓ (teste `earliestCutDate` e
  `firstAllowedDateOnOrAfter` afirmam igualdade exata).
- 19/11 (quinta, véspera) e 20/11/2026 (sexta, feriado) bloqueados nas
  duas camadas ✓; segunda às 7h recusado / às 8h liberado ✓ (janela SC
  [sex 8h, seg 8h)); ponto facultativo (25/11 sintético) recusado em SC
  e NÃO na camada federal ✓. Asserções estritas (`isTrue/isFalse/
  isEqualTo`), nenhuma frouxa.
- ADASA 59/60/120/121: contagens refeitas — 18/09→59, 17/09→60,
  19/07→120, 18/07→121 em 16/11/2026 ✓; fronteiras inclusivas nos dois
  extremos, coerentes com a fonte (V9_2: "ADASA >= 60"; ARIS 120 do
  perfil, art. 153 §2º).

### 3. Vedações temporais — **OK na regra; fiação do calendário ABERTA (item I2)**
- Federal dia-granular (iniciar: sex–dom, feriado, véspera;
  `OPTIONAL_POINT` fora desta camada) × SC em janela de hora
  ([sex 8h, seg 8h) e [8h do dia útil anterior, 8h do 1º dia útil
  seguinte) a feriado OU ponto facultativo) — granularidades certas,
  traçadas à mão em 6 instantes.
- Ordem judicial ressalva SÓ a camada temporal: no portão e na
  revalidação ela é testada depois de evidência da emergência e de
  classe protegida, e o portão do aviso continua valendo (teste
  `judicialOrderException` cobre judicial+protegida = recusado).
- Fuso: a API recebe `ZonedDateTime` já no fuso do tenant (PLT-14) —
  contrato honesto e documentado, MAS nenhum código da entrega carrega
  `cal.holidays` nem `tenant.timezone`: quem monta o `CalendarData` é a
  integração (ver I2). A regra respeita o fuso; a entrega não o lê.

### 4. Seleção mínima do aviso — **OK, com uma interpretação não declarada (A1)**
- Cada critério tem teste próprio e estrito: situação ACTIVE, revisão/
  contestação excluída, essencial/saúde nunca, tarifa social só com
  `cut.social_tariff_allowed`, idade mínima, teto (exclusão parcial
  testada: débito pretérito sai, o restante motiva), valor mínimo com
  fronteira exata. Teto ARIS 120 lido do parâmetro que a ativação da
  P2.31 grava ✓.
- **A1 (interpretação não declarada, NÃO corrigida):** com
  `cut.min_overdue_days = 60`, basta UM débito com ≥ 60 dias para a
  unidade se qualificar, mas débitos mais jovens também entram nos
  `motivatingDebts` e no total do aviso. A leitura alternativa (só
  débitos ≥ mínimo motivam) é defensável; a entrega não declara a
  escolha no relatório. Não corrigi: trocar seria inventar a regra —
  fica para o dono/integração decidirem e registrarem.

### 5. Revalidação e núcleo de OS — **OK após correções C1–C3**
- Tipos conferidos campo a campo contra o `ServiceTypeInput` real do
  espelho (18 campos, ordem posicional certa): `WATER_CUTOFF` e
  `WATER_RECONNECTION` não operacionais com efeito e schema no formato
  que o `EffectPayloadValidator` real entende (campos desconhecidos
  permitidos — o `cutLevel` do payload é válido); `CONNECTION_INSPECTION`
  operacional com `origin = INSPECTION` (existe no enum real) e sem
  efeito — exatamente o que o `declareType` real exige de tipo
  operacional. `CONSUMER_UNIT` tem verificador registrado pelo próprio
  motor. Sem motor paralelo: criar/submeter/cancelar é tudo do núcleo.
- `closeNotExecuted` é o caminho do cancelamento (estados não terminais;
  a revalidação roda antes de `execute()`, como a autorrevisão notou).
- **C1 [ALTO] corrigido — porta de efeito fora da transação do motor:**
  o `ConnectionEffectPort` original descartava a `Connection` que o
  `EffectContext` real entrega ao handler. Uma implementação ligada a
  ele teria de abrir transação própria: a situação da ligação poderia
  commitar enquanto o motor faz rollback (ou o contrário), quebrando o
  aplicador transacional do ADR-0039 e a frase do relatório "efeitos
  pelo EffectHandlerRegistry do motor". Corrigido: a porta recebe a
  `Connection` do contexto, com javadoc mandando usá-la sem commit;
  `CutoffService` passa `context.connection()` nos dois handlers.
- **C2 [MÉDIO] corrigido — registro de FAILED antes do cancelamento:**
  `revalidate` gravava `revalidation_status = FAILED` em transação
  própria ANTES de `closeNotExecuted`. Se o motor recusasse o
  cancelamento (ex.: OS já executada), o banco afirmaria um
  cancelamento que não houve — o contrário da garantia classe (a).
  Corrigido: cancela pelo motor primeiro, registra depois.
- **C3 [MÉDIO] corrigido — ordem judicial sem referência só falhava
  depois da OS criada:** o CHECK da V9_2 exige `judicial_order_ref`,
  mas `createCutoffOrder` só descobria isso no INSERT de
  `cutoff_orders`, com a OS já criada e submetida no motor (OS órfã).
  Corrigido: pré-validação no início de `createCutoffOrder`, antes do
  portão e do motor, com `ApiException.Validation` nomeando o campo.
- Portas honestas: `DeliveryProofPort` devolve o enum da própria P3.4
  com a semântica exata da P3.5 (só DELIVERED prova; BOUNCED/COMPLAINED
  = não entregue — conferido na `DeliveryProofRule`/`DeliveryStatus`
  reais da P3.5). O motor da P2.30 entra só por identidade gravada
  (código/versão), sem assinatura inventada. A proveniência (429 na 6ª
  chamada, resto de espelhos da mesma linhagem) é declaração
  verificável e consistente com o que foi usado.

### 6. Religação e compensação — **OK**
- `rec.auto_order_on_payment` (padrão sim, teste de desligado) e as 3
  opções de `rec.release_condition`, uma por teste, com negação da
  condição errada ✓. SLA por tipo de `rec.sla_hours` no formato real
  dos perfis; formato não discriminado da AGR (`{reconnectionHours:24}`)
  e o de CISAM-SUL (`{confirmedHours:[…]}`) dão erro explícito — nada
  adivinhado ✓.
- Sem prova → sem taxa: o teste afirma `feeDecision ==
  WAIVED_NO_PROOF` para NONE e NOT_DELIVERED (a ausência da taxa é a
  decisão gravada em `reconnection_orders.fee_decision`; o lançamento
  da cobrança é fronteira da ED-34/billing, fora desta tarefa) ✓.
- Pós-corte: `executedAt + cut.post_cut_notice_hours`; sem valor
  aceito, erro explícito testado ✓.
- Compensação: contas refeitas à mão — 2× R$ 50 = R$ 100 contra 20% de
  R$ 400 = R$ 80 e de R$ 600 = R$ 120, maior vence nos dois ramos (AGIR);
  ARESC sem a primeira fatura = `PENDING_BASIS` com valor NULL (CHECK
  da V9_2 impõe o mesmo); ARIS = prazo de 4 h, crédito 0,00 e taxa
  dispensada; manual só credita com aprovador ≠ proponente; sem
  `rec.urgent_fee`, erro explícito (nunca zero presumido) ✓.
- Interpretação "sem perfil → última fatura": está DECLARADA no
  relatório e no teste ("a fórmula ARESC/AGIR"). Conferido o
  PARAMETROS-DO-TENANT (linha 294): a fonte só fixa a FAMÍLIA
  ("sem perfil, a fórmula ARESC/AGIR"), sem discriminar a base — logo
  a escolha da base da AGIR é convenção declarada, não "a leitura mais
  fiel demonstrável"; nenhuma das duas bases é imposta pela fonte.
  Não é declaração falsa; é decisão de uma linha
  (`CutoffParameters.parseUndueFormula`) para o dono confirmar.

### 7. Consumo após o corte (ADR-0039) — **OK estrutural; teste comportamental ABERTO (A2)**
- `onConsumptionAfterCut` só cria a OS `CONNECTION_INSPECTION`
  (operacional, `effectCode == null` — afirmado no teste de
  consistência) e grava o alerta: nenhuma escrita de situação existe no
  código da entrega (leitura integral do `CutoffService`). A situação
  só pode mudar pelo encerramento dessa OS, via efeito do registry.
- **A2 [BAIXO] defeito aberto:** não há teste comportamental deste
  fluxo — não existe NENHUM teste de `CutoffService` na entrega (todos
  os 61 são de regras puras/consistência; serviço exigiria banco). A
  garantia hoje é estrutural (tipo sem efeito + ausência de escrita),
  não provada por teste. A integração deve cobrir com teste de serviço
  (Testcontainers): detectar consumo → situação inalterada + OS de
  fiscalização aberta.

### 8. Não geração, JSON e ausência de dado — **OK, com ressalva (A3)**
- Motivo registrado por unidade em `evaluateSelection` (e
  `NOTICE_ALREADY_VALID` em `issueNotice`, produtor que a autorrevisão
  acrescentou); o relatório da P3.22 não foi implementado ✓.
- JSON só via `Json` do core: grep na entrega — 15 usos de `Json.`,
  zero `ObjectMapper`/serialização manual ✓.
- Ausências = erro explícito em toda a cadeia (SLA, taxa de urgência,
  pós-corte, fórmula manual, prova não ligada) ✓.
- **A3 [BAIXO] ressalva:** `cut.extra_forbidden_rules` e
  `cut.business_hours` são REGISTRADAS na V9_2 mas não têm consumidor
  no código: um tenant que aceite vedações adicionais da ERI não terá
  efeito nem erro. O relatório não afirma que são aplicadas (não é
  declaração falsa), mas registrar sem consumir é o tipo de lacuna que
  a integração/dono precisam decidir: implementar o formato (hoje
  inexistente nas fontes — não inventar) ou documentar como catálogo
  futuro. Já `cut.forbidden_window` ficar fora do `loadParameters` está
  CORRETO: os perfis o fixam em `FEDERAL_FLOOR_14_015`, que é a camada
  travada que o `CutoffCalendar` implementa.

## Correções aplicadas nesta revisão (4)
- **C1** `CutoffPorts.ConnectionEffectPort` recebe a `Connection` da
  transação do motor; `CutoffService` a repassa (ver item 5).
- **C2** `CutoffService.revalidate`: cancelamento pelo motor antes do
  registro da falha (ver item 5).
- **C3** `CutoffService.createCutoffOrder`: pré-validação da referência
  da ordem judicial antes de criar a OS (ver item 5).
- **C4 [BAIXO]** `CutoffService.registerCompensation`:
  `detectedAt == null` morria como `NullPointerException` ao montar o
  JSON da base, contrariando o princípio "ausência = erro explícito" da
  própria entrega; agora é `IllegalArgumentException` nomeando o dado.

## Defeitos abertos / pontos para a integração fechar
- **I1.** Compilação e JDBC reais (sem JDK aqui): primeiro olhar no
  `setObject(LocalDate)`/`getObject(…, LocalDate.class)` e nos novos
  usos de `context.connection()` na rodada de CI.
- **I2.** Montar o `CalendarData` na integração: ler `cal.holidays`
  (separando HOLIDAY de OPTIONAL_POINT, todas as esferas da localidade
  da unidade), o `stateCode` da unidade e converter o instante para o
  `tenant.timezone` (P0.19/PLT-14) antes de chamar as regras — nenhum
  loader disso existe na entrega.
- **I3.** Ligar as portas: prova da P3.5 (`DeliveryProofPort`), efeito
  de situação do registry/P1.3 (`ConnectionEffectPort`, agora com a
  conexão do motor), renderização da P2.30, evento de pagamento do
  billing (P2.5) e evento de leitura do metering;
  `declareTypes` + `ensureRevalidationCloseReason` uma vez por tenant.
- **I4.** Teste de serviço do fluxo de fiscalização (A2) e da
  revalidação ponta a ponta (falha ⇒ OS `CLOSED_NOT_EXECUTED` com
  `CUT_REVALIDATION_FAILED`).
- **A1/A3** acima: decisão do dono sobre débitos jovens na seleção e
  sobre os parâmetros registrados sem consumidor.
- Ordem de criação × registro: `createCutoffOrder`/`onPayment` criam a
  OS no motor antes de gravar o vínculo (transações separadas, padrão
  do motor); uma falha no INSERT deixa OS sem vínculo — risco já
  inerente ao desenho, mitigado em C3 para o caso judicial; a
  integração deve tratar o caso geral (reconciliação por
  `service_order_id`).

## Declarações falsas encontradas
- Nenhuma declaração falsa no relatório da entrega. Duas afirmações
  estavam OTIMISTAS demais e foram corrigidas no código, não no texto:
  a transacionalidade do efeito (C1) e a garantia de cancelamento da
  revalidação (C2). A contagem "61 casos" confere (6+8+5+5+8+7+9+8+5);
  a autorrevisão anterior (4 correções) é real e não regride em nada
  do que foi conferido.

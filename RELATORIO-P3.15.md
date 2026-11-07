# Relatório — P3.15 "Central Ziip completa" (ADR-0018, ADR-0025)

**Aceite (literal):** "Abas Arrecadação, Pix, LGPD, Regulatório,
Infraestrutura com SLO, Radar e Suporte; a aba Regulatório lê a
biblioteca de normas da P1.34, com situação, versões, vigência futura
e perfil por agência; falha provocada alerta; kit de evidências com
hash na trilha; relatório mensal por contrato de disponibilidade
(meta de 99,9% no nível Setor Público, medida a partir do lançamento)
e de aderência por severidade, com o cálculo da multa; aba Integrações
(ver P3.37); aba Estoque (ver ED-58); agenda de obrigações do tenant
(ver ED-98)".

**Nenhum push foi feito** (entrega local). **Nenhum teste rodou nesta
VM** (sem JDK/Maven); conferência estática (chaves/parênteses
balanceados em 12 arquivos Java, imports só do projeto/JDK, casos
recalculados em Python). A prova é a CI total no PC do dono.

**Revisão independente: REPROVADO → CORRIGIDO.** Um revisor reprovou o
pacote com 2 bloqueantes, 5 médios e 3 baixos; todas as correções
estão na seção "Correções pós-revisão" ao final deste relatório, com
a justificativa de cada teste refeito (nenhuma asserção afrouxada —
as mudanças de expectativa são mudanças de SEMÂNTICA decididas pelas
fontes já entregues: P2.15, P1.34/V9_8 e P3.11/V9_1).

## Migration: NENHUMA (decisão fundamentada)

O §3 do plano v3.0 não reserva versão Flyway para a P3.15 (conferido:
nenhuma linha da tabela cita a P3.15). Seguindo a instrução da tarefa,
**não inventei número**: a entrega é leitura/agregação sobre o que já
existe — métricas e watchdog da core V9_1 (P1.17), catálogo de normas
da V9_8 + R__ da P1.34, alvos de SLA da V10_2 (P2.15). Se a integração
quiser persistir as visões/relatórios, isso pede reserva própria de
quem fizer a fiação, não desta tarefa.

## Arquivos (14, todos NOVOS — nenhum arquivo de outra tarefa alterado)

Pacote novo `platform/ziipsan-platform/.../platform/central/complete/`
(não toca o `CentralTab` da P2.11; a unificação dos dois registros de
abas é da integração — ver pendência 4):

- `CentralCompleteTabs.java` — registro das 7 abas próprias + as 3
  remissões como fronteira (`Boundary.REFERRAL`).
- `CentralSnapshots.java` — instantâneos agregados das abas
  Arrecadação (P3.1), Pix (P3.2), LGPD (P3.10) e Suporte (S1–S5 da
  V10_2), com validações (negativo/incoerente = erro explícito).
- `RegulatoryLibrary.java` — projeção de leitura da biblioteca da
  P1.34 FIEL à V9_8: situação (4 valores do CHECK), data de efeito DA
  NORMA anulável, edições/versões, vigência futura, transição
  VIGENCIA_FUTURA→VIGENTE pela regra única do
  `NormEffectivenessService` (data da norma, sem `effective_to`),
  filtro classe/área/módulo (token inteiro)/situação DERIVADA/agência
  e perfil por agência com obrigatórias derivadas da classe
  REGULATORY (não desativáveis — mecanismo do core, ver correções).
- `EvidenceKit.java` — kit de evidências: manifesto canônico só via
  `Json` do core, SHA-256 como identidade, `TrailEntry`
  (CENTRAL_EVIDENCE_KIT_GENERATED, hash validado como SHA-256 de 64
  hex + ator + instante).
- `MonthlyContractReport.java` — relatório mensal por contrato:
  disponibilidade (99,9% Setor Público, desde o lançamento,
  denominador sem manutenção planejada) e aderência/multa AGREGANDO
  a semântica já entregue da P2.15 (nenhuma fórmula nova — ver
  correções B1).
- `CentralCompleteAlerts.java` — falha provocada avaliada contra a
  regra ÚNICA existente `central.collector.stale` (V9_1/P3.11,
  RB-02) recebida como entrada — nenhuma regra nova; visão Infra+SLO
  reaproveitando `BurnRateCalculator` da P3.11 com tier derivado do
  burn calculado, e o Radar (vencimentos ordenados, janela do
  chamador).
- `CentralCompleteService.java` — visões das SETE abas sobre os
  instantâneos (incl. Infraestrutura e Radar — correção M3).
- Testes (5 classes, 41 casos): `MonthlyContractReportTest` (12),
  `RegulatoryLibraryTest` (9), `EvidenceKitTest` (5),
  `CentralCompleteAlertsTest` (8), `CentralCompleteServiceTest` (7).
- Docs: `docs/central/central-completa.md`,
  `docs/operacao/runbooks/RB-09-falha-provocada-central.md`
  (RB-01..06 são da P3.11, RB-07/08 da P2.11).

## Aceite — item a item

| # | Item | Estado |
|---|---|---|
| 1 | Abas Arrecadação, Pix, LGPD, Regulatório, Infraestrutura com SLO, Radar e Suporte | ✅ registro + visões de serviço entregues; renderização frontend e coletor são fiação de integração (pendência 3) |
| 2 | Regulatório lê a biblioteca da P1.34: situação, versões, vigência futura, perfil por agência | ✅ `RegulatoryLibrary` sobre os eixos da consulta real da P1.34; testes de filtro, token inteiro, vigência futura, transição com data simulada e perfil por agência |
| 3 | Falha provocada alerta | ✅ medição simulada avaliada pela regra ÚNICA existente `central.collector.stale` (V9_1/P3.11, RB-02; RB-09 é o procedimento do exercício); testes de disparo, fronteira 900 s (não dispara) e 901 s (dispara) e de que a avaliação usa o limiar/comparador da regra recebida. O disparo ponta a ponta no ambiente é exercício da P3.11 na integração |
| 4 | Kit de evidências com hash na trilha | ✅ manifesto + SHA-256 + `TrailEntry`; a gravação no `audit_log` é fiação (pendência 3); hash conferido à mão (abaixo) |
| 5 | Relatório mensal por contrato: disponibilidade 99,9% Setor Público desde o lançamento | ✅ com recorte pelo lançamento e erro explícito para mês pré-lançamento; premissa da manutenção planejada declarada (pendência 1) |
| 6 | Aderência por severidade com cálculo da multa | ✅ semântica ÚNICA da P2.15 agregada por severidade/contrato: aderência = no prazo/(no prazo + estouros), só resolvidos COM prazo; multa = soma por chamado estourado de mensalidade × `penalty_pct` (V10_2), mesmo com aderência na meta; casos à mão abaixo. Teto de multa: nenhum nas fontes → nenhum aplicado (pendência 2) |
| 7 | Aba Integrações (ver P3.37) | ✅ fronteira declarada — NÃO implementada (a P3.37 depende desta) |
| 8 | Aba Estoque (ver ED-58) | ✅ fronteira declarada — NÃO implementada |
| 9 | Agenda de obrigações do tenant (ver ED-98) | ✅ fronteira declarada — NÃO implementada; registrada sem rota de Central (é tela do tenant) |

## Casos calculados à mão (nos testes, recalculados em Python)

- **Disponibilidade** (mês de 30 dias = 43.200 min; orçamento de erro
  de 99,9% = 43,2 min): 60 min fora → 99,8611% (abaixo); 43 min →
  99,9005% (acima); 44 min → 99,8981% (abaixo); lançamento em 16/11 →
  mede 15 dias = 21.600 min. Com manutenção planejada (M4): 600 min
  planejados saem do denominador → 42.600; 44 min não planejados →
  42.556/42.600 = 99,8967% (abaixo).
- **Aderência/multa — semântica P2.15** (mensalidade 1.000.000 ¢ =
  R$ 10.000,00): S1 19 no prazo + 1 estouro → denominador 20 →
  95,00% < meta 99%; multa = 1 × 10% = 100.000 ¢ (R$ 1.000,00). S2
  49 + 1 → 98,00% = meta 98%, MAS 1 estouro → multa 1 × 5% = 50.000 ¢
  (é a divergência da fórmula antiga, que daria 0). S2 40 + 10
  estouros → 80,00%; multa = 10 × 5% × R$ 10.000,00 = R$ 5.000,00 =
  500.000 ¢. S3 8 + 2 estouros + 90 SEM prazo → denominador 10 →
  80,00% (não 8,00%); multa 2 × 2% = 40.000 ¢. S3 sem nenhum com
  prazo → aderência indefinida (Optional vazio), multa 0.
- **Kit**: SHA-256("") = e3b0c442…b855 (valor público); manifesto de
  1 item com esse hash → SHA-256 do manifesto = a5a242eb…f85d,
  calculado com hashlib sobre a string exata que o teste espera do
  `Json` do core.
- **Burn rate** (reuso P3.11): 250 ruins/100.000, SLO 99,9% → 2,5.
- **CSAT**: soma 45/10 respostas = 4,50; sem respostas → média vazia.

## Pendências honestas

1. **Manutenção planejada fora do denominador** é premissa DESTA
   entrega (prática usual de SLA), não texto lido do contrato-modelo:
   conferir contra a minuta do nível Setor Público (P3.9/ED-05) na
   integração; se o contrato contar a manutenção no denominador,
   basta passar `plannedMaintenanceMinutes = 0` — a fórmula não muda.
2. **Teto de multa**: nenhuma fonte local fixa teto; a multa é a soma
   simples dos `penalty_pct`. Se o contrato tiver teto, ele é dado do
   contrato e entra como parâmetro novo — não chutei percentual.
3. **Fiação** (padrão das frentes): coletor que monta os instantâneos
   dos agregados reais, frontend das abas, gravação do `TrailEntry`
   no `audit_log`, abertura de `alert_event` a partir do sinal e a
   persistência/agendamento do relatório mensal.
4. **Dois registros de abas**: `CentralTab` (P2.11, Faturamento/Fiscal)
   e `CentralCompleteTabs` (esta) coexistem de propósito para não
   alterar arquivo de outra tarefa; a integração deve unificá-los.
5. **Nível STANDARD**: sem veredito de meta de 99,9% (a meta é do
   Setor Público no aceite/ADR-0025); a aderência/multa do STANDARD
   segue os alvos da política dele na V10_2 (lá, STANDARD nasce sem
   multa — `penalty_pct` do contrato manda).
6. **Espelho parcial**: MCP respondeu 429 no meio (ver
   `espelho/LEIA-ME.md`); o conteúdo atual de eventual pacote
   `platform.central` no HEAD além da P2.11 local não foi relido.

## Riscos para a CI

- Depende de P3.11 (`observability`) e P2.11 estarem no HEAD na
  integração (pushadas: P3.11 em bd30c00; P2.11 aguarda push do lote).
  Sem a P3.11, `CentralCompleteAlerts` não compila.
- `EvidenceKitTest` fixa a forma canônica do `Json` do core: se a
  serialização mudar, o teste quebra alto — comportamento desejado,
  mas a integração deve esperar esse acoplamento consciente.
- `RegulatoryLibrary.statusAt` reproduz a regra ÚNICA já entregue da
  P1.34 (`NormEffectivenessService`): data DA NORMA, sem condição de
  edição nem de `effective_to` — risco sanado na correção B2; a
  integração só precisa conferir a projeção contra o catálogo real
  carregado (inclusive a norma real sem `effective_from`).
- Sem JDK nesta VM: compilação/testes não rodaram aqui; prova =
  `scripts/ci.sh` no PC do dono.

## Correções pós-revisão

Revisor independente REPROVOU o pacote original. Cada achado foi
corrigido contra a fonte de verdade indicada, sem afrouxar nenhuma
asserção: onde a expectativa de teste mudou, mudou porque a SEMÂNTICA
decidida pela fonte já entregue mudou — justificativa caso a caso
abaixo. Nenhum arquivo de outra tarefa foi alterado; nenhum push.

### B1 (bloqueante) — Multa/aderência divergentes da P2.15 já entregue

**Fonte:** `SupportSlaService.monthlyReport` (P2.15, espelho da P2.5):
aderência = `resolvedWithinDue / (resolvedWithinDue +
breachedResolution)` — denominador só com resolvidos COM prazo — e
`penaltyEstimate` soma, POR CHAMADO estourado,
`monthlyValue × penalty_pct / 100`.

**O que estava errado:** `MonthlyContractReport` usava denominador =
todos os resolvidos e multa = 1× `penalty_pct` por severidade abaixo
da meta — uma segunda fórmula, inventada nesta tarefa.

**Correção:** `SeverityOutcome` passou a carregar os três contadores
da P2.15 (`resolvedWithinDueCount`, `breachedResolutionCount`,
`resolvedWithoutDueCount`); aderência usa o denominador da P2.15
(denominador zero → indefinida); multa = `estouros × mensalidade ×
penalty_pct/100`, por severidade e no total, INDEPENDENTE de a
aderência estar abaixo da meta (`belowTarget` virou informativo).
`SeverityLine` expõe `breachedResolutionCount`. Javadoc e
`docs/central/central-completa.md` declaram a reconciliação: a P3.15
não cria fórmula nova, agrega a da P2.15 por severidade/contrato.

**Testes refeitos (valores sempre à mão, nunca pela fórmula do
código):**
- `penaltyHandCalculated` → `penaltyPerBreachedTicketHandCalculated`:
  S1 19+1 → 95,00%, multa 1 × 10% × 1.000.000 ¢ = 100.000 ¢ (igual ao
  caso antigo por coincidência de 1 estouro). MUDOU o total: antes
  100.000 ¢; agora 150.000 ¢, porque o S2 da fixture (49+1) gera
  50.000 ¢ de multa por estouro — ver próximo caso.
- NOVO `adherenceAtTargetStillPaysPerBreach` (o caso da divergência):
  S2 49 no prazo + 1 estouro → denominador 50 → aderência 98,00% =
  meta 98% (`belowTarget == false`) e multa = 1 × 5% × 1.000.000 ¢ =
  50.000 ¢ > 0. Pela fórmula antiga seria 0.
- `penaltySumsSeverities` → `penaltyTenBreachesHandCalculated`: S2
  40 + 10 estouros → 80,00%; multa = 10 × 1.000.000 × 5% = 500.000 ¢
  = R$ 5.000,00 (conferido: 10 × R$ 10.000,00 × 5% = R$ 5.000,00).
  Antes o mesmo cenário esperava 50.000 ¢ (1×); total antes 150.000 ¢,
  agora 600.000 ¢ (100.000 do S1 + 500.000 do S2).
- NOVO `denominatorExcludesResolvedWithoutDue`: S3 8 + 2 estouros +
  90 sem prazo → denominador 10 → 80,00% (pela fórmula antiga, com
  denominador 100, seria 8,00%); multa 2 × 2% = 40.000 ¢.
- Fixture `outcomes(...)`: as chamadas ganharam o 3º contador
  (sem prazo = 0 nos cenários antigos) e o S4-base virou 20+0 (o
  antigo "19/20 = meta → 0" só era 0 na fórmula antiga; sob a P2.15
  19+1 teria 1 estouro e multa de 10.000 ¢ — para manter o cenário
  "S4 sem multa" honesto, o desfecho-base do S4 passou a ser sem
  estouro, e a aderência esperada dele é 100,00%).

### B2 (bloqueante) — RegulatoryLibrary não representava o catálogo real da P1.34 (V9_8)

**Fontes:** V9_8 (`effective_from`/`effective_to` ANULÁVEIS em
`cert_standard` E em `cert_standard_edition`; CHECK só compara as
datas quando ambas existem), carga da P1.34 (data curada no nível da
NORMA, uma edição por norma; dos 14 itens em vigência futura, 1 —
`nt-2026-001-rtc-nfag` — ficou com `effective_from` NULL) e
`NormEffectivenessService` (regra única: `status='VIGENCIA_FUTURA'
AND effective_from IS NOT NULL AND effective_from <= hoje`, sem
condição de `effective_to`).

**O que estava errado:** `NormEdition` recusava `effectiveFrom`
null (quebrava a projeção real) e `statusAt` decidia pelas EDIÇÕES
exigindo `effective_to` vigente — regra que não existe no core.

**Correção:** `NormEntry` ganhou `effectiveFrom`/`effectiveTo` DA
NORMA (anuláveis, aceitos sem erro); `NormEdition` aceita datas
anuláveis e só valida `effective_to >= effective_from` quando ambas
existem; `statusAt` reproduz a regra real sobre a data da norma, sem
`effective_to` — norma sem data permanece `VIGENCIA_FUTURA`.

**Testes refeitos/novos:** `statusTransitionAtSimulatedDate` →
`statusTransitionBoundaryOnNormDate` (fronteira exata: 01/02/2027 →
VIGENTE; 31/01/2027 → VIGENCIA_FUTURA — antes a transição era
provada por uma edição antiga, não pela data da norma); NOVO
`undatedNormStaysFutureWithoutError` (norma com `effective_from`
NULL — inclusive edição sem datas — é aceita, permanece
VIGENCIA_FUTURA até em 2030 e é consultável); NOVO
`editionEffectiveToBoundary` (b1); fixtures reprojetadas para a
assinatura fiel (data da norma + uma edição).

### M1 (médio) — "Obrigatórias não desativáveis" por booleano solto

**Fontes:** não há coluna `mandatory` na V9_8/P1.34; no core,
`CertificationService.deactivate` recusa norma REGULATORY e o CHECK
`chk_cert_activation_regulatory_never_inactive` da tenant V21_1
proíbe REGULATORY ficar INACTIVE.

**Correção:** o campo `boolean mandatory` sumiu; `NormEntry.mandatory()`
DERIVA de `standardClass == REGULATORY`. A fixture que contradizia
(`currentNorm`: REGULATORY com `mandatory=false`) foi corrigida pela
própria derivação. `profileForAgency(agency, excludedCatalogKeys)`
desenha a operação honestamente: excluir não obrigatória funciona;
tentar excluir REGULATORY é recusado — ela PERMANECE no perfil e a
tentativa é sinalizada em `AgencyProfile.retainedMandatoryKeys`;
chave fora do perfil é erro explícito. `central-completa.md`
reescrito para dizer a verdade sobre o mecanismo (classe →
`deactivate` + CHECK da V21_1), sem falar em booleano.

**Testes:** `agencyProfile` → `mandatoryDerivesFromClass` +
`regulatoryCannotBeExcludedFromProfile`. MUDOU a expectativa do
perfil da ANA: obrigatórias antes 1 (só a que a fixture marcava
`true`); agora 2 (`nr-exemplo-futura` e `lei-exemplo-vigente`, ambas
REGULATORY) — a expectativa antiga media o booleano errado, não a
regra do core. A fixture ganhou uma norma VOLUNTARY para provar que
a exclusão permitida continua funcionando.

### M2 (médio) — Falha provocada com regra duplicada

**Fontes:** V9_1 já semeia `central.collector.stale`
(`platform.collector.last_run_age_s > 900`, CRITICAL) e a P3.11 já a
cataloga (RB-02 em `alert-rules.json`).

**O que estava errado:** `CentralCompleteAlerts` criava uma SEGUNDA
regra idêntica (`central.complete.probe_stale` → `alert_event`
duplicado na integração) e `evaluateProbe` não lia `AlertRule`
nenhuma: `> 900` hardcoded e o caminho `success == false` disparava
com coleta fresca — sinal e regra descrevendo condições diferentes.

**Correção:** regra duplicada REMOVIDA. `collectorStaleRule()` apenas
referencia a regra existente (mesma chave/métrica/comparador/limiar
e runbook RB-02 do catálogo da P3.11; RB-09 virou o procedimento do
exercício sobre ela). `evaluateCollectorStale(rule, probe)` avalia
USANDO comparador e limiar da regra recebida e recusa regra de outra
métrica. "Falha provocada" = medição simulada (idade velha e/ou
sucesso falso) alimentando essa avaliação única: sucesso falso com
coleta fresca NÃO dispara mais.

**Testes refeitos:** `provokedFailureFires` →
`provokedFailureFiresThroughExistingRule` (medição simulada: sucesso
falso + idade 3.600 s → dispara pela `central.collector.stale`);
`staleCollectorBoundary` mantido e agora contra a regra recebida
(900 não dispara / 901 dispara); NOVO `freshCollectionNeverFires`
(fresca sã não dispara; sucesso falso fresco também não — expectativa
INVERTIDA em relação ao código antigo, justificada: era exatamente o
caminho divergente apontado pelo revisor); `ruleUsesP311Gate` →
`referencedRuleIsTheExistingOne` (chave/runbook agora os da regra
real: `central.collector.stale` + RB-02, não a duplicada + RB-09);
NOVO `evaluationUsesRuleDefinition` (regra de entrada com limiar 60
dispara em 61 e não em 60 — prova de que nada está hardcoded).

### M3 (médio) — Abas Infraestrutura e Radar sem visão de aba

**Correção:** `CentralCompleteService` ganhou `InfraTabView`/
`infrastructureTab(...)` (vinculada a `INFRAESTRUTURA`) e
`RadarTabView`/`radarTab(...)` (vinculada a `RADAR`) — as 7 abas têm
visão. `infraSlo` foi reprojetado: recebe eventos das janelas curta
e longa e CALCULA os dois burn rates pela `BurnRateCalculator` da
P3.11; o tier deriva dos burns CALCULADOS (antes vinha de doubles
soltos do chamador, sem asserção).

**Testes:** `infraSloReusesBurnRate` → `infraSloBurnAndTierDerived`
(mesmos números à mão da P3.11: 250/100.000, SLO 99,9% → burn 2,5;
NOVO: tier assinalado = `TICKET_SLOW`, pois tierFor(2,5; 2,5) cai em
longa ≥ 1 e curta < 6) e NOVOS `infrastructureTabView`/`radarTabView`
no `CentralCompleteServiceTest` asserindo aba, burn e tier/ordem.

### M4 (médio) — Manutenção planejada fora do denominador sem teste

**Correção:** NOVO caso `plannedMaintenanceExcludedFromDenominator`,
calculado à mão: mês de 43.200 min, 600 min de manutenção planejada,
44 min não planejados → denominador 42.600 → disponibilidade =
(42.600 − 44)/42.600 = 99,8967% (4 casas, HALF_UP), abaixo de 99,9%.
E o `Report` ganhou o campo explícito `availabilityDenominatorNote`
(constante `AVAILABILITY_DENOMINATOR_NOTE`: "Denominador da
disponibilidade EXCLUI a manutenção planejada…"), assinalado no
teste. (A fórmula já subtraía a manutenção; faltavam o teste e a
nota — a pendência 1, de conferir a premissa contra a minuta da
P3.9/ED-05, continua valendo.)

### M5 (médio) — query() ignorava statusAt

**Correção:** `RegulatoryLibrary.query(filter, reference)` filtra a
situação pela DERIVADA em `statusAt(norm, reference)`, consistente
com a leitura da aba; `CentralCompleteService.regulatoryTab` passa a
sua referência à consulta.

**Teste:** `queryByStatusClassAgency` → `queryUsesDerivedStatus`:
norma gravada `VIGENCIA_FUTURA` com data da norma 01/10/2026 é
encontrada pelo filtro `VIGENTE` em 07/11/2026 e NÃO pelo filtro
`VIGENCIA_FUTURA` (pela situação gravada, seria o contrário).

### Baixos

- **b1** — Fronteiras de transição cobertas: `effective_from == hoje`
  → VIGENTE e véspera → VIGENCIA_FUTURA (em B2); `effective_to`
  continua na projeção das edições e da norma, então
  `editionEffectiveToBoundary` cobre `effective_to == effective_from`
  aceito (CHECK da V9_8), `effective_to` anterior recusado e a prova
  de que `statusAt` NÃO usa `effective_to` (norma com `effective_to`
  já passado e `effective_from` chegado lê-se VIGENTE).
- **b2** — `EvidenceKit.TrailEntry` valida `kitSha256` com o mesmo
  rigor de `EvidenceItem` (`[0-9a-f]{64}`); NOVO teste
  `trailEntryValidatesKitHashStrictly` (curto, maiúsculo e nulo
  recusados; hash válido aceito).
- **b3** — `RadarItem` exige `tenantId` e `detail` não nulos (NOVO
  teste `radarItemRequiresTenantAndDetail`); `MonthlyContractReport
  .Report.bySeverity` trocou `Map.copyOf` (perde ordem) por cópia em
  `LinkedHashMap` não modificável — NOVO teste
  `bySeverityPreservesOrder` assere a iteração S1–S5.

### Saldo de testes

28 → 41 casos (MonthlyContractReport 8→12, RegulatoryLibrary 6→9,
EvidenceKit 4→5, CentralCompleteAlerts 5→8, CentralCompleteService
5→7). Conferência estática refeita: chaves/parênteses balanceados
nos 12 arquivos Java, nenhum identificador da API antiga restando
(`PROBE_RULE_KEY`, `provokedFailureRule`, `evaluateProbe`, campo
`mandatory`, `query` de 1 argumento), valores recalculados em Python.
Sem JDK nesta VM: a prova continua sendo a CI total no PC do dono.

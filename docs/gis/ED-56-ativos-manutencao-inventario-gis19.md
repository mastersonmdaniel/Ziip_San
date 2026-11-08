# ED-56 — Ativos, manutenção de rede e inventário regulatório (GIS-19)

Pacote novo `br.com.ziipsan.intelligence.gis.asset` no módulo
`intelligence/ziipsan-gis` (retrato + portas, sem JPA no núcleo —
convenção das ondas GIS). Nenhum arquivo de vizinho é alterado.

## O que a GIS-19 acrescenta sobre as dependências executadas

- **P1.35** é a dona da entidade `asset` e da hierarquia de seis
  níveis. A ED-56 modela o retrato completo do inventário
  (`AssetInventoryModel`): ficha técnica, vida útil, custo
  histórico, bem compartilhado/reversível, criticidade e ciclo de
  vida (lidos pela ED-72), e valida pai/nível/sem ciclo.
- **ED-54 (GIS-11)** consome a operabilidade da válvula como fato de
  entrada (pendência P-ED54-05). O programa de exercício da OPE-09
  (`ValveHydrantProgram`) é quem PRODUZ esse dado, pela porta
  `ValveOperabilityPort`.
- **ED-36** é o único caminho de OS (`WorkOrderPort`, por
  finalidade); **ED-32** é a dona do de-para MCRSB consumido pela
  conciliação (`AccountingPort`, GES-14); **ED-53** nomeia as
  instalações (ETA/ETE/elevatória/reservatório) cujos equipamentos
  não lineares entram no plano preventivo.

## Regras duras aplicadas (nenhum dado inventado)

| Tema | Tratamento |
|---|---|
| Vida útil | Sem fábrica. Só do perfil de agência conferido na tabela original do MCPSB (`McpsbCodes.UsefulLifeProfile`); sem perfil/par, fica ausente. Pendência P-ED56-01. |
| Lista dos 19 códigos `nts_sds` | [SEM FONTE] nesta VM: a conferência exige a lista entregue pela fiação a partir da tabela original; sem lista, acusa `[SEM FONTE]`. P-ED56-01. |
| Tamanho de `up` (2 ou 3) | A P1.35 manda conferir no manual antes da migration: aceitos 2 ou 3; pendência P-ED56-01. |
| UP/UAR por classe de equipamento | Mecanismo de correspondência entregue; valores [A DEFINIR] até a conferência no MCPSB. P-ED56-01. |
| Catálogo completo de `ALVOS-DA-OS-E-HIERARQUIA-DE-ATIVOS` | O documento não está nas fontes locais: o catálogo entregue contém só as classes nomeadas nas specs executadas (bomba, motor, painel, válvula, hidrante, hidrômetro, instrumento, edificação, estrutura de reservatório), cada uma com fronteira ISO 14224; o restante entra por carga sobre o mecanismo. Frota fora (ED-59). P-ED56-02. |
| Periodicidades de PM (tempo/uso) | Do plano do tenant em cada linha; nenhuma semeada. |
| `maint.pm_generation_lead_days`, `maint.pm_overdue_tolerance_days`, `maint.checklist_templates`, `maint.hydrant_test_months`, `maint.condition_triggers`, `inv.critical_spares` | Sem `factory_default` (NULL no SQL do core). Comportamento sem parâmetro declarado no Java (lead/tolerância zero, sem checklist, sem vencimento de hidrante, sem gatilho). |
| `maint.valve_exercise_years_by_class` | Único com fábrica, porque o aceite fixa a referência AWWA: CRITICAL=1 ano, demais=5 anos. |
| Padrão CCTV (PACP × WRc) | `asset.cctv_standard` [A DEFINIR]: confirmar com cliente e responsável técnico; sem ele, a importação é recusada. O parser PACP lê o formato de transferência textual delimitado; layout proprietário de fabricante não é inventado. |
| Pontuação do trecho CCTV | Regra DECLARADA do produto (pior grau × 20 + média × 2, teto 100) — nenhuma fonte fixa fórmula oficial. |
| Escala de risco | Declarada do produto (1..5 × 1..5); probabilidade = grau de condição + 1 a cada 2 rompimentos recentes (teto 5); sem grau, o ativo sai do plano com `SEM_GRAU_DE_CONDICAO`, nunca com probabilidade presumida. |
| Formato de agência sem fonte | Inventário regulatório segue os formatos fixados pela P1.35/MCPSB; formato adicional de agência = [SEM FONTE] + mecanismo (os códigos são dados do ativo; nenhum layout novo é inventado). |

## Superfície

- API: `/api/v1/gis/assets` (`gis:view`/`gis:edit`) — inventário,
  espaços confinados (NR-33, leitura da P1.35), vencimentos do PM,
  conciliação e plano de renovação.
- SQL pendente (sem número, regra da cascata):
  `ED-56__gis_assets_maintenance.sql` (tenant GIS, 7 tabelas com
  RLS — correcao D9 da revisao da ED-56: eram anunciadas 6, sao 7)
  e `ED-56__core_maintenance_parameters.sql` (core, 8
  parâmetros + limite de valores do `asset.cctv_standard`).
- Os 14 valores da P3.9 não são tocados.
- GES-02 (instrumento contratual regulatório) fica na ED-96.

## Correções da revisão (D1–D9, ver RELATORIO-CORRECAO-ED-56)

- **Operabilidade com frescor (D3):** a ED-54 recebe o retrato
  tri-estado `OPERABLE`/`NOT_OPERABLE`/`UNKNOWN` +
  data do último exercício (`ValveOperabilitySnapshot`, via
  `ValveHydrantProgram.assessOperability`). Válvula com exercício
  vencido pela régua `maint.valve_exercise_years_by_class`
  (AWWA/tenant) ou nunca exercitada sai `UNKNOWN` — nunca o último
  `operable=true` velho. Nenhum prazo novo foi inventado.
- **Fronteira ISO 14224 (D4):** `boundaryOverlaps` examina de
  verdade as fronteiras — código `(CODIGO)`, código como token e o
  substantivo do rótulo da outra classe, na inclusão × exclusão.
  Limite declarado: sinônimos livres fora do vocabulário dos
  rótulos/códigos do catálogo não são detectáveis neste núcleo; a
  carga do catálogo completo (P-ED56-02) declara as fronteiras
  nesses termos.
- **Superfície (D8):** `RiskView` expõe probabilidade ×
  consequência decompostos; o programa de válvulas/hidrantes tem
  leitura real — `AssetOperations.valveProgram` +
  `GET /api/v1/gis/assets/valve-program`, montado por
  `ValveHydrantProgram.programView`.

## Limites declarados

- PostGIS/PostgreSQL reais: IT `Gis19AssetIT` entregue e NÃO
  rodada nesta VM (P-ED56-03, CI no PC do dono). Os dois SQL
  pendentes, porém, FORAM aplicados nesta VM em PostgreSQL 16.15
  descartável na correção D1 (7/7 tabelas com RLS — prova no
  relatório de correção).
- Ensaio TR-155/TR-1010/SAMAE (SG-1180): registro de cobertura
  entregue; ensaio é externo (P-ED56-04).
- Fiação (beans, leitura do retrato da P1.35, portas ligadas a
  ED-36/ED-32/ED-54 reais, registro dos tipos de serviço no motor
  da P1.31): integração (P-ED56-05).

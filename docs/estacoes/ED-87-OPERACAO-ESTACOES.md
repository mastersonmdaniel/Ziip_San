# ED-87 — Operação de estações sem supervisório: desenho e fiação

Módulo existente `shared/ziipsan-core`, pacote novo
`br.com.ziipsan.core.stationops` (padrão ED-50/ED-92: núcleo puro, sem
JPA/Spring; persistência, controllers, telas e fiação das portas são
da integração).

## Disciplina de fontes (regra dura desta tarefa)

- **Único número normativo com fonte:** 120 min entre registros de
  turbidez por unidade filtrante com filtração rápida — Portaria
  GM/MS 888/2021, art. 28, §3º, como o aceite fixa ("no mínimo a cada
  2 h"). Vive em `StationOpsParameters.TURBIDITY_MAX_INTERVAL_MINUTES`.
- **`ops.round_interval_by_unit`:** a norma fixa a frequência mínima
  e o tenant só reduz o intervalo — mas o número do teto por tipo de
  unidade **não consta das fontes do projeto** `[SEM FONTE]`. Por
  isso o teto é entrada da validação (conferido com a norma aplicável
  na implantação), nunca literal do produto.
- **Percentil mensal de turbidez:** o aceite pede a apuração do
  percentil sem nomear o nível; o nível é parâmetro explícito de
  `TurbidityCompliance.monthlyPercentile` — nenhum nível de fábrica.
- **`ops.shift_pattern`:** turnos do prestador, sem padrão de fábrica;
  ausência = erro explícito.
- **Os 14 valores da P3.9 não são tocados** por esta tarefa.
- Item 32 do DECISOES-DO-DONO (arquitetura tarifária): esta entrega
  não cria cobrança nem toca consumidores de conta — nada a observar
  além de não introduzir cálculo tarifário, o que foi respeitado.

## Componentes

| Classe | Papel |
|---|---|
| `StationOpsParameters` | `ops.shift_pattern` e `ops.round_interval_by_unit` (só reduz, contra teto conferido); teto de 2 h da turbidez com fonte |
| `StationUnit` | Unidade operacional: ETA/ETE/elevatória/reservatório, sistema (elo ED-50), filtração e unidades filtrantes |
| `ShiftLog` | Diário por turno: equipamentos (operação/reserva, horímetro, partidas), leituras, ocorrências, passagem de turno que fecha o diário |
| `DiaryReading` | Leitura do diário (nível/vazão/pressão/turbidez/cloro/pH) com a chave determinística da série da ED-17 |
| `StationOpsService` | Abre o diário no turno do padrão, grava cada leitura na série da ED-17 com origem `MANUAL`, ocorrência → OS da P1.31 uma única vez |
| `StationOpsPorts` | Portas: série da ED-17, núcleo de OS da P1.31, alerta de ronda |
| `RoundRoute` / `RoundExecution` | Roteiro com checklist ordenado; execução com respostas item a item (conclusão exige os obrigatórios) |
| `RoundSyncService` | Formulário offline: a execução se completa no aparelho e sincroniza uma vez (`clientExecutionKey`); reenvio = `DUPLICATE` |
| `RoundCoverage` | Apuração por slots do intervalo vigente; slot vencido sem conclusão = ronda não realizada + alerta |
| `TurbidityCompliance` | Intervalos ≤ 2 h por filtro e percentil mensal (nearest-rank, nível explícito) |
| `ProcessControlFeed` | JOR-21: conversão única diário → amostras `PROCESS_CONTROL`/resultados da ED-50, sem redigitação |
| `StationTargetRegistration` | Verificador do alvo `OPERATIONAL_SITE` da P1.31 sobre `ops_station_units` |

## Tabela de fiação (integração)

| Porta / consumidor | Ligação esperada |
|---|---|
| `ManualSeriesPort` | Repositório de `supervisory_samples` da ED-17, gravando `origin = 'MANUAL'` (coluna do bloco DO do SQL desta entrega) e dedupe pela `sampleKey` |
| `WorkOrderPort` | Motor de OS da P1.31 com alvo `OPERATIONAL_SITE` = id da unidade; registrar `StationTargetRegistration` na inicialização do tenant |
| `RoundAlertPort` | Canal de aviso do prestador (mensageria); o alerta nasce na apuração de cobertura e nas execuções vencidas |
| ED-50 (JOR-21) | `ProcessControlFeed.toEntries(log, pointId, catalogo, validador)` — a fiação resolve unidade → ponto de saída da ED-50; as entradas entram no `MonthlyClosing` e no `SisaguaExport` da ED-50 |
| ED-105 | Volumes e paradas do diário são lidos daqui (aceite da ED-105) pelas tabelas `ops_shift_logs`/`ops_diary_readings` |
| Agendamento | Abertura de execuções de ronda por slot e a apuração de cobertura rodam no worker (P1.9) com o intervalo vigente |

## Fora de escopo (mandado dos aceites vizinhos)

Supervisório/telemetria = ED-17 (aqui só se grava a série manual
nele); qualidade/conformidade/SISAGUA = ED-50 (aqui só se alimenta,
sem redigitar); insumos químicos e limpeza de reservatórios = ED-89;
efluente da ETE e lodo = ED-105 (lê volumes/paradas deste diário);
gestão de pressão = ED-86.

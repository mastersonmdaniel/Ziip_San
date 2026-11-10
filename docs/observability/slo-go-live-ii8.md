# SLOs de go-live — desenho e método de baseline (II-8)

**Pacote:** II-8 da Onda II do PDCA-01 (decisão 64). **Herança:** o
catálogo da P3.11 (`slo-catalog.md`, `BurnRateCalculator` com limiares
multi-janela 14,4/6/3/1 do SRE Workbook) e a LC.7 (SLOs/burn em
produção gerenciada). Este documento define os 4 SLOs do go-live
pedidos pelo PDCA-01 e o MÉTODO de baseline sobre a base fictícia de
12.000 UCs. **Todos os alvos numéricos ficam `[A DEFINIR]`** até haver
baseline real medida — nenhum alvo é inventado neste pacote
(pendência P-II8-ALVOS).

## 1. Os quatro SLOs

| # | SLO | SLI (definição) | Fonte do sinal | Alvo | Janela |
|---|---|---|---|---|---|
| SLO-1 | Disponibilidade da API | requisições de negócio sem erro de servidor (2xx+3xx+4xx) / total, derivado de `http.server.requests` por `status` — definição herdada da P3.11, marcada PROPOSTA lá até à decisão de modelagem (P-LC7-04) | Actuator/Prometheus (P0.8) | `[A DEFINIR]` | 30 dias (padrão P3.11) |
| SLO-2 | Latência da API | p95 de `http.server.requests` por `route_group` (sem rótulo de tenant — P0.8) | Actuator/Prometheus (P0.8) | `[A DEFINIR]` (o ≤ 800 ms da P3.11 é PROPOSTA local dela, não alvo deste pacote) | 30 dias |
| SLO-3 | Conclusão do lote de faturação | lotes de release concluídos íntegros (`BatchReport.complete()`) / lotes executados; e duração do lote como sinal de acompanhamento | `BillingBatchReleaseProof` (II-8): relatório por execução + `ziipsan_billing_batch_*` | `[A DEFINIR]` | por ciclo de faturação |
| SLO-4 | Sincronização de campo | itens de sync com outcome=`SUCCESS` / total de itens de sync | `ziipsan_field_sync_items_total{outcome}` (II-8) | `[A DEFINIR]` | 30 dias |

Burn rate e orçamento de erro: os da P3.11, sem alteração —
`BurnRateCalculator` (platform) com os limiares 14,4 (PAGE_FAST),
6 (PAGE_SLOW), 3 (TICKET_FAST), 1 (TICKET_SLOW). Janela sem eventos é
erro explícito no cálculo, nunca burn zero.

## 2. Método de baseline (como os alvos nascem — nunca antes da medida)

1. **Massa de referência:** a base fictícia de 12.000 UCs (decisão 51,
   mantida indefinidamente), tenant fictício
   `11111111-2222-4333-8444-555555555555`, totais conhecidos do
   manifesto dela: 12.000 UCs, **11.300 contas** (linhas em `bills` e
   `bill_items`), 11.300 registos de consumo, 9.150 leituras, 2.550
   débitos, repartição 9.000/3.000 pelos dois municípios fictícios.
   O subconjunto faturável do lote = as 11.300 contas (as 700 UCs sem
   conta — S16 suspensas (300) + S19 provisórias (400) — não faturam,
   pela própria matriz da base).
2. **Execuções:** correr o harness do lote (`BillingBatchReleaseProof`
   sobre o motor real no ambiente de release; mecanicamente, a
   conferência integral do lote sobre a massa — ver o script de PG
   descartável do pacote) **3 vezes** no ambiente candidato a go-live,
   registando por execução: total/sucessos/falhas, duração, ritmo.
3. **Estatística:** baseline = mediana das 3 durações e taxa de
   conclusão observada; SLO-3 propõe-se a partir da mediana com margem
   declarada na proposta — a margem também é decisão de quem ratifica,
   não constante deste pacote.
4. **SLO-1/2/4:** baseline = janela de observação em ambiente de
   go-live (LC.7) com tráfego sintético das sondas + tráfego da
   homologação; p95 e disponibilidade medidos, não estimados.
5. **Ratificação:** os alvos propostos a partir da baseline são
   registados por adendo datado a este documento (e ao catálogo
   `slo-go-live-ii8.yml`), com a baseline anexa. Até lá, `[A DEFINIR]`
   é o estado correcto e declarado (P-II8-ALVOS) — alerta de burn sem
   alvo ratificado não pagina ninguém.

## 3. Prova local deste pacote (o que a baseline local já mediu)

A conferência do lote sobre a massa integral da base 12k correu em PG
16 descartável nesta produção (script e log integral no pacote):
11.300/11.300 contas conferidas item a item (soma dos itens × valores
da conta × fórmula do total da V4_1), com duração registada no
relatório da execução. Isto prova a MECÂNICA da baseline (o harness
corre a massa toda e produz a evidência); **não** é a baseline do
alvo — a máquina local não é o ambiente de go-live, e nenhum número
local é apresentado como alvo nem como medição de produção.

## 4. Pendências

- **P-II8-ALVOS** — alvos numéricos dos 4 SLOs: `[A DEFINIR]` até à
  baseline do §2 em ambiente de go-live (LC.7).
- **P-LC7-04 (herdada)** — modelagem do SLI de disponibilidade/burn no
  catálogo fechado de métricas da Central; sem ela, SLO-1 mede-se por
  derivação no Prometheus, sem regra executável de burn na Central.

# Catálogo de SLOs — P3.11 (PLT-23)

Fonte dos SLOs locais. A disponibilidade de produção (99,9%, nível Setor
Público, ADR-0025) só é medida a partir do lançamento (LC.7/P3.15); na
homologação local os SLOs abaixo servem ao cálculo de burn rate e aos
exercícios de falha — nenhum número local é apresentado como medição de
produção.

> **Marcas de lastro (correção pós-revisão P-6):** item sem fonte
> verificada está marcado **PROPOSTA** na própria linha. PROPOSTA não é
> catálogo vigente: vale como ponto de partida para a revisão trimestral
> ratificar ou substituir, e nenhuma regra executável depende dela.

| SLO | SLI | Meta | Janela | Responsável |
|---|---|---|---|---|
| `api.availability` | **PROPOSTA (definição do SLI, sem lastro):** requisições 2xx+3xx+4xx de negócio / total (5xx é erro), derivadas da métrica real `http.server.requests` (P0.8) pela tag de status | 99,9% (ADR-0025/P3.15) | 30 dias | platform |
| `api.latency.p95` | p95 por grupo de rota (P0.8: sem rótulo de tenant) | ≤ 800 ms (**PROPOSTA** local, a confirmar na revisão trimestral) | 30 dias | platform |
| `central.collector.freshness` | idade da última coleta | ≤ 900 s (regra `central.collector.stale` da V9_1) | contínua | platform |
| `outbox.delivery` | **PROPOSTA (SLI e meta, sem lastro):** mensagens PENDING com idade ≤ 30 min | **PROPOSTA:** 99,9% | 30 dias | platform |

## Reconciliação com a P0.8 (correção pós-revisão P-1)

Conferido contra `metrics-budget.yml` e `metrics-catalog.md` reais
(lidos na revisão cruzada, HEAD `2ba51f8`):

- A métrica HTTP real é **`http.server.requests`** (timer com histograma
  de percentis, componente de 3.000 séries no orçamento de 10.000 do
  serviço `ziipsan-app`), exposta em `/actuator/metrics` e
  `/actuator/prometheus` **autenticados**. `http.server.availability` e
  `http.server.errors` **não existem** — foram retiradas de todo o pacote.
- O SLI `api.availability` só pode ser **derivado** de
  `http.server.requests`; essa derivação é a PROPOSTA da tabela acima e
  exige a decisão de modelagem registrada em
  `alert-rules-pendentes.md` §1 (chave nova em
  `platform.metric_definition` — catálogo fechado de 8 chaves
  `platform.*` na V9_1 — exigiria migration e atualização prévia do
  `metrics-budget.yml`; ou avaliação do burn fora de `alert_rule`).
- Enquanto a decisão não existe, **não há regra executável de burn** em
  `alert-rules.json`: o catálogo executável tem apenas as regras sobre
  métricas reais do catálogo fechado (`central.collector.stale`,
  `platform.outbox.oldest_pending_age_s.high`).
- A regra executável da outbox usa a métrica real
  `platform.outbox.oldest_pending_age_s` > 1800 s; ela **não** é a
  medição do SLO `outbox.delivery` proposto acima.

## Burn rate (cálculo)

Implementado em `br.com.ziipsan.platform.observability.BurnRateCalculator`
(módulo `ziipsan-platform`), com casos calculados à mão no teste:
SLO 99,9%, 250 erros em 100.000 eventos → burn 2,5 e 250% do orçamento
consumido. Janela sem eventos é erro explícito, nunca burn zero.
O cálculo recebe **contagens** (total e ruins) de quem chama — ele não lê
métrica alguma; alimentar essas contagens a partir de
`http.server.requests` depende da derivação proposta acima.
Limiares: ≥14,4 pagina rápido; ≥6 pagina; ≥3 ticket rápido; ≥1 ticket
lento. **Simplificação declarada:** o `tierFor` olha só a janela curta
para os tiers de página e só a longa para os de ticket; o multi-janela
do SRE Workbook exige as duas janelas por tier — a simplificação é
deliberada nesta tarefa e deve ser revista se/quando as regras de burn
voltarem ao catálogo executável.

## Orçamento de erro

Orçamento = (100 − meta)% × eventos da janela. Consumo acima de 100%
congela mudança não essencial no serviço afetado até a próxima revisão
trimestral registrada (ou revisão extraordinária registrada no mesmo
arquivo de revisões) — decisão operacional local desta tarefa, a ser
ratificada pelo dono na primeira revisão trimestral real.

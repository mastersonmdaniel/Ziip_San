# SLOs e burn rate em produção — LC.7

Produtor: sessão `92031a19-4d6d-4174-92b7-164897d85503` (papel: Produtor).

Fonte integral: pacote local da **P3.11** (`docs/observability/
slo-catalog.md`, `alert-rules.json`, `alert-rules-pendentes.md` e o
código `BurnRateCalculator`/`SloDefinition`). Esta LC.7 **não redefine**
SLO algum: ela leva à produção, sem alteração, o que a P3.11 fixou —
incluindo as marcas **PROPOSTA** dela. A disponibilidade de produção
(99,9%, ADR-0025) só é medida a partir do lançamento, como a própria
P3.11 declara.

## 1. Catálogo vigente (herdado da P3.11, sem alteração)

| SLO | SLI | Meta | Janela | Responsável |
|---|---|---|---|---|
| `api.availability` | **PROPOSTA (definição do SLI, sem lastro na P3.11):** requisições 2xx+3xx+4xx de negócio / total (5xx é erro), derivadas da métrica real `http.server.requests` (P0.8) pela tag de status | 99,9% (ADR-0025/P3.15) | 30 dias | platform |
| `api.latency.p95` | p95 por grupo de rota (P0.8: sem rótulo de tenant) | ≤ 800 ms (**PROPOSTA** local da P3.11, a confirmar na revisão trimestral) | 30 dias | platform |
| `central.collector.freshness` | idade da última coleta | ≤ 900 s (regra `central.collector.stale` da V9_1) | contínua | platform |
| `outbox.delivery` | **PROPOSTA (SLI e meta, sem lastro na P3.11):** mensagens PENDING com idade ≤ 30 min | **PROPOSTA:** 99,9% | 30 dias | platform |

Regras desta LC.7 sobre o catálogo:

- Item marcado **PROPOSTA** na P3.11 continua **PROPOSTA** em produção
  até a revisão trimestral de SLO o ratificar ou substituir (rito da
  P3.11, `revisao-trimestral-slo.md` dela). PROPOSTA não vira meta de
  contrato por este pacote.
- Nenhum limiar novo foi criado aqui. Limiar sem fonte = `[A DEFINIR]`
  pela revisão trimestral/dono (pendência P-LC7-04).

## 2. Burn rate em produção

- Cálculo: o mesmo `BurnRateCalculator` da P3.11 (burn = taxa de erro
  observada ÷ taxa permitida; orçamento = taxa permitida × eventos;
  janela sem eventos é erro explícito, nunca burn zero).
- Limiares de alerta (P3.11, SRE Workbook): burn **≥ 14,4** pagina
  rápido; **≥ 6** pagina; **≥ 3** ticket rápido; **≥ 1** ticket lento.
  Simplificação declarada pela P3.11 e mantida: os tiers de página
  olham só a janela curta e os de ticket só a longa.
- **Quem alimenta o cálculo em produção:** as contagens (total/ruins)
  derivadas de `http.server.requests` no serviço gerenciado, via
  coletor OTel deste pacote. A derivação em si é a decisão de
  modelagem pendente da P3.11 (`alert-rules-pendentes.md` §1 dela):
  **enquanto ela não for tomada, não há regra executável de burn**
  nem local nem em produção — esta LC.7 não a toma sozinha
  (pendência P-LC7-04, herdada).
- Orçamento de erro: consumo acima de 100% congela mudança não
  essencial no serviço afetado até a revisão trimestral (regra
  operacional da P3.11, a ratificar pelo dono na 1ª revisão real).

## 3. Alertas executáveis × pendentes (estado herdado, sem maquiagem)

**Executáveis** (catálogo da P3.11, métricas reais do catálogo fechado
da V9_1) — são os que o pipeline de produção avalia desde o dia 1:

| rule_key | Métrica | Limiar | Sev. | Runbook |
|---|---|---|---|---|
| `central.collector.stale` | `platform.collector.last_run_age_s` | > 900 (1 leitura) | CRITICAL | RB-02 |
| `platform.outbox.oldest_pending_age_s.high` | `platform.outbox.oldest_pending_age_s` | > 1800 (3 leituras) | WARNING | RB-02 |

**Pendentes de modelagem** (da P3.11; **nenhuma** vira executável nesta
LC.7, porque a métrica própria não existe): burn de disponibilidade
(RB-01), PSP (RB-03), SEFAZ (RB-04), certificado (RB-05) e réplica
(RB-06). A LC.7 é um dos lugares onde a fonte real pode nascer
(serviço gerenciado), mas nomear/criar a métrica é decisão de
modelagem do dono/integração — registrada como pendência P-LC7-04,
nunca suprida por chave inventada.

## 4. Revisão trimestral

Vale o rito e o arquivo de registro da P3.11
(`docs/observability/revisao-trimestral-slo.md`): a primeira linha só
é preenchida no primeiro trimestre real de produção, sem valores
retroativos.

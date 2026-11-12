/**
 * Porta tipada + catálogos dos dashboards de observabilidade —
 * P-II8-TELA.
 *
 * Fontes na main (lidas, ficheiro:linha no relatório do pacote):
 *  - `platform/observability/RedUseMetrics.java` — métricas de
 *    processo do go-live (lote de faturação e sync de campo);
 *  - `platform/observability/MetricLabelPolicy.java` — política de
 *    rótulos (proibidos / restritos / permitidos, vocabulário fechado);
 *  - `platform/observability/CardinalityGuard.java` — recusa no
 *    registry de qualquer medidor com rótulo proibido;
 *  - `platform/observability/SloDefinition.java` +
 *    `BurnRateCalculator.java` — burn rate e orçamento de erro (P3.11);
 *  - `platform/observability/AlertRule.java` + `AlertRuleCatalog.java`
 *    — regras de alerta (owner e runbook obrigatórios);
 *  - `docs/observability/slo-go-live-ii8.md` — os 4 SLOs do go-live,
 *    TODOS com alvo [A DEFINIR] (pendência P-II8-ALVOS);
 *  - `docs/observability/slo-catalog.md` — catálogo herdado da P3.11
 *    (definições e marcas PROPOSTA, tal como escritas);
 *  - `docs/observability/metrics-catalog.md` (P0.8) e
 *    `red-use-convencao-ii8.md` (II-8) — convenção RED/USE;
 *  - `docs/observability/alert-rules.json` — catálogo EXECUTÁVEL de
 *    alertas (2 regras, copiadas verbatim).
 *
 * NENHUM alvo de SLO é inventado: o painel mostra sempre
 * "alvo [A DEFINIR]" para os SLOs do go-live. NENHUMA rota HTTP é
 * afirmada: as séries vivas chegam por esta porta tipada quando a
 * fiação existir — pendência P-II8-FIACAO [A DEFINIR]. A porta por
 * omissão devolve `null` e a tela mostra o estado vazio honesto.
 */

// ---------------------------------------------------------------------------
// SLOs do go-live (slo-go-live-ii8.md §1) — alvos [A DEFINIR] (P-II8-ALVOS)
// ---------------------------------------------------------------------------

export interface GoLiveSlo {
  /** SLO-1..SLO-4 do documento de go-live. */
  id: string
  name: string
  /** Definição do SLI, tal como escrita no documento. */
  sliDefinition: string
  /** Fonte do sinal, tal como escrita no documento. */
  signalSource: string
  /** Alvo: SEMPRE "[A DEFINIR]" até à baseline ratificada (P-II8-ALVOS). */
  target: '[A DEFINIR]'
  window: string
}

export const GO_LIVE_SLOS: GoLiveSlo[] = [
  {
    id: 'SLO-1',
    name: 'Disponibilidade da API',
    sliDefinition:
      'requisições de negócio sem erro de servidor (2xx+3xx+4xx) / total, derivado de http.server.requests por status — definição herdada da P3.11, marcada PROPOSTA lá até à decisão de modelagem (P-LC7-04)',
    signalSource: 'Actuator/Prometheus (P0.8)',
    target: '[A DEFINIR]',
    window: '30 dias (padrão P3.11)',
  },
  {
    id: 'SLO-2',
    name: 'Latência da API',
    sliDefinition: 'p95 de http.server.requests por route_group (sem rótulo de tenant — P0.8)',
    signalSource: 'Actuator/Prometheus (P0.8)',
    target: '[A DEFINIR]',
    window: '30 dias',
  },
  {
    id: 'SLO-3',
    name: 'Conclusão do lote de faturação',
    sliDefinition:
      'lotes de release concluídos íntegros (BatchReport.complete()) / lotes executados; e duração do lote como sinal de acompanhamento',
    signalSource: 'BillingBatchReleaseProof (II-8): relatório por execução + ziipsan_billing_batch_*',
    target: '[A DEFINIR]',
    window: 'por ciclo de faturação',
  },
  {
    id: 'SLO-4',
    name: 'Sincronização de campo',
    sliDefinition: 'itens de sync com outcome=SUCCESS / total de itens de sync',
    signalSource: 'ziipsan_field_sync_items_total{outcome} (II-8)',
    target: '[A DEFINIR]',
    window: '30 dias',
  },
]

/** Pendência dos alvos numéricos dos SLOs (baseline em ambiente de go-live, LC.7). */
export const SLO_TARGETS_PENDENCY = 'P-II8-ALVOS'

// ---------------------------------------------------------------------------
// Catálogo herdado da P3.11 (slo-catalog.md) — definições e marcas, verbatim
// ---------------------------------------------------------------------------

export interface LegacySloCatalogEntry {
  slo: string
  sli: string
  /** Meta tal como escrita no catálogo, com a marca PROPOSTA quando existe. */
  targetAsDocumented: string
  window: string
  owner: string
}

export const LEGACY_SLO_CATALOG: LegacySloCatalogEntry[] = [
  {
    slo: 'api.availability',
    sli: 'PROPOSTA (definição do SLI, sem lastro): requisições 2xx+3xx+4xx de negócio / total (5xx é erro), derivadas da métrica real http.server.requests (P0.8) pela tag de status',
    targetAsDocumented: '99,9% (ADR-0025/P3.15)',
    window: '30 dias',
    owner: 'platform',
  },
  {
    slo: 'api.latency.p95',
    sli: 'p95 por grupo de rota (P0.8: sem rótulo de tenant)',
    targetAsDocumented: '≤ 800 ms (PROPOSTA local, a confirmar na revisão trimestral)',
    window: '30 dias',
    owner: 'platform',
  },
  {
    slo: 'central.collector.freshness',
    sli: 'idade da última coleta',
    targetAsDocumented: '≤ 900 s (regra central.collector.stale da V9_1)',
    window: 'contínua',
    owner: 'platform',
  },
  {
    slo: 'outbox.delivery',
    sli: 'PROPOSTA (SLI e meta, sem lastro): mensagens PENDING com idade ≤ 30 min',
    targetAsDocumented: 'PROPOSTA: 99,9%',
    window: '30 dias',
    owner: 'platform',
  },
]

// ---------------------------------------------------------------------------
// Convenção RED/USE (red-use-convencao-ii8.md + metrics-catalog.md)
// ---------------------------------------------------------------------------

export interface RedUseRow {
  signal: string
  metric: string
  origin: string
}

/** RED por rota/serviço (§1 da convenção II-8). */
export const RED_ROWS: RedUseRow[] = [
  { signal: 'Rate', metric: 'http.server.requests (contador por method/uri/status) + ziipsan_business_requests_total{route_group, tenant}', origin: 'P0.8' },
  { signal: 'Errors', metric: 'derivável de http.server.requests por classe de status/outcome (5xx = erro; definição do SLI em slo-go-live-ii8.md)', origin: 'P0.8/P3.11' },
  { signal: 'Duration', metric: 'http.server.requests como timer com histograma de percentis (percentiles-histogram ligado na base)', origin: 'P0.8' },
]

/** RED de processo de negócio via RedUseMetrics (§1 da convenção II-8). */
export const PROCESS_RED_ROWS: Array<{ process: string; rate: string; errors: string; duration: string }> = [
  {
    process: 'Lote de faturação',
    rate: 'ziipsan_billing_batch_items_total{outcome}',
    errors: 'outcome=FAILURE do mesmo contador',
    duration: 'ziipsan_billing_batch_duration_seconds (timer, sem rótulos)',
  },
  {
    process: 'Sync de campo',
    rate: 'ziipsan_field_sync_items_total{outcome}',
    errors: 'outcome=FAILURE do mesmo contador',
    duration: '[A DEFINIR] — nasce quando o sync tiver duração medida por execução; não se inventa timer sem consumidor',
  },
]

/** USE por recurso (§2 da convenção II-8; métricas já expostas pelo Actuator). */
export const USE_ROWS: RedUseRow[] = [
  { signal: 'Utilization', metric: 'system.cpu.usage, process.cpu.usage, jvm.memory.used/jvm.memory.max, hikaricp_connections_active/hikaricp_connections', origin: 'Actuator (base)' },
  { signal: 'Saturation', metric: 'hikaricp_connections_pending, tomcat_threads_busy/tomcat_threads_config_max', origin: 'Actuator (base)' },
  { signal: 'Errors', metric: 'hikaricp_connections_timeout_total, erros 5xx do RED, logback_events_total{level="error"}', origin: 'Actuator (base)' },
]

/** Nomes das métricas da fachada RedUseMetrics (constantes do Java). */
export const RED_USE_METRIC_NAMES = {
  batchItems: 'ziipsan_billing_batch_items_total',
  batchDuration: 'ziipsan_billing_batch_duration_seconds',
  fieldSyncItems: 'ziipsan_field_sync_items_total',
} as const

/** RedUseMetrics.Outcome — desfechos fechados do rótulo `outcome`. */
export const METRIC_OUTCOMES = ['SUCCESS', 'FAILURE'] as const

// ---------------------------------------------------------------------------
// Política de rótulos (MetricLabelPolicy.java — conjuntos copiados verbatim)
// ---------------------------------------------------------------------------

/** MetricLabelPolicy.FORBIDDEN_LABELS — proibidos sempre, sem allowlist. */
export const FORBIDDEN_LABELS: string[] = [
  'uc_id', 'consumer_unit_id', 'consumer_unit', 'unit_id',
  'customer_id', 'cliente_id', 'user_id', 'usuario_id',
  'bill_id', 'conta_id', 'reading_id', 'leitura_id',
  'meter_id', 'hidrometro_id', 'service_order_id', 'os_id',
  'route_id', 'rota_id', 'address_id', 'endereco_id',
  'cpf', 'cnpj', 'email', 'telefone', 'phone', 'name', 'nome',
  'trace_id', 'request_id', 'span_id', 'ip', 'url', 'path',
]

/** MetricLabelPolicy.RESTRICTED_LABELS — só na allowlist abaixo. */
export const RESTRICTED_LABELS: string[] = ['tenant', 'tenant_id']

/** MetricLabelPolicy.TENANT_LABEL_ALLOWLIST — única métrica com rótulo de tenant. */
export const TENANT_LABEL_ALLOWLIST: string[] = ['ziipsan_business_requests_total']

/** MetricLabelPolicy.ALLOWED_LABELS — vocabulário fechado RED/USE. */
export const ALLOWED_LABELS: string[] = [
  'method', 'uri', 'status', 'outcome', 'exception',
  'route_group', 'error', 'pool', 'resource', 'state',
  'probe_key', 'service', 'le', 'quantile',
]

export type LabelViolationKind = 'FORBIDDEN' | 'RESTRICTED_NOT_ALLOWLISTED' | 'UNKNOWN'

export interface LabelViolation {
  label: string
  kind: LabelViolationKind
}

/**
 * Espelho de `MetricLabelPolicy.validate`: devolve as violações na
 * ordem de entrada, sem duplicar (vazia = conforme). Nome de métrica
 * vazio e rótulos `null` são erro explícito, como no Java.
 */
export function validateLabels(metricName: string, labels: Iterable<string> | null): LabelViolation[] {
  if (!metricName || metricName.trim() === '') {
    throw new Error('Nome da métrica é obrigatório para validar rótulos')
  }
  if (labels === null) {
    throw new Error('Rótulos são obrigatórios (use conjunto vazio, não null)')
  }
  const seen = new Set<string>()
  const violations: LabelViolation[] = []
  for (const label of labels) {
    if (label === null || label === undefined || seen.has(label)) continue
    seen.add(label)
    if (FORBIDDEN_LABELS.includes(label)) {
      violations.push({ label, kind: 'FORBIDDEN' })
    } else if (RESTRICTED_LABELS.includes(label)) {
      if (!TENANT_LABEL_ALLOWLIST.includes(metricName)) {
        violations.push({ label, kind: 'RESTRICTED_NOT_ALLOWLISTED' })
      }
    } else if (!ALLOWED_LABELS.includes(label)) {
      violations.push({ label, kind: 'UNKNOWN' })
    }
  }
  return violations
}

/** Espelho de `MetricLabelPolicy.hasForbiddenLabel`. */
export function hasForbiddenLabel(labels: Iterable<string>): boolean {
  for (const label of labels) {
    if (FORBIDDEN_LABELS.includes(label)) return true
  }
  return false
}

// ---------------------------------------------------------------------------
// Burn rate (SloDefinition + BurnRateCalculator, P3.11)
// ---------------------------------------------------------------------------

/** SloDefinition — a meta entra como PARÂMETRO de quem chama (nunca presumida). */
export interface SloDefinition {
  name: string
  targetPercent: number
  windowDays: number
}

/** BurnRateCalculator.Result. */
export interface BurnRateResult {
  observedErrorRate: number
  burnRate: number
  errorBudgetEvents: number
  consumedFraction: number
  remainingFraction: number
}

export type BurnAlertTier = 'NONE' | 'TICKET_FAST' | 'PAGE_SLOW' | 'TICKET_SLOW' | 'PAGE_FAST'

/** SloDefinition.allowedErrorRate: SLO 99,9% → 0,001. */
export function allowedErrorRate(slo: SloDefinition): number {
  if (!slo.name || slo.name.trim() === '') throw new Error('Nome do SLO é obrigatório')
  if (!(slo.targetPercent > 0.0 && slo.targetPercent < 100.0)) {
    throw new Error(`Meta do SLO deve estar entre 0 e 100 (exclusivo): ${slo.targetPercent}`)
  }
  if (slo.windowDays < 1) throw new Error('Janela do SLO deve ser >= 1 dia')
  return (100.0 - slo.targetPercent) / 100.0
}

/**
 * Espelho de `BurnRateCalculator.calculate`. Janela sem eventos é erro
 * explícito — burn "zero" inventado a partir de janela vazia esconderia
 * exactamente a indisponibilidade que o alerta existe para mostrar.
 */
export function calculateBurnRate(slo: SloDefinition, totalEvents: number, badEvents: number): BurnRateResult {
  const allowed = allowedErrorRate(slo)
  if (totalEvents <= 0) {
    throw new Error(`Sem eventos na janela não há burn rate calculável (dado ausente, não zero): total=${totalEvents}`)
  }
  if (badEvents < 0 || badEvents > totalEvents) {
    throw new Error(`Eventos ruins fora do intervalo [0, total]: bad=${badEvents}, total=${totalEvents}`)
  }
  const observed = badEvents / totalEvents
  const burn = observed / allowed
  const budget = allowed * totalEvents
  const consumed = badEvents / budget
  return {
    observedErrorRate: observed,
    burnRate: burn,
    errorBudgetEvents: budget,
    consumedFraction: consumed,
    remainingFraction: 1.0 - consumed,
  }
}

/** Espelho de `BurnRateCalculator.daysToExhaustion`. */
export function daysToExhaustion(slo: SloDefinition, totalEvents: number, badEvents: number, elapsedDays: number): number {
  if (elapsedDays <= 0) throw new Error('Dias decorridos devem ser > 0')
  const r = calculateBurnRate(slo, totalEvents, badEvents)
  if (r.remainingFraction <= 0) return 0.0
  if (badEvents === 0) return Number.POSITIVE_INFINITY
  const remainingEvents = r.errorBudgetEvents * r.remainingFraction
  const badPerDay = badEvents / elapsedDays
  return remainingEvents / badPerDay
}

/**
 * Espelho de `BurnRateCalculator.tierFor` (limiares do SRE Workbook):
 * ≥14,4 PAGE_FAST; ≥6 PAGE_SLOW; ≥3 (janela longa) TICKET_FAST;
 * ≥1 (janela longa) TICKET_SLOW; senão NONE.
 */
export function tierFor(burnRateShortWindow: number, burnRateLongWindow: number): BurnAlertTier {
  if (
    !Number.isFinite(burnRateShortWindow) || !Number.isFinite(burnRateLongWindow)
    || burnRateShortWindow < 0 || burnRateLongWindow < 0
  ) {
    throw new Error('Burn rates devem ser finitos e >= 0')
  }
  if (burnRateShortWindow >= 14.4) return 'PAGE_FAST'
  if (burnRateShortWindow >= 6.0) return 'PAGE_SLOW'
  if (burnRateLongWindow >= 3.0) return 'TICKET_FAST'
  if (burnRateLongWindow >= 1.0) return 'TICKET_SLOW'
  return 'NONE'
}

// ---------------------------------------------------------------------------
// Regras de alerta executáveis (docs/observability/alert-rules.json, verbatim)
// ---------------------------------------------------------------------------

/** AlertRule (P3.11) — owner e runbook obrigatórios na construção. */
export interface AlertRule {
  ruleKey: string
  metricKey: string
  comparator: string
  threshold: number
  consecutiveReadings: number
  severity: string
  scope: string
  owner: string
  runbookUrl: string
}

/** Catálogo executável vigente: só estas 2 regras (alert-rules.json). */
export const EXECUTABLE_ALERT_RULES: AlertRule[] = [
  {
    ruleKey: 'central.collector.stale',
    metricKey: 'platform.collector.last_run_age_s',
    comparator: '>',
    threshold: 900,
    consecutiveReadings: 1,
    severity: 'CRITICAL',
    scope: 'GLOBAL',
    owner: 'platform',
    runbookUrl: 'docs/operacao/runbooks/RB-02-fila-parada.md',
  },
  {
    ruleKey: 'platform.outbox.oldest_pending_age_s.high',
    metricKey: 'platform.outbox.oldest_pending_age_s',
    comparator: '>',
    threshold: 1800,
    consecutiveReadings: 3,
    severity: 'WARNING',
    scope: 'TENANT',
    owner: 'platform',
    runbookUrl: 'docs/operacao/runbooks/RB-02-fila-parada.md',
  },
]

// ---------------------------------------------------------------------------
// Porta tipada (sem rota HTTP afirmada — P-II8-FIACAO [A DEFINIR])
// ---------------------------------------------------------------------------

/** Uma série viva de métrica, quando a fiação a entregar. */
export interface LiveMetricSeries {
  metricName: string
  labels: Record<string, string>
  /** Valor actual da série; a tela nunca inventa este número. */
  currentValue: number | null
}

/**
 * Porta dos dashboards II-8. `liveSeries()` devolve `null` enquanto a
 * fiação [A DEFINIR] não existir — a tela mostra os catálogos e
 * definições (que são documentos) e o estado vazio honesto das séries.
 */
export interface ObservabilityPort {
  liveSeries(): Promise<LiveMetricSeries[] | null>
}

/** Pendência de fiação das séries vivas (nenhuma rota HTTP afirmada). */
export const OBSERVABILITY_WIRING_PENDENCY = 'P-II8-FIACAO'

/** Porta por omissão: sem fiação, séries ausentes (estado vazio honesto). */
export const unavailableObservabilityPort: ObservabilityPort = {
  liveSeries: () => Promise.resolve(null),
}

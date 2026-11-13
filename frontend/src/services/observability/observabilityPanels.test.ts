import { describe, expect, it } from 'vitest'
import {
  ALLOWED_LABELS,
  EXECUTABLE_ALERT_RULES,
  FORBIDDEN_LABELS,
  GO_LIVE_SLOS,
  calculateBurnRate,
  daysToExhaustion,
  hasForbiddenLabel,
  isProcessMetric,
  tierFor,
  unavailableObservabilityPort,
  validateLabels,
  validateLiveSeries,
  type SloDefinition,
} from './observabilityPanels'

/**
 * Fixtures e casos copiados das suites Java do núcleo II-8/P3.11
 * (MetricLabelPolicyTest, BurnRateCalculatorTest) e dos documentos
 * docs/observability/ (slo-go-live-ii8.md, alert-rules.json).
 * Nenhum valor foi inventado para estes testes.
 */
describe('validateLabels — MetricLabelPolicyTest do núcleo Java', () => {
  it('uc_id é violação FORBIDDEN em qualquer métrica', () => {
    expect(validateLabels('ziipsan_billing_batch_items_total', ['uc_id'])).toEqual([
      { label: 'uc_id', kind: 'FORBIDDEN' },
    ])
  })

  it('identificadores por entidade e pessoais são FORBIDDEN', () => {
    for (const label of ['consumer_unit_id', 'customer_id', 'bill_id', 'meter_id', 'reading_id', 'service_order_id', 'cpf', 'cnpj', 'email', 'trace_id']) {
      expect(validateLabels('qualquer_metrica', [label])).toEqual([{ label, kind: 'FORBIDDEN' }])
    }
    expect(hasForbiddenLabel(['outcome', 'uc_id'])).toBe(true)
    expect(hasForbiddenLabel(['outcome'])).toBe(false)
  })

  it('tenant fora da allowlist é RESTRICTED_NOT_ALLOWLISTED; dentro, passa', () => {
    expect(validateLabels('http.server.requests', ['tenant'])).toEqual([
      { label: 'tenant', kind: 'RESTRICTED_NOT_ALLOWLISTED' },
    ])
    expect(validateLabels('ziipsan_business_requests_total', ['tenant', 'route_group'])).toEqual([])
  })

  it('vocabulário RED/USE declarado passa; rótulo desconhecido é UNKNOWN', () => {
    expect(validateLabels('http.server.requests', ['method', 'uri', 'status', 'outcome', 'exception'])).toEqual([])
    expect(validateLabels('ziipsan_billing_batch_items_total', ['outcome'])).toEqual([])
    expect(validateLabels('hikaricp_connections', ['pool'])).toEqual([])
    expect(validateLabels('metrica', ['cidade'])).toEqual([{ label: 'cidade', kind: 'UNKNOWN' }])
  })

  it('conjuntos da política batem com o Java: 33 proibidos, 14 permitidos, uc_id proibido', () => {
    expect(FORBIDDEN_LABELS).toHaveLength(33)
    expect(FORBIDDEN_LABELS).toContain('uc_id')
    expect(ALLOWED_LABELS).toHaveLength(14)
  })
})

const SLO_999: SloDefinition = { name: 'api.availability', targetPercent: 99.9, windowDays: 30 }

describe('calculateBurnRate — BurnRateCalculatorTest do núcleo Java', () => {
  it('calculado à mão: 250 ruins em 100.000 com SLO 99,9% → burn 2,5 e 250% do orçamento', () => {
    const r = calculateBurnRate(SLO_999, 100_000, 250)
    expect(r.observedErrorRate).toBeCloseTo(0.0025, 12)
    expect(r.burnRate).toBeCloseTo(2.5, 12)
    expect(r.errorBudgetEvents).toBeCloseTo(100.0, 9)
    expect(r.consumedFraction).toBeCloseTo(2.5, 12)
    expect(r.remainingFraction).toBeCloseTo(-1.5, 12)
  })

  it('no ritmo exato do SLO: 10 ruins em 10.000 → burn 1,0 e orçamento 100% consumido', () => {
    const r = calculateBurnRate(SLO_999, 10_000, 10)
    expect(r.burnRate).toBeCloseTo(1.0, 12)
    expect(r.consumedFraction).toBeCloseTo(1.0, 12)
    expect(r.remainingFraction).toBeCloseTo(0.0, 12)
  })

  it('esgotamento: 600 ruins em 1.000.000 em 10 dias → 400/60 dias restantes', () => {
    expect(daysToExhaustion(SLO_999, 1_000_000, 600, 10.0)).toBeCloseTo(400.0 / 60.0, 9)
  })

  it('janela sem eventos é erro explícito, não burn zero', () => {
    expect(() => calculateBurnRate(SLO_999, 0, 0)).toThrow(/Sem eventos/)
  })

  it('tiers multi-janela: 14,4 PAGE_FAST; 6 PAGE_SLOW; 3 TICKET_FAST; 1 TICKET_SLOW', () => {
    expect(tierFor(14.4, 2.0)).toBe('PAGE_FAST')
    expect(tierFor(6.0, 1.0)).toBe('PAGE_SLOW')
    expect(tierFor(2.0, 3.0)).toBe('TICKET_FAST')
    expect(tierFor(0.5, 1.0)).toBe('TICKET_SLOW')
    expect(tierFor(0.5, 0.5)).toBe('NONE')
  })
})

describe('catálogos II-8 (documentos)', () => {
  it('os 4 SLOs do go-live têm TODOS alvo [A DEFINIR] (P-II8-ALVOS)', () => {
    expect(GO_LIVE_SLOS).toHaveLength(4)
    for (const slo of GO_LIVE_SLOS) {
      expect(slo.target).toBe('[A DEFINIR]')
    }
  })

  it('catálogo executável de alertas = as 2 regras de alert-rules.json, verbatim', () => {
    expect(EXECUTABLE_ALERT_RULES.map((r) => r.ruleKey)).toEqual([
      'central.collector.stale',
      'platform.outbox.oldest_pending_age_s.high',
    ])
    expect(EXECUTABLE_ALERT_RULES[0]).toMatchObject({ metricKey: 'platform.collector.last_run_age_s', threshold: 900, severity: 'CRITICAL' })
    expect(EXECUTABLE_ALERT_RULES[1]).toMatchObject({ metricKey: 'platform.outbox.oldest_pending_age_s', threshold: 1800, consecutiveReadings: 3 })
  })
})

describe('validateLiveSeries — R41-4-15', () => {
  it('as constantes RED_USE_METRIC_NAMES / METRIC_OUTCOMES são usadas: série de processo conforme passa', () => {
    expect(isProcessMetric('ziipsan_billing_batch_items_total')).toBe(true)
    expect(isProcessMetric('ziipsan_billing_batch_duration_seconds')).toBe(true)
    expect(isProcessMetric('ziipsan_field_sync_items_total')).toBe(true)
    expect(isProcessMetric('http.server.requests')).toBe(false)
    expect(
      validateLiveSeries({
        metricName: 'ziipsan_billing_batch_items_total',
        labels: { outcome: 'SUCCESS' },
        currentValue: 42,
      }),
    ).toEqual([])
  })

  it('timer de duração não leva rótulos: sem outcome, conforme (definição do Java)', () => {
    expect(
      validateLiveSeries({ metricName: 'ziipsan_billing_batch_duration_seconds', labels: {}, currentValue: 1.5 }),
    ).toEqual([])
  })

  it('contador de itens sem o rótulo outcome é problema', () => {
    expect(
      validateLiveSeries({ metricName: 'ziipsan_field_sync_items_total', labels: {}, currentValue: 7 }),
    ).toEqual(['métrica de processo ziipsan_field_sync_items_total sem o rótulo outcome'])
  })

  it('outcome fora do vocabulário fechado SUCCESS|FAILURE é problema', () => {
    expect(
      validateLiveSeries({
        metricName: 'ziipsan_billing_batch_items_total',
        labels: { outcome: 'PARTIAL' },
        currentValue: 3,
      }),
    ).toEqual(['outcome fora do vocabulário fechado (SUCCESS|FAILURE): PARTIAL'])
  })

  it('rótulo proibido na série é problema (via validateLabels)', () => {
    expect(
      validateLiveSeries({ metricName: 'http.server.requests', labels: { uc_id: 'x' }, currentValue: 1 }),
    ).toEqual(['rótulo uc_id: FORBIDDEN'])
  })

  it('série mal formada (nome vazio) é problema listado, nunca exceção lançada', () => {
    const problems = validateLiveSeries({ metricName: '', labels: {}, currentValue: null })
    expect(problems.length).toBeGreaterThan(0)
    expect(problems[0]).toMatch(/obrigatório/)
  })
})

describe('porta por omissão — R41-4-16', () => {
  it('sem fiação, séries E burn rate devolvem null (estado vazio honesto)', async () => {
    expect(await unavailableObservabilityPort.liveSeries()).toBeNull()
    expect(await unavailableObservabilityPort.burnRateInputs?.()).toBeNull()
  })
})

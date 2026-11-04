import { useEffect, useState } from 'react'
import { Alert, Spinner } from '@components/ui/Alert'
import { Button } from '@components/ui/Button'
import { Card, CardContent } from '@components/ui/Card'
import { PageHeader } from '@components/ui/PageHeader'
import { api } from '@services/api'
import { errorMessage } from '@services/registry/errors'

type TabId = 'overview' | 'platform' | 'security' | 'alerts' | 'contracts' | 'sla'

const TABS: Array<{ id: TabId; label: string; path: string }> = [
  { id: 'overview', label: 'Visão geral', path: '/platform/central/overview' },
  { id: 'platform', label: 'Plataforma', path: '/platform/central/platform' },
  { id: 'security', label: 'Segurança', path: '/platform/central/security' },
  { id: 'alerts', label: 'Alertas', path: '/platform/central/alerts' },
  { id: 'contracts', label: 'Contratos', path: '/platform/central/contracts' },
  { id: 'sla', label: 'SLA de suporte', path: '/platform/central/sla-report' },
]

type SlaSeverityReport = {
  severity: string
  opened: number
  resolved: number
  resolvedWithinDue: number
  breachedResolution: number
  adherencePct: number | null
  avgFrtMinutes: number | null
  avgMttrMinutes: number | null
  csatAvg: number | null
  csatCount: number
  csatReviewCount: number
  penaltyEstimate: number | null
}

type SlaReport = {
  tenantId: string
  month: string
  opened: number
  resolved: number
  adherencePct: number | null
  csatAvg: number | null
  csatCount: number
  penaltyEstimateTotal: number | null
  bySeverity: SlaSeverityReport[]
}

type HealthScore = {
  tenantRef: string | null
  score: number
  status: string
  factors: Array<{ key: string; weight: number; points: number; reason: string; measured: boolean }>
  unmeasured: string[]
}

type AlertView = {
  id: string
  ruleKey: string
  tenantRef: string | null
  severity: string
  status: string
  openedAt: string | null
}

type ContractRow = {
  tenantId: string
  contractId: string
  contractNumber: string
  status: string
  startsAt: string
  endsAt: string
  daysToExpiry: number
  commitmentBalance: number
  openInvoices: number
  openInvoicesTotal: number
  overdueInvoices: number
  providerCostMonth: number
  invoicedMonth: number
  margin: number | null
}

/**
 * Central Ziip v0 (P1.17): as 4 abas do plano, sobre o console v0 (P1.6).
 * P2.12 acrescenta a aba Contratos (ADR-0019): uma linha por contrato
 * SaaS, com vigencia, saldo de empenho, faturas em aberto, custo do
 * provedor no mes e margem realizada — sem dado pessoal de tenant.
 * P2.15 acrescenta a aba SLA de suporte (ADR-0025): o relatório mensal
 * de FRT, MTTR, aderência e CSAT por severidade, com multa estimada —
 * por tenant e mês, informados no formulário (o relatório não é global).
 * A tela só exibe o que a API devolve — contagens, scores e alertas, sem
 * nenhum dado pessoal de tenant (o tenant aparece só como UUID, o
 * `tenant_ref` do envelope da API de métricas). Sem o papel de plataforma
 * com `central:read`, o servidor nega a rota; o menu só a exibe para quem
 * tem a permissão.
 */
export function Central() {
  const [tab, setTab] = useState<TabId>('overview')
  const [data, setData] = useState<unknown>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [acting, setActing] = useState<string | null>(null)
  const [slaTenant, setSlaTenant] = useState('')
  const [slaMonth, setSlaMonth] = useState(() => new Date().toISOString().slice(0, 7))
  const [slaReport, setSlaReport] = useState<SlaReport | null>(null)
  const [slaLoading, setSlaLoading] = useState(false)

  const path = TABS.find((t) => t.id === tab)?.path ?? '/platform/central/overview'

  const load = async () => {
    setLoading(true)
    setError(null)
    try {
      const res = await api.get(path)
      setData(res.data)
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setLoading(false)
    }
  }

  const loadSlaReport = async () => {
    setSlaLoading(true)
    setError(null)
    try {
      const res = await api.get(`/platform/central/sla-report?tenantId=${slaTenant}&month=${slaMonth}`)
      setSlaReport(res.data as SlaReport)
    } catch (e) {
      setError(errorMessage(e))
      setSlaReport(null)
    } finally {
      setSlaLoading(false)
    }
  }

  useEffect(() => {
    // A aba SLA carrega por formulário (tenant + mês), não na troca de aba.
    if (tab === 'sla') {
      setLoading(false)
      return
    }
    void load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab])

  const acknowledge = async (id: string) => {
    setActing(id)
    setError(null)
    try {
      await api.post(`/platform/central/alerts/${id}/acknowledge`)
      await load()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setActing(null)
    }
  }

  const overview = tab === 'overview' ? (data as {
    healthScores?: HealthScore[]
    todayFocus?: Array<{ kind: string; detail: string }>
    watchdog?: { lastRunAt: string | null; stale: boolean; ageSeconds: number | null }
  } | null) : null
  const alerts = tab === 'alerts' ? (data as {
    open?: AlertView[]
    recentResolved?: AlertView[]
    rules?: Array<{ ruleKey: string; metricKey: string; severity: string; enabled: boolean }>
  } | null) : null
  const contracts = tab === 'contracts' ? (data as ContractRow[] | null) : null

  return (
    <div className="space-y-6">
      <PageHeader
        title="Central Ziip"
        description="Visão da plataforma por tenant e no conjunto: saúde, filas, segurança e alertas. Sem dado pessoal de tenant."
      />
      <div role="tablist" aria-label="Abas da Central" className="flex flex-wrap gap-2">
        {TABS.map((t) => (
          <Button
            key={t.id}
            role="tab"
            aria-selected={tab === t.id}
            variant={tab === t.id ? 'primary' : 'secondary'}
            onClick={() => setTab(t.id)}
          >
            {t.label}
          </Button>
        ))}
      </div>
      {error ? (
        <Alert tone="error" title="Não foi possível carregar a Central">
          {error}
        </Alert>
      ) : null}
      {loading ? (
        <Spinner label="Carregando..." />
      ) : tab === 'overview' && overview ? (
        <div className="space-y-6">
          <Card>
            <CardContent className="pt-6">
              <h2 className="text-lg font-semibold">Hoje</h2>
              <ul className="mt-2 list-disc pl-5">
                {overview.todayFocus?.map((f) => (
                  <li key={f.kind}>{f.detail}</li>
                ))}
              </ul>
              <p className="mt-2 text-sm">
                Coletor: {overview.watchdog?.stale ? 'sem coleta recente' : 'em dia'}
                {overview.watchdog?.lastRunAt ? ` (última coleta: ${overview.watchdog.lastRunAt})` : ' (nunca coletou)'}
              </p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="pt-6">
              <h2 className="text-lg font-semibold">Health Score por tenant</h2>
              <ul className="mt-2 space-y-2">
                {overview.healthScores?.map((h) => (
                  <li key={h.tenantRef ?? 'global'}>
                    {h.tenantRef ?? 'Global'} — {h.score} ({h.status})
                    <ul className="list-disc pl-5 text-sm">
                      {h.factors.map((f) => (
                        <li key={f.key}>{f.key}: {f.points} — {f.reason}</li>
                      ))}
                      <li>Fatores ainda não medidos: {h.unmeasured.join(', ')}</li>
                    </ul>
                  </li>
                ))}
              </ul>
            </CardContent>
          </Card>
        </div>
      ) : tab === 'alerts' && alerts ? (
        <div className="space-y-6">
          <Card>
            <CardContent className="pt-6">
              <h2 className="text-lg font-semibold">Alertas abertos</h2>
              {alerts.open?.length ? (
                <ul className="mt-2 space-y-2">
                  {alerts.open.map((a) => (
                    <li key={a.id}>
                      {a.ruleKey} — {a.severity} — {a.status}
                      {a.status === 'OPEN' ? (
                        <Button
                          variant="secondary"
                          disabled={acting === a.id}
                          onClick={() => void acknowledge(a.id)}
                        >
                          Reconhecer
                        </Button>
                      ) : null}
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="mt-2 text-sm">Nenhum alerta aberto.</p>
              )}
            </CardContent>
          </Card>
          <Card>
            <CardContent className="pt-6">
              <h2 className="text-lg font-semibold">Regras</h2>
              <ul className="mt-2 list-disc pl-5">
                {alerts.rules?.map((r) => (
                  <li key={r.ruleKey}>{r.ruleKey} ({r.metricKey}, {r.severity}) — {r.enabled ? 'ligada' : 'desligada'}</li>
                ))}
              </ul>
            </CardContent>
          </Card>
        </div>
      ) : tab === 'contracts' && contracts ? (
        <div className="space-y-6">
          <Card>
            <CardContent className="pt-6">
              <h2 className="text-lg font-semibold">Contratos SaaS</h2>
              {contracts.length ? (
                <ul className="mt-2 space-y-2">
                  {contracts.map((c) => (
                    <li key={c.contractId}>
                      {c.contractNumber} — {c.status} — vence em {c.daysToExpiry} dias —
                      empenho {c.commitmentBalance} — faturas em aberto {c.openInvoices}
                      ({c.openInvoicesTotal}, {c.overdueInvoices} vencidas) —
                      custo no mês {c.providerCostMonth} — faturado no mês {c.invoicedMonth} —
                      margem {c.margin === null ? '—' : `${(c.margin * 100).toFixed(1)}%`}
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="mt-2 text-sm">Nenhum contrato SaaS.</p>
              )}
            </CardContent>
          </Card>
        </div>
      ) : tab === 'sla' ? (
        <div className="space-y-6">
          <Card>
            <CardContent className="pt-6">
              <h2 className="text-lg font-semibold">Relatório mensal de SLA</h2>
              <div className="mt-2 flex flex-wrap items-end gap-2">
                <label className="text-sm">
                  Tenant (UUID)
                  <input
                    aria-label="Tenant (UUID)"
                    className="ml-2 border px-2 py-1"
                    value={slaTenant}
                    onChange={(e) => setSlaTenant(e.target.value)}
                  />
                </label>
                <label className="text-sm">
                  Mês (AAAA-MM)
                  <input
                    aria-label="Mês (AAAA-MM)"
                    className="ml-2 border px-2 py-1"
                    value={slaMonth}
                    onChange={(e) => setSlaMonth(e.target.value)}
                  />
                </label>
                <Button
                  variant="secondary"
                  disabled={slaLoading || !slaTenant}
                  onClick={() => void loadSlaReport()}
                >
                  Consultar relatório
                </Button>
              </div>
              {slaLoading ? (
                <Spinner label="Carregando..." />
              ) : slaReport ? (
                <div className="mt-4">
                  <p className="text-sm">
                    Abertos {slaReport.opened} — resolvidos {slaReport.resolved} —
                    aderência {slaReport.adherencePct === null ? '—' : `${slaReport.adherencePct.toFixed(1)}%`} —
                    CSAT {slaReport.csatAvg === null ? '—' : slaReport.csatAvg.toFixed(1)} ({slaReport.csatCount} avaliações) —
                    multa estimada {slaReport.penaltyEstimateTotal === null ? '—' : slaReport.penaltyEstimateTotal}
                  </p>
                  <ul className="mt-2 space-y-2">
                    {slaReport.bySeverity.map((s) => (
                      <li key={s.severity}>
                        {s.severity} — abertos {s.opened} — resolvidos {s.resolved} ({s.resolvedWithinDue} no prazo,{' '}
                        {s.breachedResolution} estourados) — aderência{' '}
                        {s.adherencePct === null ? '—' : `${s.adherencePct.toFixed(1)}%`} —
                        FRT {s.avgFrtMinutes === null ? '—' : `${s.avgFrtMinutes.toFixed(0)} min`} —
                        MTTR {s.avgMttrMinutes === null ? '—' : `${s.avgMttrMinutes.toFixed(0)} min`} —
                        CSAT {s.csatAvg === null ? '—' : s.csatAvg.toFixed(1)} ({s.csatCount} avaliações,{' '}
                        {s.csatReviewCount} em revisão) —
                        multa estimada {s.penaltyEstimate === null ? '—' : s.penaltyEstimate}
                      </li>
                    ))}
                  </ul>
                </div>
              ) : (
                <p className="mt-2 text-sm">Informe o tenant e o mês para consultar o relatório.</p>
              )}
            </CardContent>
          </Card>
        </div>
      ) : (
        <Card>
          <CardContent className="pt-6">
            <pre className="overflow-auto text-sm">{JSON.stringify(data, null, 2)}</pre>
          </CardContent>
        </Card>
      )}
    </div>
  )
}

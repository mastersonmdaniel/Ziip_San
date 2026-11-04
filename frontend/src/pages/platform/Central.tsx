import { useEffect, useState } from 'react'
import { Alert, Spinner } from '@components/ui/Alert'
import { Button } from '@components/ui/Button'
import { Card, CardContent } from '@components/ui/Card'
import { PageHeader } from '@components/ui/PageHeader'
import { api } from '@services/api'
import { errorMessage } from '@services/registry/errors'

type TabId = 'overview' | 'platform' | 'security' | 'alerts'

const TABS: Array<{ id: TabId; label: string; path: string }> = [
  { id: 'overview', label: 'Visão geral', path: '/platform/central/overview' },
  { id: 'platform', label: 'Plataforma', path: '/platform/central/platform' },
  { id: 'security', label: 'Segurança', path: '/platform/central/security' },
  { id: 'alerts', label: 'Alertas', path: '/platform/central/alerts' },
]

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

/**
 * Central Ziip v0 (P1.17): as 4 abas do plano, sobre o console v0 (P1.6).
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

  useEffect(() => {
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

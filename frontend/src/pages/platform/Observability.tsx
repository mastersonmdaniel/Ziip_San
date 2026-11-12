import { useEffect, useState } from 'react'
import { Alert, Spinner } from '@components/ui/Alert'
import { Badge } from '@components/ui/Badge'
import { Card, CardContent } from '@components/ui/Card'
import { PageHeader } from '@components/ui/PageHeader'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@components/ui/Table'
import { Tabs } from '@components/ui/Tabs'
import {
  ALLOWED_LABELS,
  EXECUTABLE_ALERT_RULES,
  FORBIDDEN_LABELS,
  GO_LIVE_SLOS,
  LEGACY_SLO_CATALOG,
  OBSERVABILITY_WIRING_PENDENCY,
  PROCESS_RED_ROWS,
  RED_ROWS,
  RESTRICTED_LABELS,
  SLO_TARGETS_PENDENCY,
  TENANT_LABEL_ALLOWLIST,
  USE_ROWS,
  unavailableObservabilityPort,
  type LiveMetricSeries,
  type ObservabilityPort,
} from '@services/observability/observabilityPanels'

type TabId = 'slos' | 'metricas' | 'alertas' | 'rotulos' | 'series'

const TABS = [
  { id: 'slos', label: 'SLOs' },
  { id: 'metricas', label: 'Métricas RED/USE' },
  { id: 'alertas', label: 'Alertas e burn rate' },
  { id: 'rotulos', label: 'Política de rótulos' },
  { id: 'series', label: 'Séries vivas' },
]

/**
 * Dashboards de observabilidade — P-II8-TELA.
 *
 * Apresenta os CATÁLOGOS e DEFINIÇÕES que existem como documentos e
 * código na main: os 4 SLOs do go-live (slo-go-live-ii8.md) com
 * "alvo [A DEFINIR]" — pendência P-II8-ALVOS, NENHUM alvo é
 * inventado —, o catálogo herdado da P3.11 com as suas marcas, a
 * convenção RED/USE (red-use-convencao-ii8.md), as 2 regras de alerta
 * executáveis (alert-rules.json) e a política de rótulos
 * (MetricLabelPolicy). As séries vivas chegam pela porta tipada
 * `ObservabilityPort`; sem fiação (P-II8-FIACAO [A DEFINIR], nenhuma
 * rota HTTP afirmada), a aba de séries mostra o estado vazio honesto.
 *
 * Paridade web↔mobile: padrão responsivo — tabelas com `mobileCards`
 * e abas com rolagem horizontal; nenhuma função exclusiva do desktop.
 */
export function Observability({ port = unavailableObservabilityPort }: { port?: ObservabilityPort }) {
  const [tab, setTab] = useState<TabId>('slos')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [series, setSeries] = useState<LiveMetricSeries[] | null>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    port
      .liveSeries()
      .then((result) => {
        if (!cancelled) setSeries(result)
      })
      .catch((e: unknown) => {
        if (!cancelled) setError(e instanceof Error ? e.message : 'erro ao carregar a observabilidade')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [port])

  return (
    <div className="space-y-6">
      <PageHeader
        title="Observabilidade"
        description="Catálogos de SLOs, convenção RED/USE, regras de alerta e política de rótulos (II-8). Os alvos dos SLOs do go-live são [A DEFINIR] até à baseline ratificada."
      />
      {error ? (
        <Alert tone="error" title="Não foi possível carregar a observabilidade">
          {error}
        </Alert>
      ) : null}
      {loading ? (
        <Spinner label="Carregando observabilidade..." />
      ) : (
        <Tabs tabs={TABS} active={tab} onChange={(id) => setTab(id as TabId)} idPrefix="observability" label="Observabilidade">
          {tab === 'slos' ? (
            <div className="space-y-6">
              <Card>
                <CardContent className="pt-6">
                  <h2 className="text-lg font-semibold">SLOs do go-live (II-8)</h2>
                  <p className="mt-2 text-sm">
                    Todos os alvos numéricos são [A DEFINIR] ({SLO_TARGETS_PENDENCY}) até haver baseline medida em
                    ambiente de go-live (LC.7) e ratificação — nenhum alvo é inventado neste painel.
                  </p>
                  <Table caption="SLOs do go-live" mobileCards>
                    <TableHeader>
                      <TableRow>
                        <TableHead>SLO</TableHead>
                        <TableHead>SLI (definição)</TableHead>
                        <TableHead>Fonte do sinal</TableHead>
                        <TableHead>Alvo</TableHead>
                        <TableHead>Janela</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {GO_LIVE_SLOS.map((slo) => (
                        <TableRow key={slo.id}>
                          <TableCell label="SLO">
                            {slo.id} — {slo.name}
                          </TableCell>
                          <TableCell label="SLI (definição)">{slo.sliDefinition}</TableCell>
                          <TableCell label="Fonte do sinal">{slo.signalSource}</TableCell>
                          <TableCell label="Alvo">
                            <Badge variant="warning">alvo [A DEFINIR]</Badge>
                          </TableCell>
                          <TableCell label="Janela">{slo.window}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </CardContent>
              </Card>
              <Card>
                <CardContent className="pt-6">
                  <h2 className="text-lg font-semibold">Catálogo herdado (P3.11)</h2>
                  <p className="mt-2 text-sm">
                    Definições do catálogo de SLOs da P3.11, com as marcas tal como escritas (PROPOSTA não é catálogo
                    vigente: vale como ponto de partida para a revisão trimestral ratificar ou substituir).
                  </p>
                  <Table caption="Catálogo de SLOs da P3.11" mobileCards>
                    <TableHeader>
                      <TableRow>
                        <TableHead>SLO</TableHead>
                        <TableHead>SLI</TableHead>
                        <TableHead>Meta (como documentada)</TableHead>
                        <TableHead>Janela</TableHead>
                        <TableHead>Responsável</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {LEGACY_SLO_CATALOG.map((entry) => (
                        <TableRow key={entry.slo}>
                          <TableCell label="SLO">{entry.slo}</TableCell>
                          <TableCell label="SLI">{entry.sli}</TableCell>
                          <TableCell label="Meta (como documentada)">{entry.targetAsDocumented}</TableCell>
                          <TableCell label="Janela">{entry.window}</TableCell>
                          <TableCell label="Responsável">{entry.owner}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </CardContent>
              </Card>
            </div>
          ) : null}

          {tab === 'metricas' ? (
            <div className="space-y-6">
              <Card>
                <CardContent className="pt-6">
                  <h2 className="text-lg font-semibold">RED — por rota/serviço</h2>
                  <Table caption="Sinais RED por rota/serviço" mobileCards>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Sinal</TableHead>
                        <TableHead>Métrica</TableHead>
                        <TableHead>Origem</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {RED_ROWS.map((row) => (
                        <TableRow key={row.signal}>
                          <TableCell label="Sinal">{row.signal}</TableCell>
                          <TableCell label="Métrica">{row.metric}</TableCell>
                          <TableCell label="Origem">{row.origin}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </CardContent>
              </Card>
              <Card>
                <CardContent className="pt-6">
                  <h2 className="text-lg font-semibold">RED — processos de negócio (RedUseMetrics)</h2>
                  <Table caption="Sinais RED de processo de negócio" mobileCards>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Processo</TableHead>
                        <TableHead>Rate</TableHead>
                        <TableHead>Errors</TableHead>
                        <TableHead>Duration</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {PROCESS_RED_ROWS.map((row) => (
                        <TableRow key={row.process}>
                          <TableCell label="Processo">{row.process}</TableCell>
                          <TableCell label="Rate">{row.rate}</TableCell>
                          <TableCell label="Errors">{row.errors}</TableCell>
                          <TableCell label="Duration">{row.duration}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </CardContent>
              </Card>
              <Card>
                <CardContent className="pt-6">
                  <h2 className="text-lg font-semibold">USE — por recurso</h2>
                  <Table caption="Sinais USE por recurso" mobileCards>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Sinal</TableHead>
                        <TableHead>Métricas</TableHead>
                        <TableHead>Origem</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {USE_ROWS.map((row) => (
                        <TableRow key={row.signal}>
                          <TableCell label="Sinal">{row.signal}</TableCell>
                          <TableCell label="Métricas">{row.metric}</TableCell>
                          <TableCell label="Origem">{row.origin}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </CardContent>
              </Card>
            </div>
          ) : null}

          {tab === 'alertas' ? (
            <div className="space-y-6">
              <Card>
                <CardContent className="pt-6">
                  <h2 className="text-lg font-semibold">Regras de alerta executáveis</h2>
                  <p className="mt-2 text-sm">
                    Catálogo executável vigente (alert-rules.json): apenas regras sobre métricas reais do catálogo
                    fechado. Toda a regra tem responsável e runbook obrigatórios (P3.11).
                  </p>
                  <Table caption="Regras de alerta executáveis" mobileCards>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Regra</TableHead>
                        <TableHead>Métrica</TableHead>
                        <TableHead>Condição</TableHead>
                        <TableHead>Leituras consecutivas</TableHead>
                        <TableHead>Severidade</TableHead>
                        <TableHead>Âmbito</TableHead>
                        <TableHead>Responsável</TableHead>
                        <TableHead>Runbook</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {EXECUTABLE_ALERT_RULES.map((rule) => (
                        <TableRow key={rule.ruleKey}>
                          <TableCell label="Regra">{rule.ruleKey}</TableCell>
                          <TableCell label="Métrica">{rule.metricKey}</TableCell>
                          <TableCell label="Condição">
                            {rule.comparator} {rule.threshold}
                          </TableCell>
                          <TableCell label="Leituras consecutivas">{rule.consecutiveReadings}</TableCell>
                          <TableCell label="Severidade">{rule.severity}</TableCell>
                          <TableCell label="Âmbito">{rule.scope}</TableCell>
                          <TableCell label="Responsável">{rule.owner}</TableCell>
                          <TableCell label="Runbook">{rule.runbookUrl}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </CardContent>
              </Card>
              <Card>
                <CardContent className="pt-6">
                  <h2 className="text-lg font-semibold">Burn rate (P3.11)</h2>
                  <p className="mt-2 text-sm">
                    Burn rate = taxa de erro observada / taxa permitida pelo SLO, calculado por
                    BurnRateCalculator a partir de contagens (total e ruins) de quem chama — janela sem eventos é erro
                    explícito, nunca burn zero. Limiares multi-janela (SRE Workbook): burn ≥ 14,4 pagina rápido
                    (PAGE_FAST); ≥ 6 pagina (PAGE_SLOW); ≥ 3 na janela longa abre ticket rápido (TICKET_FAST); ≥ 1 na
                    janela longa abre ticket lento (TICKET_SLOW). Sem alvo ratificado ({SLO_TARGETS_PENDENCY}), alerta
                    de burn não pagina ninguém.
                  </p>
                </CardContent>
              </Card>
            </div>
          ) : null}

          {tab === 'rotulos' ? (
            <div className="space-y-6">
              <Card>
                <CardContent className="pt-6">
                  <h2 className="text-lg font-semibold">Rótulos proibidos sempre</h2>
                  <p className="mt-2 text-sm">
                    Qualquer medidor com um destes rótulos é RECUSADO no registry (CardinalityGuard). Regra-mãe do
                    PDCA-01: uc_id NUNCA é rótulo — detalhe por UC é por log/trace correlacionado, nunca por série.
                  </p>
                  <p className="mt-2 text-sm">{FORBIDDEN_LABELS.join(', ')}</p>
                </CardContent>
              </Card>
              <Card>
                <CardContent className="pt-6">
                  <h2 className="text-lg font-semibold">Rótulos restritos</h2>
                  <p className="mt-2 text-sm">
                    {RESTRICTED_LABELS.join(', ')} — só na métrica da allowlist: {TENANT_LABEL_ALLOWLIST.join(', ')}{' '}
                    (herdada da P0.8). Fora dela, a política acusa violação.
                  </p>
                </CardContent>
              </Card>
              <Card>
                <CardContent className="pt-6">
                  <h2 className="text-lg font-semibold">Vocabulário permitido (fechado)</h2>
                  <p className="mt-2 text-sm">
                    {ALLOWED_LABELS.join(', ')}. Rótulo fora das três listas é violação: vocabulário novo entra por
                    revisão da política e do orçamento de séries, nunca por uso solto.
                  </p>
                </CardContent>
              </Card>
            </div>
          ) : null}

          {tab === 'series' ? (
            series && series.length > 0 ? (
              <Card>
                <CardContent className="pt-6">
                  <h2 className="text-lg font-semibold">Séries vivas</h2>
                  <Table caption="Séries vivas de métricas" mobileCards>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Métrica</TableHead>
                        <TableHead>Rótulos</TableHead>
                        <TableHead>Valor actual</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {series.map((s) => (
                        <TableRow key={`${s.metricName}-${JSON.stringify(s.labels)}`}>
                          <TableCell label="Métrica">{s.metricName}</TableCell>
                          <TableCell label="Rótulos">
                            {Object.entries(s.labels).map(([k, v]) => `${k}=${v}`).join(', ') || '—'}
                          </TableCell>
                          <TableCell label="Valor actual">{s.currentValue ?? '—'}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </CardContent>
              </Card>
            ) : (
              <Alert tone="info" title="Sem séries vivas">
                As séries vivas ainda não estão fiadas ({OBSERVABILITY_WIRING_PENDENCY} [A DEFINIR] — nenhuma rota HTTP
                afirmada). Os catálogos e definições das outras abas são documentos e código vigentes; os valores das
                séries só aparecem quando a fiação os entregar — nenhum valor é inventado.
              </Alert>
            )
          ) : null}
        </Tabs>
      )}
    </div>
  )
}

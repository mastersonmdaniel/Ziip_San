import { useEffect, useState } from 'react'
import { Alert } from '@components/ui/Alert'
import { Badge } from '@components/ui/Badge'
import { Button } from '@components/ui/Button'
import { Card, CardContent } from '@components/ui/Card'
import { PageHeader } from '@components/ui/PageHeader'
import {
  isPcrComplete,
  type CloseRefusal,
  type PcrCatalogState,
  type ProblemCauseRemedy,
  type SlaOrderView,
  type SlaOsPort,
  type TypeSlaConfig,
} from '@services/os/slaOs'

/**
 * P-II4-TELA — SLA e exigências de encerramento das ordens de serviço.
 *
 * Rota: /os/sla (decisão de executor pela convenção das rotas irmãs;
 * nenhuma fonte declara o caminho — registado no relatório do pacote).
 * A tela consome a porta tipada `SlaOsPort`, espelho do núcleo Java
 * `core/slaos/`; NÃO há rota HTTP afirmada — a fiação é a pendência
 * P-II4-FIACAO. Sem porta ligada, mostra o mecanismo e as pendências.
 *
 * O catálogo problema–causa–remédio é conteúdo [A DEFINIR] do tenant
 * (pendência P-II4-CATALOGO): a tela mostra o MECANISMO — a tripla
 * problema–causa–remédio obrigatória no encerramento, validada contra
 * o catálogo — e o estado "catálogo por configurar". Nenhum código de
 * catálogo é inventado ou apresentado como real.
 *
 * Paridade web↔mobile: padrão responsivo da casca e da biblioteca
 * `components/ui`; nenhuma função exclusiva de desktop.
 */
export function SlaOrders({ port }: { port?: SlaOsPort }) {
  const [configs, setConfigs] = useState<TypeSlaConfig[]>([])
  const [orders, setOrders] = useState<SlaOrderView[]>([])
  const [catalog, setCatalog] = useState<PcrCatalogState | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [coding, setCoding] = useState<ProblemCauseRemedy>({
    problemCode: '',
    causeCode: '',
    remedyCode: '',
  })
  const [refusals, setRefusals] = useState<CloseRefusal[] | null>(null)

  useEffect(() => {
    if (!port) return
    Promise.all([port.typeConfigs(), port.orders(), port.pcrCatalogState()])
      .then(([c, o, cat]) => {
        setConfigs(c)
        setOrders(o)
        setCatalog(cat)
      })
      .catch(() => setError('Não foi possível carregar as configurações de SLA.'))
  }, [port])

  async function evaluateClose(order: SlaOrderView) {
    if (!port) return
    try {
      setError(null)
      const result = await port.evaluateCloseGate({
        status: order.status,
        requirements: { minClearedPhotos: 0, requiredChecklistItems: [], requiredDocumentKinds: [] },
        evidence: { clearedPhotoCount: 0, completedChecklistItems: [], presentDocumentKinds: [] },
        codingOrNull: isPcrComplete(coding) ? coding : null,
      })
      setRefusals(result)
    } catch {
      setError('A avaliação do encerramento foi recusada pela porta.')
    }
  }

  return (
    <div>
      <PageHeader
        title="SLA das ordens de serviço"
        description="Prazos duplos (interno e regulador) por tipo de OS, escalonamento e exigências de encerramento — incluindo a tripla problema–causa–remédio."
      />

      {!port && (
        <Alert tone="warning" title="Fiação pendente — P-II4-FIACAO">
          Esta tela consome a porta tipada do núcleo de SLA/OS. Nenhuma rota HTTP está ligada (a
          fiação é a pendência P-II4-FIACAO); por isso nenhuma configuração nem ordem é mostrada.
        </Alert>
      )}
      {error && <Alert tone="error">{error}</Alert>}

      <Card>
        <CardContent>
          <h2>Catálogo problema–causa–remédio</h2>
          {catalog?.configured ? (
            <p>Catálogo do tenant configurado: a tripla é validada contra ele no encerramento.</p>
          ) : (
            <p>
              <strong>Catálogo por configurar</strong> — o conteúdo do catálogo
              problema–causa–remédio é <strong>[A DEFINIR]</strong> pelo tenant (pendência
              P-II4-CATALOGO). O mecanismo está pronto: sem a tripla completa e válida contra o
              catálogo, o encerramento é recusado (MISSING_PROBLEM_CAUSE_REMEDY /
              INVALID_PROBLEM_CAUSE_REMEDY). Nenhum código de catálogo é apresentado como real.
            </p>
          )}
          <label>
            Problema
            <input
              aria-label="Código do problema"
              value={coding.problemCode}
              onChange={(e) => setCoding({ ...coding, problemCode: e.target.value })}
            />
          </label>
          <label>
            Causa
            <input
              aria-label="Código da causa"
              value={coding.causeCode}
              onChange={(e) => setCoding({ ...coding, causeCode: e.target.value })}
            />
          </label>
          <label>
            Remédio
            <input
              aria-label="Código do remédio"
              value={coding.remedyCode}
              onChange={(e) => setCoding({ ...coding, remedyCode: e.target.value })}
            />
          </label>
          <p>
            Tripla{' '}
            {isPcrComplete(coding) ? (
              <Badge variant="success">completa</Badge>
            ) : (
              <Badge variant="warning">incompleta — os três códigos são obrigatórios</Badge>
            )}
          </p>
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <h2>Configuração de SLA por tipo de OS</h2>
          {configs.length === 0 ? (
            <p>Nenhuma configuração carregada.</p>
          ) : (
            <ul>
              {configs.map((c) => (
                <li key={c.typeCode}>
                  {c.typeCode} — interno:{' '}
                  {c.internalDeadlineHours == null
                    ? 'não configurado'
                    : `${c.internalDeadlineHours}h`}{' '}
                  · regulador:{' '}
                  {c.regulatorDeadlineHours == null
                    ? 'não configurado'
                    : `${c.regulatorDeadlineHours}h`}{' '}
                  · risco aos {c.riskThresholdPercent}% · escalonamento aos{' '}
                  {c.escalationThresholdsPercent.length === 0
                    ? '—'
                    : c.escalationThresholdsPercent.map((p) => `${p}%`).join(', ')}
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <h2>Ordens e prazos duplos</h2>
          {orders.length === 0 ? (
            <p>Nenhuma ordem carregada.</p>
          ) : (
            <ul>
              {orders.map((o) => (
                <li key={o.orderId}>
                  {o.orderId} — {o.typeCode} — {o.status} — interno:{' '}
                  <Badge>{o.assessment.internal.state}</Badge> · regulador:{' '}
                  <Badge>{o.assessment.regulator.state}</Badge> · nível de escalonamento{' '}
                  {o.escalationLevel}
                  <ul>
                    {o.events.map((ev, i) => (
                      <li key={`${o.orderId}-${i}`}>
                        {ev.kind}
                        {ev.deadlineOrNull ? ` — ${ev.deadlineOrNull}` : ''} — nível{' '}
                        {ev.escalationLevel} — {ev.at}
                      </li>
                    ))}
                  </ul>
                  <Button onClick={() => evaluateClose(o)} disabled={!port}>
                    Avaliar encerramento
                  </Button>
                </li>
              ))}
            </ul>
          )}
          {refusals != null &&
            (refusals.length === 0 ? (
              <Alert tone="success">Encerramento liberado: nenhuma exigência em falta.</Alert>
            ) : (
              <div>
                <h3>Exigências em falta</h3>
                <ul>
                  {refusals.map((r, i) => (
                    <li key={`${r.kind}-${i}`}>
                      {r.kind}: {r.detail}
                    </li>
                  ))}
                </ul>
              </div>
            ))}
        </CardContent>
      </Card>
    </div>
  )
}

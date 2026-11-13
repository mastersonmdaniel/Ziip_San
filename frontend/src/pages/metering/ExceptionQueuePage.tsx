import { useEffect, useState } from 'react'
import { Alert } from '@components/ui/Alert'
import { Badge } from '@components/ui/Badge'
import { Button } from '@components/ui/Button'
import { Card, CardContent } from '@components/ui/Card'
import { PageHeader } from '@components/ui/PageHeader'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@components/ui/Table'
import {
  allowedTransitions, isOpenStatus, sortExceptions,
  type CritiqueExceptionView, type CritiquePort, type ExceptionStatus,
} from '@services/metering/critique'

const STATUS_LABEL: Record<ExceptionStatus, string> = {
  PENDING: 'Pendente', ACCEPTED: 'Aceite', CORRECTED: 'Corrigida', REREADING_REQUESTED: 'Releitura pedida',
}

/**
 * P-II2-TELA — fila de exceções e crítica de leituras (II-2).
 * Sem porta ligada (fiação P-II2-FIACAO pendente), a tela mostra o estado
 * vazio honesto: nenhuma exceção é fingida. Limiares das regras são do
 * tenant [A DEFINIR] (P-II2-LIMIARES) e chegam nos próprios findings.
 * Paridade web↔mobile: mesma página responsiva (tabela com cartões no celular).
 */
export function ExceptionQueuePage({ port }: { port?: CritiquePort }) {
  const [items, setItems] = useState<CritiqueExceptionView[]>([])
  const [error, setError] = useState<string | null>(null)
  const [onlyOpen, setOnlyOpen] = useState(true)
  const [drafts, setDrafts] = useState<Record<string, string>>({})

  useEffect(() => {
    if (!port) return
    let alive = true
    port.listExceptions()
      .then((list) => { if (alive) setItems(sortExceptions(list)) })
      .catch(() => { if (alive) setError('Não foi possível carregar a fila de exceções.') })
    return () => { alive = false }
  }, [port])

  const visible = onlyOpen ? items.filter((e) => isOpenStatus(e.status)) : items

  async function act(e: CritiqueExceptionView, action: 'accept' | 'rereading') {
    if (!port) return
    try {
      const updated = action === 'accept' ? await port.accept(e) : await port.requestRereading(e)
      setError(null)
      setItems((prev) => sortExceptions(prev.map((x) => (x.unitId === e.unitId && x.reference === e.reference ? updated : x))))
    } catch { setError('A transição foi recusada pelo núcleo (transição inválida para o estado atual).') }
  }

  // Correção (CritiqueException.correct): leitura corrigida inteira >= 0;
  // negativa ou não inteira é recusada aqui e pelo núcleo — nunca ajustada.
  async function correct(e: CritiqueExceptionView) {
    if (!port) return
    const key = `${e.reference}-${e.unitId}`
    const value = Number(drafts[key])
    if (!Number.isInteger(value) || value < 0) {
      setError('Leitura corrigida inválida: tem de ser um inteiro maior ou igual a zero.')
      return
    }
    try {
      const updated = await port.correct(e, value)
      setError(null)
      setItems((prev) => sortExceptions(prev.map((x) => (x.unitId === e.unitId && x.reference === e.reference ? updated : x))))
    } catch { setError('A correção foi recusada pelo núcleo (transição inválida para o estado atual).') }
  }

  return (
    <div>
      <PageHeader title="Fila de exceções de leitura"
        description="Crítica de leituras (II-2): exceções por regra disparada, com decisão de aceitar, corrigir ou pedir releitura." />
      {!port ? (
        <Alert tone="warning" title="Fiação pendente">
          A fila ainda não está ligada ao servidor (pendência P-II2-FIACAO). Nenhuma exceção é mostrada sem fonte.
          Os limiares das regras são definidos pelo tenant/regulador [A DEFINIR] (P-II2-LIMIARES).
        </Alert>
      ) : null}
      {error ? <Alert tone="error" title="Erro">{error}</Alert> : null}
      <Card><CardContent>
        <label>
          <input type="checkbox" checked={onlyOpen} onChange={(ev) => setOnlyOpen(ev.target.checked)} />
          {' '}Só abertas (pendentes ou a aguardar releitura)
        </label>
        {visible.length === 0 ? (
          <p>Nenhuma exceção {onlyOpen ? 'aberta ' : ''}na fila.</p>
        ) : (
          <Table caption="Fila de exceções de leitura" mobileCards>
            <TableHeader><TableRow>
              <TableHead>Referência</TableHead><TableHead>Unidade</TableHead>
              <TableHead>Regras disparadas</TableHead><TableHead>Estado</TableHead>
              <TableHead>Leitura corrigida</TableHead><TableHead>Decisão</TableHead>
            </TableRow></TableHeader>
            <TableBody>
              {visible.map((e) => (
                <TableRow key={`${e.reference}-${e.unitId}`}>
                  <TableCell label="Referência">{e.reference}</TableCell>
                  <TableCell label="Unidade">{e.unitId}</TableCell>
                  <TableCell label="Regras disparadas">
                    {e.findings.map((f) => (
                      <div key={f.ruleCode}>
                        <Badge variant="warning">{f.ruleCode}</Badge> {f.detail}
                        {f.observed !== null ? ` (observado: ${f.observed}${f.threshold !== null ? `, limiar: ${f.threshold}` : ''})` : ''}
                      </div>
                    ))}
                  </TableCell>
                  <TableCell label="Estado">{STATUS_LABEL[e.status]}</TableCell>
                  <TableCell label="Leitura corrigida">{e.correctedReading ?? '—'}</TableCell>
                  <TableCell label="Decisão">
                    {allowedTransitions(e.status).includes('ACCEPTED') ? (
                      <Button size="sm" onClick={() => void act(e, 'accept')}>Aceitar</Button>) : null}
                    {allowedTransitions(e.status).includes('REREADING_REQUESTED') ? (
                      <Button size="sm" variant="outline" onClick={() => void act(e, 'rereading')}>Pedir releitura</Button>) : null}
                    {allowedTransitions(e.status).includes('CORRECTED') ? (
                      <span>
                        <input type="number" min={0} step={1}
                          aria-label={`Leitura corrigida para ${e.unitId} em ${e.reference}`}
                          value={drafts[`${e.reference}-${e.unitId}`] ?? ''}
                          onChange={(ev) => setDrafts((d) => ({ ...d, [`${e.reference}-${e.unitId}`]: ev.target.value }))} />
                        <Button size="sm" variant="outline" onClick={() => void correct(e)}>Corrigir</Button>
                      </span>) : null}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent></Card>
    </div>
  )
}

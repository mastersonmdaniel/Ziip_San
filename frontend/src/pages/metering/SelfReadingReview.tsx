import { useEffect, useState } from 'react'
import { Alert, Spinner } from '@components/ui/Alert'
import { Badge } from '@components/ui/Badge'
import { Button } from '@components/ui/Button'
import { Card, CardContent } from '@components/ui/Card'
import { Field, Select } from '@components/ui/Field'
import { PageHeader } from '@components/ui/PageHeader'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@components/ui/Table'
import {
  ALL_REASONS,
  REASON_LABELS,
  STATUS_LABELS,
  filterByReason,
  sortReviewQueue,
  type SelfReadingOutcome,
  type SelfReadingReason,
  type SelfReadingReviewPort,
} from '@services/metering/selfReading'

/**
 * P-II7-TELA — Fila de revisao humana da autoleitura (backoffice).
 * Mostra a fila ordenada de HumanReviewQueue.pending() (competencia,
 * unidade, submissao) com o detalhe necessario a decisao humana:
 * motivos declarados, leitura OCR (nunca inventada), score por
 * componentes e prova geografica como o nucleo a define.
 * A DECISAO em si (aceitar/corrigir/pedir nova foto) NAO esta
 * tipificada no nucleo — e fluxo da P-II7-FIACAO e fica como residual
 * nomeado abaixo; esta tela nao inventa accoes de decisao.
 * Sem porta ligada (fiacao P-II7-FIACAO por fazer), a tela declara a
 * pendencia em vez de fingir dados. Rota: decisao de executor pela
 * convencao das rotas irmaes — /operacao/autoleitura/revisao.
 * Sem entrada de menu: nenhuma permissao deste nucleo esta provada
 * em controlador/pagina da main (pendencia registada no relatorio).
 */

function fmtScore(v: number): string {
  return v.toFixed(2).replace('.', ',')
}

function OutcomeDetail({ item }: { item: SelfReadingOutcome }) {
  return (
    <div>
      <p>Submissão: {item.submissionId}</p>
      <p>Unidade: {item.unitId}</p>
      <p>Competência: {item.reference}</p>
      <p>Estado: {STATUS_LABELS[item.status]}</p>
      <p>
        Leitura da foto:{' '}
        {item.ocrReading === null ? 'nenhuma leitura extraída' : item.ocrReading}
      </p>
      {item.score === null ? (
        <p>Score: sem score (não houve leitura extraída).</p>
      ) : (
        <>
          <p>Score total: {fmtScore(item.score.total)}</p>
          <p>Componente OCR: {fmtScore(item.score.ocrComponent)}</p>
          <p>Componente de coerência: {fmtScore(item.score.coherenceComponent)}</p>
          <p>Componente da foto: {fmtScore(item.score.photoComponent)}</p>
          <p>Componente geográfica: {fmtScore(item.score.geoComponent)}</p>
        </>
      )}
      {item.geo.present ? (
        <>
          <p>
            Distância à ligação:{' '}
            {item.geo.distanceMetres === null
              ? 'não medida'
              : `${item.geo.distanceMetres.toFixed(1).replace('.', ',')} m`}
          </p>
          <p>Dentro da distância máxima: {item.geo.withinMaxDistance ? 'sim' : 'não'}</p>
          <p>Precisão suficiente: {item.geo.accuracySufficient ? 'sim' : 'não'}</p>
        </>
      ) : (
        <p>Prova geográfica: não medida (sem prova ou sem coordenadas da ligação).</p>
      )}
      <p>
        Decisão humana (aceitar, corrigir ou pedir nova foto): pendente da fiação
        P-II7-FIACAO — o núcleo não tipifica esta decisão.
      </p>
    </div>
  )
}

export function SelfReadingReview({ port }: { port?: SelfReadingReviewPort }) {
  const [items, setItems] = useState<SelfReadingOutcome[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [reason, setReason] = useState<SelfReadingReason | ''>('')
  const [selectedId, setSelectedId] = useState<string | null>(null)

  useEffect(() => {
    if (!port) return
    let alive = true
    port
      .pending()
      .then((v) => {
        if (alive) setItems(sortReviewQueue(v))
      })
      .catch((e: unknown) => {
        if (alive) setError(e instanceof Error ? e.message : String(e))
      })
    return () => {
      alive = false
    }
  }, [port])

  const visible = items === null ? [] : filterByReason(items, reason)
  const selected = visible.find((o) => o.submissionId === selectedId) ?? null

  return (
    <div>
      <PageHeader
        title="Autoleitura — revisão humana"
        description="Fila de submissões que aguardam conferência humana, na ordem do núcleo (competência, unidade, submissão)."
      />
      <Card>
        <CardContent>
          {!port && (
            <Alert>
              Fila ainda não ligada: a fiação desta fila ao núcleo é a pendência
              P-II7-FIACAO. Nenhum dado é apresentado sem a porta.
            </Alert>
          )}
          {error && <Alert>{error}</Alert>}
          {port && items === null && !error && <Spinner />}
          {items !== null && (
            <>
              <Field label="Filtrar por motivo">
                <Select
                  value={reason}
                  onChange={(e) => setReason(e.target.value as SelfReadingReason | '')}
                >
                  <option value="">Todos os motivos</option>
                  {ALL_REASONS.map((r) => (
                    <option key={r} value={r}>
                      {REASON_LABELS[r]}
                    </option>
                  ))}
                </Select>
              </Field>
              <Table caption="Fila de revisão humana da autoleitura" mobileCards>
                <TableHeader>
                  <TableRow>
                    <TableHead>Competência</TableHead>
                    <TableHead>Unidade</TableHead>
                    <TableHead>Submissão</TableHead>
                    <TableHead>Motivos</TableHead>
                    <TableHead>Score</TableHead>
                    <TableHead>Detalhe</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {visible.map((item) => (
                    <TableRow key={item.submissionId}>
                      <TableCell label="Competência">{item.reference}</TableCell>
                      <TableCell label="Unidade">{item.unitId}</TableCell>
                      <TableCell label="Submissão">{item.submissionId}</TableCell>
                      <TableCell label="Motivos">
                        {item.reasons.map((r) => (
                          <Badge key={r}>{REASON_LABELS[r]}</Badge>
                        ))}
                      </TableCell>
                      <TableCell label="Score">
                        {item.score === null ? 'sem score' : fmtScore(item.score.total)}
                      </TableCell>
                      <TableCell label="Detalhe">
                        <Button onClick={() => setSelectedId(item.submissionId)}>
                          Ver detalhe
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
              {visible.length === 0 ? <p>Nenhuma submissão neste filtro.</p> : null}
              {selected && <OutcomeDetail item={selected} />}
            </>
          )}
        </CardContent>
      </Card>
    </div>
  )
}

import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Spinner } from '@components/ui/Alert'
import { Badge } from '@components/ui/Badge'
import { Button } from '@components/ui/Button'
import { Card, CardContent } from '@components/ui/Card'
import { Field, Input } from '@components/ui/Field'
import { PageHeader } from '@components/ui/PageHeader'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@components/ui/Table'
import { useCan } from '@hooks/usePermissions'
import { errorMessage } from '@services/registry/errors'
import { changeSets, type ChangeSetItem } from '@services/gis/changeSets'

/**
 * GIS-04: tela de revisão de pacote de edição. Mostra, por feição, o
 * antes (snapshot da versão de base) e o depois (estado proposto) nos
 * atributos e na geometria em WKT, os problemas de validação do pacote
 * e as decisões anteriores. O mapa web (GIS-06) ainda não existe no
 * produto: por isso o antes/depois é tabular/textual, sem fingir uma
 * renderização de mapa. Aprovar aplica o pacote numa só transação no
 * servidor; aviso aberto exige a justificativa do revisor.
 */
export function ChangeSetReview({ changeSetId }: { changeSetId: string }) {
  const canReview = useCan('gis:change-set-review')
  const queryClient = useQueryClient()
  const [reason, setReason] = useState('')
  const [justification, setJustification] = useState('')
  const [actionError, setActionError] = useState<string | null>(null)

  const detail = useQuery({
    queryKey: ['gis', 'change-sets', changeSetId, 'review'],
    queryFn: () => changeSets.reviewDetail(changeSetId),
  })

  const invalidate = () =>
    queryClient.invalidateQueries({ queryKey: ['gis', 'change-sets', changeSetId] })

  const approve = useMutation({
    mutationFn: () => changeSets.approve(changeSetId, justification.trim() || undefined),
    onSuccess: invalidate,
    onError: (e) => setActionError(errorMessage(e)),
  })
  const returnToAuthor = useMutation({
    mutationFn: () => changeSets.returnToAuthor(changeSetId, reason.trim()),
    onSuccess: invalidate,
    onError: (e) => setActionError(errorMessage(e)),
  })
  const reject = useMutation({
    mutationFn: () => changeSets.reject(changeSetId, reason.trim()),
    onSuccess: invalidate,
    onError: (e) => setActionError(errorMessage(e)),
  })

  function onReturn(e: FormEvent) {
    e.preventDefault()
    setActionError(null)
    returnToAuthor.mutate()
  }

  // Correção M6(a): Rejeitar NÃO é submit do formulário de devolução —
  // é botão próprio, sem depender de preventDefault no clique para não
  // disparar a devolução junto (dupla fiação).
  function onReject() {
    setActionError(null)
    reject.mutate()
  }

  if (detail.isLoading) return <Spinner />
  if (detail.isError) return <Alert>{errorMessage(detail.error)}</Alert>
  if (!detail.data) return null

  const { changeSet: set, items, reviews, validationIssues } = detail.data
  const hasOpenError = validationIssues.some((i) => i.severity === 'ERROR' && i.status === 'OPEN')
  const hasOpenWarning = validationIssues.some(
    (i) => i.severity === 'WARNING' && i.status === 'OPEN',
  )
  // Correção M6(b): cada ação aparece só nos estados que ela aceita
  // (máquinas idênticas no banco, na V13_9, e no Java,
  // GisChangeSetLifecycle). Aprovar-e-aplicar chama
  // gis_apply_change_set, que só aceita IN_REVIEW/APPROVED; em
  // SUBMITTED o pacote ainda nem entrou em revisão — oferecer Aprovar
  // ali era clique para erro certo. Devolver vale em SUBMITTED e
  // IN_REVIEW; rejeitar, só em IN_REVIEW; em APPROVED só resta aplicar.
  const canApprove = set.status === 'IN_REVIEW' || set.status === 'APPROVED'
  const canReturn = set.status === 'SUBMITTED' || set.status === 'IN_REVIEW'
  const canReject = set.status === 'IN_REVIEW'
  const canDecide = canApprove || canReturn || canReject

  return (
    <div>
      <PageHeader
        title={`Pacote ${set.type} — ${set.status}`}
        description={`Autor ${set.authorId} · ${items.length} feições · canal ${set.channel}`}
      />
      {set.technicalReport && (
        <Card>
          <CardContent>
            <strong>Relatório técnico</strong>
            <p>{set.technicalReport}</p>
          </CardContent>
        </Card>
      )}

      {validationIssues.length > 0 && (
        <Card>
          <CardContent>
            <strong>Validação do pacote</strong>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Regra</TableHead>
                  <TableHead>Severidade</TableHead>
                  <TableHead>Feição</TableHead>
                  <TableHead>Estado</TableHead>
                  <TableHead>Mensagem</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {validationIssues.map((issue, idx) => (
                  <TableRow key={idx}>
                    <TableCell>{issue.ruleCode}</TableCell>
                    <TableCell>
                      <Badge>{issue.severity}</Badge>
                    </TableCell>
                    <TableCell>{`${issue.featureTable} ${issue.featureId}`}</TableCell>
                    <TableCell>{issue.status}</TableCell>
                    <TableCell>{issue.message}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}

      {items.map((item) => (
        <ItemDiff key={item.id} item={item} />
      ))}

      {reviews.length > 0 && (
        <Card>
          <CardContent>
            <strong>Decisões anteriores</strong>
            <ul>
              {reviews.map((r) => (
                <li key={r.id}>
                  {r.decision} por {r.reviewerId} em {r.createdAt}
                  {r.reason ? ` — ${r.reason}` : ''}
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      )}

      {canReview && canDecide && (
        <Card>
          <CardContent>
            {actionError && <Alert>{actionError}</Alert>}
            {hasOpenError && (
              <Alert>Pacote com problema de severidade erro não pode ser aprovado.</Alert>
            )}
            {canApprove && hasOpenWarning && (
              <Field label="Justificativa do revisor (aviso aberto)">
                <Input value={justification} onChange={(e) => setJustification(e.target.value)} />
              </Field>
            )}
            {canApprove && (
              <Button
                disabled={hasOpenError || approve.isPending}
                onClick={() => {
                  setActionError(null)
                  approve.mutate()
                }}
              >
                Aprovar e aplicar
              </Button>
            )}
            {(canReturn || canReject) && (
              <form onSubmit={onReturn}>
                <Field label="Motivo (devolução ou rejeição)">
                  <Input value={reason} onChange={(e) => setReason(e.target.value)} />
                </Field>
                {canReturn && (
                  <Button type="submit" disabled={!reason.trim() || returnToAuthor.isPending}>
                    Devolver ao autor
                  </Button>
                )}
                {canReject && (
                  <Button type="button" disabled={!reason.trim() || reject.isPending} onClick={onReject}>
                    Rejeitar
                  </Button>
                )}
              </form>
            )}
          </CardContent>
        </Card>
      )}
    </div>
  )
}

/** Antes e depois de uma feição: união das chaves dos dois estados. */
function ItemDiff({ item }: { item: ChangeSetItem }) {
  // Geometria fora da união de atributos: ela tem linha própria
  // (correção M6c), senão o geom/geom_point do snapshot viraria uma
  // linha de atributo duplicada e sem o depois em WKT.
  const keys = Array.from(
    new Set([...Object.keys(item.before ?? {}), ...Object.keys(item.after ?? {})]),
  )
    .filter((key) => key !== 'geom' && key !== 'geom_point')
    .sort()
  const format = (value: unknown) =>
    value === undefined || value === null ? '—' : typeof value === 'object' ? JSON.stringify(value) : String(value)
  // Antes: a geometria do snapshot da base (to_jsonb da feição: GeoJSON
  // em geom — ou geom_point, nas estruturas de ponto). Depois: o WKT
  // proposto do item. Sem isto, o "antes" da geometria era sempre "—".
  const beforeGeom = item.before?.['geom'] ?? item.before?.['geom_point']
  const afterGeom = item.proposedGeomWkt ?? item.proposedGeomPointWkt
  return (
    <Card>
      <CardContent>
        <strong>
          {item.operation} — {item.featureTable} {item.featureId}
        </strong>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Atributo</TableHead>
              <TableHead>Antes</TableHead>
              <TableHead>Depois</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {keys.map((key) => (
              <TableRow key={key}>
                <TableCell>{key}</TableCell>
                <TableCell>{format(item.before?.[key])}</TableCell>
                <TableCell>{format(item.after?.[key])}</TableCell>
              </TableRow>
            ))}
            {(beforeGeom !== undefined || afterGeom) && (
              <TableRow>
                <TableCell>geometria (antes: GeoJSON do cadastro · depois: WKT proposto)</TableCell>
                <TableCell>{format(beforeGeom)}</TableCell>
                <TableCell>{afterGeom ?? '—'}</TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  )
}

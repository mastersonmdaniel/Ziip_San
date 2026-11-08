import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Spinner } from '@components/ui/Alert'
import { Badge } from '@components/ui/Badge'
import { Button } from '@components/ui/Button'
import { Card, CardContent } from '@components/ui/Card'
import { Field, Input } from '@components/ui/Field'
import { PageHeader } from '@components/ui/PageHeader'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@components/ui/Table'
import { errorMessage } from '@services/registry/errors'
import {
  fiscalDocuments,
  type FiscalQueueItem,
  type FiscalQueueState,
} from '@services/fiscal/fiscalDocuments'

/**
 * P2.33 — "Documentos fiscais" (item de menu do tenant, rota /documentos-fiscais,
 * permissão fiscal:document-read): a fila de pendências do operador — NFAg-e
 * autorizadas, rejeitadas (com a explicação do catálogo da P2.16), em contingência
 * (com o tempo decorrido), canceladas e substituídas, e cancelamentos pendentes —
 * com filtro por estado, competência e grupo. A NFS-e avulsa da P3.6 aparece pelo
 * mesmo modelo. Ações (reenvio, inutilização, cancelamento pendente) têm permissão
 * própria no servidor e exigem motivo: a tela não finge a regra — sem a permissão,
 * o servidor recusa e o erro aparece aqui. A visão da Ziip na Central é da P2.11.
 */
const STATE_LABELS: Record<FiscalQueueState, string> = {
  AUTHORIZED: 'Autorizada',
  REJECTED: 'Rejeitada',
  CONTINGENCY: 'Em contingência',
  CANCELLED: 'Cancelada',
  REPLACED: 'Substituída',
  CANCELLATION_PENDING: 'Cancelamento pendente',
}

const STATES = Object.keys(STATE_LABELS) as FiscalQueueState[]

export function DocumentosFiscais() {
  const queryClient = useQueryClient()
  const [state, setState] = useState<FiscalQueueState | ''>('')
  const [competence, setCompetence] = useState('')
  const [groupId, setGroupId] = useState('')
  const [responsibleId, setResponsibleId] = useState('')
  const [reason, setReason] = useState('')
  const [actionError, setActionError] = useState<string | null>(null)

  const queue = useQuery({
    queryKey: ['fiscal-documents', state, competence, groupId],
    queryFn: () =>
      fiscalDocuments.queue({
        state: state || undefined,
        competence: competence || undefined,
        billingGroupId: groupId || undefined,
      }),
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['fiscal-documents'] })

  const resend = useMutation({
    mutationFn: (id: string) => fiscalDocuments.resend(id, reason.trim()),
    onSuccess: invalidate,
    onError: (e) => setActionError(errorMessage(e)),
  })
  const cancel = useMutation({
    mutationFn: (vars: { id: string; contractResponsibleId: string }) =>
      fiscalDocuments.requestCancellation(vars.id, reason.trim(), vars.contractResponsibleId),
    onSuccess: invalidate,
    onError: (e) => setActionError(errorMessage(e)),
  })

  return (
    <div>
      <PageHeader title="Documentos fiscais" />
      <Card>
        <CardContent>
          <div role="tablist" aria-label="Filtro por estado">
            <Button onClick={() => setState('')}>Todos</Button>
            {STATES.map((s) => (
              <Button key={s} onClick={() => setState(s)}>
                {STATE_LABELS[s]}
              </Button>
            ))}
          </div>
          <Field label="Competência (AAAA-MM)">
            <Input value={competence} onChange={(e) => setCompetence(e.target.value)} placeholder="2026-12" />
          </Field>
          <Field label="Grupo de faturamento (id, opcional)">
            <Input value={groupId} onChange={(e) => setGroupId(e.target.value)} />
          </Field>
          <Field label="Responsável pelo contrato (id, para o cancelamento pendente)">
            <Input value={responsibleId} onChange={(e) => setResponsibleId(e.target.value)} />
          </Field>
          <Field label="Motivo (obrigatório para as ações)">
            <Input value={reason} onChange={(e) => setReason(e.target.value)} />
          </Field>
          {actionError && <Alert>{actionError}</Alert>}
          {queue.isLoading && <Spinner />}
          {queue.data && (
            <Table>
              <TableHead>
                <TableRow>
                  <TableHeader>Documento</TableHeader>
                  <TableHeader>Estado</TableHeader>
                  <TableHeader>Explicação / tempo</TableHeader>
                  <TableHeader>Ações</TableHeader>
                </TableRow>
              </TableHead>
              <TableBody>
                {queue.data.map((item: FiscalQueueItem) => (
                  <TableRow key={item.document.id}>
                    <TableCell>
                      {item.document.accessKey ?? item.document.id} — série {item.document.serie}, nº{' '}
                      {item.document.numero}
                    </TableCell>
                    <TableCell>
                      <Badge>{STATE_LABELS[item.queueState]}</Badge>
                    </TableCell>
                    <TableCell>
                      {item.rejectionExplanation
                        ? `${item.rejectionExplanation.catalogCode}: ${item.rejectionExplanation.officialMessage}`
                        : (item.contingencyElapsed ?? '')}
                    </TableCell>
                    <TableCell>
                      {item.queueState === 'CONTINGENCY' && (
                        <Button onClick={() => resend.mutate(item.document.id)}>Reenviar</Button>
                      )}
                      {item.queueState === 'AUTHORIZED' && (
                        <Button
                          onClick={() =>
                            cancel.mutate({ id: item.document.id, contractResponsibleId: responsibleId })
                          }
                        >
                          Pedir cancelamento
                        </Button>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </div>
  )
}

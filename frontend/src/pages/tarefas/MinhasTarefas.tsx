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
import { workItems, type DueFilter, type WorkItem } from '@services/work/workItems'

/**
 * P2.28 — "Minhas tarefas" (item de menu): a caixa do usuário logado, com TODO o
 * trabalho que o endereça (responsável, aprovador elegível, permissão ou delegação
 * vigente) em qualquer módulo, com filtro por prazo. Aprovações decidem pela porta
 * comum do servidor — quem solicitou não aprova (wf.self_approval, classe a): o
 * servidor recusa e o erro aparece aqui; a tela não finge a regra.
 *
 * REGISTRO NO MENU (integração): item "Minhas tarefas" no menu principal, rota
 * /minhas-tarefas, sem permissão própria (a caixa é do próprio usuário; o servidor
 * só devolve o que é dele). O contador do menu usa GET /me/work-items/count.
 */
const TYPE_LABELS: Record<WorkItem['type'], string> = {
  SERVICE_ORDER_APPROVAL: 'Aprovação de OS',
  BILL_RECTIFICATION_APPROVAL: 'Aprovação de retificação',
  PARAMETER_APPROVAL: 'Aprovação de parâmetro',
  DOCUMENT_APPROVAL: 'Aprovação de documento',
  PROTOCOL_DUE: 'Protocolo a vencer',
  SERVICE_ORDER_OVERDUE: 'OS atrasada',
  HELD_BILL: 'Conta retida',
  FISCAL_REJECTION: 'Rejeição fiscal',
  GIS_PACKAGE_REVIEW: 'Pacote GIS a revisar',
  CORRECTIVE_ACTION: 'Ação corretiva',
  CALIBRATION_DUE: 'Calibração vencendo',
  HOLDER_REQUEST: 'Pedido de titular',
  REGULATORY_OBLIGATION: 'Obrigação regulatória',
  RECONCILIATION_DIVERGENCE: 'Divergência de conciliação',
}

const FILTERS: { value: DueFilter; label: string }[] = [
  { value: 'ALL', label: 'Todas' },
  { value: 'OVERDUE', label: 'Vencidas' },
  { value: 'DUE_TODAY', label: 'Vencem hoje' },
  // Semântica real do servidor (DueFilter): datas locais de hoje até hoje + 7 dias, inclusive.
  { value: 'DUE_IN_7_DAYS', label: 'Vencem até hoje + 7 dias' },
]

export function MinhasTarefas() {
  const queryClient = useQueryClient()
  const [filter, setFilter] = useState<DueFilter>('ALL')
  const [reason, setReason] = useState('')
  const [actionError, setActionError] = useState<string | null>(null)

  const inbox = useQuery({
    queryKey: ['work-items', filter],
    queryFn: () => workItems.list(filter),
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['work-items'] })

  const start = useMutation({
    mutationFn: (id: string) => workItems.start(id),
    onSuccess: invalidate,
    onError: (e) => setActionError(errorMessage(e)),
  })
  const decide = useMutation({
    mutationFn: (vars: { id: string; approved: boolean }) =>
      workItems.decide(vars.id, vars.approved, reason.trim()),
    onSuccess: invalidate,
    onError: (e) => setActionError(errorMessage(e)),
  })

  return (
    <div>
      <PageHeader title="Minhas tarefas" />
      <Card>
        <CardContent>
          <div role="tablist" aria-label="Filtro por prazo">
            {FILTERS.map((f) => (
              <Button key={f.value} variant={filter === f.value ? 'primary' : 'ghost'}
                onClick={() => setFilter(f.value)}>
                {f.label}
              </Button>
            ))}
          </div>
          <Field label="Motivo da decisão (obrigatório para aprovar ou rejeitar)">
            <Input value={reason} onChange={(e) => setReason(e.target.value)} />
          </Field>
          {actionError ? <Alert variant="danger">{actionError}</Alert> : null}
          {inbox.isLoading ? <Spinner /> : null}
          {inbox.isError ? <Alert variant="danger">{errorMessage(inbox.error)}</Alert> : null}
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Tipo</TableHead>
                <TableHead>Origem</TableHead>
                <TableHead>Prazo</TableHead>
                <TableHead>Estado</TableHead>
                <TableHead>Ações</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(inbox.data ?? []).map((item) => (
                <TableRow key={item.id}>
                  <TableCell>{TYPE_LABELS[item.type]}</TableCell>
                  <TableCell>
                    <a href={item.recordAddress}>{item.objectType}</a>
                  </TableCell>
                  <TableCell>{item.dueAt ? new Date(item.dueAt).toLocaleString('pt-BR') : '—'}</TableCell>
                  <TableCell>
                    <Badge>{item.status}</Badge>
                    {item.escalatedAt ? <Badge>escalado</Badge> : null}
                  </TableCell>
                  <TableCell>
                    <Button onClick={() => start.mutate(item.id)}>Iniciar</Button>
                    {item.approvalTaskId ? (
                      <>
                        <Button disabled={reason.trim() === ''}
                          onClick={() => decide.mutate({ id: item.id, approved: true })}>
                          Aprovar
                        </Button>
                        <Button disabled={reason.trim() === ''}
                          onClick={() => decide.mutate({ id: item.id, approved: false })}>
                          Rejeitar
                        </Button>
                      </>
                    ) : null}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          {inbox.data && inbox.data.length === 0 ? <p>Nenhuma tarefa neste filtro.</p> : null}
        </CardContent>
      </Card>
    </div>
  )
}

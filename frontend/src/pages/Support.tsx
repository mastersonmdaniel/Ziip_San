import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Spinner } from '@components/ui/Alert'
import { Button } from '@components/ui/Button'
import { Card, CardContent } from '@components/ui/Card'
import { Field, Input, Select, Textarea } from '@components/ui/Field'
import { PageHeader } from '@components/ui/PageHeader'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@components/ui/Table'
import { errorMessage } from '@services/registry/errors'
import { formatDate } from '@utils/labels'
import {
  attachmentDownloadUrl,
  cancelTicket,
  createTicket,
  getTicket,
  listTickets,
  sendTicketMessage,
  uploadTicketAttachment,
  type Ticket,
  type TicketCategory,
  type TicketSeverity,
} from '@services/support'

const STATUS_LABEL: Record<string, string> = {
  NEW: 'Novo',
  IN_PROGRESS: 'Em atendimento',
  WAITING_CUSTOMER: 'Aguardando cliente',
  RESOLVED: 'Resolvido',
  CLOSED: 'Fechado',
  CANCELLED: 'Cancelado',
}

const CATEGORY_LABEL: Record<string, string> = {
  QUESTION: 'Dúvida',
  INCIDENT: 'Incidente',
  REQUEST: 'Requisição',
  IMPROVEMENT: 'Melhoria',
  SECURITY: 'Segurança',
  PRIVACY: 'Privacidade',
}

/**
 * Tela /support (P1.23): lista os chamados do usuário, abre um novo e
 * acompanha a conversa. O termo exibido é "chamado de suporte"
 * (SUP-AAAA-NNNNNN) — nunca "protocolo", que é o atendimento ao consumidor
 * (P1.36). A fila da Central (operador) NÃO vive aqui: ela é da P1.17.
 *
 * Minimização (ADR-0020): o formulário orienta a não incluir nem anexar
 * dado pessoal desnecessário e a citar a ligação em vez do consumidor. O
 * servidor marca contains_personal_data quando o texto contém CPF, CNPJ,
 * e-mail ou telefone — o aviso abaixo é o equivalente, no formulário, a
 * essa regra; a marca em si é do servidor, não de um cálculo do navegador.
 */
export function Support() {
  const queryClient = useQueryClient()
  const [statusFilter, setStatusFilter] = useState<string>('')
  const [selected, setSelected] = useState<string | null>(null)
  const [subject, setSubject] = useState('')
  const [category, setCategory] = useState<TicketCategory>('QUESTION')
  const [severity, setSeverity] = useState<TicketSeverity>('S4')
  const [initialMessage, setInitialMessage] = useState('')
  const [reply, setReply] = useState('')
  const [formError, setFormError] = useState<string | null>(null)
  const [attachmentFile, setAttachmentFile] = useState<File | null>(null)

  const tickets = useQuery({ queryKey: ['support-tickets', statusFilter], queryFn: () => listTickets(statusFilter || undefined) })
  const detail = useQuery({
    queryKey: ['support-ticket', selected],
    queryFn: () => getTicket(selected as string),
    enabled: selected !== null,
  })

  const create = useMutation({
    mutationFn: () => createTicket({ subject, category, severity, initialMessage }),
    onSuccess: (ticket) => {
      setFormError(null)
      setSubject('')
      setInitialMessage('')
      void queryClient.invalidateQueries({ queryKey: ['support-tickets'] })
      setSelected(ticket.number)
    },
    onError: (e) => setFormError(errorMessage(e)),
  })

  const send = useMutation({
    mutationFn: () => sendTicketMessage(selected as string, reply),
    onSuccess: () => {
      setReply('')
      void queryClient.invalidateQueries({ queryKey: ['support-ticket', selected] })
    },
    onError: (e) => setFormError(errorMessage(e)),
  })

  const cancel = useMutation({
    mutationFn: () => cancelTicket(selected as string),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['support-ticket', selected] })
      void queryClient.invalidateQueries({ queryKey: ['support-tickets'] })
    },
    onError: (e) => setFormError(errorMessage(e)),
  })

  const attach = useMutation({
    mutationFn: async () => {
      const messages = detail.data?.messages ?? []
      const last = messages[messages.length - 1]
      if (!last || !attachmentFile || !selected) throw new Error('Escolha a mensagem e o arquivo.')
      return uploadTicketAttachment(selected, last.id, attachmentFile)
    },
    onSuccess: () => {
      setAttachmentFile(null)
      void queryClient.invalidateQueries({ queryKey: ['support-ticket', selected] })
    },
    onError: (e) => setFormError(errorMessage(e)),
  })

  const ticket = detail.data?.ticket as Ticket | undefined

  return (
    <div className="space-y-6">
      <PageHeader
        title="Suporte"
        description="Chamados de suporte ao Ziip, numerados SUP-AAAA-NNNNNN. Não confunda com o protocolo de atendimento ao consumidor: o chamado é o seu pedido ao Ziip."
      />
      {formError ? (
        <Alert tone="error" title="Não foi possível concluir a operação">
          {formError}
        </Alert>
      ) : null}

      <Card>
        <CardContent className="pt-6 space-y-4">
          <h2 className="text-lg font-semibold">Abrir chamado</h2>
          <Alert tone="info" title="Não inclua dado pessoal desnecessário">
            Evite CPF, CNPJ, e-mail ou telefone no assunto, na mensagem e nos anexos; cite a
            ligação em vez do consumidor. O texto que contiver dado pessoal é marcado pelo
            servidor e as listas exibem o texto mascarado.
          </Alert>
          <Field label="Assunto">
            {(aria) => <Input {...aria} value={subject} onChange={(e) => setSubject(e.target.value)} />}
          </Field>
          <div className="grid grid-cols-2 gap-4">
            <Field label="Categoria">
              {(aria) => (
                <Select {...aria} value={category} onChange={(e) => setCategory(e.target.value as TicketCategory)}>
                  {Object.entries(CATEGORY_LABEL).map(([value, label]) => (
                    <option key={value} value={value}>{label}</option>
                  ))}
                </Select>
              )}
            </Field>
            <Field label="Severidade sugerida">
              {(aria) => (
                <Select {...aria} value={severity} onChange={(e) => setSeverity(e.target.value as TicketSeverity)}>
                  <option value="S1">S1 — função essencial parada</option>
                  <option value="S2">S2 — função essencial degradada</option>
                  <option value="S3">S3 — falha com contorno</option>
                  <option value="S4">S4 — dúvida ou falha sem impacto</option>
                  <option value="S5">S5 — solicitação ou melhoria</option>
                </Select>
              )}
            </Field>
          </div>
          <Field label="Mensagem inicial">
            {(aria) => <Textarea {...aria} value={initialMessage} onChange={(e) => setInitialMessage(e.target.value)} rows={4} />}
          </Field>
          <Button onClick={() => create.mutate()} disabled={!subject.trim() || !initialMessage.trim() || create.isPending}>
            Abrir chamado
          </Button>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="pt-6 space-y-4">
          <div className="flex items-center gap-4">
            <h2 className="text-lg font-semibold">Meus chamados</h2>
            <label className="text-sm">
              Estado{' '}
              <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)} className="rounded border px-2 py-1">
                <option value="">Todos</option>
                {Object.entries(STATUS_LABEL).map(([value, label]) => (
                  <option key={value} value={value}>{label}</option>
                ))}
              </select>
            </label>
          </div>
          {tickets.isPending ? <Spinner label="Carregando chamados..." /> : null}
          {tickets.data?.length === 0 ? <p>Nenhum chamado ainda. Abra o primeiro acima.</p> : null}
          {tickets.data && tickets.data.length > 0 ? (
            <Table caption="Meus chamados de suporte">
              <TableHeader>
                <TableRow>
                  <TableHead>Número</TableHead>
                  <TableHead>Assunto</TableHead>
                  <TableHead>Categoria</TableHead>
                  <TableHead>Estado</TableHead>
                  <TableHead>Aberto em</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {tickets.data.map((t) => (
                  <TableRow key={t.number}>
                    <TableCell>
                      <button type="button" className="underline" onClick={() => setSelected(t.number)}>
                        {t.number}
                      </button>
                    </TableCell>
                    <TableCell>{t.subject}</TableCell>
                    <TableCell>{CATEGORY_LABEL[t.category] ?? t.category}</TableCell>
                    <TableCell>{STATUS_LABEL[t.status] ?? t.status}</TableCell>
                    <TableCell>{formatDate(t.createdAt)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          ) : null}
        </CardContent>
      </Card>

      {selected && detail.data ? (
        <Card>
          <CardContent className="pt-6 space-y-4">
            <h2 className="text-lg font-semibold">
              {ticket?.number} — {ticket?.subject}
            </h2>
            <p className="text-sm">Estado: {ticket ? STATUS_LABEL[ticket.status] ?? ticket.status : ''}</p>
            {ticket?.containsPersonalData ? (
              <Alert tone="info" title="Este chamado contém dado pessoal">
                O chamado foi marcado pelo servidor; as listas exibem o texto mascarado.
              </Alert>
            ) : null}
            <div className="space-y-3">
              {detail.data.messages.map((m) => (
                <div key={m.id} className="rounded border p-3">
                  <p className="text-xs text-gray-500">
                    {m.authorType === 'AGENT' ? 'Ziip' : m.authorType === 'SYSTEM' ? 'Sistema' : 'Você'} — {formatDate(m.createdAt)}
                  </p>
                  <p className="whitespace-pre-wrap">{m.body}</p>
                </div>
              ))}
            </div>
            {detail.data.attachments.length > 0 ? (
              <div>
                <h3 className="font-semibold">Anexos</h3>
                <ul className="list-disc pl-5">
                  {detail.data.attachments.map((a) => (
                    <li key={a.id}>
                      {a.fileName} ({a.detectedMime}, {a.scanStatus})
                      {a.scanStatus === 'CLEAN' ? (
                        <>
                          {' '}
                          <a href={attachmentDownloadUrl(a.id)} className="underline">Baixar</a>
                        </>
                      ) : null}
                    </li>
                  ))}
                </ul>
              </div>
            ) : null}
            {ticket?.status !== 'CLOSED' && ticket?.status !== 'CANCELLED' ? (
              <>
                <Field label="Responder">
                  {(aria) => <Textarea {...aria} value={reply} onChange={(e) => setReply(e.target.value)} rows={3} />}
                </Field>
                <div className="flex gap-2">
                  <Button onClick={() => send.mutate()} disabled={!reply.trim() || send.isPending}>
                    Enviar resposta
                  </Button>
                  {ticket?.status === 'NEW' ? (
                    <Button onClick={() => cancel.mutate()} disabled={cancel.isPending}>
                      Cancelar chamado
                    </Button>
                  ) : null}
                </div>
                <Field label="Anexar à última mensagem (não anexe dado pessoal)">
                  {(aria) => (
                    <input
                      {...aria}
                      type="file"
                      onChange={(e) => setAttachmentFile(e.target.files?.[0] ?? null)}
                      className="w-full"
                    />
                  )}
                </Field>
                <Button onClick={() => attach.mutate()} disabled={!attachmentFile || attach.isPending}>
                  Enviar anexo
                </Button>
                <p className="text-xs text-gray-500">
                  Até 5 anexos por mensagem; PNG, JPEG, WebP, PDF, TXT, CSV ou XML, até 10 MB cada
                  (o envio pela tela respeita o limite de corpo da API). O anexo fica em quarentena
                  até a varredura e só é liberado para download quando limpo.
                </p>
              </>
            ) : null}
          </CardContent>
        </Card>
      ) : null}
    </div>
  )
}

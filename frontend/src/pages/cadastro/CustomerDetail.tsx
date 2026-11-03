import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Icon } from '@components/ui/Icon'
import { DeleteReasonDialog } from '@components/registry/DeleteReasonDialog'
import { Alert, Spinner } from '@components/ui/Alert'
import { Badge } from '@components/ui/Badge'
import { Button } from '@components/ui/Button'
import { Card, CardContent, CardHeader, CardTitle } from '@components/ui/Card'
import { PageHeader } from '@components/ui/PageHeader'
import { errorMessage, toApiError } from '@services/registry/errors'
import { customers } from '@services/registry/resources'
import { formatDate, orDash, PERSON_TYPE_LABEL, PHONE_TYPE_LABEL } from '@utils/labels'
import { formatDocument, detectDocumentKind } from '@utils/validators/document'
import { formatPhone, fromE164 } from '@utils/validators/phone'

/** Tempo máximo que um documento revelado fica na tela. */
export const REVEAL_SECONDS = 15

type RevealState =
  | { status: 'hidden' }
  | { status: 'loading' }
  | { status: 'shown'; text: string; secondsLeft: number }
  | { status: 'error'; message: string }

/**
 * Finalidades aceitas para ver o documento completo. Lista fechada de propósito: texto livre aqui
 * acabaria com dado pessoal na trilha de auditoria, que registra a finalidade de cada acesso.
 */
export const REVEAL_PURPOSES = [
  'Atendimento ao titular (confirmação de identidade)',
  'Conferência cadastral',
  'Cobrança e negociação de débito',
  'Cumprimento de obrigação legal ou ordem judicial',
] as const

/**
 * Revela o documento por ação explícita: chama POST .../reveal-document (a API decide a
 * permissão e registra o log) e mostra o resultado por tempo curto. O valor fica só neste
 * estado: nunca em storage, URL ou console, e some ao desmontar.
 */
export function RevealDocument({ customerId, masked }: { customerId: string; masked: string | null }) {
  const [state, setState] = useState<RevealState>({ status: 'hidden' })
  const [purpose, setPurpose] = useState('')
  const timer = useRef<number | null>(null)

  const stopTimer = () => {
    if (timer.current !== null) {
      window.clearInterval(timer.current)
      timer.current = null
    }
  }
  useEffect(() => stopTimer, [])

  const hide = () => {
    stopTimer()
    setState({ status: 'hidden' })
  }

  const reveal = async () => {
    setState({ status: 'loading' })
    try {
      const result = await customers.revealDocument(customerId, purpose)
      const limit = Math.min(result.expiresInSeconds ?? REVEAL_SECONDS, REVEAL_SECONDS)
      const text = formatDocument(result.document, detectDocumentKind(result.document))
      setState({ status: 'shown', text, secondsLeft: limit })
      stopTimer()
      timer.current = window.setInterval(() => {
        setState((s) => {
          if (s.status !== 'shown') return s
          if (s.secondsLeft <= 1) {
            stopTimer()
            return { status: 'hidden' }
          }
          return { ...s, secondsLeft: s.secondsLeft - 1 }
        })
      }, 1000)
    } catch (e) {
      const err = toApiError(e)
      setState({
        status: 'error',
        message: err.kind === 'forbidden' ? 'Você não tem permissão para ver o documento completo.' : err.message,
      })
    }
  }

  return (
    <div className="space-y-2">
      <p className="font-mono text-sm">
        <span className="sr-only">Documento: </span>
        {state.status === 'shown' ? state.text : orDash(masked)}
      </p>
      {masked ? (
        state.status === 'shown' ? (
          <div className="flex flex-wrap items-center gap-3">
            <Button size="sm" variant="outline" onClick={hide}>
              <Icon name="eye-off" className="h-4 w-4" />
              Ocultar agora
            </Button>
            <span className="text-xs text-muted-foreground" role="status">
              O documento some em {state.secondsLeft} s.
            </span>
          </div>
        ) : (
          <div className="flex flex-wrap items-end gap-2">
            <label className="text-xs text-muted-foreground">
              Finalidade do acesso
              <select
                className="mt-1 block rounded-lg border border-input bg-background px-2 py-1 text-sm"
                value={purpose}
                onChange={(e) => setPurpose(e.target.value)}
              >
                <option value="">Selecione...</option>
                {REVEAL_PURPOSES.map((p) => (
                  <option key={p} value={p}>
                    {p}
                  </option>
                ))}
              </select>
            </label>
            <Button
              size="sm"
              variant="outline"
              onClick={() => void reveal()}
              loading={state.status === 'loading'}
              disabled={purpose === ''}
            >
              <Icon name="eye" className="h-4 w-4" />
              Revelar documento
            </Button>
          </div>
        )
      ) : null}
      {state.status === 'error' ? <Alert tone="error">{state.message}</Alert> : null}
      {masked ? <p className="text-xs text-muted-foreground">Revelar exige permissão e fica registrado em log.</p> : null}
    </div>
  )
}

function Item({ term, children }: { term: string; children: ReactNode }) {
  return (
    <div>
      <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{term}</dt>
      <dd className="mt-0.5 text-sm">{children}</dd>
    </div>
  )
}

/** S4: ficha do cliente. Rota: /cadastro/clientes/:id */
export function CustomerDetail() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [deleting, setDeleting] = useState(false)
  const query = useQuery({ queryKey: ['customer', id], queryFn: ({ signal }) => customers.get(id, signal), enabled: id !== '' })

  if (query.isLoading) return <Spinner label="Carregando cliente..." />
  if (query.isError || !query.data) {
    return (
      <div className="space-y-4">
        <PageHeader title="Cliente" />
        <Alert tone="error" title="Não foi possível carregar o cliente">
          <p>{errorMessage(query.error)}</p>
          <Link to="/cadastro/clientes" className="mt-2 inline-block text-sm font-medium underline underline-offset-4">
            Voltar à lista
          </Link>
        </Alert>
      </div>
    )
  }

  const c = query.data
  const deleted = c.deletedAt !== null

  return (
    <div className="space-y-6">
      <PageHeader
        title={c.name}
        description={
          <>
            {PERSON_TYPE_LABEL[c.personType]}
            {c.customerType ? ` · ${c.customerType.name}` : ''}
            {deleted ? <Badge variant="destructive" className="ml-2">Excluído</Badge> : null}
          </>
        }
        actions={
          !deleted ? (
            <>
              <Link to={`/cadastro/clientes/${c.id}/editar`} className="inline-flex h-10 items-center gap-2 rounded-lg border bg-background px-4 text-sm font-medium hover:bg-accent">
                <Icon name="pencil" className="h-4 w-4" />
                Editar
              </Link>
              <Button variant="outline" onClick={() => setDeleting(true)}>
                <Icon name="trash-2" className="h-4 w-4" />
                Excluir
              </Button>
            </>
          ) : null
        }
      />

      <Card>
        <CardHeader>
          <CardTitle as="h2" className="text-base">Dados</CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="grid gap-x-6 gap-y-4 sm:grid-cols-2 lg:grid-cols-3">
            <Item term={c.personType === 'PF' ? 'CPF' : 'CNPJ'}>
              <RevealDocument customerId={c.id} masked={c.documentMasked} />
            </Item>
            {c.personType === 'PJ' ? <Item term="Nome fantasia">{orDash(c.tradeName)}</Item> : null}
            {c.personType === 'PF' ? <Item term="Data de nascimento">{formatDate(c.birthDate)}</Item> : null}
            {c.personType === 'PJ' ? <Item term="Ramo de atividade">{orDash(c.activityBranch?.name)}</Item> : null}
            {c.personType === 'PJ' ? <Item term="Responsável superior">{orDash(c.parentCustomer?.name)}</Item> : null}
            <Item term="Imóveis vinculados">{c.unitCount}</Item>
            <Item term="Dia de vencimento">{orDash(c.billing.dueDay)}</Item>
            <Item term="Fatura antecipada">{c.billing.advanceInvoice ? 'Sim' : 'Não'}</Item>
            <Item term="Bloqueado para negativação">{c.billing.blockNegativation ? 'Sim' : 'Não'}</Item>
          </dl>
        </CardContent>
      </Card>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle as="h2" className="text-base">Endereços</CardTitle>
          </CardHeader>
          <CardContent>
            {c.addresses.length === 0 ? (
              <p className="text-sm text-muted-foreground">Nenhum endereço.</p>
            ) : (
              <ul className="space-y-3 text-sm">
                {c.addresses.map((a, i) => (
                  <li key={i}>
                    {a.address.streetName}, {a.address.number}
                    {a.address.complement ? ` – ${a.address.complement}` : ''}
                    <br />
                    <span className="text-muted-foreground">
                      {[a.address.neighborhood, a.address.city, a.address.state].filter(Boolean).join(' · ')}
                      {a.address.cep ? ` · CEP ${a.address.cep}` : ''}
                    </span>
                    {a.correspondence ? <Badge variant="info" className="ml-2">Correspondência</Badge> : null}
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle as="h2" className="text-base">Contatos</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3 text-sm">
            {c.emails.length === 0 && c.phones.length === 0 ? <p className="text-muted-foreground">Nenhum contato.</p> : null}
            {c.emails.map((e) => (
              <p key={e.address}>{e.address}</p>
            ))}
            {c.phones.map((p, i) => (
              <p key={i}>
                {PHONE_TYPE_LABEL[p.type]}: {formatPhone(fromE164(p.number))}
                {p.extension ? ` (ramal ${p.extension})` : ''}
                {p.primary ? <Badge variant="info" className="ml-2">Principal</Badge> : null}
              </p>
            ))}
          </CardContent>
        </Card>
      </div>

      {deleting ? (
        <DeleteReasonDialog
          title="Excluir cliente"
          description={`A exclusão é lógica: ${c.name} deixa de aparecer nas pesquisas, mas os vínculos e o histórico são preservados.`}
          onClose={() => setDeleting(false)}
          onConfirm={async (reason) => {
            await customers.remove(c.id, reason)
            setDeleting(false)
            await queryClient.invalidateQueries({ queryKey: ['customers'] })
            navigate('/cadastro/clientes')
          }}
        />
      ) : null}
    </div>
  )
}

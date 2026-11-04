import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Spinner } from '@components/ui/Alert'
import { Button } from '@components/ui/Button'
import { Card, CardContent } from '@components/ui/Card'
import { Checkbox, Field, Input, Select } from '@components/ui/Field'
import { PageHeader } from '@components/ui/PageHeader'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@components/ui/Table'
import { errorMessage } from '@services/registry/errors'
import { formatDate } from '@utils/labels'
import {
  PUBLIC_API_SCOPES,
  WEBHOOK_EVENT_TYPES,
  createApiClient,
  createWebhookEndpoint,
  disableWebhookEndpoint,
  enableWebhookEndpoint,
  listApiClients,
  listWebhookDeliveries,
  listWebhookEndpoints,
  retryWebhookDelivery,
  revokeApiClient,
  rotateApiClientSecret,
  rotateWebhookEndpointSecret,
  sendWebhookTest,
} from '@services/integration'

/**
 * Tela /integracao (P2.26): credenciais da API pública (criar, girar,
 * revogar, ver o último uso) e webhooks de saída (endpoints, entregas,
 * reenvio por 30 dias). Só quem tem `integration:manage` (ADMIN) vê o
 * item no menu; o servidor nega a rota sem ela.
 *
 * O segredo em claro só volta na criação e na rotação, uma única vez:
 * aparece no aviso abaixo para copiar na hora e some ao navegar — a
 * listagem nunca o inclui, e ele nunca é guardado no navegador.
 * A tela não exibe dado pessoal: só identificadores, escopos e estados.
 */
export function Integration() {
  const queryClient = useQueryClient()
  const [notice, setNotice] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const clientsQuery = useQuery({ queryKey: ['integration-clients'], queryFn: listApiClients })
  const endpointsQuery = useQuery({ queryKey: ['integration-endpoints'], queryFn: listWebhookEndpoints })
  const deliveriesQuery = useQuery({ queryKey: ['integration-deliveries'], queryFn: () => listWebhookDeliveries() })

  const refreshAll = () => {
    void queryClient.invalidateQueries({ queryKey: ['integration-clients'] })
    void queryClient.invalidateQueries({ queryKey: ['integration-endpoints'] })
    void queryClient.invalidateQueries({ queryKey: ['integration-deliveries'] })
  }

  const run = <T,>(fn: () => Promise<T>, onOk: (value: T) => void) => {
    setError(null)
    setNotice(null)
    fn().then(onOk, (e: unknown) => setError(errorMessage(e)))
  }

  // ------------------------------------------------------------ cliente
  const [clientId, setClientId] = useState('')
  const [clientName, setClientName] = useState('')
  const [authMethod, setAuthMethod] = useState<'CLIENT_SECRET' | 'PRIVATE_KEY_JWT'>('CLIENT_SECRET')
  const [environment, setEnvironment] = useState<'PRODUCTION' | 'SANDBOX'>('PRODUCTION')
  const [clientScopes, setClientScopes] = useState<string[]>(['properties:read'])
  const [publicKeyPem, setPublicKeyPem] = useState('')

  const createClient = useMutation({
    mutationFn: () =>
      createApiClient({
        clientId,
        name: clientName,
        authMethod,
        scopes: clientScopes,
        environment,
        publicKeyPem: authMethod === 'PRIVATE_KEY_JWT' ? publicKeyPem : null,
      }),
    onSuccess: (created) => {
      setNotice(
        created.secret
          ? `Cliente criado. Copie o segredo agora — ele não será exibido novamente: ${created.secret}`
          : 'Cliente criado (private_key_jwt: sem segredo a copiar).',
      )
      refreshAll()
    },
    onError: (e: unknown) => setError(errorMessage(e)),
  })

  // ----------------------------------------------------------- endpoint
  const [endpointUrl, setEndpointUrl] = useState('')
  const [endpointTypes, setEndpointTypes] = useState<string[]>(['integration.webhook.test'])

  const createEndpoint = useMutation({
    mutationFn: () => createWebhookEndpoint(endpointUrl, endpointTypes),
    onSuccess: (created) => {
      setNotice(`Endpoint criado. Copie o segredo de assinatura agora — ele não será exibido novamente: ${created.secret}`)
      refreshAll()
    },
    onError: (e: unknown) => setError(errorMessage(e)),
  })

  const toggle = (list: string[], value: string, set: (next: string[]) => void) =>
    set(list.includes(value) ? list.filter((v) => v !== value) : [...list, value])

  return (
    <div className="space-y-6">
      <PageHeader title="Integrações" description="Clientes da API pública e webhooks de saída do tenant." />
      {notice ? <Alert tone="success">{notice}</Alert> : null}
      {error ? <Alert tone="error">{error}</Alert> : null}

      <Card>
        <CardContent>
          <h2 className="text-lg font-semibold">Clientes da API pública</h2>
          <p className="text-sm text-muted-foreground">
            Cliente OAuth 2.0 por tenant (client_credentials). O ambiente de teste (SANDBOX) devolve massa
            sintética e nunca toca os dados reais.
          </p>
          {clientsQuery.isPending ? <Spinner label="Carregando clientes..." /> : null}
          {clientsQuery.data ? (
            <Table caption="Clientes da API pública">
              <TableHeader>
                <TableRow>
                  <TableHead>Cliente</TableHead>
                  <TableHead>Nome</TableHead>
                  <TableHead>Método</TableHead>
                  <TableHead>Ambiente</TableHead>
                  <TableHead>Escopos</TableHead>
                  <TableHead>Último uso</TableHead>
                  <TableHead>Estado</TableHead>
                  <TableHead>Ações</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {clientsQuery.data.map((client) => (
                  <TableRow key={client.id}>
                    <TableCell>{client.clientId}</TableCell>
                    <TableCell>{client.name}</TableCell>
                    <TableCell>{client.authMethod}</TableCell>
                    <TableCell>{client.environment}</TableCell>
                    <TableCell>{client.scopes.join(', ')}</TableCell>
                    <TableCell>{client.lastUsedAt ? formatDate(client.lastUsedAt) : '—'}</TableCell>
                    <TableCell>{client.status}</TableCell>
                    <TableCell>
                      {client.authMethod === 'CLIENT_SECRET' && client.status === 'ACTIVE' ? (
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() =>
                            run(() => rotateApiClientSecret(client.id), (created) => {
                              setNotice(
                                created.secret
                                  ? `Novo segredo de ${client.clientId}. Copie agora — o anterior vale por 24 h e depois expira: ${created.secret}`
                                  : 'Segredo girado.',
                              )
                              refreshAll()
                            })
                          }
                        >
                          Girar segredo
                        </Button>
                      ) : null}{' '}
                      {client.status === 'ACTIVE' ? (
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => run(() => revokeApiClient(client.id), refreshAll)}
                        >
                          Revogar
                        </Button>
                      ) : null}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          ) : null}

          <form
            className="mt-4 space-y-3"
            onSubmit={(e) => {
              e.preventDefault()
              createClient.mutate()
            }}
          >
            <Field label="client_id" required id="integration-client-id">
              {(aria) => <Input {...aria} value={clientId} onChange={(e) => setClientId(e.target.value)} required />}
            </Field>
            <Field label="Nome" required id="integration-client-name">
              {(aria) => <Input {...aria} value={clientName} onChange={(e) => setClientName(e.target.value)} required />}
            </Field>
            <Field label="Método de autenticação" id="integration-auth-method">
              {(aria) => (
                <Select
                  {...aria}
                  value={authMethod}
                  onChange={(e) => setAuthMethod(e.target.value as 'CLIENT_SECRET' | 'PRIVATE_KEY_JWT')}
                >
                  <option value="CLIENT_SECRET">Segredo do cliente</option>
                  <option value="PRIVATE_KEY_JWT">private_key_jwt (chave RSA)</option>
                </Select>
              )}
            </Field>
            <Field label="Ambiente" id="integration-environment">
              {(aria) => (
                <Select
                  {...aria}
                  value={environment}
                  onChange={(e) => setEnvironment(e.target.value as 'PRODUCTION' | 'SANDBOX')}
                >
                  <option value="PRODUCTION">Produção</option>
                  <option value="SANDBOX">Teste (massa sintética)</option>
                </Select>
              )}
            </Field>
            <fieldset>
              <legend className="text-sm font-medium">Escopos</legend>
              {PUBLIC_API_SCOPES.map((scope) => (
                <Checkbox
                  key={scope}
                  label={scope}
                  checked={clientScopes.includes(scope)}
                  onChange={() => toggle(clientScopes, scope, setClientScopes)}
                />
              ))}
            </fieldset>
            {authMethod === 'PRIVATE_KEY_JWT' ? (
              <Field label="Chave pública RSA (PEM)" required id="integration-public-key">
                {(aria) => (
                  <textarea
                    {...aria}
                    className="w-full rounded border p-2 font-mono text-xs"
                    rows={4}
                    value={publicKeyPem}
                    onChange={(e) => setPublicKeyPem(e.target.value)}
                    required
                  />
                )}
              </Field>
            ) : null}
            <Button type="submit" loading={createClient.isPending}>
              Criar cliente
            </Button>
          </form>
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <h2 className="text-lg font-semibold">Webhooks de saída</h2>
          <p className="text-sm text-muted-foreground">
            Entrega por HTTPS com assinatura HMAC-SHA-256, reenvio com recuo exponencial e desativação
            automática após 5 falhas consecutivas. Só HTTPS, sem endereço privado.
          </p>
          {endpointsQuery.isPending ? <Spinner label="Carregando endpoints..." /> : null}
          {endpointsQuery.data ? (
            <Table caption="Endpoints de webhook">
              <TableHeader>
                <TableRow>
                  <TableHead>URL</TableHead>
                  <TableHead>Eventos</TableHead>
                  <TableHead>Falhas</TableHead>
                  <TableHead>Estado</TableHead>
                  <TableHead>Ações</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {endpointsQuery.data.map((endpoint) => (
                  <TableRow key={endpoint.id}>
                    <TableCell>{endpoint.url}</TableCell>
                    <TableCell>{endpoint.eventTypes.join(', ')}</TableCell>
                    <TableCell>{endpoint.consecutiveFailures}</TableCell>
                    <TableCell>{endpoint.status}</TableCell>
                    <TableCell>
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() =>
                          run(() => rotateWebhookEndpointSecret(endpoint.id), (created) => {
                            setNotice(`Novo segredo de assinatura. Copie agora — o anterior vale por 24 h: ${created.secret}`)
                            refreshAll()
                          })
                        }
                      >
                        Girar segredo
                      </Button>{' '}
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => run(() => sendWebhookTest(endpoint.id), () => {
                          setNotice('Evento de teste enfileirado para entrega.')
                          refreshAll()
                        })}
                      >
                        Enviar teste
                      </Button>{' '}
                      {endpoint.status === 'ACTIVE' ? (
                        <Button variant="outline" size="sm" onClick={() => run(() => disableWebhookEndpoint(endpoint.id), refreshAll)}>
                          Desativar
                        </Button>
                      ) : (
                        <Button variant="outline" size="sm" onClick={() => run(() => enableWebhookEndpoint(endpoint.id), refreshAll)}>
                          Reativar
                        </Button>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          ) : null}

          <form
            className="mt-4 space-y-3"
            onSubmit={(e) => {
              e.preventDefault()
              createEndpoint.mutate()
            }}
          >
            <Field label="URL (só HTTPS)" required id="integration-endpoint-url">
              {(aria) => <Input {...aria} value={endpointUrl} onChange={(e) => setEndpointUrl(e.target.value)} required />}
            </Field>
            <fieldset>
              <legend className="text-sm font-medium">Tipos de evento</legend>
              {WEBHOOK_EVENT_TYPES.map((type) => (
                <Checkbox
                  key={type}
                  label={type}
                  checked={endpointTypes.includes(type)}
                  onChange={() => toggle(endpointTypes, type, setEndpointTypes)}
                />
              ))}
            </fieldset>
            <Button type="submit" loading={createEndpoint.isPending}>
              Criar endpoint
            </Button>
          </form>
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <h2 className="text-lg font-semibold">Entregas</h2>
          <p className="text-sm text-muted-foreground">
            Reenvio manual disponível por 30 dias a partir da criação. A carga leva só identificadores.
          </p>
          {deliveriesQuery.isPending ? <Spinner label="Carregando entregas..." /> : null}
          {deliveriesQuery.data ? (
            <Table caption="Entregas de webhook">
              <TableHeader>
                <TableRow>
                  <TableHead>Evento</TableHead>
                  <TableHead>Estado</TableHead>
                  <TableHead>Tentativas</TableHead>
                  <TableHead>Último erro</TableHead>
                  <TableHead>Ações</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {deliveriesQuery.data.map((delivery) => (
                  <TableRow key={delivery.id}>
                    <TableCell>{delivery.eventName}</TableCell>
                    <TableCell>{delivery.status}</TableCell>
                    <TableCell>{delivery.attempts}</TableCell>
                    <TableCell>{delivery.lastError ?? '—'}</TableCell>
                    <TableCell>
                      {delivery.status !== 'SENT' ? (
                        <Button variant="outline" size="sm" onClick={() => run(() => retryWebhookDelivery(delivery.id), refreshAll)}>
                          Reenviar
                        </Button>
                      ) : null}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          ) : null}
        </CardContent>
      </Card>
    </div>
  )
}

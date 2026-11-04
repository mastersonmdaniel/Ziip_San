import { api } from '@services/api'

/**
 * API pública e webhooks de saída (P2.26) — telas do tenant.
 *
 * O segredo em claro só volta na criação e na rotação, uma única vez:
 * quem chama mostra e o usuário copia na hora; a listagem nunca o inclui.
 * Nada aqui é dado pessoal: a tela lista identificadores, escopos e
 * estados, nunca nome de titular, documento ou endereço.
 */

export interface ApiClient {
  id: string
  clientId: string
  name: string
  authMethod: 'CLIENT_SECRET' | 'PRIVATE_KEY_JWT'
  scopes: string[]
  rateLimitPerMinute: number | null
  ipAllowlist: string[]
  environment: 'PRODUCTION' | 'SANDBOX'
  status: 'ACTIVE' | 'REVOKED'
  lastUsedAt: string | null
  createdAt: string
}

export interface CreatedApiClient {
  client: ApiClient
  secret: string | null
}

export interface CreateApiClientInput {
  clientId: string
  name: string
  authMethod: 'CLIENT_SECRET' | 'PRIVATE_KEY_JWT'
  scopes: string[]
  rateLimitPerMinute?: number | null
  ipAllowlist?: string[]
  environment?: 'PRODUCTION' | 'SANDBOX'
  publicKeyPem?: string | null
}

export interface WebhookEndpoint {
  id: string
  url: string
  eventTypes: string[]
  status: 'ACTIVE' | 'DISABLED'
  consecutiveFailures: number
  disabledAt: string | null
  createdAt: string
}

export interface CreatedWebhookEndpoint {
  endpoint: WebhookEndpoint
  secret: string
}

export interface WebhookDelivery {
  id: string
  endpointId: string
  eventId: string
  eventName: string
  eventVersion: number
  aggregateKey: string
  payload: string
  status: 'PENDING' | 'SENT' | 'FAILED'
  attempts: number
  nextAttemptAt: string | null
  lastError: string | null
  deliveredAt: string | null
  createdAt: string
}

/** Escopos da v1 da API pública (o catálogo recurso:ação da P2.26). */
export const PUBLIC_API_SCOPES = [
  'properties:read',
  'bills:read',
  'payments:read',
  'service-orders:read',
  'service-orders:write',
] as const

/** Tipos de evento entregáveis por webhook (o catálogo da P2.26). */
export const WEBHOOK_EVENT_TYPES = [
  'integration.webhook.test',
  'billing.bill.issued',
  'collection.payment.received',
  'serviceorder.status.changed',
  'support.ticket.status',
] as const

const BASE = '/tenant/integration'

export async function listApiClients(): Promise<ApiClient[]> {
  const { data } = await api.get<ApiClient[]>(`${BASE}/clients`)
  return data
}

export async function createApiClient(input: CreateApiClientInput): Promise<CreatedApiClient> {
  const { data } = await api.post<CreatedApiClient>(`${BASE}/clients`, input)
  return data
}

export async function rotateApiClientSecret(id: string): Promise<CreatedApiClient> {
  const { data } = await api.post<CreatedApiClient>(`${BASE}/clients/${encodeURIComponent(id)}/rotate-secret`, {})
  return data
}

export async function revokeApiClient(id: string): Promise<void> {
  await api.post(`${BASE}/clients/${encodeURIComponent(id)}/revoke`, {})
}

export async function listWebhookEndpoints(): Promise<WebhookEndpoint[]> {
  const { data } = await api.get<WebhookEndpoint[]>(`${BASE}/webhooks/endpoints`)
  return data
}

export async function createWebhookEndpoint(url: string, eventTypes: string[]): Promise<CreatedWebhookEndpoint> {
  const { data } = await api.post<CreatedWebhookEndpoint>(`${BASE}/webhooks/endpoints`, { url, eventTypes })
  return data
}

export async function rotateWebhookEndpointSecret(id: string): Promise<CreatedWebhookEndpoint> {
  const { data } = await api.post<CreatedWebhookEndpoint>(
    `${BASE}/webhooks/endpoints/${encodeURIComponent(id)}/rotate-secret`, {},
  )
  return data
}

export async function disableWebhookEndpoint(id: string): Promise<void> {
  await api.post(`${BASE}/webhooks/endpoints/${encodeURIComponent(id)}/disable`, {})
}

export async function enableWebhookEndpoint(id: string): Promise<void> {
  await api.post(`${BASE}/webhooks/endpoints/${encodeURIComponent(id)}/enable`, {})
}

export async function sendWebhookTest(id: string): Promise<string> {
  const { data } = await api.post<string>(`${BASE}/webhooks/endpoints/${encodeURIComponent(id)}/send-test`, {})
  return data
}

export async function listWebhookDeliveries(endpointId?: string, status?: string): Promise<WebhookDelivery[]> {
  const { data } = await api.get<WebhookDelivery[]>(`${BASE}/webhooks/deliveries`, {
    params: { ...(endpointId ? { endpointId } : {}), ...(status ? { status } : {}) },
  })
  return data
}

export async function retryWebhookDelivery(id: string): Promise<void> {
  await api.post(`${BASE}/webhooks/deliveries/${encodeURIComponent(id)}/retry`, {})
}

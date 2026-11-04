import { api } from '@services/api'

/**
 * Chamados de suporte v0 (P1.23; ADR-0020). O termo é "chamado de suporte"
 * (pedido do tenant ao Ziip, SUP-AAAA-NNNNNN) — nunca "protocolo", que é o
 * atendimento do tenant ao consumidor (P1.36). A API de ajuda (/help) NÃO
 * está aqui: ela é pendência declarada de outra frente.
 */

export type TicketStatus = 'NEW' | 'IN_PROGRESS' | 'WAITING_CUSTOMER' | 'RESOLVED' | 'CLOSED' | 'CANCELLED'
export type TicketCategory = 'QUESTION' | 'INCIDENT' | 'REQUEST' | 'IMPROVEMENT' | 'SECURITY' | 'PRIVACY'
export type TicketSeverity = 'S1' | 'S2' | 'S3' | 'S4' | 'S5'

export interface Ticket {
  id: string
  number: string
  requesterId: string
  subject: string
  category: TicketCategory
  module: string | null
  originRoute: string | null
  appVersion: string | null
  correlationId: string | null
  problemCode: string | null
  severity: TicketSeverity
  status: TicketStatus
  level: string | null
  assigneeId: string | null
  containsPersonalData: boolean
  reopenCount: number
  createdAt: string
  updatedAt: string
}

export interface TicketMessage {
  id: string
  ticketId: string
  authorType: 'REQUESTER' | 'AGENT' | 'SYSTEM'
  authorId: string | null
  visibility: 'PUBLIC' | 'INTERNAL'
  body: string
  containsPersonalData: boolean
  createdAt: string
}

export interface TicketAttachment {
  id: string
  ticketId: string
  messageId: string | null
  fileName: string
  detectedMime: string
  sizeBytes: number
  sha256: string
  objectKey: string
  scanStatus: 'QUARANTINED' | 'CLEAN' | 'INFECTED' | 'NOT_SCANNED'
  threat: string | null
  createdAt: string
}

export interface TicketDetail {
  ticket: Ticket
  messages: TicketMessage[]
  attachments: TicketAttachment[]
  events: Array<Record<string, unknown>>
}

export interface CreateTicketInput {
  subject: string
  category: TicketCategory
  severity: TicketSeverity
  initialMessage: string
}

export async function listTickets(status?: string): Promise<Ticket[]> {
  const { data } = await api.get<Ticket[]>('/support/tickets', { params: status ? { status } : {} })
  return data
}

export async function getTicket(number: string): Promise<TicketDetail> {
  const { data } = await api.get<TicketDetail>(`/support/tickets/${encodeURIComponent(number)}`)
  return data
}

export async function createTicket(input: CreateTicketInput): Promise<Ticket> {
  const { data } = await api.post<Ticket>('/support/tickets', input)
  return data
}

export async function sendTicketMessage(number: string, body: string): Promise<TicketMessage> {
  const { data } = await api.post<TicketMessage>(`/support/tickets/${encodeURIComponent(number)}/messages`, { body })
  return data
}

export async function cancelTicket(number: string): Promise<Ticket> {
  const { data } = await api.post<Ticket>(`/support/tickets/${encodeURIComponent(number)}/cancel`, {})
  return data
}

function fileToBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => {
      const result = reader.result as string
      resolve(result.includes(',') ? result.split(',')[1] : result)
    }
    reader.onerror = () => reject(reader.error)
    reader.readAsDataURL(file)
  })
}

export async function uploadTicketAttachment(
  number: string,
  messageId: string,
  file: File,
): Promise<TicketAttachment> {
  const contentBase64 = await fileToBase64(file)
  const { data } = await api.post<TicketAttachment>(
    `/support/tickets/${encodeURIComponent(number)}/attachments`,
    { messageId, fileName: file.name, contentBase64 },
  )
  return data
}

export function attachmentDownloadUrl(attachmentId: string): string {
  return `/api/v1/support/tickets/attachments/${encodeURIComponent(attachmentId)}/download`
}

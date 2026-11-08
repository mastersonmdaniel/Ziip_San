import { api } from '@services/api'

/** Contratos da fila "Documentos fiscais" do tenant (P2.33). O baseURL do cliente HTTP já é /api/v1. */
export const FISCAL_DOCUMENTS_BASE = '/fiscal/documents'

export type FiscalQueueState =
  | 'AUTHORIZED'
  | 'REJECTED'
  | 'CONTINGENCY'
  | 'CANCELLED'
  | 'REPLACED'
  | 'CANCELLATION_PENDING'

export interface FiscalDocumentView {
  id: string
  tenantId: string
  kind: 'NFAGE' | 'NFSE_AVULSA'
  nfageStatus: string | null
  accessKey: string | null
  serie: number
  numero: number
  competence: string
  billingGroupId: string | null
  contingency: boolean
  rejectionCode: string | null
  sourceType: string | null
  sourceId: string | null
  issuedAt: string | null
}

export interface FiscalQueueItem {
  document: FiscalDocumentView
  queueState: FiscalQueueState
  rejectionExplanation: { catalogCode: string; officialMessage: string } | null
  /** ISO-8601 duration (ex.: PT26H15M) da contingência ainda não transmitida. */
  contingencyElapsed: string | null
}

export interface FiscalOperatorAction {
  id: string
  type: 'RESEND' | 'VOIDING' | 'CANCELLATION_REQUEST'
  status: 'REQUESTED' | 'PENDING' | 'COMPLETED' | 'FAILED'
  fiscalDocumentId: string | null
  protocol: string | null
}

export const fiscalDocuments = {
  async queue(params: {
    state?: FiscalQueueState
    competence?: string
    billingGroupId?: string
  }): Promise<FiscalQueueItem[]> {
    const { data } = await api.get<FiscalQueueItem[]>(FISCAL_DOCUMENTS_BASE, { params })
    return data
  },
  async resend(id: string, reason: string): Promise<FiscalOperatorAction> {
    const { data } = await api.post<FiscalOperatorAction>(`${FISCAL_DOCUMENTS_BASE}/${id}/resend`, { reason })
    return data
  },
  async voidNumbers(body: {
    issuerId: string
    serie: number
    numberFrom: number
    numberTo: number
    reason: string
    tpAmb: number
  }): Promise<FiscalOperatorAction> {
    const { data } = await api.post<FiscalOperatorAction>(`${FISCAL_DOCUMENTS_BASE}/voidings`, body)
    return data
  },
  async requestCancellation(
    id: string,
    reason: string,
    contractResponsibleId: string,
  ): Promise<FiscalOperatorAction> {
    const { data } = await api.post<FiscalOperatorAction>(
      `${FISCAL_DOCUMENTS_BASE}/${id}/cancellation-requests`,
      { reason, contractResponsibleId },
    )
    return data
  },
}

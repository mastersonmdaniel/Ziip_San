import { api } from '@services/api'
import { toApiError } from '@services/registry/errors'

/** Contratos da GIS-04 (pacotes de edição). O baseURL do cliente HTTP já é /api/v1. */
export const CHANGE_SETS_BASE = '/gis/change-sets'

export interface ChangeSet {
  id: string
  type: string
  channel: string
  status: string
  authorId: string
  extentWkt: string | null
  serviceOrderRef: string | null
  projectRef: string | null
  technicalReport: string | null
  qualityReport: Record<string, unknown>
  submittedAt: string | null
  reviewedBy: string | null
  reviewedAt: string | null
  appliedAt: string | null
  createdAt: string
  updatedAt: string
  itemCount: number
}

export interface ChangeSetItem {
  id: string
  operation: 'INSERT' | 'UPDATE' | 'RETIRE'
  featureTable: string
  featureId: string
  baseVersion: number | null
  before: Record<string, unknown>
  after: Record<string, unknown>
  proposedGeomWkt: string | null
  proposedGeomPointWkt: string | null
  validationResult: Record<string, unknown>
}

export interface ChangeSetReview {
  id: string
  decision: 'APPROVED' | 'RETURNED' | 'REJECTED'
  reviewerId: string
  reason: string | null
  createdAt: string
}

export interface ValidationIssue {
  ruleCode: string
  severity: 'ERROR' | 'WARNING'
  featureTable: string
  featureId: string
  status: string
  message: string
}

export interface ReviewDetail {
  changeSet: ChangeSet
  items: ChangeSetItem[]
  reviews: ChangeSetReview[]
  validationIssues: ValidationIssue[]
}

export interface PendingReview {
  changeSetId: string
  type: string
  channel: string
  status: string
  authorId: string
  submittedAt: string | null
  createdAt: string
  itemCount: number
}

/**
 * Executa a requisição e converte qualquer falha em ApiError
 * (problem+json tratado), como o cliente do registry — correção B3:
 * sem a conversão, a página recebia o erro cru do axios.
 */
async function request<T>(promise: Promise<{ data: T }>): Promise<T> {
  try {
    const response = await promise
    return response.data
  } catch (error) {
    throw toApiError(error)
  }
}

export const changeSets = {
  pendingReview: () =>
    request<PendingReview[]>(api.get(`${CHANGE_SETS_BASE}/pending-review`)),
  reviewDetail: (id: string) =>
    request<ReviewDetail>(api.get(`${CHANGE_SETS_BASE}/${id}/review`)),
  approve: (id: string, warningJustification?: string) =>
    request<ChangeSet>(
      api.post(`${CHANGE_SETS_BASE}/${id}/approve`, {
        warningJustification: warningJustification || null,
      }),
    ),
  returnToAuthor: (id: string, reason: string) =>
    request<ChangeSet>(api.post(`${CHANGE_SETS_BASE}/${id}/return`, { reason })),
  reject: (id: string, reason: string) =>
    request<ChangeSet>(api.post(`${CHANGE_SETS_BASE}/${id}/reject`, { reason })),
}

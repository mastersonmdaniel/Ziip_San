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

/** Corpo de criação do pacote (CreateInput do controlador GIS-04). */
export interface CreateChangeSetInput {
  type: string
  channel: string
  extentWkt?: string | null
  serviceOrderRef?: string | null
  projectRef?: string | null
  technicalReport?: string | null
  qualityReport?: Record<string, unknown> | null
}

/** Corpo de um item do pacote (ItemInput do controlador GIS-04). */
export interface ChangeSetItemInput {
  operation: 'INSERT' | 'UPDATE' | 'RETIRE'
  featureTable: string
  featureId: string
  proposedState?: Record<string, unknown>
  proposedGeomWkt?: string | null
  proposedGeomPointWkt?: string | null
}

/**
 * Tabelas de feição que um item de pacote pode referenciar —
 * espelho de GisChangeSetLifecycle.FEATURE_TABLES (backend); a
 * autoridade continua a ser o servidor, isto só falha cedo.
 */
export const CHANGE_SET_FEATURE_TABLES = [
  'gis_node',
  'gis_arc',
  'gis_connection',
  'gis_structure',
  'gis_zone',
  'gis_poi',
] as const

/**
 * Monta o item de um pacote a partir da geometria editada em WKT.
 * Regra da V13_9 (§ da tabela gis_change_set_item): a geometria
 * proposta vai em proposedGeomWkt — e, para gis_structure com
 * ponto, em proposedGeomPointWkt, porque a estrutura guarda o
 * ponto na coluna geom_point. Tabela/operação fora dos domínios
 * da GIS-04 são recusadas aqui, antes da rede.
 */
export function geometryItemInput(input: {
  featureTable: string
  featureId: string
  operation: string
  geomWkt: string
  proposedState?: Record<string, unknown>
}): ChangeSetItemInput {
  if (!(CHANGE_SET_FEATURE_TABLES as readonly string[]).includes(input.featureTable)) {
    throw new Error(`tabela de feição fora das tabelas da GIS-04: ${input.featureTable}`)
  }
  if (input.operation !== 'INSERT' && input.operation !== 'UPDATE' && input.operation !== 'RETIRE') {
    throw new Error(`operação fora de INSERT/UPDATE/RETIRE: ${input.operation}`)
  }
  const isPoint = input.geomWkt.trim().toUpperCase().startsWith('POINT')
  const toPointColumn = isPoint && input.featureTable === 'gis_structure'
  return {
    operation: input.operation,
    featureTable: input.featureTable,
    featureId: input.featureId,
    proposedState: input.proposedState ?? {},
    proposedGeomWkt: toPointColumn ? null : input.geomWkt,
    proposedGeomPointWkt: toPointColumn ? input.geomWkt : null,
  }
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
  // Lado do autor (GIS-04): criar o pacote, acrescentar itens e
  // submeter — a submissão põe o pacote IN_REVIEW e a revisão é
  // por outra pessoa (o backend recusa o autor como revisor).
  create: (input: CreateChangeSetInput) =>
    request<ChangeSet>(api.post(`${CHANGE_SETS_BASE}`, input)),
  addItem: (id: string, input: ChangeSetItemInput) =>
    request<ChangeSetItem>(api.post(`${CHANGE_SETS_BASE}/${id}/items`, input)),
  submit: (id: string) =>
    request<ChangeSet>(api.post(`${CHANGE_SETS_BASE}/${id}/submit`, {})),
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

import { api } from '@services/api'

/** Contratos da caixa "Minhas tarefas" (P2.28). O baseURL do cliente HTTP já é /api/v1. */
export const WORK_ITEMS_BASE = '/me/work-items'
export const WORK_DELEGATIONS_BASE = '/me/work-delegations'

export type WorkItemType =
  | 'SERVICE_ORDER_APPROVAL'
  | 'BILL_RECTIFICATION_APPROVAL'
  | 'PARAMETER_APPROVAL'
  | 'DOCUMENT_APPROVAL'
  | 'PROTOCOL_DUE'
  | 'SERVICE_ORDER_OVERDUE'
  | 'HELD_BILL'
  | 'FISCAL_REJECTION'
  | 'GIS_PACKAGE_REVIEW'
  | 'CORRECTIVE_ACTION'
  | 'CALIBRATION_DUE'
  | 'HOLDER_REQUEST'
  | 'REGULATORY_OBLIGATION'
  | 'RECONCILIATION_DIVERGENCE'

export type WorkItemStatus = 'OPEN' | 'IN_PROGRESS' | 'ESCALATED' | 'DONE' | 'CANCELLED'

export type DueFilter = 'ALL' | 'OVERDUE' | 'DUE_TODAY' | 'DUE_IN_7_DAYS'

export interface WorkItem {
  id: string
  tenantId: string
  type: WorkItemType
  objectType: string
  objectId: string
  originModule: string
  requesterId: string
  assigneeId: string | null
  eligibleApproverIds: string[]
  requiredPermission: string | null
  dueAt: string | null
  status: WorkItemStatus
  recordAddress: string
  approvalTaskId: string | null
  escalatedAt: string | null
  escalationCount: number
  escalatedToId: string | null
  createdAt: string
}

export interface WorkDelegation {
  id: string
  delegatorId: string
  delegateId: string
  startsAt: string
  endsAt: string
  grantedPermissions: string[]
  reason: string
  revokedAt: string | null
  revokedBy: string | null
  createdAt: string
}

export const workItems = {
  async list(filter: DueFilter, limit = 50): Promise<WorkItem[]> {
    const { data } = await api.get<WorkItem[]>(WORK_ITEMS_BASE, { params: { filter, limit } })
    return data
  },
  async count(filter: DueFilter): Promise<number> {
    const { data } = await api.get<{ count: number }>(`${WORK_ITEMS_BASE}/count`, { params: { filter } })
    return data.count
  },
  async start(id: string): Promise<WorkItem> {
    const { data } = await api.post<WorkItem>(`${WORK_ITEMS_BASE}/${id}/start`)
    return data
  },
  async decide(id: string, approved: boolean, reason: string): Promise<unknown> {
    const { data } = await api.post(`${WORK_ITEMS_BASE}/${id}/decision`, { approved, reason })
    return data
  },
}

export const workDelegations = {
  async received(): Promise<WorkDelegation[]> {
    const { data } = await api.get<WorkDelegation[]>(`${WORK_DELEGATIONS_BASE}/received`)
    return data
  },
  async sent(): Promise<WorkDelegation[]> {
    const { data } = await api.get<WorkDelegation[]>(`${WORK_DELEGATIONS_BASE}/sent`)
    return data
  },
  async create(body: {
    delegateId: string
    startsAt: string
    endsAt: string
    grantedPermissions: string[]
    reason: string
  }): Promise<WorkDelegation> {
    const { data } = await api.post<WorkDelegation>(WORK_DELEGATIONS_BASE, body)
    return data
  },
  async revoke(id: string): Promise<WorkDelegation> {
    const { data } = await api.post<WorkDelegation>(`${WORK_DELEGATIONS_BASE}/${id}/revoke`)
    return data
  },
}

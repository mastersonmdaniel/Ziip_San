import { api } from '@services/api'

/** Prefixo do catálogo GIS. O baseURL do cliente HTTP já é /api/v1. */
export const GIS_CATALOG_BASE = '/gis/catalog'

export interface GisClass {
  id: string
  code: string
  domain: string
  topologyRole: string
  geometryType: string
  origin: string
  active: boolean
}

export interface GisType {
  id: string
  classId: string
  classCode: string
  code: string
  label: string
  attributesSchema: string
  origin: string
  factoryCode: string | null
  factoryRetiredOn: string | null
  active: boolean
  approvalStatus: string | null
  proposedBy: string | null
  approvedBy: string | null
  approvedAt: string | null
  requiresCatalogItem: boolean
}

export interface GisItem {
  id: string
  featureTypeId: string
  featureTypeCode: string
  materialId: string | null
  materialCode: string | null
  dnMm: number | null
  dn2Mm: number | null
  angleDeg: number | null
  pressureClass: string | null
  stiffnessClass: string | null
  jointType: string | null
  sectionShape: string | null
  geom1: number | null
  geom2: number | null
  manufacturer: string | null
  model: string | null
  standardRef: string | null
  referenceStatus: string
  origin: string
  factoryCode: string | null
  factoryRetiredOn: string | null
  active: boolean
  approvalStatus: string | null
  proposedBy: string | null
}

export interface GisMaterial {
  id: string
  code: string
  family: string | null
  standardRef: string | null
  hazenWilliamsC: number | null
  manningN: number | null
  legacyOnly: boolean
  origin: string
  active: boolean
}

export interface GisReferenceReport {
  factoryTypes: number
  factoryItems: number
  factoryItemsToValidate: number
  factoryItemsStandard: number
  factoryTypesRetired: number
  factoryItemsRetired: number
  tenantTypesPending: number
  tenantItemsPending: number
  normsPurchasePending: boolean
  note: string
}

export interface GisTypeInput {
  classId: string
  code: string
  label: string
  attributesSchema?: string
  requiresCatalogItem?: boolean
}

export interface GisItemInput {
  featureTypeId: string
  materialId?: string | null
  dnMm?: number | null
  dn2Mm?: number | null
  angleDeg?: number | null
  pressureClass?: string | null
  stiffnessClass?: string | null
  jointType?: string | null
  sectionShape?: string | null
  geom1?: number | null
  geom2?: number | null
  manufacturer?: string | null
  model?: string | null
}

async function get<T>(url: string, params?: Record<string, string>): Promise<T> {
  const response = await api.request<T>({ method: 'get', url, params })
  return response.data
}

async function send<T>(method: 'post' | 'patch', url: string, data?: unknown): Promise<T> {
  const response = await api.request<T>({ method, url, data })
  return response.data
}

export const gisCatalogApi = {
  listClasses: () => get<GisClass[]>(`${GIS_CATALOG_BASE}/classes`),
  listTypes: (params?: { q?: string; active?: boolean; usable?: boolean }) =>
    get<GisType[]>(`${GIS_CATALOG_BASE}/types`, {
      ...(params?.q ? { q: params.q } : {}),
      ...(params?.active !== undefined ? { active: String(params.active) } : {}),
      ...(params?.usable !== undefined ? { usable: String(params.usable) } : {}),
    }),
  createType: (input: GisTypeInput) => send<GisType>('post', `${GIS_CATALOG_BASE}/types`, input),
  approveType: (id: string) => send<GisType>('post', `${GIS_CATALOG_BASE}/types/${id}/approve`),
  rejectType: (id: string) => send<GisType>('post', `${GIS_CATALOG_BASE}/types/${id}/reject`),
  setTypeActive: (id: string, active: boolean) =>
    send<GisType>('patch', `${GIS_CATALOG_BASE}/types/${id}/active`, { active }),
  listItems: (params?: { featureTypeId?: string; active?: boolean; usable?: boolean; referenceStatus?: string }) =>
    get<GisItem[]>(`${GIS_CATALOG_BASE}/items`, {
      ...(params?.featureTypeId ? { featureTypeId: params.featureTypeId } : {}),
      ...(params?.active !== undefined ? { active: String(params.active) } : {}),
      ...(params?.usable !== undefined ? { usable: String(params.usable) } : {}),
      ...(params?.referenceStatus ? { referenceStatus: params.referenceStatus } : {}),
    }),
  createItem: (input: GisItemInput) => send<GisItem>('post', `${GIS_CATALOG_BASE}/items`, input),
  approveItem: (id: string) => send<GisItem>('post', `${GIS_CATALOG_BASE}/items/${id}/approve`),
  rejectItem: (id: string) => send<GisItem>('post', `${GIS_CATALOG_BASE}/items/${id}/reject`),
  setItemActive: (id: string, active: boolean) =>
    send<GisItem>('patch', `${GIS_CATALOG_BASE}/items/${id}/active`, { active }),
  listMaterials: () => get<GisMaterial[]>(`${GIS_CATALOG_BASE}/materials`),
  referenceReport: () => get<GisReferenceReport>(`${GIS_CATALOG_BASE}/report/reference-to-validate`),
}

export interface SchemaProperty {
  name: string
  type: 'string' | 'integer' | 'number' | 'boolean'
  required: boolean
}

/**
 * Monta o attributes_schema (JSON Schema) do tipo a partir das linhas do
 * formulário — sem código nem migration. Propriedades sem nome são ignoradas;
 * nomes duplicados: vale a última linha. O backend valida que o resultado é
 * um objeto JSON.
 */
export function buildAttributesSchema(properties: SchemaProperty[]): string {
  const props: Record<string, { type: string }> = {}
  const required: string[] = []
  for (const p of properties) {
    const name = p.name.trim()
    if (!name) continue
    props[name] = { type: p.type }
    if (p.required && !required.includes(name)) required.push(name)
  }
  return JSON.stringify({ type: 'object', properties: props, required })
}

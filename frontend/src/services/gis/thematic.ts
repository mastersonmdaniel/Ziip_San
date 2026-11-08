import { api } from '@services/api'
import { toApiError } from '@services/registry/errors'

/**
 * Contratos da GIS-08 (temáticos, OS no mapa, seleção por polígono
 * e relatórios do cadastro). O baseURL do cliente HTTP já é
 * /api/v1. Pontos pessoais só são consumidos por esta superfície
 * no backoffice autenticado; o canal público tem serviço próprio
 * (aggregate) e nunca devolve ponto de imóvel ou ligação (§12).
 */
export const THEMATICS_BASE = '/gis/thematics'
export const PUBLIC_THEMATICS_BASE = '/gis/public/thematics'
export const ORDERS_BASE = '/gis/service-orders'
export const SELECTION_BASE = '/gis/selection'
export const GIS_REPORTS_BASE = '/gis/reports'

export interface ThematicClass {
  key: string
  label: string
  colorToken: string | null
  min: number | null
  max: number | null
}

export interface GisThematic {
  id: string
  key: string
  name: string
  description: string | null
  sourceKind: string
  categoryField: string | null
  measureField: string | null
  personal: boolean
  aggregation: string
  classes: ThematicClass[]
  filterJson: string
  origin: 'FACTORY' | 'TENANT'
  active: boolean
  requiresConfiguration: boolean
}

export interface LegendEntry {
  key: string
  label: string
  colorToken: string | null
  count: number
}

export interface ThematicPoint {
  id: string
  lon: number
  lat: number
  category: string | null
  measure: number | null
}

export interface ThematicData {
  thematicKey: string
  points: ThematicPoint[]
  legend: LegendEntry[]
  truncated: boolean
}

export interface PublicCell {
  cellKind: string
  cellId: string
  cellLabel: string
  unitCount: number
  categories: LegendEntry[]
  sectorGeomGeoJson: string | null
}

export interface PublicThematic {
  thematicKey: string
  /** Mínimo de unidades aplicado na supressão; null no ramo de ativos (sem supressão). */
  minUnitsApplied: number | null
  cells: PublicCell[]
  suppressedCells: number
  /** true quando as contagens de ativos pararam no teto de renderização. */
  truncated: boolean
}

export interface ServiceOrderPoint {
  orderId: string
  serviceTypeCode: string
  serviceTypeName: string
  status: string
  priority: string
  lon: number
  lat: number
  addressText: string | null
  crewUserIds: string[]
  scheduledFor: string | null
  createdAt: string | null
}

export interface PolygonStats {
  units: number
  connections: number
  economies: number
  averageConsumption: number | null
  areaM2: number | null
}

/**
 * Paleta de recurso para tokens map-thematic-* ainda não
 * registrados no tema (pendência da P1.30/ADR-0017, declarada no
 * relatório da P3.27): a legenda usa o token quando o tema o
 * resolve e esta sequência, pela ordem da classe, quando não.
 * Não é a paleta oficial — é o fallback visível e estável.
 */
export const THEMATIC_FALLBACK_PALETTE = [
  '#00548f',
  '#d97706',
  '#15803d',
  '#b91c1c',
  '#6d28d9',
  '#0e7490',
  '#a16207',
  '#374151',
] as const

export function legendColor(
  entry: LegendEntry,
  index: number,
  theme: Record<string, string>,
): string {
  if (entry.colorToken && theme[entry.colorToken]) {
    return theme[entry.colorToken]
  }
  return THEMATIC_FALLBACK_PALETTE[index % THEMATIC_FALLBACK_PALETTE.length]
}

/** Expressão MapLibre `match` da categoria do ponto para a cor da legenda. */
export function categoryColorExpression(
  legend: LegendEntry[],
  theme: Record<string, string>,
): unknown[] {
  const expression: unknown[] = ['match', ['get', 'category']]
  legend.forEach((entry, index) => {
    expression.push(entry.key, legendColor(entry, index, theme))
  })
  expression.push('#9ca3af')
  return expression
}

async function request<T>(promise: Promise<{ data: T }>): Promise<T> {
  try {
    const { data } = await promise
    return data
  } catch (error) {
    throw toApiError(error)
  }
}

export function listThematics(): Promise<GisThematic[]> {
  return request(api.get(THEMATICS_BASE))
}

export function thematicData(
  key: string,
  categoryCode?: string,
): Promise<ThematicData> {
  return request(
    api.get(`${THEMATICS_BASE}/${key}/data`, {
      params: categoryCode ? { categoryCode } : undefined,
    }),
  )
}

export function publicAggregate(key: string): Promise<PublicThematic> {
  return request(api.get(`${PUBLIC_THEMATICS_BASE}/${key}/aggregate`))
}

export function serviceOrders(params: {
  statuses?: string[]
  serviceTypeCode?: string
  crewUserId?: string
  address?: string
}): Promise<ServiceOrderPoint[]> {
  return request(api.get(ORDERS_BASE, { params }))
}

export function polygonStatistics(polygonGeoJson: string): Promise<PolygonStats> {
  return request(api.post(`${SELECTION_BASE}/statistics`, { polygonGeoJson }))
}

export function extensionReport(asOf: string): Promise<unknown> {
  return request(api.get(`${GIS_REPORTS_BASE}/extension`, { params: { asOf } }))
}

export function precisionReport(): Promise<unknown> {
  return request(api.get(`${GIS_REPORTS_BASE}/precision`))
}

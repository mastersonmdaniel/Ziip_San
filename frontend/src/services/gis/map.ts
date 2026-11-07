import { api } from '@services/api'
import { toApiError } from '@services/registry/errors'

/**
 * Contratos da GIS-06 (mapa web de consulta). O baseURL do cliente
 * HTTP já é /api/v1. Tiles NÃO passam por aqui: são binário MVT
 * consumido direto pelo MapLibre na URL do estilo gerado.
 */
export const MAP_BASE = '/gis/map'
export const TILES_BASE = '/gis/tiles'

export interface GisLayer {
  key: string
  label: string
  groupLabel: string
  personal: boolean
  tileMinZoom: number
  layerVersion: number
  active: boolean
}

/** Camada -> tabela física (identificação); espelho do GisTileLayers. */
export const LAYER_TABLE: Record<string, string> = {
  'water-arcs': 'gis_arc',
  'sewer-arcs': 'gis_arc',
  'storm-arcs': 'gis_arc',
  'water-nodes': 'gis_node',
  'sewer-nodes': 'gis_node',
  'storm-nodes': 'gis_node',
  structures: 'gis_structure',
  zones: 'gis_zone',
  pois: 'gis_poi',
  'service-lines': 'gis_connection',
}

export interface GisSymbol {
  key: string
  typeCode: string
  typeLabel: string
  domainKey: string
}

export interface GisStyleRow {
  id: string
  origin: 'FACTORY' | 'TENANT'
  domainKey: string | null
  featureTypeId: string | null
  featureTypeCode: string | null
  lifecycleStatus: string
  accuracyStatus: string | null
  colorToken: string | null
  lineWidth: number | null
  lineDash: number[] | null
  halo: boolean | null
  iconKey: string | null
  labelField: string | null
  labelMinZoom: number | null
}

export interface StyleOverrideInput {
  featureTypeId?: string | null
  domainKey?: string | null
  lifecycleStatus: string
  accuracyStatus?: string | null
  colorToken?: string | null
  lineWidth?: number | null
  lineDash?: number[] | null
  halo?: boolean | null
  iconKey?: string | null
  labelField?: string | null
  labelMinZoom?: number | null
}

export interface GisBasemap {
  id: string
  key: string
  label: string
  provider: string
  tilesUrl: string | null
  styleUrl: string | null
  attribution: string | null
  isDefault: boolean
  origin: string
}

export interface GisWmsSource {
  id: string
  label: string
  url: string
  layers: string
  version: string
  format: string
  transparent: boolean
  active: boolean
}

export interface FeatureDetail {
  featureTable: string
  id: string
  code: string
  name: string | null
  typeCode: string
  typeLabel: string
  domainKey: string
  lifecycleStatus: string
  accuracyStatus: string | null
  attributes: Record<string, unknown>
  geomWkt: string | null
  serviceConnectionId: string | null
  personalDataVisible: boolean
  updatedAt: string
}

export interface SearchHit {
  kind: 'FEATURE' | 'ADDRESS' | 'NEAR'
  featureTable: string
  id: string
  code: string | null
  name: string | null
  typeCode: string
  lon: number
  lat: number
}

async function request<T>(promise: Promise<{ data: T }>): Promise<T> {
  try {
    const response = await promise
    return response.data
  } catch (error) {
    throw toApiError(error)
  }
}

export const gisMap = {
  layers: () => request<GisLayer[]>(api.get(`${MAP_BASE}/layers`)),
  /** Documento de estilo MapLibre (JSON) gerado da fonte única. */
  style: async (theme: 'light' | 'dark'): Promise<Record<string, unknown>> => {
    const response = await api.get(`${MAP_BASE}/style`, { params: { theme } })
    return response.data as Record<string, unknown>
  },
  styleRows: () => request<GisStyleRow[]>(api.get(`${MAP_BASE}/styles`)),
  saveOverride: (input: StyleOverrideInput) =>
    request<GisStyleRow>(api.put(`${MAP_BASE}/styles/override`, input)),
  restoreFactory: (id: string) =>
    request<void>(api.delete(`${MAP_BASE}/styles/override/${id}`)),
  symbols: () => request<GisSymbol[]>(api.get(`${MAP_BASE}/symbols`)),
  basemaps: () => request<GisBasemap[]>(api.get(`${MAP_BASE}/basemaps`)),
  wmsSources: () => request<GisWmsSource[]>(api.get(`${MAP_BASE}/wms-sources`)),
  addWmsSource: (input: Omit<GisWmsSource, 'id' | 'active'>) =>
    request<GisWmsSource>(api.post(`${MAP_BASE}/wms-sources`, input)),
  removeWmsSource: (id: string) =>
    request<void>(api.delete(`${MAP_BASE}/wms-sources/${id}`)),
  identify: (featureTable: string, id: string) =>
    request<FeatureDetail>(api.get(`${MAP_BASE}/features/${featureTable}/${id}`)),
  searchAttribute: (q: string) =>
    request<SearchHit[]>(api.get(`${MAP_BASE}/search/attribute`, { params: { q } })),
  searchAddress: (q: string) =>
    request<SearchHit[]>(api.get(`${MAP_BASE}/search/address`, { params: { q } })),
  searchNear: (lon: number, lat: number, radius = 250) =>
    request<SearchHit[]>(
      api.get(`${MAP_BASE}/search/near`, { params: { lon, lat, radius } }),
    ),
  searchUtm: (easting: number, northing: number, zone: number, southern = true, radius = 250) =>
    request<SearchHit[]>(
      api.get(`${MAP_BASE}/search/utm`, {
        params: { easting, northing, zone, southern, radius },
      }),
    ),
}

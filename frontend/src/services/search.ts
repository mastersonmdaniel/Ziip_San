import { api } from '@services/api'
import { toApiError } from '@services/registry/errors'

/**
 * P1.33: busca global e visões salvas. O texto da busca vai no corpo do POST,
 * nunca na URL — o texto pode ser um CPF/CNPJ, e documento nunca vai na URL
 * (o mesmo motivo do POST /customers/search). O cliente nunca monta máscara
 * própria: o que aparece é o `documentMasked` que o servidor devolve.
 */

export type SearchType = 'CUSTOMER' | 'CONSUMER_UNIT' | 'SERVICE_ORDER' | 'BILL' | 'DOCUMENT' | 'PROTOCOL'

export interface SearchHit {
  type: SearchType
  id: string
  title: string
  subtitle: string
  href: string | null
}

export interface GlobalSearchResult {
  q: string
  results: SearchHit[]
  deniedTypes: SearchType[]
  pendingTypes: SearchType[]
  documentSearch: boolean
}

export async function globalSearch(q: string, types?: SearchType[], signal?: AbortSignal): Promise<GlobalSearchResult> {
  try {
    const response = await api.post<GlobalSearchResult>('/search/global', { q, types }, { signal })
    return response.data
  } catch (error) {
    throw toApiError(error)
  }
}

export type SavedViewResource = 'customers' | 'consumer-units' | 'service-orders' | 'bills' | 'documents' | 'global'
export type TableDensity = 'comfortable' | 'compact'
export type SavedViewVisibility = 'PRIVATE' | 'ORG_UNIT'

export interface SavedViewInput {
  title: string
  resource: SavedViewResource
  filters?: string
  columns?: string
  sort?: string | null
  density?: TableDensity
  visibility?: SavedViewVisibility
  sharedOrgUnitId?: string | null
  isDefault?: boolean
}

export interface SavedView {
  id: string
  ownerUserId: string
  title: string
  resource: SavedViewResource
  filters: string
  columns: string
  sort: string | null
  density: TableDensity
  visibility: SavedViewVisibility
  sharedOrgUnitId: string | null
  isDefault: boolean
}

export const savedViewsApi = {
  async list(signal?: AbortSignal): Promise<SavedView[]> {
    try {
      const response = await api.get<SavedView[]>('/search/saved-views', { signal })
      return response.data
    } catch (error) {
      throw toApiError(error)
    }
  },
  async create(input: SavedViewInput, signal?: AbortSignal): Promise<SavedView> {
    try {
      const response = await api.post<SavedView>('/search/saved-views', input, { signal })
      return response.data
    } catch (error) {
      throw toApiError(error)
    }
  },
  async update(id: string, input: SavedViewInput, signal?: AbortSignal): Promise<SavedView> {
    try {
      const response = await api.put<SavedView>(`/search/saved-views/${id}`, input, { signal })
      return response.data
    } catch (error) {
      throw toApiError(error)
    }
  },
  async remove(id: string, signal?: AbortSignal): Promise<void> {
    try {
      await api.delete(`/search/saved-views/${id}`, { signal })
    } catch (error) {
      throw toApiError(error)
    }
  },
}

/** Rótulo da seção, para a paleta e para o leitor de tela. */
export const SEARCH_TYPE_LABEL: Record<SearchType, string> = {
  CUSTOMER: 'Cliente',
  CONSUMER_UNIT: 'Unidade consumidora',
  SERVICE_ORDER: 'Ordem de serviço',
  BILL: 'Conta',
  DOCUMENT: 'Documento',
  PROTOCOL: 'Protocolo',
}

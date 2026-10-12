import { createResourceClient, request, resourceUrl, REGISTRY_BASE } from './client'
import { createDraftClient } from './drafts'
import type {
  Block,
  BlockFace,
  CepLookupResult,
  CommercialSector,
  ConsumerUnit,
  ConsumerUnitInput,
  ConsumerUnitSummary,
  CatalogLookupItem,
  CrudItem,
  Customer,
  CustomerInput,
  CustomerSummary,
  HistoryEvent,
  Locality,
  ReadingRoute,
  RegistrySettings,
  RevealDocumentResult,
  UnitCustomerLink,
  Uuid,
} from './types'

// ---- Imóveis ---------------------------------------------------------------------------------

const consumerUnitsBase = createResourceClient<ConsumerUnit, ConsumerUnitSummary, ConsumerUnitInput>('consumer-units', {
  personalSearch: true,
})

export const consumerUnits = {
  ...consumerUnitsBase,
  /** Vínculos cliente-imóvel (vigentes e encerrados). */
  customers(id: Uuid, signal?: AbortSignal): Promise<UnitCustomerLink[]> {
    return request<UnitCustomerLink[]>({ method: 'GET', url: resourceUrl('consumer-units', id, 'customers'), signal })
  },
  /** Linha do tempo: situação, inscrição e auditoria campo a campo. */
  history(id: Uuid, signal?: AbortSignal): Promise<HistoryEvent[]> {
    return request<HistoryEvent[]>({ method: 'GET', url: resourceUrl('consumer-units', id, 'history'), signal })
  },
}

/** Dados enviados à validação por etapa: fatia do payload correspondente à etapa. */
export type ConsumerUnitStepPayload = Partial<ConsumerUnitInput>

export const consumerUnitDrafts = createDraftClient<unknown, ConsumerUnitStepPayload>('consumer-units')

// ---- Clientes --------------------------------------------------------------------------------

const customersBase = createResourceClient<Customer, CustomerSummary, CustomerInput>('customers', { personalSearch: true })

export const customers = {
  ...customersBase,
  /** A API decide a permissão (403) e registra em log. A tela só chama e mostra por tempo curto. */
  revealDocument(id: Uuid): Promise<RevealDocumentResult> {
    return request<RevealDocumentResult>({ method: 'POST', url: resourceUrl('customers', id, 'reveal-document') })
  },
}

// ---- Hierarquia territorial ------------------------------------------------------------------

export const localities = createResourceClient<Locality, Locality, Omit<Locality, 'id' | 'version'>>('localities')
export const commercialSectors = createResourceClient<
  CommercialSector,
  CommercialSector,
  Omit<CommercialSector, 'id' | 'version' | 'locality'>
>('commercial-sectors')
export const blocks = createResourceClient<Block, Block, Omit<Block, 'id' | 'version' | 'sector' | 'readingRoute'>>('blocks')
export const blockFaces = createResourceClient<BlockFace, BlockFace, Omit<BlockFace, 'id' | 'version'>>('block-faces')
export const readingRoutes = createResourceClient<
  ReadingRoute,
  ReadingRoute,
  Omit<ReadingRoute, 'id' | 'version' | 'locality' | 'sector'>
>('reading-routes')

// ---- CRUD genérico (catálogos e hierarquia territorial) -------------------------------------

/** Cliente sem tipagem por entidade: o CRUD é dirigido por metadados (ver CatalogCrud). */
export function crudClient(resource: string) {
  return createResourceClient<CrudItem, CrudItem, Record<string, unknown>>(resource)
}

const catalogSourceCache = new Map<string, ReturnType<typeof createResourceClient<CatalogLookupItem, CatalogLookupItem, Record<string, unknown>>>>()

/** Fonte de busca para seletores de catálogo (ex.: 'water-statuses'). */
export function catalogSource(resource: string) {
  let source = catalogSourceCache.get(resource)
  if (!source) {
    source = createResourceClient<CatalogLookupItem, CatalogLookupItem, Record<string, unknown>>(resource)
    catalogSourceCache.set(resource, source)
  }
  return source
}

// ---- Auxiliares ------------------------------------------------------------------------------

export function getSettings(signal?: AbortSignal): Promise<RegistrySettings> {
  return request<RegistrySettings>({ method: 'GET', url: `${REGISTRY_BASE}/settings`, signal })
}

export function lookupCep(cep: string, signal?: AbortSignal): Promise<CepLookupResult> {
  return request<CepLookupResult>({
    method: 'GET',
    url: `${REGISTRY_BASE}/addresses/cep/${encodeURIComponent(cep.replace(/\D/g, ''))}`,
    signal,
  })
}

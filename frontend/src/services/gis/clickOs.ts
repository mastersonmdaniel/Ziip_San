import { api } from '@services/api'
import { toApiError } from '@services/registry/errors'

/**
 * Cliente da II-1 — OS por clique no elemento GIS (PDCA GIS Ciclo 2,
 * fiação P-II1-FIACAO): propagação do clique, payload pré-preenchido
 * e criação da OS no núcleo, sobre o endpoint
 * /api/v1/gis/click-os do backend.
 *
 * O tipo de serviço é o do catálogo do tenant: quem chama passa o
 * código que o operador escolheu na ficha; sem código, o payload
 * nasce não pronto e o servidor nomeia a falta — nenhum tipo é
 * presumido aqui.
 *
 * Paridade mobile (decisão 73): a criação é acção de backoffice
 * nesta mesma página web (responsiva no celular). O app de campo
 * NÃO cria OS: executa as OS dos pacotes que o servidor lhe envia
 * (campo/src/orders/) — a OS criada aqui chega-lhe por esse fluxo.
 */
export const CLICK_OS_BASE = '/gis/click-os'

export type PropagationMode = 'DOWNSTREAM' | 'UPSTREAM' | 'BOTH' | 'ISOLATION'

export interface ClickOsInput {
  arcId?: string | null
  nodeId?: string | null
  mode: PropagationMode
  serviceTypeCode?: string | null
  maxHops?: number | null
  maxAffectedUnits?: number | null
}

export interface PropagationResultView {
  mode: string
  outcome: string
  affectedNodeIds: string[]
  affectedArcIds: string[]
  affectedConnectionIds: string[]
  affectedUnitIds: string[]
  serviceConnectionIds: string[]
  totalEconomies: number
  essentialUnitIds: string[]
  sensitiveUnitIds: string[]
  valvesToCloseCodes: string[]
  isolationFailure: string | null
  truncated: boolean
  unitLimitExceeded: boolean
}

export interface ClickPayloadView {
  serviceTypeCode: string | null
  mode: string
  outcome: string
  affectedNodeIds: string[]
  affectedArcIds: string[]
  affectedConnectionIds: string[]
  affectedUnitIds: string[]
  totalEconomies: number
  truncated: boolean
  readyToSubmit: boolean
  missingForSubmit: string[]
}

export interface CreatedOrderView {
  orderId: string
  payload: ClickPayloadView
}

/**
 * Alvo do clique a partir da ficha da feição: o núcleo II-1 só
 * propaga de nós (gis_node) e arcos (gis_arc) da rede. Qualquer
 * outra tabela (ligação, estrutura, zona, POI) não é alvo de clique
 * — devolve null e a ficha não mostra a acção de OS.
 */
export function clickTargetForFeature(
  featureTable: string,
  id: string,
): { arcId: string | null; nodeId: string | null } | null {
  if (!id) return null
  if (featureTable === 'gis_node') return { arcId: null, nodeId: id }
  if (featureTable === 'gis_arc') return { arcId: id, nodeId: null }
  return null
}

async function request<T>(promise: Promise<{ data: T }>): Promise<T> {
  try {
    const response = await promise
    return response.data
  } catch (error) {
    throw toApiError(error)
  }
}

export const clickOs = {
  propagate: (input: ClickOsInput) =>
    request<PropagationResultView>(api.post(`${CLICK_OS_BASE}/propagate`, input)),
  payload: (input: ClickOsInput) =>
    request<ClickPayloadView>(api.post(`${CLICK_OS_BASE}/payload`, input)),
  create: (input: ClickOsInput) =>
    request<CreatedOrderView>(api.post(`${CLICK_OS_BASE}/orders`, input)),
}

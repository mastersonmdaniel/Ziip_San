/**
 * Cliente HTTP compartilhado — superficie derivada do uso entregue:
 * - home (P3.39): api.get<T>(url, { params }).then((r) => r.data)
 * - workItems (P2.28) / fiscalDocuments (P2.33): idem get/post, com e
 *   sem corpo; post sem corpo tambem (ex.: `${base}/${id}/start`).
 * - registry/client (espelho ED-08): api.request<T>(config) com
 *   { method, url, params, data, signal } (AxiosRequestConfig).
 * - search (espelho ED-08): api.post(url, corpo, { signal }),
 *   api.get(url, { signal }), api.put(url, corpo, { signal }),
 *   api.delete(url, { signal }).
 * - gis (P2.22/P3.25/P3.27): api.get/post/put/delete devolvendo
 *   Promise<{ data }>, sempre embrulhados em toApiError pelos servicos.
 * Todas as frentes declaram: "O baseURL do cliente HTTP ja e /api/v1".
 *
 * [A DEFINIR] O transporte original nunca foi entregue: o package.json
 * do espelho ED-08 declara axios ^1.6.5, mas nenhum ficheiro de cliente
 * veio em pacote algum. Esta implementacao usa fetch (sem dependencia)
 * mantendo a forma axios ({ data, status } e erro com .response), para
 * os servicos entregues funcionarem sem alteracao. Autenticacao,
 * interceptores e renovacao de sessao: nenhuma fonte entregue os
 * define — pendencia P-TEMA-09. Trocar o transporte por axios, se for
 * essa a decisao, nao muda esta superficie.
 */
export interface ApiRequestConfig {
  params?: Record<string, unknown>
  data?: unknown
  signal?: AbortSignal
  headers?: Record<string, string>
  method?: string
  url?: string
}

export interface ApiResponse<T> {
  data: T
  status: number
}

export const API_BASE_URL = '/api/v1'

function buildUrl(url: string, params?: Record<string, unknown>): string {
  const full = url.startsWith('http') || url.startsWith(API_BASE_URL) ? url : `${API_BASE_URL}${url}`
  if (!params) return full
  const qs = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null) continue
    qs.append(key, String(value))
  }
  const s = qs.toString()
  return s ? `${full}?${s}` : full
}

async function doRequest<T>(method: string, url: string, config: ApiRequestConfig = {}): Promise<ApiResponse<T>> {
  const body = config.data !== undefined ? JSON.stringify(config.data) : undefined
  let res: Response
  try {
    res = await fetch(buildUrl(url, config.params), {
      method,
      headers: { 'Content-Type': 'application/json', ...(config.headers ?? {}) },
      body,
      signal: config.signal,
    })
  } catch (error) {
    // Falha de rede/abort: propaga com a forma que toApiError entende
    // (sem .response; a mensagem original fica).
    throw error
  }
  const text = await res.text()
  let data: unknown = null
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      data = text
    }
  }
  if (!res.ok) {
    const error = new Error(`HTTP ${res.status} em ${url}`) as Error & {
      response: { status: number; data: unknown }
    }
    error.response = { status: res.status, data }
    throw error
  }
  return { data: data as T, status: res.status }
}

export const api = {
  get: <T>(url: string, config?: ApiRequestConfig) => doRequest<T>('GET', url, config),
  post: <T>(url: string, data?: unknown, config?: ApiRequestConfig) =>
    doRequest<T>('POST', url, { ...config, data }),
  put: <T>(url: string, data?: unknown, config?: ApiRequestConfig) =>
    doRequest<T>('PUT', url, { ...(config ?? {}), data }),
  delete: <T>(url: string, config?: ApiRequestConfig) => doRequest<T>('DELETE', url, config),
  request: <T>(config: ApiRequestConfig & { url: string }) =>
    doRequest<T>((config.method ?? 'GET').toUpperCase(), config.url, config),
}

export default api

import type { AxiosRequestConfig } from 'axios'
import { api } from '@services/api'
import { toApiError } from './errors'
import type { FilterValue, ListParams, Page, Uuid, Versioned } from './types'

/** Prefixo do cadastro. O baseURL do cliente HTTP já é /api, resultando em /api/registry/... */
export const REGISTRY_BASE = '/registry'

export interface WriteOptions {
  /** Tokens de avisos confirmáveis aceitos pelo usuário (reenvio após warnings[]). */
  confirmTokens?: string[]
  signal?: AbortSignal
}

/** Executa a requisição e converte qualquer falha em ApiError (problem+json tratado). */
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  try {
    const response = await api.request<T>(config)
    return response.data
  } catch (error) {
    throw toApiError(error)
  }
}

/**
 * Parece CPF ou CNPJ (numérico ou alfanumérico), com ou sem pontuação? Usado para impedir que um
 * documento vá na URL, onde fica gravado em log de servidor, proxy e histórico do navegador.
 */
export function looksLikeDocument(value: unknown): boolean {
  if (typeof value !== 'string') return false
  const compact = value.replace(/[\s.\-/]/g, '').toUpperCase()
  if (/^\d{11}$/.test(compact) || /^\d{14}$/.test(compact)) return true
  return /^[0-9A-Z]{12}\d{2}$/.test(compact) && /\d/.test(compact.slice(0, 12))
}

/**
 * Remove parâmetros vazios para não poluir a URL. Recusa (lança erro) valor com cara de CPF/CNPJ:
 * dado pessoal nunca vai na URL; a busca por documento é POST com corpo (ResourceClient.search).
 */
export function cleanParams(params: Record<string, FilterValue> | undefined): Record<string, string | number | boolean> {
  const out: Record<string, string | number | boolean> = {}
  if (!params) return out
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') continue
    if (looksLikeDocument(value)) {
      throw new Error(`parâmetro "${key}" parece CPF/CNPJ: documento não vai na URL, use search() (POST)`)
    }
    out[key] = value
  }
  return out
}

/** Corpo de busca: mesmos filtros de list(), sem vazios, enviados no corpo de um POST. */
function cleanBody(params: Record<string, FilterValue> | undefined): Record<string, string | number | boolean> {
  const out: Record<string, string | number | boolean> = {}
  if (!params) return out
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') continue
    out[key] = value
  }
  return out
}

function isRecord(v: unknown): v is Record<string, unknown> {
  return typeof v === 'object' && v !== null
}

/**
 * Normaliza a página. Aceita `page` ou `number` (Spring) e deriva totalPages se faltar.
 * Concentra aqui a suposição sobre o formato para facilitar o ajuste com o backend.
 */
export function normalizePage<T>(raw: unknown, fallbackSize: number): Page<T> {
  if (Array.isArray(raw)) {
    return { content: raw as T[], page: 0, size: raw.length, totalElements: raw.length, totalPages: 1 }
  }
  if (!isRecord(raw)) {
    return { content: [], page: 0, size: fallbackSize, totalElements: 0, totalPages: 0 }
  }
  const content = Array.isArray(raw.content) ? (raw.content as T[]) : []
  const size = typeof raw.size === 'number' && raw.size > 0 ? raw.size : fallbackSize
  const pageNumber = typeof raw.page === 'number' ? raw.page : typeof raw.number === 'number' ? raw.number : 0
  const totalElements = typeof raw.totalElements === 'number' ? raw.totalElements : content.length
  const totalPages = typeof raw.totalPages === 'number' ? raw.totalPages : Math.ceil(totalElements / size)
  return { content, page: pageNumber, size, totalElements, totalPages }
}

export const DEFAULT_PAGE_SIZE = 25

export interface ResourceClient<TItem extends Versioned, TSummary, TInput> {
  readonly path: string
  /**
   * Recurso de pessoa (clientes, imóveis com titular): a busca livre (q = nome ou documento) só vai
   * pelo corpo de search(). list() recusa q.
   */
  readonly personalSearch: boolean
  list(params?: ListParams, signal?: AbortSignal): Promise<Page<TSummary>>
  /** Busca por POST /registry/{path}/search com os filtros no corpo, nunca na URL. */
  search(params?: ListParams, signal?: AbortSignal): Promise<Page<TSummary>>
  get(id: Uuid, signal?: AbortSignal): Promise<TItem>
  create(input: TInput, options?: WriteOptions): Promise<TItem>
  /** `version` é enviado no corpo do PUT (controle otimista). */
  update(id: Uuid, input: TInput, version: number, options?: WriteOptions): Promise<TItem>
  /** Exclusão LÓGICA (deleted_at + motivo). Nunca física. */
  remove(id: Uuid, reason: string): Promise<void>
}

function withConfirm<T extends object>(body: T, options?: WriteOptions): T & { confirmTokens?: string[] } {
  if (options?.confirmTokens && options.confirmTokens.length > 0) {
    return { ...body, confirmTokens: options.confirmTokens }
  }
  return body
}

export function resourceUrl(path: string, id?: Uuid, suffix?: string): string {
  const parts = [REGISTRY_BASE, path]
  if (id) parts.push(encodeURIComponent(id))
  if (suffix) parts.push(suffix)
  return parts.join('/')
}

export interface ResourceOptions {
  /** Ver ResourceClient.personalSearch. */
  personalSearch?: boolean
}

export function createResourceClient<TItem extends Versioned, TSummary = TItem, TInput = Omit<TItem, keyof Versioned>>(
  path: string,
  options: ResourceOptions = {},
): ResourceClient<TItem, TSummary, TInput> {
  const personalSearch = options.personalSearch === true
  return {
    path,
    personalSearch,
    async list(params, signal) {
      if (personalSearch && typeof params?.q === 'string' && params.q.trim() !== '') {
        throw new Error(`busca livre em "${path}" vai por search() (POST): nome e documento não vão na URL`)
      }
      const size = params?.size ?? DEFAULT_PAGE_SIZE
      const raw = await request<unknown>({
        method: 'GET',
        url: resourceUrl(path),
        params: cleanParams({ ...params, size }),
        signal,
      })
      return normalizePage<TSummary>(raw, size)
    },
    async search(params, signal) {
      const size = params?.size ?? DEFAULT_PAGE_SIZE
      const raw = await request<unknown>({
        method: 'POST',
        url: resourceUrl(path, undefined, 'search'),
        data: cleanBody({ ...params, size }),
        signal,
      })
      return normalizePage<TSummary>(raw, size)
    },
    get(id, signal) {
      return request<TItem>({ method: 'GET', url: resourceUrl(path, id), signal })
    },
    create(input, options) {
      return request<TItem>({
        method: 'POST',
        url: resourceUrl(path),
        data: withConfirm(input as object, options),
        signal: options?.signal,
      })
    },
    update(id, input, version, options) {
      return request<TItem>({
        method: 'PUT',
        url: resourceUrl(path, id),
        data: withConfirm({ ...(input as object), version }, options),
        signal: options?.signal,
      })
    },
    async remove(id, reason) {
      await request<void>({ method: 'DELETE', url: resourceUrl(path, id), data: { reason } })
    },
  }
}

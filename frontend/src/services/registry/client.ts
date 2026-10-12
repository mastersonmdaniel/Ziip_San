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

/** Remove parâmetros vazios para não poluir a URL (nunca colocar dado pessoal aqui). */
export function cleanParams(params: Record<string, FilterValue> | undefined): Record<string, string | number | boolean> {
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
  list(params?: ListParams, signal?: AbortSignal): Promise<Page<TSummary>>
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

export function createResourceClient<TItem extends Versioned, TSummary = TItem, TInput = Omit<TItem, keyof Versioned>>(
  path: string,
): ResourceClient<TItem, TSummary, TInput> {
  return {
    path,
    async list(params, signal) {
      const size = params?.size ?? DEFAULT_PAGE_SIZE
      const raw = await request<unknown>({
        method: 'GET',
        url: resourceUrl(path),
        params: cleanParams({ ...params, size }),
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

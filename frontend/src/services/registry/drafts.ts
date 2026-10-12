import { request, resourceUrl, type WriteOptions } from './client'
import type { Draft, DraftInput, Uuid } from './types'

/**
 * Rascunho do assistente no servidor (nada de estado em sessão HTTP nem em localStorage).
 *   POST   /api/registry/{recurso}/drafts                      cria
 *   PUT    /api/registry/{recurso}/drafts/{id}   {version,...}  atualiza (409 = mudou)
 *   GET    /api/registry/{recurso}/drafts/{id}                  retoma
 *   DELETE /api/registry/{recurso}/drafts/{id}                  descarta
 *   POST   /api/registry/{recurso}/validate-step                valida UMA etapa, sem gravar
 *
 * SUPOSIÇÃO: validate-step responde 200 quando a etapa está ok; 422 problem+json com errors[]
 * quando há erro bloqueante; problem+json só com warnings[] pede confirmação (reenviar com
 * confirmTokens).
 */
export interface DraftClient<TData, TStepPayload> {
  create(input: DraftInput<TData>): Promise<Draft<TData>>
  update(id: Uuid, version: number, input: DraftInput<TData>): Promise<Draft<TData>>
  get(id: Uuid): Promise<Draft<TData>>
  discard(id: Uuid): Promise<void>
  validateStep(step: number, payload: TStepPayload, targetId: Uuid | null, options?: WriteOptions): Promise<void>
}

export function createDraftClient<TData, TStepPayload>(path: string): DraftClient<TData, TStepPayload> {
  const base = `${resourceUrl(path)}/drafts`
  return {
    create(input) {
      return request<Draft<TData>>({ method: 'POST', url: base, data: input })
    },
    update(id, version, input) {
      return request<Draft<TData>>({
        method: 'PUT',
        url: `${base}/${encodeURIComponent(id)}`,
        data: { ...input, version },
      })
    },
    get(id) {
      return request<Draft<TData>>({ method: 'GET', url: `${base}/${encodeURIComponent(id)}` })
    },
    async discard(id) {
      await request<void>({ method: 'DELETE', url: `${base}/${encodeURIComponent(id)}` })
    },
    async validateStep(step, payload, targetId, options) {
      const body: Record<string, unknown> = { step, data: payload, targetId }
      if (options?.confirmTokens && options.confirmTokens.length > 0) body.confirmTokens = options.confirmTokens
      await request<unknown>({
        method: 'POST',
        url: `${resourceUrl(path)}/validate-step`,
        data: body,
        signal: options?.signal,
      })
    },
  }
}

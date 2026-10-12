import { AxiosError, AxiosHeaders, type AxiosResponse } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@services/api', () => ({ api: { request: vi.fn() } }))

import { api } from '@services/api'
import { createResourceClient, normalizePage, cleanParams } from './client'
import { ApiError, classify, parseProblem, toApiError, toFormPath } from './errors'
import type { Versioned } from './types'

const request = vi.mocked(api.request)

function response(status: number, data: unknown): AxiosResponse {
  return { data, status, statusText: '', headers: {}, config: { headers: new AxiosHeaders() } }
}

function failure(status: number, data: unknown): AxiosError {
  return new AxiosError('falha de teste', String(status), undefined, undefined, response(status, data))
}

describe('problem+json', () => {
  it('interpreta errors[] e warnings[] ignorando lixo', () => {
    const problem = parseProblem({
      title: 'Validação',
      errors: [{ field: 'lot', code: 'RANGE', message: 'Lote fora da faixa.' }, 'lixo'],
      warnings: [{ code: 'SEQ', message: 'Sequência ocupada.', confirmToken: 't1' }, { code: 'X', message: 'sem token' }],
    })
    expect(problem?.errors).toEqual([{ field: 'lot', code: 'RANGE', message: 'Lote fora da faixa.' }])
    expect(problem?.warnings).toEqual([{ code: 'SEQ', message: 'Sequência ocupada.', confirmToken: 't1' }])
    expect(parseProblem('texto')).toBeNull()
  })

  it('classifica: aviso, validação, conflito (409), permissão, ausência e servidor', () => {
    expect(classify(422, { warnings: [{ code: 'A', message: 'm', confirmToken: 't' }] })).toBe('warning')
    expect(classify(422, { errors: [{ field: 'a', code: 'b', message: 'c' }] })).toBe('validation')
    expect(classify(409, null)).toBe('conflict')
    expect(classify(409, { warnings: [{ code: 'A', message: 'm', confirmToken: 't' }] })).toBe('warning')
    expect(classify(403, null)).toBe('forbidden')
    expect(classify(404, null)).toBe('not-found')
    expect(classify(503, null)).toBe('server')
  })

  it('converte erro de rede, 409 e validação em ApiError com mensagem em português', () => {
    const net = toApiError(new AxiosError('Network Error', 'ERR_NETWORK'))
    expect(net.kind).toBe('network')
    expect(net.message).toMatch(/Não foi possível falar com o servidor/)

    const conflict = toApiError(failure(409, { title: 'Conflict' }))
    expect(conflict.kind).toBe('conflict')
    expect(conflict.message).toMatch(/o registro mudou|O registro mudou/i)

    const validation = toApiError(failure(422, { errors: [{ field: 'number', code: 'REQUIRED', message: 'Informe o número.' }] }))
    expect(validation.kind).toBe('validation')
    expect(validation.errors[0]?.field).toBe('number')
  })

  it('caminho de campo vira caminho do react-hook-form', () => {
    expect(toFormPath('customers[0].customerId')).toBe('customers.0.customerId')
  })
})

describe('página', () => {
  it('aceita page ou number (Spring) e deriva totalPages', () => {
    expect(normalizePage({ content: [{ a: 1 }], number: 2, size: 10, totalElements: 31 }, 25)).toEqual({
      content: [{ a: 1 }],
      page: 2,
      size: 10,
      totalElements: 31,
      totalPages: 4,
    })
    expect(normalizePage({ content: [], page: 1, size: 25, totalElements: 0, totalPages: 0 }, 25).page).toBe(1)
  })

  it('aceita array puro e lixo', () => {
    expect(normalizePage([1, 2, 3], 25).totalElements).toBe(3)
    expect(normalizePage(null, 25).content).toEqual([])
  })

  it('remove parâmetros vazios', () => {
    expect(cleanParams({ q: '', a: undefined, b: null, c: 0, d: false, e: 'x' })).toEqual({ c: 0, d: false, e: 'x' })
  })
})

describe('cliente de recurso', () => {
  interface Thing extends Versioned {
    name: string
  }
  const things = createResourceClient<Thing, Thing, { name: string }>('things')

  beforeEach(() => {
    request.mockReset()
  })

  it('lista paginado com q, page, size e sort em /registry/things', async () => {
    request.mockResolvedValue(response(200, { content: [], page: 0, size: 25, totalElements: 0, totalPages: 0 }))
    await things.list({ q: 'ab', page: 1, sort: 'name,asc' })
    const config = request.mock.calls[0]?.[0]
    expect(config?.method).toBe('GET')
    expect(config?.url).toBe('/registry/things')
    expect(config?.params).toEqual({ q: 'ab', page: 1, sort: 'name,asc', size: 25 })
  })

  it('PUT envia a version no corpo e os confirmTokens aceitos', async () => {
    request.mockResolvedValue(response(200, { id: 'x', version: 4, name: 'n' }))
    await things.update('x', { name: 'n' }, 3, { confirmTokens: ['tok-1'] })
    const config = request.mock.calls[0]?.[0]
    expect(config?.method).toBe('PUT')
    expect(config?.url).toBe('/registry/things/x')
    expect(config?.data).toEqual({ name: 'n', version: 3, confirmTokens: ['tok-1'] })
  })

  it('POST sem confirmação não envia confirmTokens', async () => {
    request.mockResolvedValue(response(201, { id: 'x', version: 0, name: 'n' }))
    await things.create({ name: 'n' })
    expect(request.mock.calls[0]?.[0]?.data).toEqual({ name: 'n' })
  })

  it('DELETE é lógico e leva o motivo', async () => {
    request.mockResolvedValue(response(204, null))
    await things.remove('x', 'duplicado')
    const config = request.mock.calls[0]?.[0]
    expect(config?.method).toBe('DELETE')
    expect(config?.data).toEqual({ reason: 'duplicado' })
  })

  it('409 no PUT vira ApiError de conflito', async () => {
    request.mockRejectedValue(failure(409, { title: 'Conflict' }))
    await expect(things.update('x', { name: 'n' }, 1)).rejects.toMatchObject({ kind: 'conflict', status: 409 })
    await expect(things.update('x', { name: 'n' }, 1)).rejects.toBeInstanceOf(ApiError)
  })

  it('aviso confirmável chega como ApiError com warnings[]', async () => {
    request.mockRejectedValue(failure(422, { warnings: [{ code: 'SOFT_LIMIT', message: 'Acima do limite.', confirmToken: 'tok-9' }] }))
    await expect(things.create({ name: 'n' })).rejects.toMatchObject({
      kind: 'warning',
      warnings: [{ code: 'SOFT_LIMIT', message: 'Acima do limite.', confirmToken: 'tok-9' }],
    })
  })
})

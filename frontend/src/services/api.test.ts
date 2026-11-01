import { AxiosError, AxiosHeaders, type AxiosAdapter, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi, type MockInstance } from 'vitest'
import { api, authApi } from './api'
import { browser } from './auth/oidc'
import { clearXsrfCookie, setXsrfCookie } from '../test/auth'

/** Servidor falso no lugar da rede: registra cada requisição e responde conforme `respond`. */
function fakeServer(respond: (config: InternalAxiosRequestConfig, call: number) => number) {
  const calls: InternalAxiosRequestConfig[] = []
  const adapter = vi.fn<Parameters<AxiosAdapter>, ReturnType<AxiosAdapter>>(async (config) => {
    calls.push({ ...config, headers: new AxiosHeaders(config.headers) })
    const status = respond(config, calls.length)
    const response = { data: { ok: true }, status, statusText: '', headers: {}, config }
    if (status >= 400) {
      throw new AxiosError(`HTTP ${status}`, 'ERR_BAD_REQUEST', config, null, response)
    }
    return response
  })
  api.defaults.adapter = adapter
  return { calls, adapter }
}

function headerNames(config: InternalAxiosRequestConfig): string[] {
  return Object.keys(AxiosHeaders.from(config.headers).toJSON()).map((h) => h.toLowerCase())
}

let assign: MockInstance<[url: string], void>
beforeEach(() => {
  assign = vi.spyOn(browser, 'assign').mockImplementation(() => undefined)
  clearXsrfCookie()
})
afterEach(() => {
  assign.mockRestore()
  clearXsrfCookie()
  vi.unstubAllGlobals()
})

describe('interceptor de requisição (BFF: cookie, nunca Bearer)', () => {
  it('não envia Authorization nem cabeçalho de tenant; envia o cookie de sessão (withCredentials)', async () => {
    const { calls } = fakeServer(() => 200)
    await api.get('/registry/categories')
    expect(headerNames(calls[0])).not.toContain('authorization')
    expect(headerNames(calls[0]).filter((h) => h.startsWith('x-tenant'))).toEqual([])
    expect(calls[0].withCredentials).toBe(true)
  })

  it('POST envia o anti-CSRF do cookie em X-XSRF-TOKEN', async () => {
    setXsrfCookie('csrf-de-teste')
    const { calls } = fakeServer(() => 200)
    await api.post('/registry/customers/search', { q: 'maria' })
    expect(calls[0].headers['X-XSRF-TOKEN']).toBe('csrf-de-teste')
  })

  it('POST sem cookie busca /bff/csrf antes de sair', async () => {
    const fetchMock = vi.fn(async () => new Response('{}'))
    vi.stubGlobal('fetch', fetchMock)
    fakeServer(() => 200)
    await api.post('/registry/customers/search', { q: 'maria' })
    expect(fetchMock).toHaveBeenCalledWith('/bff/csrf', expect.objectContaining({ credentials: 'include' }))
  })

  it('não grava nada em localStorage nem sessionStorage', async () => {
    fakeServer(() => 200)
    await api.get('/me')
    await api.post('/registry/customers/search', { q: 'maria' })
    expect(localStorage.length).toBe(0)
    expect(sessionStorage.length).toBe(0)
  })

  it('getMe chama GET /api/v1/me', async () => {
    const { calls } = fakeServer(() => 200)
    await authApi.getMe()
    expect(calls[0].method).toBe('get')
    expect(calls[0].baseURL).toBe('/api/v1')
    expect(calls[0].url).toBe('/me')
  })
})

describe('401: sessão acabou', () => {
  it('em chamada de negócio volta para o login do backend (sem repetir a requisição)', async () => {
    const { calls } = fakeServer(() => 401)
    await expect(api.get('/registry/categories')).rejects.toMatchObject({ response: { status: 401 } })
    expect(calls).toHaveLength(1)
    expect(assign).toHaveBeenCalledWith('/bff/login?returnTo=%2F')
  })

  it('em GET /me não redireciona: o guarda de rota decide (PrivateRoute -> /login)', async () => {
    fakeServer(() => 401)
    await expect(authApi.getMe()).rejects.toMatchObject({ response: { status: 401 } })
    expect(assign).not.toHaveBeenCalled()
  })

  it('403 (ex.: tenant divergente) não desloga', async () => {
    fakeServer(() => 403)
    await expect(api.get('/registry/categories')).rejects.toMatchObject({ response: { status: 403 } })
    expect(assign).not.toHaveBeenCalled()
  })
})

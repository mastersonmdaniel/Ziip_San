import { AxiosError, AxiosHeaders, type AxiosAdapter, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi, type MockInstance } from 'vitest'
import { api, authApi } from './api'
import { browser } from './auth/oidc'
import { fakeOidcUser, installFakeAuth, signInForTests } from '../test/auth'

/** Servidor falso no lugar da rede: registra cada requisição e responde conforme `respond`. */
function fakeServer(respond: (config: InternalAxiosRequestConfig, call: number) => number) {
  const calls: InternalAxiosRequestConfig[] = []
  const adapter = vi.fn<Parameters<AxiosAdapter>, ReturnType<AxiosAdapter>>(async (config) => {
    // Cópia: a repetição após o 401 reaproveita (e altera) o mesmo objeto de configuração.
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
})
afterEach(() => {
  assign.mockRestore()
})

describe('interceptor de requisição', () => {
  it('injeta o Bearer do UserManager e não envia cabeçalho de tenant', async () => {
    signInForTests('abc.def.ghi')
    const { calls } = fakeServer(() => 200)
    await api.get('/registry/categories')
    expect(calls[0].headers.Authorization).toBe('Bearer abc.def.ghi')
    expect(headerNames(calls[0]).filter((h) => h.startsWith('x-tenant'))).toEqual([])
  })

  it('sem sessão a requisição sai sem Authorization', async () => {
    installFakeAuth(null)
    const { calls } = fakeServer(() => 200)
    await api.get('/registry/categories')
    expect(headerNames(calls[0])).not.toContain('authorization')
  })

  it('não grava nada em localStorage (nem token nem tenant)', async () => {
    const setItem = vi.spyOn(Storage.prototype, 'setItem')
    signInForTests()
    fakeServer(() => 200)
    await api.get('/me')
    await api.post('/registry/customers/search', { q: 'maria' })
    expect(localStorage.length).toBe(0)
    expect((setItem.mock.instances as unknown[]).some((storage) => storage === localStorage)).toBe(false)
    setItem.mockRestore()
  })

  it('getMe chama GET /api/me', async () => {
    signInForTests()
    const { calls } = fakeServer(() => 200)
    await authApi.getMe()
    expect(calls[0].method).toBe('get')
    expect(calls[0].baseURL).toBe('/api')
    expect(calls[0].url).toBe('/me')
  })
})

describe('401: renova uma vez e, se não der, volta ao login', () => {
  it('renova o token e repete a requisição com o token novo', async () => {
    const auth = signInForTests('velho')
    auth.signinSilent.mockImplementation(async () => {
      const renewed = fakeOidcUser({ access_token: 'novo' })
      auth.getUser.mockResolvedValue(renewed)
      return renewed
    })
    const { calls } = fakeServer((config) => (config.headers.Authorization === 'Bearer velho' ? 401 : 200))
    const response = await api.get('/registry/categories')
    expect(response.status).toBe(200)
    expect(auth.signinSilent).toHaveBeenCalledTimes(1)
    expect(calls.map((c) => c.headers.Authorization)).toEqual(['Bearer velho', 'Bearer novo'])
    expect(assign).not.toHaveBeenCalled()
  })

  it('renovação recusada apaga a sessão e vai para /login', async () => {
    const auth = signInForTests('velho')
    auth.signinSilent.mockRejectedValue(new Error('invalid_grant'))
    const { calls } = fakeServer(() => 401)
    await expect(api.get('/registry/categories')).rejects.toMatchObject({ response: { status: 401 } })
    expect(calls).toHaveLength(1)
    expect(auth.removeUser).toHaveBeenCalled()
    expect(assign).toHaveBeenCalledWith('/login')
  })

  it('401 de novo com o token renovado não entra em laço: uma renovação só e o erro sobe', async () => {
    const auth = signInForTests('velho')
    auth.signinSilent.mockResolvedValue(fakeOidcUser({ access_token: 'novo' }))
    const { calls } = fakeServer(() => 401)
    await expect(api.get('/registry/categories')).rejects.toMatchObject({ response: { status: 401 } })
    expect(calls).toHaveLength(2)
    expect(auth.signinSilent).toHaveBeenCalledTimes(1)
    expect(assign).not.toHaveBeenCalled()
  })

  it('403 (ex.: tenant divergente) não renova nem desloga', async () => {
    const auth = signInForTests()
    fakeServer(() => 403)
    await expect(api.get('/registry/categories')).rejects.toMatchObject({ response: { status: 403 } })
    expect(auth.signinSilent).not.toHaveBeenCalled()
    expect(assign).not.toHaveBeenCalled()
  })
})

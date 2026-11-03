import { afterEach, beforeEach, describe, expect, it, vi, type MockInstance } from 'vitest'
import {
  BFF_CSRF_PATH,
  BFF_LOGIN_PATH,
  BFF_LOGOUT_PATH,
  browser,
  completeLogin,
  getCsrfToken,
  goToLogin,
  logout,
  safeReturnPath,
  startLogin,
} from './oidc'
import { clearXsrfCookie, setXsrfCookie } from '../../test/auth'

let assign: MockInstance<(url: string) => void>
beforeEach(() => {
  assign = vi.spyOn(browser, 'assign').mockImplementation(() => undefined)
  clearXsrfCookie()
})
afterEach(() => {
  assign.mockRestore()
  clearXsrfCookie()
  vi.unstubAllGlobals()
})

describe('safeReturnPath (sem open redirect)', () => {
  it.each([
    ['/cadastro/imoveis?pagina=2', '/cadastro/imoveis?pagina=2'],
    ['/', '/'],
    ['https://evil.example/x', '/'],
    ['//evil.example/x', '/'],
    ['/\\evil.example', '/'],
    ['/auth/callback?code=1', '/'],
    ['/login', '/'],
    ['/bff/login', '/'],
    ['/bff/csrf', '/'],
    [undefined, '/'],
    [42, '/'],
  ])('%s -> %s', (input, expected) => {
    expect(safeReturnPath(input)).toBe(expected)
  })
})

describe('login BFF (navegação, sem token no navegador)', () => {
  it('startLogin vai para /bff/login levando o caminho de volta', async () => {
    await startLogin('/cadastro/clientes')
    expect(assign).toHaveBeenCalledWith(`${BFF_LOGIN_PATH}?returnTo=%2Fcadastro%2Fclientes`)
  })

  it('completeLogin lê o returnTo da URL legada (ou / sem ele)', () => {
    expect(completeLogin('http://localhost:3000/auth/callback?returnTo=/cadastro/clientes')).toBe('/cadastro/clientes')
    expect(completeLogin('http://localhost:3000/auth/callback')).toBe('/')
    expect(completeLogin('http://localhost:3000/auth/callback?returnTo=https://evil.example/x')).toBe('/')
  })

  it('goToLogin volta para o login do backend com a tela atual', async () => {
    await goToLogin()
    expect(assign).toHaveBeenCalledWith(`${BFF_LOGIN_PATH}?returnTo=%2F`)
  })
})

describe('anti-CSRF e logout', () => {
  it('getCsrfToken lê o cookie XSRF-TOKEN (ou null sem ele)', () => {
    expect(getCsrfToken()).toBeNull()
    setXsrfCookie('csrf-de-teste')
    expect(getCsrfToken()).toBe('csrf-de-teste')
  })

  it('logout faz POST /bff/logout com CSRF e volta para /login', async () => {
    setXsrfCookie('csrf-de-teste')
    const fetchMock = vi.fn<(input: string, init?: RequestInit) => Promise<Response>>(async () => new Response('{}'))
    vi.stubGlobal('fetch', fetchMock)
    await logout()
    expect(fetchMock).toHaveBeenCalledWith(
      BFF_LOGOUT_PATH,
      expect.objectContaining({ method: 'POST', credentials: 'include' }),
    )
    const headers = fetchMock.mock.calls[0][1]?.headers as Record<string, string>
    expect(headers['X-XSRF-TOKEN']).toBe('csrf-de-teste')
    expect(assign).toHaveBeenCalledWith('/login')
  })

  it('logout com backend fora do ar ainda volta para /login', async () => {
    setXsrfCookie('csrf-de-teste')
    vi.stubGlobal('fetch', vi.fn(async () => {
      throw new TypeError('Failed to fetch')
    }))
    await logout()
    expect(assign).toHaveBeenCalledWith('/login')
  })

  it('nenhum teste aqui toca em sessionStorage ou localStorage com token', () => {
    expect(sessionStorage.length).toBe(0)
    expect(localStorage.length).toBe(0)
    expect(BFF_CSRF_PATH).toBe('/bff/csrf')
  })
})

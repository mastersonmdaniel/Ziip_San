import { OidcClient, UserManager } from 'oidc-client-ts'
import { describe, expect, it, vi } from 'vitest'
import { fakeOidcUser, installFakeAuth } from '../../test/auth'
import {
  browser,
  completeLogin,
  getAccessToken,
  logout,
  oidcSettings,
  renewAccessToken,
  safeReturnPath,
  startLogin,
} from './oidc'

const AUTHORITY = 'http://localhost:8080/realms/ziipsan'
// Metadados fixos: o teste não busca o .well-known (não há Keycloak aqui).
const metadata = {
  issuer: AUTHORITY,
  authorization_endpoint: `${AUTHORITY}/protocol/openid-connect/auth`,
  token_endpoint: `${AUTHORITY}/protocol/openid-connect/token`,
  end_session_endpoint: `${AUTHORITY}/protocol/openid-connect/logout`,
}

describe('configuração do oidc-client-ts', () => {
  it('usa o realm ziipsan, o client ziipsan-web, code e o callback desta origem', () => {
    const s = oidcSettings('http://localhost:3000')
    expect(s.authority).toBe(AUTHORITY)
    expect(s.client_id).toBe('ziipsan-web')
    expect(s.response_type).toBe('code')
    expect(s.redirect_uri).toBe('http://localhost:3000/auth/callback')
    expect(s.post_logout_redirect_uri).toBe('http://localhost:3000/login')
    expect(s.automaticSilentRenew).toBe(true)
    expect(s.monitorSession).toBe(false)
  })

  it('a requisição de autorização leva PKCE S256 e guarda o estado só em sessionStorage', async () => {
    const client = new OidcClient({ ...oidcSettings('http://localhost:3000'), metadata })
    const request = await client.createSigninRequest({ state: { returnTo: '/cadastro/clientes' } })
    const url = new URL(request.url)
    expect(url.origin + url.pathname).toBe(metadata.authorization_endpoint)
    expect(url.searchParams.get('response_type')).toBe('code')
    expect(url.searchParams.get('client_id')).toBe('ziipsan-web')
    expect(url.searchParams.get('redirect_uri')).toBe('http://localhost:3000/auth/callback')
    expect(url.searchParams.get('code_challenge_method')).toBe('S256')
    // S256 = base64url(SHA-256(verifier)): 43 caracteres, sem o verifier na URL.
    expect(url.searchParams.get('code_challenge')).toMatch(/^[A-Za-z0-9_-]{43}$/)
    expect(url.searchParams.get('code_verifier')).toBeNull()
    expect(url.searchParams.get('scope')).toBe('openid profile email')

    const stateKeys = Object.keys(sessionStorage).filter((k) => k.startsWith('oidc.'))
    expect(stateKeys).toHaveLength(1)
    // O code_verifier fica no estado desta aba (sessionStorage), nunca em localStorage.
    expect(sessionStorage.getItem(stateKeys[0])).toContain('code_verifier')
    expect(localStorage.length).toBe(0)
  })

  it('o UserManager guarda o usuário (tokens) em sessionStorage, nunca em localStorage', async () => {
    const manager = new UserManager({ ...oidcSettings('http://localhost:3000'), metadata, automaticSilentRenew: false })
    await manager.storeUser(fakeOidcUser({ access_token: 'token-guardado' }))
    expect(sessionStorage.getItem(`oidc.user:${AUTHORITY}:ziipsan-web`)).toContain('token-guardado')
    expect(localStorage.length).toBe(0)
    expect((await manager.getUser())?.access_token).toBe('token-guardado')
  })
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
    [undefined, '/'],
    [42, '/'],
  ])('%s -> %s', (input, expected) => {
    expect(safeReturnPath(input)).toBe(expected)
  })
})

describe('login, callback e logout', () => {
  it('startLogin leva ao Keycloak com o caminho de volta (saneado) no state', async () => {
    const auth = installFakeAuth()
    await startLogin('/cadastro/clientes')
    expect(auth.signinRedirect).toHaveBeenCalledWith({ state: { returnTo: '/cadastro/clientes' } })
    await startLogin('https://evil.example')
    expect(auth.signinRedirect).toHaveBeenLastCalledWith({ state: { returnTo: '/' } })
  })

  it('completeLogin processa a mesma URL uma vez só (StrictMode monta duas vezes)', async () => {
    const auth = installFakeAuth()
    auth.signinCallback.mockResolvedValue(fakeOidcUser({ state: { returnTo: '/cadastro/catalogos' } }))
    const url = 'http://localhost:3000/auth/callback?code=abc&state=xyz'
    const [a, b] = await Promise.all([completeLogin(url), completeLogin(url)])
    expect(a).toBe('/cadastro/catalogos')
    expect(b).toBe('/cadastro/catalogos')
    expect(auth.signinCallback).toHaveBeenCalledTimes(1)
  })

  it('completeLogin com state adulterado volta para /', async () => {
    const auth = installFakeAuth()
    auth.signinCallback.mockResolvedValue(fakeOidcUser({ state: { returnTo: '//evil.example' } }))
    expect(await completeLogin('http://localhost:3000/auth/callback?code=1&state=2')).toBe('/')
  })

  it('logout sai pelo end_session_endpoint (signoutRedirect)', async () => {
    const auth = installFakeAuth(fakeOidcUser())
    const assign = vi.spyOn(browser, 'assign').mockImplementation(() => undefined)
    await logout()
    expect(auth.signoutRedirect).toHaveBeenCalledTimes(1)
    expect(assign).not.toHaveBeenCalled()
    assign.mockRestore()
  })

  it('logout com o Keycloak fora do ar apaga a sessão local e vai para /login', async () => {
    const auth = installFakeAuth(fakeOidcUser())
    auth.signoutRedirect.mockRejectedValue(new Error('rede'))
    const assign = vi.spyOn(browser, 'assign').mockImplementation(() => undefined)
    await logout()
    expect(auth.removeUser).toHaveBeenCalledTimes(1)
    expect(assign).toHaveBeenCalledWith('/login')
    assign.mockRestore()
  })
})

describe('access token e renovação', () => {
  it('sem sessão não há token', async () => {
    installFakeAuth(null)
    expect(await getAccessToken()).toBeNull()
  })

  it('token válido é devolvido sem renovar', async () => {
    const auth = installFakeAuth(fakeOidcUser({ access_token: 'valido' }))
    expect(await getAccessToken()).toBe('valido')
    expect(auth.signinSilent).not.toHaveBeenCalled()
  })

  it('token vencido (aba parada) é renovado antes de usar', async () => {
    const auth = installFakeAuth(fakeOidcUser({ access_token: 'velho', expiresInSeconds: -10 }))
    auth.signinSilent.mockResolvedValue(fakeOidcUser({ access_token: 'novo' }))
    expect(await getAccessToken()).toBe('novo')
    expect(auth.signinSilent).toHaveBeenCalledTimes(1)
  })

  it('renovações simultâneas viram uma só', async () => {
    const auth = installFakeAuth(fakeOidcUser())
    auth.signinSilent.mockResolvedValue(fakeOidcUser({ access_token: 'novo' }))
    const tokens = await Promise.all([renewAccessToken(), renewAccessToken(), renewAccessToken()])
    expect(tokens).toEqual(['novo', 'novo', 'novo'])
    expect(auth.signinSilent).toHaveBeenCalledTimes(1)
  })

  it('renovação recusada devolve null e apaga a sessão local', async () => {
    const auth = installFakeAuth(fakeOidcUser())
    auth.signinSilent.mockRejectedValue(new Error('invalid_grant'))
    expect(await renewAccessToken()).toBeNull()
    expect(auth.removeUser).toHaveBeenCalledTimes(1)
  })
})

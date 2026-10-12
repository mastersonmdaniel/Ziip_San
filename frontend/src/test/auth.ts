import { User } from 'oidc-client-ts'
import { vi } from 'vitest'
import { setAuthClientForTests, type AuthClient } from '@services/auth/oidc'

/** Usuário OIDC de mentira (token opaco, só para teste). */
export function fakeOidcUser(overrides: { access_token?: string; expiresInSeconds?: number; state?: unknown } = {}): User {
  return new User({
    access_token: overrides.access_token ?? 'token-de-teste',
    refresh_token: 'refresh-de-teste',
    token_type: 'Bearer',
    profile: { sub: 'user-1', iss: 'http://localhost:8080/realms/sigsan', aud: 'sigsan-web', exp: 0, iat: 0 },
    expires_at: Math.floor(Date.now() / 1000) + (overrides.expiresInSeconds ?? 300),
    userState: overrides.state,
  })
}

export type FakeAuthClient = { [K in keyof AuthClient]: ReturnType<typeof vi.fn> } & AuthClient

/** Cliente OIDC falso instalado no lugar do UserManager. Sem usuário = sem sessão. */
export function installFakeAuth(user: User | null = null): FakeAuthClient {
  let current = user
  const fake = {
    getUser: vi.fn(async () => current),
    signinRedirect: vi.fn(async () => undefined),
    signinCallback: vi.fn(async () => current ?? undefined),
    signinSilent: vi.fn(async () => current),
    signoutRedirect: vi.fn(async () => undefined),
    removeUser: vi.fn(async () => {
      current = null
    }),
  }
  setAuthClientForTests(fake as unknown as AuthClient)
  return fake as unknown as FakeAuthClient
}

/** Substitui o antigo localStorage.setItem('access_token', ...): entra com um usuário válido. */
export function signInForTests(accessToken = 'token-de-teste'): FakeAuthClient {
  return installFakeAuth(fakeOidcUser({ access_token: accessToken }))
}

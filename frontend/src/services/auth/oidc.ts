import { UserManager, WebStorageStateStore, type User, type UserManagerSettings } from 'oidc-client-ts'

/**
 * Login OIDC no Keycloak (realm ziipsan, client público ziipsan-web): authorization code com PKCE
 * S256. O SPA não tem segredo; quem prova que a troca do código é legítima é o code_verifier, que
 * só existe nesta aba.
 *
 * Armazenamento: tokens e estado do redirecionamento (state, nonce, code_verifier) ficam em
 * sessionStorage, nunca em localStorage. O padrão da biblioteca guarda o estado em localStorage;
 * por isso os dois stores são passados explicitamente. sessionStorage morre com a aba e não é
 * compartilhado entre abas, o que reduz o tempo de exposição de um token furtado.
 *
 * O tenant NÃO é escolhido aqui: vem do claim tenant_id do access token, que o administrador grava
 * no usuário do Keycloak. O frontend não envia cabeçalho de tenant.
 */
export const OIDC_AUTHORITY: string = import.meta.env.VITE_OIDC_AUTHORITY || 'http://localhost:8080/realms/ziipsan'
export const OIDC_CLIENT_ID: string = import.meta.env.VITE_OIDC_CLIENT_ID || 'ziipsan-web'
export const CALLBACK_PATH = '/auth/callback'
export const LOGIN_PATH = '/login'

/** O que a aplicação usa do UserManager. Os testes trocam por um objeto de mentira com essa forma. */
export type AuthClient = Pick<
  UserManager,
  'getUser' | 'signinRedirect' | 'signinCallback' | 'signinSilent' | 'signoutRedirect' | 'removeUser'
>

export function oidcSettings(origin: string = window.location.origin): UserManagerSettings {
  const store = new WebStorageStateStore({ store: window.sessionStorage })
  return {
    authority: OIDC_AUTHORITY,
    client_id: OIDC_CLIENT_ID,
    redirect_uri: `${origin}${CALLBACK_PATH}`,
    post_logout_redirect_uri: `${origin}${LOGIN_PATH}`,
    response_type: 'code',
    scope: 'openid profile email',
    userStore: store,
    stateStore: store,
    // Renovação antes de expirar. Com refresh token (o Keycloak emite para o ziipsan-web) a
    // biblioteca chama o token endpoint direto; o iframe de /auth/callback é só o plano B.
    automaticSilentRenew: true,
    // O perfil vem do id token e de GET /api/v1/me; não há por que chamar o userinfo.
    loadUserInfo: false,
    // O iframe de verificação de sessão depende de cookie de terceiro e não é necessário: o logout
    // em outra aba aparece no próximo 401.
    monitorSession: false,
  }
}

/** Navegação de página inteira (separada para os testes: o jsdom não navega). */
export const browser = {
  assign: (url: string): void => window.location.assign(url),
}

let client: AuthClient | null = null

export function getAuthClient(): AuthClient {
  if (!client) client = new UserManager(oidcSettings())
  return client
}

/** Só para testes: troca o cliente por um falso (ou volta ao real com null). */
export function setAuthClientForTests(fake: AuthClient | null): void {
  client = fake
  renewing = null
  callbacks.clear()
}

/** Caminho interno seguro para voltar após o login. Recusa URL absoluta e "//host" (open redirect). */
export function safeReturnPath(value: unknown): string {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//') || value.startsWith('/\\')) return '/'
  if (value.startsWith(CALLBACK_PATH) || value.startsWith(LOGIN_PATH)) return '/'
  return value
}

let renewing: Promise<string | null> | null = null

/**
 * Renova o access token (refresh token). Chamadas simultâneas (vários 401 ao mesmo tempo) dividem
 * a mesma renovação. Falha devolve null e apaga o usuário: o refresh token não serve mais.
 */
export function renewAccessToken(): Promise<string | null> {
  if (!renewing) {
    renewing = (async () => {
      try {
        const user = await getAuthClient().signinSilent()
        return user?.access_token ?? null
      } catch {
        await getAuthClient()
          .removeUser()
          .catch(() => undefined)
        return null
      } finally {
        renewing = null
      }
    })()
  }
  return renewing
}

/** Access token atual; se venceu (aba parada), tenta renovar uma vez. Sem sessão, null. */
export async function getAccessToken(): Promise<string | null> {
  let user: User | null
  try {
    user = await getAuthClient().getUser()
  } catch {
    return null
  }
  if (!user) return null
  if (!user.expired) return user.access_token
  return renewAccessToken()
}

/** Leva ao formulário do Keycloak. O caminho de volta vai no state (que o Keycloak devolve). */
export function startLogin(returnTo?: string): Promise<void> {
  return getAuthClient().signinRedirect({ state: { returnTo: safeReturnPath(returnTo) } })
}

const callbacks = new Map<string, Promise<string>>()

/**
 * Conclui o login em /auth/callback e devolve o caminho de volta. O state é de uso único; o React
 * (StrictMode) monta o componente duas vezes, então a mesma URL reaproveita o mesmo processamento.
 */
export function completeLogin(url: string = window.location.href): Promise<string> {
  let pending = callbacks.get(url)
  if (!pending) {
    pending = getAuthClient()
      .signinCallback(url)
      .then((user) => safeReturnPath((user?.state as { returnTo?: unknown } | undefined)?.returnTo))
    callbacks.set(url, pending)
  }
  return pending
}

/**
 * Sai pelo end_session_endpoint do Keycloak (encerra a sessão SSO, não só a local). Se o Keycloak
 * não responder, ao menos apaga a sessão local.
 */
export async function logout(): Promise<void> {
  const auth = getAuthClient()
  try {
    await auth.signoutRedirect()
  } catch {
    await auth.removeUser().catch(() => undefined)
    browser.assign(LOGIN_PATH)
  }
}

/** Sessão local inválida (renovação falhou): apaga o usuário e volta para a tela de entrada. */
export async function goToLogin(): Promise<void> {
  await getAuthClient()
    .removeUser()
    .catch(() => undefined)
  browser.assign(LOGIN_PATH)
}

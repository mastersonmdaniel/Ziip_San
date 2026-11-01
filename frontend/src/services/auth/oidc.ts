/**
 * Login BFF (P0.14): o navegador NUNCA vê token.
 *
 * Antes, o SPA usava oidc-client-ts: access token em sessionStorage, Bearer em
 * cada requisição e renovação silenciosa. Agora a sessão vive no servidor (o
 * processo api, em PostgreSQL — core V5): o navegador só carrega dois cookies,
 * o de sessão (__Host-, HttpOnly, Secure, SameSite=Lax) e o anti-CSRF
 * (XSRF-TOKEN, legível pelo JS). O login é uma navegação de página inteira:
 * /bff/login (backend) -> Keycloak -> callback do backend -> caminho de volta.
 *
 * O tenant NÃO é escolhido aqui: vem do claim tenant_id do access token guardado
 * no servidor, que o administrador grava no usuário do Keycloak. O frontend não
 * envia cabeçalho de tenant e nunca envia Authorization.
 */

export const LOGIN_PATH = '/login'
/** Rota legada do fluxo antigo: hoje o BFF volta direto para o returnTo; mantida para links antigos. */
export const CALLBACK_PATH = '/auth/callback'
export const BFF_LOGIN_PATH = '/bff/login'
export const BFF_LOGOUT_PATH = '/bff/logout'
export const BFF_CSRF_PATH = '/bff/csrf'
export const CSRF_COOKIE_NAME = 'XSRF-TOKEN'
export const CSRF_HEADER_NAME = 'X-XSRF-TOKEN'

/** Navegação de página inteira (separada para os testes: o jsdom não navega). */
export const browser = {
  assign: (url: string): void => window.location.assign(url),
}

/** Caminho interno seguro para voltar após o login. Recusa URL absoluta e "//host" (open redirect). */
export function safeReturnPath(value: unknown): string {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//') || value.startsWith('/\\')) return '/'
  if (value.startsWith(CALLBACK_PATH) || value.startsWith(LOGIN_PATH) || value.startsWith('/bff')) return '/'
  return value
}

/** Leva ao login do backend (que leva ao Keycloak). O caminho de volta vai na query, guardado na sessão. */
export async function startLogin(returnTo?: string): Promise<void> {
  browser.assign(`${BFF_LOGIN_PATH}?returnTo=${encodeURIComponent(safeReturnPath(returnTo))}`)
}

/**
 * Caminho de volta após o login, a partir da URL atual. No fluxo BFF o backend já
 * redireciona para o returnTo; isto só serve à rota legada /auth/callback (link
 * antigo que ainda carregue ?returnTo=...).
 */
export function completeLogin(url: string = window.location.href): string {
  try {
    return safeReturnPath(new URL(url).searchParams.get('returnTo'))
  } catch {
    return '/'
  }
}

/** Token anti-CSRF lido do cookie (o axios o reenvia em X-XSRF-TOKEN). Null = ainda sem cookie. */
export function getCsrfToken(): string | null {
  const match = document.cookie.match(new RegExp(`(?:^|; )${CSRF_COOKIE_NAME}=([^;]*)`))
  return match ? decodeURIComponent(match[1]) : null
}

/** Garante o cookie anti-CSRF antes do primeiro POST (o backend o grava ao responder /bff/csrf). */
export async function ensureCsrf(): Promise<void> {
  if (getCsrfToken()) return
  try {
    await fetch(BFF_CSRF_PATH, { credentials: 'include' })
  } catch {
    // Sem cookie, o backend responde 403 no POST; o erro aparece na tela, sem máscara.
  }
}

/**
 * Sai: POST /bff/logout (com CSRF) invalida a sessão no PostgreSQL e encerra o
 * SSO no Keycloak; depois volta para a tela de entrada, haja o que houver.
 */
export async function logout(): Promise<void> {
  try {
    await ensureCsrf()
    await fetch(BFF_LOGOUT_PATH, {
      method: 'POST',
      credentials: 'include',
      headers: { [CSRF_HEADER_NAME]: getCsrfToken() ?? '' },
    })
  } catch {
    // Sessão já morta ou backend fora do ar: sair da tela de qualquer forma.
  }
  browser.assign(LOGIN_PATH)
}

/** Sessão acabou (401 da API): volta para o login do backend, voltando depois para a tela atual. */
export async function goToLogin(): Promise<void> {
  browser.assign(`${BFF_LOGIN_PATH}?returnTo=${encodeURIComponent(safeReturnPath(window.location.pathname))}`)
}

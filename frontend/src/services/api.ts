import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { ensureCsrf, getCsrfToken, goToLogin } from '@services/auth/oidc'
import { resolveApiBaseUrl } from '@services/runtimeConfig'
import {
  VERSION_OUTDATED_EVENT,
  getClientVersion,
  isClientOutdated,
  type VersionOutdatedDetail,
} from '@services/versionInfo'

/**
 * Cliente HTTP único. Base padrão: /api/v1 (P0.10: rotas de negócio sob /api/v1; o proxy do
 * Vite repassa para o backend; o prefixo NÃO é removido, porque o contrato do cadastro é
 * /api/v1/registry/...).
 *
 * Autenticação (P0.14, BFF): NENHUM token no navegador. O navegador envia o cookie de
 * sessão (withCredentials) e o anti-CSRF (XSRF-TOKEN -> X-XSRF-TOKEN, gerenciado pelo
 * axios a partir do cookie). Nunca é enviado Authorization: Bearer, e qualquer
 * Authorization que chegue a este cliente é removido antes de sair. Não há cabeçalho
 * de tenant (X-Tenant-ID/X-Tenant-UUID): o tenant vem do claim tenant_id do token
 * guardado no servidor, e o backend responde 403 se receber um cabeçalho que diverge
 * dele. Dados pessoais (CPF/CNPJ etc.) nunca vão para localStorage/sessionStorage/URL.
 */
/**
 * Fallback de build (VITE_API_BASE_URL ou /api/v1). O valor EFETIVO por requisição vem
 * de resolveApiBaseUrl() (P0.5: config de runtime carregada antes do app) e é aplicado
 * no interceptor abaixo — a instância nasce com o fallback porque o módulo é avaliado
 * antes de main.tsx aguardar loadRuntimeConfig().
 */
export const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL || '/api/v1'

export const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use(async (config: InternalAxiosRequestConfig) => {
  // P0.5: a base de runtime (se houver) vence o fallback de build em cada requisição,
  // sem recriar a instância. Sem config de runtime, resolveApiBaseUrl() devolve o fallback.
  config.baseURL = resolveApiBaseUrl()
  // Defesa: este cliente nunca envia Bearer, mesmo que algum código o tenha posto.
  delete config.headers.Authorization
  // POST/PUT/DELETE exigem o anti-CSRF do BFF; o GET do /bff/csrf grava o cookie.
  // O cabeçalho é posto aqui (não só pelo adaptador xhr do axios), para valer com
  // qualquer adaptador — inclusive o falso dos testes.
  if (config.method && !['get', 'head', 'options'].includes(config.method.toLowerCase())) {
    await ensureCsrf()
    const csrf = getCsrfToken()
    if (csrf) config.headers['X-XSRF-TOKEN'] = csrf
  }
  return config
})

/**
 * 401: a sessão acabou. Em chamada de negócio, volta para o login do backend (que
 * depois devolve para a tela atual). Em GET /me, NÃO redireciona: quem decide é o
 * guarda de rota (PrivateRoute -> /login), e o redirecionamento aqui tiraria o
 * usuário da tela de entrada antes de ele clicar em Entrar.
 */
/**
 * P0.39: toda resposta traz Ziip-Version e Ziip-Min-Client-Version (VersionHeaderFilter).
 * Se o mínimo do servidor está acima da versão deste build, dispara o evento que o
 * VersionBanner escuta — uma única vez por versão mínima, para não reanunciar a cada
 * requisição. Build dev nunca dispara (sem versão real para comparar).
 */
let lastNotifiedMinVersion: string | null = null

function checkServerVersion(response: { headers?: unknown }): void {
  const headers = response.headers as
    | { get?: (name: string) => string | undefined; [key: string]: unknown }
    | undefined
  const minClient =
    headers?.get?.('Ziip-Min-Client-Version') ??
    (typeof headers?.['ziip-min-client-version'] === 'string'
      ? (headers['ziip-min-client-version'] as string)
      : undefined)
  const serverVersion =
    headers?.get?.('Ziip-Version') ??
    (typeof headers?.['ziip-version'] === 'string' ? (headers['ziip-version'] as string) : 'desconhecida')
  if (!minClient || minClient === lastNotifiedMinVersion) return
  if (!isClientOutdated(getClientVersion(), minClient)) return
  lastNotifiedMinVersion = minClient
  if (typeof window !== 'undefined') {
    const detail: VersionOutdatedDetail = { serverVersion, minClientVersion: minClient }
    window.dispatchEvent(new CustomEvent(VERSION_OUTDATED_EVENT, { detail }))
  }
}

api.interceptors.response.use(
  (response) => {
    checkServerVersion(response)
    return response
  },
  async (error: AxiosError) => {
    // Respostas de erro também trazem os cabeçalhos de versão (o filtro roda antes da segurança).
    if (error.response) checkServerVersion(error.response)
    const original = error.config as InternalAxiosRequestConfig | undefined
    if (error.response?.status === 401 && original && !original.url?.endsWith('/me')) {
      await goToLogin()
    }
    return Promise.reject(error)
  },
)

/**
 * Usuário atual, como o backend devolve em GET /api/v1/me (montado a partir do token
 * guardado no servidor: sub, preferred_username, email, name, realm_access.roles e tenant_id).
 */
export interface AuthUser {
  id: string
  username: string
  email: string
  fullName: string
  roles: string[]
  tenantId: string
}

export const authApi = {
  getMe: () => api.get<AuthUser>('/me').then((r) => r.data),
}

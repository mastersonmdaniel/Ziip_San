import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { getAccessToken, goToLogin, renewAccessToken } from '@services/auth/oidc'
import { resolveApiBaseUrl } from '@services/runtimeConfig'

/**
 * Cliente HTTP único. Base padrão: /api/v1 (P0.10: rotas de negócio sob /api/v1; o proxy do
 * Vite repassa para o backend; o prefixo NÃO é removido, porque o contrato do cadastro é
 * /api/v1/registry/...).
 *
 * Autenticação: só `Authorization: Bearer <access token do Keycloak>`. Não há cabeçalho de tenant
 * (X-Tenant-ID/X-Tenant-UUID): o tenant vem do claim tenant_id do token, e o backend responde 403
 * se receber um cabeçalho que diverge dele. O token fica com o oidc-client-ts (sessionStorage);
 * este módulo não grava nada em storage. Dados pessoais (CPF/CNPJ etc.) nunca vão para
 * localStorage/sessionStorage/URL.
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
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use(async (config: InternalAxiosRequestConfig) => {
  // P0.5: a base de runtime (se houver) vence o fallback de build em cada requisição,
  // sem recriar a instância. Sem config de runtime, resolveApiBaseUrl() devolve o fallback.
  config.baseURL = resolveApiBaseUrl()
  const token = await getAccessToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

type RetriableConfig = InternalAxiosRequestConfig & { _retry?: boolean }

/**
 * 401: o token venceu ou foi revogado. Renova UMA vez e repete a requisição; se a renovação falhar,
 * a sessão acabou e o usuário volta para a tela de entrada. Um segundo 401 com token novo não
 * renova de novo (evita laço) e sobe como erro para a tela.
 */
api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as RetriableConfig | undefined
    if (error.response?.status === 401 && original && !original._retry) {
      original._retry = true
      const token = await renewAccessToken()
      if (token) {
        original.headers.Authorization = `Bearer ${token}`
        return api.request(original)
      }
      await goToLogin()
    }
    return Promise.reject(error)
  },
)

/**
 * Usuário atual, como o backend devolve em GET /api/me (montado a partir do token validado:
 * sub, preferred_username, email, name, realm_access.roles e tenant_id).
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

import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'

/**
 * Cliente HTTP único. Base padrão: /api (o proxy do Vite repassa para o backend; o prefixo NÃO
 * é removido, porque o contrato do cadastro é /api/registry/...).
 *
 * Observação de segurança: o token de acesso fica em localStorage (decisão herdada). Dados
 * pessoais (CPF/CNPJ etc.) nunca são gravados em localStorage/sessionStorage/URL.
 */
export const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL || '/api'

export const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
})

function readStorage(key: string): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

function writeStorage(key: string, value: string | null): void {
  try {
    if (value === null) localStorage.removeItem(key)
    else localStorage.setItem(key, value)
  } catch {
    // armazenamento indisponível (modo privativo): a sessão vale só até recarregar
  }
}

api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = readStorage('access_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  const tenant = readStorage('tenant_id')
  if (tenant) config.headers['X-Tenant-ID'] = tenant
  return config
})

type RetriableConfig = InternalAxiosRequestConfig & { _retry?: boolean }

interface TokenPair {
  access_token: string
  refresh_token: string
}

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as RetriableConfig | undefined
    const isAuthCall = original?.url?.startsWith('/auth/') ?? false
    if (error.response?.status === 401 && original && !original._retry && !isAuthCall) {
      original._retry = true
      const refreshToken = readStorage('refresh_token')
      if (refreshToken) {
        try {
          const { data } = await axios.post<TokenPair>(`${API_BASE_URL}/auth/refresh`, {
            refresh_token: refreshToken,
          })
          writeStorage('access_token', data.access_token)
          writeStorage('refresh_token', data.refresh_token)
          original.headers.Authorization = `Bearer ${data.access_token}`
          return api.request(original)
        } catch {
          writeStorage('access_token', null)
          writeStorage('refresh_token', null)
          window.location.href = '/login'
        }
      }
    }
    return Promise.reject(error)
  },
)

export interface AuthUser {
  id: string
  username: string
  email: string
  fullName: string
  roles: string[]
  tenantId: string
}

export interface LoginResponse extends TokenPair {
  user: AuthUser
}

/**
 * Contrato de autenticação herdado do código anterior. ATENÇÃO: o módulo platform/sigsan-auth
 * ainda está vazio; estes endpoints não existem no backend até ser implementado.
 */
export const authApi = {
  login: (username: string, password: string) =>
    api.post<LoginResponse>('/auth/login', { username, password }).then((r) => r.data),
  logout: () => api.post<void>('/auth/logout').then(() => undefined),
  getMe: () => api.get<AuthUser>('/auth/me').then((r) => r.data),
}

export const storage = { read: readStorage, write: writeStorage }

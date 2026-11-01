import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { authApi, type AuthUser } from '@services/api'
import * as oidc from '@services/auth/oidc'
import { errorMessage } from '@services/registry/errors'

interface AuthContextType {
  user: AuthUser | null
  isAuthenticated: boolean
  loading: boolean
  /** Falha ao carregar o usuário com uma sessão válida (ex.: backend fora do ar). 401 não é erro: é sem sessão. */
  error: string | null
  /** Vai para o login do backend (que leva ao Keycloak); returnTo é o caminho interno para voltar depois. */
  login: (returnTo?: string) => Promise<void>
  /** Rota legada /auth/callback: carrega o usuário e devolve o caminho de volta. */
  completeLogin: (url?: string) => Promise<string>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

/**
 * Estado de autenticação da aplicação (P0.14, BFF). Quem autentica é o Keycloak, via
 * o backend; aqui só se sabe se há sessão e quem é o usuário segundo o backend
 * (GET /api/v1/me). Nenhum token passa pelo SPA: a sessão é um cookie HttpOnly do
 * servidor. A senha nunca passa pelo SPA: o formulário é o do Keycloak.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const loadMe = useCallback(async () => {
    try {
      const me = await authApi.getMe()
      setUser(me)
      setError(null)
    } catch (e) {
      setUser(null)
      // 401 = sem sessão: o guarda de rota leva para /login; não é erro a exibir.
      const status = (e as { response?: { status?: number } } | null)?.response?.status
      setError(status === 401 ? null : errorMessage(e))
    }
  }, [])

  useEffect(() => {
    let cancelled = false
    async function init() {
      // Em /auth/callback ainda não se carrega o usuário; quem carrega é o completeLogin.
      if (window.location.pathname !== oidc.CALLBACK_PATH) {
        await loadMe()
      }
      if (!cancelled) setLoading(false)
    }
    void init()
    return () => {
      cancelled = true
    }
  }, [loadMe])

  const login = useCallback((returnTo?: string) => oidc.startLogin(returnTo), [])

  // O StrictMode monta o callback duas vezes; as duas montagens dividem o mesmo processamento (e o
  // mesmo GET /api/v1/me).
  const completing = useRef<Promise<string> | null>(null)
  const completeLogin = useCallback((url?: string) => {
    if (!completing.current) {
      completing.current = (async () => {
        const returnTo = oidc.completeLogin(url)
        await loadMe()
        setLoading(false)
        return returnTo
      })()
    }
    return completing.current
  }, [loadMe])

  const logout = useCallback(async () => {
    setUser(null)
    await oidc.logout()
  }, [])

  const value = useMemo(
    () => ({ user, isAuthenticated: user !== null, loading, error, login, completeLogin, logout }),
    [user, loading, error, login, completeLogin, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextType {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth deve ser usado dentro de um AuthProvider')
  }
  return context
}

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { authApi, type AuthUser } from '@services/api'
import * as oidc from '@services/auth/oidc'
import { errorMessage } from '@services/registry/errors'

interface AuthContextType {
  user: AuthUser | null
  isAuthenticated: boolean
  loading: boolean
  /** Falha ao carregar o usuário com um token válido (ex.: backend fora do ar). */
  error: string | null
  /** Vai para o Keycloak; returnTo é o caminho interno para voltar depois. */
  login: (returnTo?: string) => Promise<void>
  /** Conclui o login em /auth/callback e devolve o caminho de volta. */
  completeLogin: () => Promise<string>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

/**
 * Estado de autenticação da aplicação. Quem autentica é o Keycloak (oidc-client-ts); aqui só se
 * sabe se há token e quem é o usuário segundo o backend (GET /api/me). A senha nunca passa pelo
 * SPA: o formulário é o do Keycloak.
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
      setError(errorMessage(e))
    }
  }, [])

  useEffect(() => {
    let cancelled = false
    async function init() {
      // Em /auth/callback ainda não há token; quem carrega o usuário é o completeLogin.
      if (window.location.pathname !== oidc.CALLBACK_PATH && (await oidc.getAccessToken())) {
        if (!cancelled) await loadMe()
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
  // mesmo GET /api/me).
  const completing = useRef<Promise<string> | null>(null)
  const completeLogin = useCallback(() => {
    if (!completing.current) {
      completing.current = (async () => {
        const returnTo = await oidc.completeLogin()
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

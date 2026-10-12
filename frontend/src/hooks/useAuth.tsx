import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { authApi, storage, type AuthUser } from '@services/api'

interface AuthContextType {
  user: AuthUser | null
  isAuthenticated: boolean
  loading: boolean
  login: (username: string, password: string, tenant: string) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    async function init() {
      if (storage.read('access_token')) {
        try {
          const me = await authApi.getMe()
          if (!cancelled) setUser(me)
        } catch {
          storage.write('access_token', null)
          storage.write('refresh_token', null)
        }
      }
      if (!cancelled) setLoading(false)
    }
    void init()
    return () => {
      cancelled = true
    }
  }, [])

  const login = useCallback(async (username: string, password: string, tenant: string) => {
    storage.write('tenant_id', tenant.trim())
    const result = await authApi.login(username, password)
    storage.write('access_token', result.access_token)
    storage.write('refresh_token', result.refresh_token)
    setUser(result.user)
  }, [])

  const logout = useCallback(async () => {
    try {
      await authApi.logout()
    } catch {
      // sair localmente mesmo se o servidor não responder
    }
    storage.write('access_token', null)
    storage.write('refresh_token', null)
    setUser(null)
  }, [])

  const value = useMemo(
    () => ({ user, isAuthenticated: user !== null, loading, login, logout }),
    [user, loading, login, logout],
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

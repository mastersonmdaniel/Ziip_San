import { useState } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { LogIn } from 'lucide-react'
import { Alert } from '@components/ui/Alert'
import { Button } from '@components/ui/Button'
import { useAuth } from '@hooks/useAuth'

/**
 * Entrada no sistema. Não há formulário aqui: o botão leva ao Keycloak (authorization code com
 * PKCE), que pede usuário e senha e devolve em /auth/callback. A empresa (tenant) não é digitada:
 * vem do cadastro do usuário no Keycloak, dentro do token.
 */
export function Login() {
  const { login, isAuthenticated, error } = useAuth()
  const location = useLocation()
  const [busy, setBusy] = useState(false)
  const [startError, setStartError] = useState<string | null>(null)
  const from = (location.state as { from?: string } | null)?.from

  if (isAuthenticated) {
    return <Navigate to={from ?? '/'} replace />
  }

  const onLogin = async () => {
    setBusy(true)
    setStartError(null)
    try {
      await login(from)
      // Sucesso = o navegador já está indo para o Keycloak; o botão fica ocupado até sair da página.
    } catch {
      setStartError('Não foi possível falar com o servidor de autenticação. Tente novamente em instantes.')
      setBusy(false)
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-muted/40 p-4">
      <section className="w-full max-w-sm space-y-4 rounded-xl border bg-card p-6 shadow-sm" aria-labelledby="login-title">
        <h1 id="login-title" className="text-2xl font-bold text-primary">
          SIGSAN
        </h1>
        <p className="text-sm text-muted-foreground">Entre com a sua conta da empresa para continuar.</p>
        {startError ? <Alert tone="error">{startError}</Alert> : null}
        {!startError && error ? (
          <Alert tone="error" title="Sua sessão não pôde ser carregada">
            {error}
          </Alert>
        ) : null}
        <Button type="button" className="w-full" loading={busy} onClick={() => void onLogin()}>
          {busy ? null : <LogIn className="h-4 w-4" aria-hidden="true" />}
          Entrar
        </Button>
      </section>
    </main>
  )
}

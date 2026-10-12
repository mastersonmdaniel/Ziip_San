import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Alert, Spinner } from '@components/ui/Alert'
import { useAuth } from '@hooks/useAuth'

/**
 * Volta do Keycloak (/auth/callback?code=...&state=...). Troca o código pelos tokens (com o
 * code_verifier guardado nesta aba) e segue para a tela de onde o usuário veio. A navegação usa
 * replace: o código (de uso único) não fica no histórico do navegador.
 */
export function AuthCallback() {
  const { completeLogin } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    completeLogin()
      .then((returnTo) => {
        if (active) navigate(returnTo, { replace: true })
      })
      .catch((e: unknown) => {
        if (!active) return
        // ErrorResponse do oidc-client-ts traz error_description do Keycloak (ex.: acesso negado).
        const description = (e as { error_description?: unknown } | null)?.error_description
        setError(typeof description === 'string' && description ? description : 'O login não foi concluído.')
      })
    return () => {
      active = false
    }
  }, [completeLogin, navigate])

  if (!error) {
    return (
      <main className="flex min-h-screen items-center justify-center p-4">
        <Spinner label="Concluindo a entrada..." />
      </main>
    )
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-muted/40 p-4">
      <section className="w-full max-w-sm space-y-4 rounded-xl border bg-card p-6 shadow-sm" aria-labelledby="callback-title">
        <h1 id="callback-title" className="text-2xl font-bold text-primary">
          SIGSAN
        </h1>
        <Alert tone="error" title="Não foi possível entrar">
          {error}
        </Alert>
        <Link to="/login" className="inline-block text-sm font-medium text-primary underline underline-offset-4">
          Tentar de novo
        </Link>
      </section>
    </main>
  )
}

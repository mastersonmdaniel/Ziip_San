import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { Alert, Spinner } from '@components/ui/Alert'
import { useAuth } from '@hooks/useAuth'
import { useBrand } from '../brand/BrandProvider'

/**
 * Rota legada /auth/callback (fluxo antigo com oidc-client-ts). No fluxo BFF (P0.14)
 * o backend já devolve o navegador direto para o caminho de volta, então ninguém
 * deveria cair aqui; quem cair (link antigo com ?returnTo=...) só carrega o usuário
 * e segue. Não há mais troca de código por token no navegador.
 */
export function AuthCallback() {
  const { completeLogin } = useAuth()
  const brand = useBrand()
  const navigate = useNavigate()
  const location = useLocation()
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    // A URL vem do router (não de window.location): no teste com MemoryRouter a
    // barra do navegador não muda, e em produção as duas coincidem.
    completeLogin(`${window.location.origin}${location.pathname}${location.search}`)
      .then((returnTo) => {
        if (active) navigate(returnTo, { replace: true })
      })
      .catch(() => {
        if (active) setError('O login não foi concluído.')
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
          {brand.productName}
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

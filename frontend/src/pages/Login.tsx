import { useState, type FormEvent } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { Alert } from '@components/ui/Alert'
import { Button } from '@components/ui/Button'
import { Field, Input } from '@components/ui/Field'
import { useAuth } from '@hooks/useAuth'
import { storage } from '@services/api'
import { errorMessage } from '@services/registry/errors'

/**
 * Entrada no sistema. ATENÇÃO: o módulo de autenticação do backend (platform/sigsan-auth) ainda
 * não existe; sem ele o envio mostra o erro real de rede. A senha nunca é guardada.
 */
export function Login() {
  const { login, isAuthenticated } = useAuth()
  const location = useLocation()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [tenant, setTenant] = useState(() => storage.read('tenant_id') ?? '')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (isAuthenticated) {
    const from = (location.state as { from?: string } | null)?.from
    return <Navigate to={from ?? '/'} replace />
  }

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault()
    if (!username.trim() || !password || !tenant.trim()) {
      setError('Informe a empresa, o usuário e a senha.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      await login(username.trim(), password, tenant)
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-muted/40 p-4">
      <form onSubmit={(e) => void onSubmit(e)} noValidate className="w-full max-w-sm space-y-4 rounded-xl border bg-card p-6 shadow-sm" aria-labelledby="login-title">
        <h1 id="login-title" className="text-2xl font-bold text-primary">
          SIGSAN
        </h1>
        <p className="text-sm text-muted-foreground">Entre para continuar.</p>
        {error ? <Alert tone="error">{error}</Alert> : null}
        <Field label="Empresa (identificador do tenant)" required>
          {(aria) => <Input {...aria} value={tenant} onChange={(e) => setTenant(e.target.value)} autoComplete="organization" />}
        </Field>
        <Field label="Usuário" required>
          {(aria) => <Input {...aria} value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" />}
        </Field>
        <Field label="Senha" required>
          {(aria) => <Input {...aria} type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" />}
        </Field>
        <Button type="submit" className="w-full" loading={busy}>
          Entrar
        </Button>
      </form>
    </main>
  )
}

import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { axe } from 'vitest-axe'
import { describe, expect, it, vi } from 'vitest'
import type { AxiosAdapter } from 'axios'
import { AuthProvider } from '@hooks/useAuth'
import { api } from '@services/api'
import { ErrorResponse } from 'oidc-client-ts'
import { fakeOidcUser, installFakeAuth } from '../test/auth'
import { TEST_USER } from '../test/fixtures'
import { AuthCallback } from './AuthCallback'
import { Login } from './Login'

/** /api/me responde com o usuário de teste (ou com o status informado). */
function fakeMe(status = 200) {
  const adapter = vi.fn<Parameters<AxiosAdapter>, ReturnType<AxiosAdapter>>(async (config) => {
    if (status !== 200) {
      const { AxiosError } = await import('axios')
      throw new AxiosError('erro', 'ERR_BAD_RESPONSE', config, null, { data: {}, status, statusText: '', headers: {}, config })
    }
    return { data: TEST_USER, status: 200, statusText: '', headers: {}, config }
  })
  api.defaults.adapter = adapter
  return adapter
}

function renderAt(route: string | { pathname: string; state?: unknown }) {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/auth/callback" element={<AuthCallback />} />
          <Route path="/cadastro/clientes" element={<h1>Tela de clientes</h1>} />
          <Route path="/" element={<h1>Início</h1>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe('tela de entrada (/login)', () => {
  it('só tem o botão Entrar: nada de empresa, usuário ou senha no SPA', async () => {
    installFakeAuth(null)
    const { container } = renderAt('/login')
    expect(await screen.findByRole('button', { name: 'Entrar' })).toBeEnabled()
    expect(screen.queryAllByRole('textbox')).toHaveLength(0)
    expect(container.querySelector('input[type="password"]')).toBeNull()
    expect(await axe(container)).toHaveNoViolations()
  })

  it('Entrar inicia o fluxo no Keycloak levando a tela de origem', async () => {
    const auth = installFakeAuth(null)
    const user = userEvent.setup()
    renderAt({ pathname: '/login', state: { from: '/cadastro/clientes' } })
    await user.click(await screen.findByRole('button', { name: 'Entrar' }))
    expect(auth.signinRedirect).toHaveBeenCalledWith({ state: { returnTo: '/cadastro/clientes' } })
  })

  it('Keycloak fora do ar mostra erro e libera o botão', async () => {
    const auth = installFakeAuth(null)
    auth.signinRedirect.mockRejectedValue(new TypeError('Failed to fetch'))
    const user = userEvent.setup()
    renderAt('/login')
    await user.click(await screen.findByRole('button', { name: 'Entrar' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível falar com o servidor de autenticação')
    expect(screen.getByRole('button', { name: 'Entrar' })).toBeEnabled()
  })

  it('com sessão válida, /login segue direto para a aplicação', async () => {
    installFakeAuth(fakeOidcUser())
    fakeMe()
    renderAt('/login')
    expect(await screen.findByRole('heading', { name: 'Início' })).toBeInTheDocument()
  })

  it('token válido mas /api/me falhando: mostra o erro em vez de fingir que entrou', async () => {
    installFakeAuth(fakeOidcUser())
    fakeMe(503)
    renderAt('/login')
    expect(await screen.findByRole('alert')).toHaveTextContent('Sua sessão não pôde ser carregada')
  })
})

describe('retorno do Keycloak (/auth/callback)', () => {
  it('conclui o login, carrega /api/me e volta para a tela de origem', async () => {
    const auth = installFakeAuth(null)
    auth.signinCallback.mockImplementation(async () => {
      const u = fakeOidcUser({ state: { returnTo: '/cadastro/clientes' } })
      auth.getUser.mockResolvedValue(u)
      return u
    })
    const me = fakeMe()
    renderAt('/auth/callback?code=abc&state=xyz')
    expect(await screen.findByRole('heading', { name: 'Tela de clientes' })).toBeInTheDocument()
    expect(auth.signinCallback).toHaveBeenCalledTimes(1)
    await waitFor(() => expect(me).toHaveBeenCalled())
    expect(me.mock.calls[0][0].url).toBe('/me')
    expect(me.mock.calls[0][0].headers.Authorization).toBe('Bearer token-de-teste')
  })

  it('erro devolvido pelo Keycloak aparece com a descrição e um caminho para tentar de novo', async () => {
    const auth = installFakeAuth(null)
    auth.signinCallback.mockRejectedValue(new ErrorResponse({ error: 'access_denied', error_description: 'Acesso negado pelo usuário' }))
    const { container } = renderAt('/auth/callback?error=access_denied&state=xyz')
    expect(await screen.findByRole('alert')).toHaveTextContent('Acesso negado pelo usuário')
    expect(screen.getByRole('link', { name: 'Tentar de novo' })).toHaveAttribute('href', '/login')
    expect(await axe(container)).toHaveNoViolations()
  })
})

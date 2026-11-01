import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Navigate, Route, Routes } from 'react-router-dom'
import { axe } from 'vitest-axe'
import { afterEach, beforeEach, describe, expect, it, vi, type MockInstance } from 'vitest'
import type { AxiosAdapter } from 'axios'
import { AuthProvider, useAuth } from '@hooks/useAuth'
import { api } from '@services/api'
import { browser } from '@services/auth/oidc'
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

/** Guarda igual ao PrivateRoute do App: sem usuário, /cadastro/clientes volta para /login. */
function GuardedClientes() {
  const { isAuthenticated, loading } = useAuth()
  if (loading) return null
  if (!isAuthenticated) return <Navigate to="/login" replace />
  return <h1>Tela de clientes</h1>
}

function renderAt(route: string | { pathname: string; state?: unknown }) {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/auth/callback" element={<AuthCallback />} />
          <Route path="/cadastro/clientes" element={<GuardedClientes />} />
          <Route path="/" element={<h1>Início</h1>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  )
}

let assign: MockInstance<[url: string], void>
beforeEach(() => {
  assign = vi.spyOn(browser, 'assign').mockImplementation(() => undefined)
})
afterEach(() => {
  assign.mockRestore()
})

describe('tela de entrada (/login)', () => {
  it('só tem o botão Entrar: nada de empresa, usuário ou senha no SPA', async () => {
    fakeMe(401)
    const { container } = renderAt('/login')
    expect(await screen.findByRole('button', { name: 'Entrar' })).toBeEnabled()
    expect(screen.queryAllByRole('textbox')).toHaveLength(0)
    expect(container.querySelector('input[type="password"]')).toBeNull()
    expect(await axe(container)).toHaveNoViolations()
  })

  it('Entrar vai para o login do backend levando a tela de origem', async () => {
    fakeMe(401)
    const user = userEvent.setup()
    renderAt({ pathname: '/login', state: { from: '/cadastro/clientes' } })
    await user.click(await screen.findByRole('button', { name: 'Entrar' }))
    expect(assign).toHaveBeenCalledWith('/bff/login?returnTo=%2Fcadastro%2Fclientes')
  })

  it('login do backend fora do ar mostra erro e libera o botão', async () => {
    fakeMe(401)
    assign.mockImplementation(() => {
      throw new TypeError('Failed to fetch')
    })
    const user = userEvent.setup()
    renderAt('/login')
    await user.click(await screen.findByRole('button', { name: 'Entrar' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível falar com o servidor de autenticação')
    expect(screen.getByRole('button', { name: 'Entrar' })).toBeEnabled()
  })

  it('com sessão válida, /login segue direto para a aplicação', async () => {
    fakeMe()
    renderAt('/login')
    expect(await screen.findByRole('heading', { name: 'Início' })).toBeInTheDocument()
  })

  it('sessão válida mas /api/me falhando: mostra o erro em vez de fingir que entrou', async () => {
    fakeMe(503)
    renderAt('/login')
    expect(await screen.findByRole('alert')).toHaveTextContent('Sua sessão não pôde ser carregada')
  })
})

describe('retorno legado (/auth/callback)', () => {
  it('carrega /api/me e volta para a tela de origem', async () => {
    const me = fakeMe()
    renderAt('/auth/callback?returnTo=/cadastro/clientes')
    expect(await screen.findByRole('heading', { name: 'Tela de clientes' })).toBeInTheDocument()
    await waitFor(() => expect(me).toHaveBeenCalled())
    expect(me.mock.calls[0][0].url).toBe('/me')
    expect(me.mock.calls[0][0].headers.Authorization).toBeUndefined()
  })

  it('/api/me falhando no callback termina na tela de entrada, com o erro e um caminho para tentar de novo', async () => {
    fakeMe(503)
    const { container } = renderAt('/auth/callback?returnTo=/cadastro/clientes')
    expect(await screen.findByRole('alert')).toHaveTextContent('Sua sessão não pôde ser carregada')
    expect(screen.getByRole('button', { name: 'Entrar' })).toBeInTheDocument()
    expect(await axe(container)).toHaveNoViolations()
  })
})

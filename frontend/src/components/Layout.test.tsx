import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'vitest-axe'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { Layout } from './Layout'

const { authUser } = vi.hoisted(() => ({
  authUser: { user: { fullName: 'TESTE Usuário', username: 'teste', email: 't@x.invalid', permissions: ['registry:read'] }, logout: vi.fn() },
}))

vi.mock('@hooks/useAuth', () => ({
  useAuth: () => authUser as never,
  useAuthOptional: () => authUser as never,
}))

vi.mock('@hooks/useTheme', () => ({
  useTheme: () => ({ theme: 'light', toggleTheme: vi.fn(), palette: 'aqua-blue', setPalette: vi.fn() }),
}))

function renderLayout() {
  return render(
    <MemoryRouter initialEntries={['/cadastro/imoveis']}>
      <Routes>
        <Route element={<Layout />}>
          <Route path="/cadastro/imoveis" element={<p>Conteúdo</p>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

describe('Layout: menu por papel + modal com axe limpo (P1.12)', () => {
  beforeEach(() => {
    authUser.user = { fullName: 'TESTE Usuário', username: 'teste', email: 't@x.invalid', permissions: ['registry:read'] }
  })

  it('mostra o menu com Arquivos gerados para quem tem registry:read, e o menu aberto passa no axe', async () => {
    const user = userEvent.setup()
    const { container } = renderLayout()
    await user.click(screen.getByRole('button', { name: 'Abrir menu' }))
    const nav = screen.getByRole('navigation', { name: 'Principal' })
    expect(within(nav).getByRole('link', { name: 'Arquivos gerados' })).toHaveAttribute('href', '/arquivos-gerados')
    expect(await axe(container)).toHaveNoViolations()
  })

  it('sem registry:read, o menu não mostra as telas do cadastro', () => {
    authUser.user = { fullName: 'TESTE Usuário', username: 'teste', email: 't@x.invalid', permissions: [] }
    renderLayout()
    const nav = screen.getByRole('navigation', { name: 'Principal' })
    expect(within(nav).queryByRole('link')).not.toBeInTheDocument()
  })

  it('P1.37: só com gis:catalog-read, o menu mostra só o Catálogo GIS', () => {
    authUser.user = { fullName: 'TESTE Usuário', username: 'teste', email: 't@x.invalid', permissions: ['gis:catalog-read'] }
    renderLayout()
    const nav = screen.getByRole('navigation', { name: 'Principal' })
    expect(within(nav).getByRole('link', { name: 'Catálogo GIS' })).toHaveAttribute('href', '/gis/catalogo')
    expect(within(nav).queryByRole('link', { name: 'Imóveis' })).not.toBeInTheDocument()
    expect(within(nav).queryByRole('link', { name: 'Arquivos gerados' })).not.toBeInTheDocument()
  })

  it('P1.37: com registry:read mas sem gis:catalog-read, o Catálogo GIS não aparece', () => {
    authUser.user = { fullName: 'TESTE Usuário', username: 'teste', email: 't@x.invalid', permissions: ['registry:read'] }
    renderLayout()
    const nav = screen.getByRole('navigation', { name: 'Principal' })
    expect(within(nav).getByRole('link', { name: 'Imóveis' })).toHaveAttribute('href', '/cadastro/imoveis')
    expect(within(nav).queryByRole('link', { name: 'Catálogo GIS' })).not.toBeInTheDocument()
  })

  it('Escape fecha o menu', async () => {
    const user = userEvent.setup()
    renderLayout()
    const openButton = screen.getByRole('button', { name: 'Abrir menu' })
    await user.click(openButton)
    expect(openButton).toHaveAttribute('aria-expanded', 'true')
    await user.keyboard('{Escape}')
    expect(openButton).toHaveAttribute('aria-expanded', 'false')
  })
})

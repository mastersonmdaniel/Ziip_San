import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { render, screen, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { Layout } from './Layout'

/**
 * P-GISROTAS-04 — itens GIS do menu (Layout) para as rotas que já existiam
 * em App.tsx: /gis, /gis/styles, /gis/editor e /gis/tematicos, todas com a
 * permissão de leitura gis:view provada nos controladores
 * (GisMapController, GisWebEditController READ, GisThematicController).
 * /gis/change-sets/:id fica fora: é detalhe por id, sem rota de lista.
 *
 * Nota sobre o mock de usePermissions: o módulo entregue NÃO exporta
 * hasPermission (divergência da casca, pendência P-TEMA-01 — é ela que faz
 * o Layout.test.tsx da baseline falhar 6/6 com "hasPermission is not a
 * function"). Este ficheiro isola o comportamento do menu mockando o
 * módulo com a semântica que o Layout assume (a permissão está na lista
 * `permissions` do utilizador, a mesma forma do utilizador do
 * Layout.test.tsx). NÃO corrige a fiação da casca: só prova o menu.
 */
const { authUser } = vi.hoisted(() => ({
  authUser: { user: { fullName: 'TESTE Usuário', username: 'teste', email: 't@x.invalid', permissions: ['gis:view'] }, logout: vi.fn() },
}))

vi.mock('@hooks/useAuth', () => ({
  useAuth: () => authUser as never,
  useAuthOptional: () => authUser as never,
}))

vi.mock('@hooks/useTheme', () => ({
  useTheme: () => ({ theme: 'light', toggleTheme: vi.fn(), palette: 'aqua-blue', setPalette: vi.fn() }),
}))

vi.mock('@hooks/usePermissions', () => ({
  hasPermission: (user: { permissions?: string[] } | null, permission: string) =>
    Boolean(user?.permissions?.includes(permission)),
  useCan: () => false,
  usePermissions: () => ({ permissions: [], can: () => false }),
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

describe('P-GISROTAS-04: menu GIS do Layout', () => {
  beforeEach(() => {
    authUser.user = { fullName: 'TESTE Usuário', username: 'teste', email: 't@x.invalid', permissions: ['gis:view'] }
  })

  it('com gis:view, mostra Mapa, Estilos, Editor e Temáticos com os hrefs das rotas de App.tsx', () => {
    renderLayout()
    const nav = screen.getByRole('navigation', { name: 'Principal' })
    expect(within(nav).getByRole('link', { name: 'Mapa do cadastro técnico' })).toHaveAttribute('href', '/gis')
    expect(within(nav).getByRole('link', { name: 'Estilos do mapa' })).toHaveAttribute('href', '/gis/styles')
    expect(within(nav).getByRole('link', { name: 'Editor GIS' })).toHaveAttribute('href', '/gis/editor')
    expect(within(nav).getByRole('link', { name: 'Mapas temáticos' })).toHaveAttribute('href', '/gis/tematicos')
  })

  it('com gis:view mas sem gis:catalog-read, o Catálogo GIS não aparece e as telas do cadastro também não', () => {
    renderLayout()
    const nav = screen.getByRole('navigation', { name: 'Principal' })
    expect(within(nav).queryByRole('link', { name: 'Catálogo GIS' })).not.toBeInTheDocument()
    expect(within(nav).queryByRole('link', { name: 'Imóveis' })).not.toBeInTheDocument()
  })

  it('sem gis:view (só registry:read), nenhuma das quatro telas GIS novas aparece', () => {
    authUser.user = { fullName: 'TESTE Usuário', username: 'teste', email: 't@x.invalid', permissions: ['registry:read'] }
    renderLayout()
    const nav = screen.getByRole('navigation', { name: 'Principal' })
    expect(within(nav).queryByRole('link', { name: 'Mapa do cadastro técnico' })).not.toBeInTheDocument()
    expect(within(nav).queryByRole('link', { name: 'Estilos do mapa' })).not.toBeInTheDocument()
    expect(within(nav).queryByRole('link', { name: 'Editor GIS' })).not.toBeInTheDocument()
    expect(within(nav).queryByRole('link', { name: 'Mapas temáticos' })).not.toBeInTheDocument()
  })

  it('com gis:view e gis:catalog-read, o Catálogo GIS coexiste com as quatro entradas novas', () => {
    authUser.user = {
      fullName: 'TESTE Usuário',
      username: 'teste',
      email: 't@x.invalid',
      permissions: ['gis:view', 'gis:catalog-read'],
    }
    renderLayout()
    const nav = screen.getByRole('navigation', { name: 'Principal' })
    expect(within(nav).getByRole('link', { name: 'Catálogo GIS' })).toHaveAttribute('href', '/gis/catalogo')
    expect(within(nav).getByRole('link', { name: 'Mapa do cadastro técnico' })).toHaveAttribute('href', '/gis')
    expect(within(nav).getByRole('link', { name: 'Mapas temáticos' })).toHaveAttribute('href', '/gis/tematicos')
  })

  it('nenhum item do menu aponta para change-sets (detalhe por id, sem rota de lista)', () => {
    authUser.user = {
      fullName: 'TESTE Usuário',
      username: 'teste',
      email: 't@x.invalid',
      permissions: ['gis:view', 'gis:catalog-read', 'gis:change-set-read', 'gis:change-set-review', 'registry:read'],
    }
    renderLayout()
    const nav = screen.getByRole('navigation', { name: 'Principal' })
    const hrefs = within(nav)
      .getAllByRole('link')
      .map((link) => link.getAttribute('href') ?? '')
    expect(hrefs.some((href) => href.includes('change-sets'))).toBe(false)
  })

  it('todo item GIS do menu tem ícone (cobertura P0.46 sobre a lista navigation)', async () => {
    const { navigation } = await import('./Layout')
    const gisItems = navigation.filter((item) => item.href.startsWith('/gis'))
    expect(gisItems.map((item) => item.href)).toEqual(['/gis/catalogo', '/gis', '/gis/styles', '/gis/editor', '/gis/tematicos'])
    for (const item of gisItems) {
      expect(item.icon, `item sem ícone: ${item.href}`).toBeTruthy()
      expect(item.permission, `item sem permissão: ${item.href}`).toBeTruthy()
      expect(item.name, `item sem rótulo i18n resolvido: ${item.href}`).not.toMatch(/^nav\./)
    }
  })
})

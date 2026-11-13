import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ICONS, Icon, type IconName } from './Icon'
import { ROUTE_ICONS } from './routeIcons'
import { ICON_LABELS } from '../../i18n/iconLabels'
import { FRONTEND_LICENSES } from '../../brand/licenses'
import { navigation } from '../Layout'
// Fonte de App.tsx como texto (import ?raw do Vite): o teste le os paths
// reais, nao uma lista espelhada.
import appSource from '../../App.tsx?raw'

/**
 * P0.46 — cobertura de icones (ICONES-E-SIMBOLOGIA §4.1): todo icone do
 * registro tem rotulo acessivel; toda rota de App.tsx e todo item de menu
 * tem icone; a licenca do Lucide esta na lista do Sobre do frontend.
 * Os paths de App.tsx sao lidos do arquivo (nao de uma lista espelhada no
 * teste): rota nova sem entrada em ROUTE_ICONS quebra o teste.
 */
describe('IconRegistry (P0.46)', () => {
  it('82 icones no registro, cada um com rotulo acessivel em pt-BR', () => {
    const names = Object.keys(ICONS) as IconName[]
    expect(names).toHaveLength(82)
    for (const name of names) {
      expect(ICON_LABELS[name], `rotulo de ${name}`).toBeTruthy()
    }
    expect(Object.keys(ICON_LABELS).sort()).toEqual([...names].sort())
  })

  it('componente <Icon> renderiza com nome acessivel quando label=true', () => {
    render(<Icon name="search" label />)
    expect(screen.getByLabelText('Pesquisar')).toBeInTheDocument()
  })

  it('icone decorativo (sem label) fica aria-hidden', () => {
    const { container } = render(<Icon name="search" className="h-4 w-4" />)
    expect(container.querySelector('svg')).toHaveAttribute('aria-hidden', 'true')
  })

  it('toda rota de App.tsx (exceto layout e *) tem icone', () => {
    // Rotas filhas sao relativas ("cadastro/imoveis") sob a rota '/' (layout):
    // normaliza para o caminho absoluto antes de conferir o mapa.
    const paths = [...appSource.matchAll(/path="([^"]+)"/g)]
      .map((m) => m[1])
      .filter((p) => p !== '*' && p !== '/')
      .map((p) => (p.startsWith('/') ? p : `/${p}`))
    expect(paths.length).toBeGreaterThan(0)
    for (const path of paths) {
      expect(ROUTE_ICONS[path], `rota sem icone: ${path}`).toBeTruthy()
      expect(ICONS[ROUTE_ICONS[path]]).toBeTruthy()
    }
  })

  it('todo item de menu tem icone do registro', () => {
    expect(navigation.length).toBeGreaterThan(0)
    for (const item of navigation) {
      expect(ICONS[item.icon], `menu sem icone: ${item.name}`).toBeTruthy()
      expect(ROUTE_ICONS[item.href], `menu sem rota com icone: ${item.href}`).toBeTruthy()
    }
  })

  it('licenca do Lucide esta na lista do Sobre do frontend', () => {
    const lucide = FRONTEND_LICENSES.find((entry) => entry.name === 'Lucide')
    expect(lucide).toBeTruthy()
    expect(lucide?.license).toBe('ISC')
    expect(lucide?.version).toBe('0.303.0')
  })
})

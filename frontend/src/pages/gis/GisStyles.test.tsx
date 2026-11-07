import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { axe } from 'vitest-axe'
import { describe, expect, it, vi } from 'vitest'
import { GisStyles } from './GisStyles'

/**
 * GIS-06: acessibilidade da tela de estilos (vitest-axe, o padrão
 * do repo) nos temas claro e escuro — o tema do app é aplicado por
 * atributo no documento; o axe confere a estrutura rotulada da
 * tabela de estilos (cada controle com Field rotulado). O axe da
 * PÁGINA DO MAPA roda no E2E (gis-map.spec.ts), porque o jsdom não
 * executa o WebGL do MapLibre.
 */
vi.mock('@hooks/usePermissions', () => ({
  useCan: () => true,
}))

vi.mock('@services/gis/map', () => ({
  gisMap: {
    styleRows: vi.fn().mockResolvedValue([
      {
        id: '11111111-1111-1111-1111-111111111111',
        origin: 'FACTORY',
        domainKey: 'WATER',
        featureTypeId: null,
        featureTypeCode: null,
        lifecycleStatus: 'IN_SERVICE',
        accuracyStatus: null,
        colorToken: 'map-water',
        lineWidth: 2,
        lineDash: [],
        halo: false,
        iconKey: null,
        labelField: 'code',
        labelMinZoom: 16,
      },
    ]),
    symbols: vi.fn().mockResolvedValue([
      { key: 'valve', typeCode: 'GATE_VALVE', typeLabel: 'Registro de gaveta', domainKey: 'WATER' },
    ]),
    saveOverride: vi.fn(),
    restoreFactory: vi.fn(),
  },
}))

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <GisStyles />
    </QueryClientProvider>,
  )
}

describe('GisStyles (GIS-06)', () => {
  it('lista o estilo de fábrica e o símbolo da biblioteca', async () => {
    renderPage()
    expect(await screen.findByText('Domínio WATER')).toBeInTheDocument()
    expect(await screen.findByText(/valve — Registro de gaveta/)).toBeInTheDocument()
  })

  it('axe sem violações no tema claro', async () => {
    document.documentElement.dataset.theme = 'light'
    const { container } = renderPage()
    await screen.findByText('Domínio WATER')
    expect(await axe(container)).toHaveNoViolations()
  })

  it('axe sem violações no tema escuro', async () => {
    document.documentElement.dataset.theme = 'dark'
    const { container } = renderPage()
    await screen.findByText('Domínio WATER')
    expect(await axe(container)).toHaveNoViolations()
  })
})

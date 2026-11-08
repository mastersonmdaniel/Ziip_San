import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import GisThematics from './GisThematics'
import {
  listThematics,
  thematicData,
  type GisThematic,
  type ThematicData,
} from '@services/gis/thematic'

/**
 * GIS-08 — Correção RG-Lote3 (08/11/2026): estado de carregando da
 * página de temáticos. No padrão do `GisStyles.test.tsx` (vitest +
 * @testing-library, serviço mockado), com uma diferença declarada:
 * o `Spinner` de `@components/ui/Alert` é substituído por um dublê
 * identificável (`data-testid="spinner"`), porque o que este teste
 * prova é a PÁGINA usar o componente durante as duas buscas — a
 * renderização do Spinner real é da base do repo, que não está nos
 * pacotes. Promessas adiadas controlam cada fase da busca.
 */
vi.mock('@services/gis/thematic', () => ({
  listThematics: vi.fn(),
  thematicData: vi.fn(),
}))

vi.mock('@components/ui/Alert', async () => {
  const React = await import('react')
  return {
    Spinner: () => React.createElement('div', { 'data-testid': 'spinner' }),
    Alert: ({ children }: { children?: unknown }) =>
      React.createElement('div', { role: 'alert' }, children as never),
  }
})

afterEach(() => cleanup())

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

const THEMATIC: GisThematic = {
  id: '11111111-1111-1111-1111-111111111111',
  key: 'consumo-por-setor',
  name: 'Consumo por setor',
  description: null,
  sourceKind: 'CONSUMPTION',
  categoryField: null,
  measureField: null,
  personal: false,
  aggregation: 'NONE',
  classes: [],
  filterJson: '{}',
  origin: 'FACTORY',
  active: true,
  requiresConfiguration: false,
}

const DATA: ThematicData = {
  thematicKey: 'consumo-por-setor',
  points: [{ id: 'p1', lon: -48.0, lat: -26.0, category: 'A', measure: 1 }],
  legend: [{ key: 'A', label: 'Faixa A', colorToken: null, count: 1 }],
  truncated: false,
}

describe('GisThematics (GIS-08) — carregando', () => {
  it('mostra o Spinner enquanto a lista de temáticos carrega', () => {
    const list = deferred<GisThematic[]>()
    vi.mocked(listThematics).mockReturnValue(list.promise)
    vi.mocked(thematicData).mockReturnValue(deferred<ThematicData>().promise)

    render(<GisThematics />)

    expect(screen.getByTestId('spinner')).toBeInTheDocument()
    expect(screen.queryByText('Consumo por setor')).not.toBeInTheDocument()
  })

  it('mostra o Spinner enquanto os dados do temático carregam e a legenda ao concluir', async () => {
    const data = deferred<ThematicData>()
    vi.mocked(listThematics).mockResolvedValue([THEMATIC])
    vi.mocked(thematicData).mockReturnValue(data.promise)

    render(<GisThematics />)

    // Lista resolvida (o temático aparece) e dados ainda pendentes:
    // o Spinner da busca de dados aparece (findBy aguarda o efeito da
    // seleção disparar a segunda busca).
    expect(await screen.findByText('Consumo por setor')).toBeInTheDocument()
    expect(await screen.findByTestId('spinner')).toBeInTheDocument()
    expect(screen.queryByText('Legenda')).not.toBeInTheDocument()

    data.resolve(DATA)

    expect(await screen.findByText('Legenda')).toBeInTheDocument()
    expect(screen.queryByTestId('spinner')).not.toBeInTheDocument()
    expect(screen.getByText('1 pontos')).toBeInTheDocument()
  })
})

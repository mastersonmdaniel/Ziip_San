import { describe, expect, it } from 'vitest'
import {
  collectIdentifiedFeatures,
  hasUnidentifiedLayer,
  type RenderedFeatureLike,
} from '@services/gis/identify'

function feature(sourceLayer: string | undefined, id?: string, code?: string): RenderedFeatureLike {
  return {
    sourceLayer,
    properties: {
      ...(id !== undefined ? { id } : {}),
      ...(code !== undefined ? { code } : {}),
    },
  }
}

describe('collectIdentifiedFeatures — identificação multi-camadas', () => {
  it('lista TODAS as feições com ficha sob o ponto, na ordem de renderização', () => {
    const candidates = collectIdentifiedFeatures([
      feature('water-nodes', 'n1', 'N-1'),
      feature('water-arcs', 'a1', 'A-1'),
      feature('service-lines', 'c1'),
    ])
    expect(candidates).toEqual([
      { sourceLayer: 'water-nodes', table: 'gis_node', id: 'n1', code: 'N-1' },
      { sourceLayer: 'water-arcs', table: 'gis_arc', id: 'a1', code: 'A-1' },
      { sourceLayer: 'service-lines', table: 'gis_connection', id: 'c1', code: null },
    ])
  })

  it('com uma só feição devolve uma só candidata (comportamento anterior)', () => {
    expect(collectIdentifiedFeatures([feature('sewer-arcs', 'a9')])).toEqual([
      { sourceLayer: 'sewer-arcs', table: 'gis_arc', id: 'a9', code: null },
    ])
  })

  it('ignora camadas sem ficha e feições sem camada de origem', () => {
    expect(
      collectIdentifiedFeatures([
        feature('basemap-streets', 'x1'),
        feature(undefined, 'x2'),
        feature('zones', 'z1'),
      ]),
    ).toEqual([{ sourceLayer: 'zones', table: 'gis_zone', id: 'z1', code: null }])
  })

  it('ignora feições sem id identificável', () => {
    expect(
      collectIdentifiedFeatures([
        feature('water-nodes'),
        { sourceLayer: 'water-nodes', properties: { id: 42 } },
        { sourceLayer: 'water-nodes', properties: null },
        feature('water-nodes', 'n7'),
      ]),
    ).toEqual([{ sourceLayer: 'water-nodes', table: 'gis_node', id: 'n7', code: null }])
  })

  it('desduplica a mesma feição vinda em duas camadas de estilo', () => {
    const candidates = collectIdentifiedFeatures([
      feature('water-arcs', 'a1'),
      feature('sewer-arcs', 'a1'),
    ])
    expect(candidates).toHaveLength(1)
    expect(candidates[0].sourceLayer).toBe('water-arcs')
  })

  it('sem feições devolve lista vazia', () => {
    expect(collectIdentifiedFeatures([])).toEqual([])
  })
})

describe('hasUnidentifiedLayer — aviso de camada sem ficha (B3)', () => {
  it('verdadeiro quando há camada renderizada fora da lista de fichas', () => {
    expect(hasUnidentifiedLayer([feature('properties-parcels', 'p1')])).toBe(true)
  })

  it('falso quando todas as camadas têm ficha ou não há camadas', () => {
    expect(hasUnidentifiedLayer([feature('water-nodes', 'n1')])).toBe(false)
    expect(hasUnidentifiedLayer([])).toBe(false)
    expect(hasUnidentifiedLayer([feature(undefined, 'x')])).toBe(false)
  })
})

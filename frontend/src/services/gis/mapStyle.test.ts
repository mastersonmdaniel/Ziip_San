import { describe, expect, it } from 'vitest'
import {
  buildWmsTileUrl,
  computeLayerVisibility,
  resolveTokenColors,
  type StyleLayerLike,
} from './mapStyle'

/**
 * GIS-06: resolução dos tokens map-* por tema e visibilidade de
 * camadas/rótulos do mapa web. Os hex aqui são valores de fixture
 * do teste (não são a paleta): a paleta real é a do ADR-0017.
 */
describe('resolveTokenColors (GIS-06)', () => {
  const light = { 'map-water': '#00548f', 'map-sewer': '#7a4a21' }
  const dark = { 'map-water': '#4db2e8', 'map-sewer': '#d29a5b' }

  const styleDoc = {
    version: 8,
    layers: [
      {
        id: 'water-arcs-in_service',
        type: 'line',
        paint: { 'line-color': 'token:map-water', 'line-width': 2 },
      },
      {
        id: 'sewer-arcs-planned',
        type: 'line',
        paint: { 'line-color': 'token:map-sewer', 'line-dasharray': [3, 2] },
      },
    ],
  }

  it('tema claro resolve para as cores claras; tema escuro, para as escuras', () => {
    const resolvedLight = resolveTokenColors(structuredClone(styleDoc), light)
    const resolvedDark = resolveTokenColors(structuredClone(styleDoc), dark)
    expect(resolvedLight.layers[0].paint['line-color']).toBe('#00548f')
    expect(resolvedDark.layers[0].paint['line-color']).toBe('#4db2e8')
    expect(resolvedLight.layers[1].paint['line-color']).toBe('#7a4a21')
  })

  it('não altera números, arrays de traço nem o documento original', () => {
    const original = structuredClone(styleDoc)
    const resolved = resolveTokenColors(original, light)
    expect(resolved.layers[1].paint['line-dasharray']).toEqual([3, 2])
    expect(resolved.layers[0].paint['line-width']).toBe(2)
    expect(original.layers[0].paint['line-color']).toBe('token:map-water')
  })

  it('token sem cor no tema cai no fallback, nunca numa cor de domínio', () => {
    const resolved = resolveTokenColors({ c: 'token:map-waste' }, light, '#808080')
    expect(resolved.c).toBe('#808080')
  })
})

describe('computeLayerVisibility (GIS-06)', () => {
  const layers: StyleLayerLike[] = [
    { id: 'water-arcs-in_service', source: 'gis-water-arcs', 'source-layer': 'water-arcs' },
    { id: 'water-arcs-labels', source: 'gis-water-arcs', 'source-layer': 'water-arcs' },
    { id: 'sewer-arcs-in_service', source: 'gis-sewer-arcs', 'source-layer': 'sewer-arcs' },
    { id: 'basemap-osm', source: 'basemap-osm' },
  ]

  it('desliga camada e rótulo juntos; rótulo tem chave própria', () => {
    const vis = computeLayerVisibility(layers, new Set(['water-arcs']), true)
    expect(vis['water-arcs-in_service']).toBe('visible')
    expect(vis['water-arcs-labels']).toBe('visible')
    expect(vis['sewer-arcs-in_service']).toBe('none')
    expect(vis['basemap-osm']).toBeUndefined()

    const semRotulos = computeLayerVisibility(layers, new Set(['water-arcs']), false)
    expect(semRotulos['water-arcs-labels']).toBe('none')
    expect(semRotulos['water-arcs-in_service']).toBe('visible')
  })
})

describe('buildWmsTileUrl (GIS-06)', () => {
  it('monta GetMap com BBOX 3857', () => {
    const url = buildWmsTileUrl({
      url: 'https://geo.exemplo.invalid/wms',
      layers: 'orto2024',
      version: '1.3.0',
      format: 'image/png',
      transparent: true,
    })
    expect(url).toContain('SERVICE=WMS')
    expect(url).toContain('LAYERS=orto2024')
    expect(url).toContain('BBOX={bbox-epsg-3857}')
  })
})

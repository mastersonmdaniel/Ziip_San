import { describe, expect, it } from 'vitest'
import {
  formatArea,
  formatDistance,
  lngLatBounds,
  pathDistanceM,
  polygonAreaM2,
  segmentDistanceM,
} from './measure'

// Valores esperados calculados a partir das próprias fórmulas do
// módulo (raio terrestre médio 6 371 008,8 m): 1° no equador =
// 111 195,08 m. Não são limiares de negócio — são geometria.
describe('measure — distâncias (haversina)', () => {
  it('1 grau de latitude no equador mede ~111 195 m', () => {
    expect(segmentDistanceM({ lng: 0, lat: 0 }, { lng: 0, lat: 1 })).toBeCloseTo(111195.08, 1)
  })

  it('1 grau de longitude encolhe com a latitude (~99 075 m a -27°)', () => {
    expect(segmentDistanceM({ lng: 0, lat: -27 }, { lng: 1, lat: -27 })).toBeCloseTo(99075.28, 1)
  })

  it('distância de um ponto para ele próprio é zero', () => {
    expect(segmentDistanceM({ lng: -48.6, lat: -27.1 }, { lng: -48.6, lat: -27.1 })).toBe(0)
  })

  it('pathDistanceM soma os segmentos; menos de 2 pontos dá zero', () => {
    expect(pathDistanceM([{ lng: 0, lat: 0 }])).toBe(0)
    expect(pathDistanceM([])).toBe(0)
    const total = pathDistanceM([
      { lng: 0, lat: 0 },
      { lng: 0, lat: 1 },
      { lng: 0, lat: 2 },
    ])
    expect(total).toBeCloseTo(2 * 111195.08, 1)
  })
})

describe('measure — área esférica aproximada', () => {
  it('quadrado de 1°×1° no equador tem ~1,236 × 10^10 m²', () => {
    const area = polygonAreaM2([
      { lng: 0, lat: 0 },
      { lng: 1, lat: 0 },
      { lng: 1, lat: 1 },
      { lng: 0, lat: 1 },
    ])
    expect(area).toBeGreaterThan(1.23e10)
    expect(area).toBeLessThan(1.24e10)
  })

  it('menos de 3 pontos não é polígono: área zero', () => {
    expect(polygonAreaM2([{ lng: 0, lat: 0 }, { lng: 1, lat: 1 }])).toBe(0)
    expect(polygonAreaM2([])).toBe(0)
  })
})

describe('measure — formatação pt-BR', () => {
  it('distância abaixo de 1 km sai em metros com vírgula decimal', () => {
    expect(formatDistance(250.5)).toBe('250,5 m')
  })

  it('distância de 1 km ou mais sai em km com 3 casas', () => {
    expect(formatDistance(1500)).toBe('1,500 km')
  })

  it('área abaixo de 1 ha sai em m²; acima sai em ha', () => {
    expect(formatArea(500.25)).toBe('500,3 m²')
    expect(formatArea(25000)).toBe('2,5000 ha')
  })
})

describe('measure — lngLatBounds (enquadrar resultados, Ciclo 1 PDCA GIS)', () => {
  it('lista vazia devolve null (nada para enquadrar)', () => {
    expect(lngLatBounds([])).toBeNull()
  })

  it('um único ponto devolve bounds degenerado nesse ponto', () => {
    expect(lngLatBounds([{ lng: -48.6, lat: -27.1 }])).toEqual({
      west: -48.6,
      south: -27.1,
      east: -48.6,
      north: -27.1,
    })
  })

  it('vários pontos devolvem o mínimo/máximo por eixo', () => {
    expect(
      lngLatBounds([
        { lng: -48.6, lat: -27.1 },
        { lng: -48.9, lat: -26.9 },
        { lng: -48.7, lat: -27.3 },
      ]),
    ).toEqual({ west: -48.9, south: -27.3, east: -48.6, north: -26.9 })
  })

  it('pontos não-finitos são ignorados; se nenhum for válido, null', () => {
    expect(
      lngLatBounds([
        { lng: Number.NaN, lat: 0 },
        { lng: -48.6, lat: -27.1 },
      ]),
    ).toEqual({ west: -48.6, south: -27.1, east: -48.6, north: -27.1 })
    expect(lngLatBounds([{ lng: Number.NaN, lat: Number.NaN }])).toBeNull()
  })
})

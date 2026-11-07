import { describe, expect, it } from 'vitest'
import { centralMeridianDeg, utmToLonLat } from './utm'
import { formatArea, formatDistance, pathDistanceM, polygonAreaM2 } from './measure'

/**
 * GIS-06: mesmos pontos de conferência do UtmTest.java do backend
 * (origem falsa do fuso 22S) e sanidade da medição em tela.
 */
describe('utm (GIS-06)', () => {
  it('origem falsa do fuso 22S é -51 no Equador', () => {
    const { lon, lat } = utmToLonLat(500_000, 10_000_000, 22, true)
    expect(lon).toBeCloseTo(-51, 6)
    expect(lat).toBeCloseTo(0, 6)
  })

  it('meridiano central e fuso inválido', () => {
    expect(centralMeridianDeg(22)).toBe(-51)
    expect(() => centralMeridianDeg(0)).toThrow()
  })
})

describe('measure (GIS-06)', () => {
  it('1 grau de latitude mede ~111,19 km', () => {
    const d = pathDistanceM([
      { lng: -51, lat: 0 },
      { lng: -51, lat: 1 },
    ])
    expect(d).toBeGreaterThan(111_000)
    expect(d).toBeLessThan(111_400)
  })

  it('quadrado de 1x1 grau no Equador tem área coerente e formatação pt-BR', () => {
    const area = polygonAreaM2([
      { lng: 0, lat: 0 },
      { lng: 1, lat: 0 },
      { lng: 1, lat: 1 },
      { lng: 0, lat: 1 },
    ])
    expect(area).toBeGreaterThan(1.2e10)
    expect(area).toBeLessThan(1.25e10)
    expect(formatDistance(850)).toContain('m')
    expect(formatDistance(2500)).toContain('km')
    expect(formatArea(500)).toContain('m²')
  })
})

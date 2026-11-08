import { describe, expect, it } from 'vitest'
import {
  categoryColorExpression,
  legendColor,
  THEMATIC_FALLBACK_PALETTE,
  type LegendEntry,
} from './thematic'

/**
 * GIS-08: a cor da legenda — token do tema quando ele resolve;
 * paleta de recurso estável (pela ordem da classe) quando o token
 * map-thematic-* ainda não está no tema. Os hex do tema aqui são
 * fixture do teste, não a paleta (ADR-0017).
 */
describe('legendColor (GIS-08)', () => {
  const theme = { 'map-thematic-1': '#00548f' }
  const withToken: LegendEntry = {
    key: 'BELOW_AVERAGE',
    label: 'Abaixo da média',
    colorToken: 'map-thematic-1',
    count: 3,
  }
  const unknownToken: LegendEntry = {
    key: 'ABOVE_AVERAGE',
    label: 'Acima da média',
    colorToken: 'map-thematic-9',
    count: 1,
  }

  it('usa a cor do tema quando o token resolve', () => {
    expect(legendColor(withToken, 0, theme)).toBe('#00548f')
  })

  it('cai para a paleta de recurso, pela ordem, quando o token não resolve', () => {
    expect(legendColor(unknownToken, 1, theme)).toBe(THEMATIC_FALLBACK_PALETTE[1])
    expect(legendColor(unknownToken, 0, {})).toBe(THEMATIC_FALLBACK_PALETTE[0])
  })
})

describe('categoryColorExpression (GIS-08)', () => {
  it('monta o match do MapLibre com uma cor por classe da legenda e o cinza final', () => {
    const legend: LegendEntry[] = [
      { key: 'CUT', label: 'Cortada', colorToken: null, count: 2 },
      { key: 'SUSPENDED', label: 'Suprimida', colorToken: null, count: 1 },
    ]
    const expression = categoryColorExpression(legend, {})
    expect(expression[0]).toBe('match')
    expect(expression).toContain('CUT')
    expect(expression).toContain('SUSPENDED')
    expect(expression[expression.length - 1]).toBe('#9ca3af')
    expect(expression).toHaveLength(2 + legend.length * 2 + 1)
  })
})

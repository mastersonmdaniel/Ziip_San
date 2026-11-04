import { describe, expect, it } from 'vitest'
import { formatCurrencyIntl, formatDateIntl, formatNumberIntl } from './format'

describe('P1.18 formatação por Intl (pt-BR)', () => {
  it('data em dd/MM/yyyy', () => {
    expect(formatDateIntl('2026-11-03')).toBe('03/11/2026')
    expect(formatDateIntl(null)).toBe('—')
    expect(formatDateIntl('não-data')).toBe('—')
  })

  it('número com separadores pt-BR', () => {
    expect(formatNumberIntl(1234.5, 'pt-BR', 2)).toBe('1.234,50')
    expect(formatNumberIntl(undefined)).toBe('—')
  })

  it('moeda BRL', () => {
    expect(formatCurrencyIntl(1234.5)).toBe('R$ 1.234,50')
    expect(formatCurrencyIntl(null)).toBe('—')
  })
})

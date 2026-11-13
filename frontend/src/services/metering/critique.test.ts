import { describe, expect, it } from 'vitest'
import { allowedTransitions, isOpenStatus, sortExceptions, type CritiqueExceptionView } from './critique'

// Fixtures: valores das suites Java do núcleo — ExceptionQueueTest.java
// (UUIDs ...0001/...0002, referências 2026-10/2026-11, finding ZERO_CONSUMPTION
// com observed=0 e detalhe "fixture") e VeeEstimatorTest (métodos do enum).
const finding = { ruleCode: 'RZ', kind: 'ZERO_CONSUMPTION' as const, observed: '0', threshold: null, detail: 'fixture' }
function exc(unitId: string, reference: string, code: string): CritiqueExceptionView {
  return { unitId, reference, findings: [{ ...finding, ruleCode: code }], status: 'PENDING', correctedReading: null }
}
describe('fila de exceções (espelho de ExceptionQueue)', () => {
  it('ordena por referência, unidade e primeira regra', () => {
    const u1 = '00000000-0000-0000-0000-000000000001'
    const u2 = '00000000-0000-0000-0000-000000000002'
    const sorted = sortExceptions([exc(u2, '2026-11', 'RB'), exc(u2, '2026-10', 'RA'), exc(u1, '2026-10', 'RZ')])
    expect(sorted.map((e) => e.unitId)).toEqual([u1, u2, u2])
    expect(sorted[2].reference).toBe('2026-11')
  })
  it('abertas = PENDING ou REREADING_REQUESTED', () => {
    expect(isOpenStatus('PENDING')).toBe(true)
    expect(isOpenStatus('REREADING_REQUESTED')).toBe(true)
    expect(isOpenStatus('ACCEPTED')).toBe(false)
    expect(isOpenStatus('CORRECTED')).toBe(false)
  })
  it('transições terminais não têm saída', () => {
    expect(allowedTransitions('PENDING')).toEqual(['ACCEPTED', 'CORRECTED', 'REREADING_REQUESTED'])
    expect(allowedTransitions('REREADING_REQUESTED')).toEqual(['CORRECTED', 'ACCEPTED'])
    expect(allowedTransitions('ACCEPTED')).toEqual([])
    expect(allowedTransitions('CORRECTED')).toEqual([])
  })
})

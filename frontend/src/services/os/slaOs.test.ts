import { describe, expect, it } from 'vitest'
import { isPcrComplete } from './slaOs'

/**
 * Códigos TST-* das suites Java do núcleo (ProblemCauseRemedyGateTest.java:
 * TST-P1/TST-C1/TST-R1) — fixtures de teste, não catálogo de tenant.
 */
describe('tripla problema–causa–remédio (ProblemCauseRemedy)', () => {
  it('completa quando os três códigos estão presentes', () => {
    expect(isPcrComplete({ problemCode: 'TST-P1', causeCode: 'TST-C1', remedyCode: 'TST-R1' })).toBe(true)
  })

  it('incompleta quando falta qualquer código ou a tripla é nula', () => {
    expect(isPcrComplete({ problemCode: 'TST-P1', causeCode: '', remedyCode: 'TST-R1' })).toBe(false)
    expect(isPcrComplete(null)).toBe(false)
  })
})

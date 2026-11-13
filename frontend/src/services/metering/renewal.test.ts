import { describe, expect, it } from 'vitest'
import { restrictionLabel } from './renewal'

// Fixtures: RenewalFixtures.java e AnnualRenewalPlanTest.java —
// ByCount(10) do teste do piso de score; ByBudget(900) do teste do orçamento.
describe('plano de troca (espelho de AnnualRenewalPlan)', () => {
  it('rótulos das duas formas de restrição', () => {
    expect(restrictionLabel({ type: 'ByCount', maxReplacements: 10 })).toContain('10')
    expect(restrictionLabel({ type: 'ByBudget', maxBudget: '900' })).toContain('900')
  })
})

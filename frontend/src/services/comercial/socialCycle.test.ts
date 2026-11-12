import { describe, expect, it } from 'vitest'
import {
  DECLARED_PROOF_FORMAT,
  PROOF_PARAMETERS,
  declaredHeaderLine,
  groupActiveSearch,
  type ActiveSearchEntry,
} from './socialCycle'

/**
 * Fixtures: índices cegos das suites Java do núcleo
 * (SocialCycleFixtures.java: hash(n) = "%064x" de n; F1=0xF1, F2=0xF2).
 */
const F1 = 'f1'.padStart(64, '0')
const F2 = 'f2'.padStart(64, '0')

describe('formato declarado do ciclo social (DeclaredSocialListFormat)', () => {
  it('é o proofFormat() do núcleo, não o leiaute oficial [SEM FONTE]', () => {
    expect(DECLARED_PROOF_FORMAT.formatId).toBe('II3-LISTA-SOCIAL-DECLARADA')
    expect(DECLARED_PROOF_FORMAT.version).toBe('1')
    expect(DECLARED_PROOF_FORMAT.delimiter).toBe(';')
    expect(declaredHeaderLine(DECLARED_PROOF_FORMAT)).toBe(
      'family_code_hash;person_document_hash;person_role;eligibility_basis;' +
        'cadunico_updated_on;benefit_valid_from;benefit_valid_to;municipality_code',
    )
  })

  it('parâmetros são os de prova da P1.5 (24/3/3) e recertificação ausente [A DEFINIR]', () => {
    expect(PROOF_PARAMETERS).toEqual({
      cadunicoMaxAgeMonths: 24,
      lossNoticeMonths: 3,
      graceMonths: 3,
      recertificationIntervalMonths: null,
    })
  })
})

describe('busca ativa agrupada (SocialActiveSearch.GroupKey)', () => {
  it('agrupa por município e rota', () => {
    const entries: ActiveSearchEntry[] = [
      { familyCodeHash: F1, basis: 'CADUNICO', municipalityCode: 'M1', routeCode: 'R1', reason: 'MATCHED_NOT_ENROLLED' },
      { familyCodeHash: F2, basis: 'BPC', municipalityCode: 'M1', routeCode: 'R1', reason: 'NO_UNIT_MATCH' },
    ]
    const groups = groupActiveSearch(entries)
    expect(groups).toHaveLength(1)
    expect(groups[0].key).toEqual({ municipalityCode: 'M1', routeCode: 'R1' })
    expect(groups[0].entries.map((e) => e.familyCodeHash)).toEqual([F1, F2])
  })
})

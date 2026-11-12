import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { CycleReport, SocialCyclePort } from '@services/comercial/socialCycle'
import { SocialTariffCycle } from './SocialTariffCycle'

/**
 * Fixtures das suites Java do núcleo: índices cegos de
 * SocialCycleFixtures.java (F1 = hash(0xF1), F2 = hash(0xF2)) e as
 * contagens do primeiro cenário de SocialTariffCycleServiceTest.java
 * (importedRows 4, duplicatesRemoved 0, já inscritos 0).
 */
const F1 = 'f1'.padStart(64, '0')
const F2 = 'f2'.padStart(64, '0')

const REPORT: CycleReport = {
  importedRows: 4,
  duplicatesRemoved: 0,
  importRejections: [],
  crossing: {
    matched: [
      {
        familyCodeHash: F1, basis: 'CADUNICO', cadunicoUpdatedOn: '2026-06-01',
        benefitValidFrom: '2026-01-01', benefitValidTo: null,
        unitId: 'u1', holderCustomerId: 'c1', municipalityCode: 'M1', routeCode: 'R1',
        personCount: 3,
      },
    ],
    unmatched: [
      { familyCodeHash: F2, basis: 'BPC', municipalityCode: 'M1', personCount: 2 },
    ],
    ineligible: [],
    divergences: [{ familyCodeHash: F2, kind: 'NO_UNIT_MATCH', detail: 'sem unidade' }],
  },
  grantsCreated: [
    {
      grantId: 'g1', unitId: 'u1', holderCustomerId: 'c1', familyCodeHash: F1,
      basis: 'CADUNICO', cadunicoUpdatedOn: '2026-06-01', validFrom: '2026-01-01',
      validTo: null, status: 'GRANTED', lossNoticeStartedOn: null, graceStartedOn: null,
      endReasonCode: null, legalBasis: null, trail: [],
    },
  ],
  alreadyEnrolled: 0,
  grantRefusals: [],
  activeSearch: [
    { familyCodeHash: F2, basis: 'BPC', municipalityCode: 'M1', routeCode: null, reason: 'NO_UNIT_MATCH' },
  ],
}

function fakePort(): SocialCyclePort {
  return {
    declaredFormat: vi.fn(async () => ({ formatId: 'II3-LISTA-SOCIAL-DECLARADA', version: '1', delimiter: ';', columns: [] })),
    parameters: vi.fn(async () => ({ cadunicoMaxAgeMonths: 24, lossNoticeMonths: 3, graceMonths: 3, recertificationIntervalMonths: null })),
    runCycle: vi.fn(async () => REPORT),
    familySummaries: vi.fn(async () => []),
  }
}

describe('P-II3-TELA — SocialTariffCycle', () => {
  it('sem porta, declara a fiação pendente e o leiaute [SEM FONTE]', () => {
    render(<SocialTariffCycle />)
    // P-II3-INTEGRACAO aparece no título e no corpo do alerta; [A DEFINIR]
    // aparece nos parâmetros e na recertificação: consultas *AllBy*.
    expect(screen.getAllByText(/P-II3-INTEGRACAO/).length).toBeGreaterThan(0)
    expect(screen.getByText(/\[SEM FONTE\]/)).toBeTruthy()
    expect(screen.getByText(/II3-LISTA-SOCIAL-DECLARADA/)).toBeTruthy()
    expect(screen.getAllByText(/\[A DEFINIR\]/).length).toBeGreaterThan(0)
  })

  it('com porta, executa o ciclo e mostra o relatório e a busca ativa', async () => {
    const user = userEvent.setup()
    render(<SocialTariffCycle port={fakePort()} />)
    await user.click(screen.getByRole('button', { name: 'Executar ciclo' }))
    await waitFor(() => expect(screen.getByText('Relatório da corrida')).toBeTruthy())
    expect(screen.getByText('Linhas importadas: 4')).toBeTruthy()
    expect(screen.getByText(/Busca ativa/)).toBeTruthy()
    expect(screen.getAllByText(new RegExp(F2)).length).toBeGreaterThan(0)
  })
})

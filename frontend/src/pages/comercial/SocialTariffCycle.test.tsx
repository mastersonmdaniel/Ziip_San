import { fireEvent, render, screen, waitFor } from '@testing-library/react'
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
  // Formato e parâmetros DISTINTOS das constantes de prova, para provar
  // (R41-4-11) que a tela usa o que a porta declara e não as constantes.
  return {
    declaredFormat: vi.fn(async () => ({ formatId: 'II3-FORMATO-DA-PORTA', version: '2', delimiter: ',', columns: ['a', 'b'] })),
    parameters: vi.fn(async () => ({ cadunicoMaxAgeMonths: 12, lossNoticeMonths: 1, graceMonths: 2, recertificationIntervalMonths: 6 })),
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
    const port = fakePort()
    render(<SocialTariffCycle port={port} />)
    // R41-4-11: a tela mostra o formato e os parâmetros declarados pela
    // porta, não as constantes de prova.
    await waitFor(() => expect(screen.getByText(/II3-FORMATO-DA-PORTA/)).toBeTruthy())
    expect(screen.getByText(/Idade máxima do CadÚnico: 12 meses/)).toBeTruthy()
    // R41-4-14: o ciclo só corre com os cinco campos preenchidos.
    await user.type(screen.getByLabelText('Conteúdo da lista'), 'lista;de;prova')
    fireEvent.change(screen.getByLabelText('Data de referência'), { target: { value: '2026-11-12' } })
    await user.type(screen.getByLabelText('Natureza jurídica'), 'TST-NAT')
    await user.type(screen.getByLabelText('Origem da lista'), 'TST-ORIGEM')
    await user.type(screen.getByLabelText('Ator'), 'TST-ATOR')
    await user.click(screen.getByRole('button', { name: 'Executar ciclo' }))
    await waitFor(() => expect(screen.getByText('Relatório da corrida')).toBeTruthy())
    expect(screen.getByText('Linhas importadas: 4')).toBeTruthy()
    expect(screen.getByText(/Busca ativa/)).toBeTruthy()
    expect(screen.getAllByText(new RegExp(F2)).length).toBeGreaterThan(0)
    // R41-4-12: os códigos de enumeração aparecem com rótulo pt-BR.
    expect(screen.getByText('Concedida')).toBeTruthy()
    expect(screen.getAllByText(/Sem unidade correspondente/).length).toBeGreaterThan(0)
    expect(screen.queryByText('GRANTED')).toBeNull()
    expect(screen.queryByText('NO_UNIT_MATCH')).toBeNull()
  })

  it('com porta, o envio vazio mostra uma mensagem por campo e não corre o ciclo', async () => {
    const user = userEvent.setup()
    const port = fakePort()
    render(<SocialTariffCycle port={port} />)
    await user.click(screen.getByRole('button', { name: 'Executar ciclo' }))
    expect(screen.getByText('Conteúdo da lista é obrigatório.')).toBeTruthy()
    expect(screen.getByText('Data de referência é obrigatória.')).toBeTruthy()
    expect(screen.getByText('Natureza jurídica é obrigatória.')).toBeTruthy()
    expect(screen.getByText('Origem da lista é obrigatória.')).toBeTruthy()
    expect(screen.getByText('Ator é obrigatório.')).toBeTruthy()
    expect(port.runCycle).not.toHaveBeenCalled()
    expect(screen.queryByText('Relatório da corrida')).toBeNull()
  })
})

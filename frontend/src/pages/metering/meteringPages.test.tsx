import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { CritiquePort } from '@services/metering/critique'
import type { RenewalPlanPort } from '@services/metering/renewal'
import { ExceptionQueuePage } from './ExceptionQueuePage'
import { formatCost, RenewalPlanPage } from './RenewalPlanPage'

// Fixtures das suites Java: ExceptionQueueTest (finding ZERO_CONSUMPTION) e
// AnnualRenewalPlanTest/RenewalFixtures (M1 = H-001/...0021; H-009/...0029 sem custo).
describe('P-II2-TELA sem fiação', () => {
  it('mostra a pendência P-II2-FIACAO e nenhuma exceção fingida', () => {
    render(<ExceptionQueuePage />)
    expect(screen.getByText(/P-II2-FIACAO/)).toBeTruthy()
    expect(screen.getByText(/Nenhuma exceção aberta na fila/)).toBeTruthy()
    // R41-4-06: a página não rende <main> próprio — vive dentro do
    // <main id="conteudo"> do Layout; <main> aninhado é HTML inválido.
    expect(document.querySelectorAll('main')).toHaveLength(0)
  })
})
describe('P-II2-TELA com porta', () => {
  it('lista a exceção da fixture Java', async () => {
    const port: CritiquePort = {
      listExceptions: async () => [{
        unitId: '00000000-0000-0000-0000-000000000001', reference: '2026-10', status: 'PENDING',
        correctedReading: null,
        findings: [{ ruleCode: 'RZ', kind: 'ZERO_CONSUMPTION', observed: '0', threshold: null, detail: 'fixture' }],
      }],
      accept: async (e) => ({ ...e, status: 'ACCEPTED' }),
      correct: async (e) => e, requestRereading: async (e) => e, estimate: async () => 0,
    }
    render(<ExceptionQueuePage port={port} />)
    expect(await screen.findByText('RZ')).toBeTruthy()
    expect(screen.getByText('Pendente')).toBeTruthy()
  })
})
describe('P-II2-TELA interacções', () => {
  it('aceitar chama a porta e a exceção sai das abertas (ACCEPTED é terminal)', async () => {
    const user = userEvent.setup()
    const port: CritiquePort = {
      listExceptions: async () => [{
        unitId: '00000000-0000-0000-0000-000000000001', reference: '2026-10', status: 'PENDING',
        correctedReading: null,
        findings: [{ ruleCode: 'RZ', kind: 'ZERO_CONSUMPTION', observed: '0', threshold: null, detail: 'fixture' }],
      }],
      accept: async (e) => ({ ...e, status: 'ACCEPTED' }),
      correct: async (e) => e, requestRereading: async (e) => e, estimate: async () => 0,
    }
    render(<ExceptionQueuePage port={port} />)
    expect(await screen.findByText('RZ')).toBeTruthy()
    await user.click(screen.getByRole('button', { name: 'Aceitar' }))
    await waitFor(() => expect(screen.queryByText('RZ')).toBeNull())
    expect(screen.getByText(/Nenhuma exceção aberta na fila/)).toBeTruthy()
  })
  it('correção com leitura negativa é recusada na tela, sem chamar a porta', async () => {
    const user = userEvent.setup()
    let correctCalls = 0
    const port: CritiquePort = {
      listExceptions: async () => [{
        unitId: '00000000-0000-0000-0000-000000000001', reference: '2026-10', status: 'PENDING',
        correctedReading: null,
        findings: [{ ruleCode: 'RZ', kind: 'ZERO_CONSUMPTION', observed: '0', threshold: null, detail: 'fixture' }],
      }],
      accept: async (e) => e,
      correct: async (e) => { correctCalls += 1; return e },
      requestRereading: async (e) => e, estimate: async () => 0,
    }
    render(<ExceptionQueuePage port={port} />)
    expect(await screen.findByText('RZ')).toBeTruthy()
    await user.type(screen.getByLabelText(/Leitura corrigida para/), '-1')
    await user.click(screen.getByRole('button', { name: 'Corrigir' }))
    expect(await screen.findByText(/Leitura corrigida inválida/)).toBeTruthy()
    expect(correctCalls).toBe(0)
  })
})
describe('P-II5-TELA', () => {
  it('sem fiação mostra P-II5-INT; com porta mostra a linha M1 da fixture Java', async () => {
    const { unmount } = render(<RenewalPlanPage planYear={2026} />)
    expect(screen.getByText(/P-II5-INT/)).toBeTruthy()
    unmount()
    // M1 de AnnualRenewalPlanTest/RenewalFixtures: H-001 (id ...0021),
    // score 0.90, custo 600, última verificação 2018-06-01 + 7 anos =
    // 2025-06-01 -> OVERDUE à data de referência 2026-01-01 do fixture.
    const port: RenewalPlanPort = {
      getPlan: async () => ({
        planYear: 2026, restriction: { type: 'ByCount', maxReplacements: 10 }, minScore: null,
        eligibleCount: 1, excludedByStatusCount: 0, excludedBelowMinScoreCount: 0,
        excludedByRestrictionCount: 0, totalEstimatedCost: '600',
        lines: [{
          position: 1, meterId: '00000000-0000-0000-0000-000000000021', meterNumber: 'H-001',
          unitId: '99999999-9999-9999-9999-999999999999', totalScore: '0.90',
          dominantCriterion: 'AGE', clockStatus: 'OVERDUE', nextVerificationOn: '2025-06-01', replacementCost: '600',
        }],
      }),
      buildPayloads: async () => [],
    }
    render(<RenewalPlanPage port={port} planYear={2026} />)
    expect(await screen.findByText('H-001')).toBeTruthy()
    expect(screen.getByText('0.90')).toBeTruthy()
    expect(screen.getByText('Verificação vencida')).toBeTruthy()
    // R41-4-09: custo formatado em pt-BR, em reais (R$, BRL) — moeda
    // decidida pelo dono (decisão 76, 13/11/2026; P-R41-4-09-MOEDA, Opção A).
    expect(screen.getByText(/Custo total estimado: R\$ 600/)).toBeTruthy()
    expect(screen.getByText('R$ 600')).toBeTruthy()
    expect(document.body.textContent).toContain('R$')
    // R41-4-06: a página não rende <main> próprio.
    expect(document.querySelectorAll('main')).toHaveLength(0)
  })
  it('linha sem custo (H-009 da suite Java) mostra o total como desconhecido', async () => {
    const port: RenewalPlanPort = {
      getPlan: async () => ({
        planYear: 2026, restriction: { type: 'ByCount', maxReplacements: 2 }, minScore: null,
        eligibleCount: 1, excludedByStatusCount: 0, excludedBelowMinScoreCount: 0,
        excludedByRestrictionCount: 0, totalEstimatedCost: null,
        lines: [{
          position: 1, meterId: '00000000-0000-0000-0000-000000000029', meterNumber: 'H-009',
          unitId: '99999999-9999-9999-9999-999999999999', totalScore: '0.90',
          dominantCriterion: 'AGE', clockStatus: 'UNKNOWN_NO_VERIFICATION', nextVerificationOn: null, replacementCost: null,
        }],
      }),
      buildPayloads: async () => [],
    }
    render(<RenewalPlanPage port={port} planYear={2026} />)
    expect(await screen.findByText('H-009')).toBeTruthy()
    expect(screen.getByText(/desconhecido/)).toBeTruthy()
    // R53-2-01: o caminho null → "—" ao nível da página — custo de troca
    // (formatCost(null)) e próxima verificação (null ?? '—'): exactamente
    // as duas células "—" da linha H-009; o total "desconhecido" não é "—".
    expect(screen.getAllByText('—')).toHaveLength(2)
  })
  it('restrição ByBudget rende o orçamento máximo em reais (R53-2-02)', async () => {
    const port: RenewalPlanPort = {
      getPlan: async () => ({
        planYear: 2026, restriction: { type: 'ByBudget', maxBudget: '900' }, minScore: null,
        eligibleCount: 1, excludedByStatusCount: 0, excludedBelowMinScoreCount: 0,
        excludedByRestrictionCount: 0, totalEstimatedCost: '900',
        lines: [{
          position: 1, meterId: '00000000-0000-0000-0000-000000000021', meterNumber: 'H-001',
          unitId: '99999999-9999-9999-9999-999999999999', totalScore: '0.90',
          dominantCriterion: 'AGE', clockStatus: 'OVERDUE', nextVerificationOn: '2025-06-01', replacementCost: '600',
        }],
      }),
      buildPayloads: async () => [],
    }
    render(<RenewalPlanPage port={port} planYear={2026} />)
    expect(await screen.findByText('H-001')).toBeTruthy()
    // R53-2-02: o ramo ByBudget ao nível da página, em reais (R$, BRL —
    // decisão 76); antes só era provado ao nível unitário (renewal.test.ts).
    expect(screen.getByText(/Orçamento máximo de R\$ 900/)).toBeTruthy()
  })
})
describe('R41-4-09 formatCost (custo do plano, em reais — decisão 76)', () => {
  it('formata em pt-BR com R$ (BRL); null vira "—"', () => {
    expect(formatCost('600')).toBe('R$ 600')
    expect(formatCost('1234.56')).toBe('R$ 1.234,56')
    expect(formatCost(null)).toBe('—')
    expect(formatCost('abc')).toBe('abc')
  })
  it('preserva a precisão da fracção e não inventa zero para string vazia (R41B-R-02)', () => {
    expect(formatCost('1234.5678')).toBe('R$ 1.234,5678')
    expect(formatCost('')).toBe('')
    expect(formatCost('   ')).toBe('   ')
    expect(formatCost('9007199254740993')).toBe('R$ 9.007.199.254.740.993')
  })
})

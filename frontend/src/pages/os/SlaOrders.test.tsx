import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { SlaOsPort } from '@services/os/slaOs'
import { SlaOrders } from './SlaOrders'

/**
 * Fixtures das suites Java do núcleo slaos: TypeSlaConfig de
 * EscalationSequenceTest.java:20 — ("TST-TIPO", 100, 100, 80,
 * [80, 100, 150]) — e a tripla de ProblemCauseRemedyGateTest.java
 * (TST-P1/TST-C1/TST-R1). São códigos de TESTE, não catálogo de tenant.
 */
function fakePort(): SlaOsPort {
  return {
    typeConfigs: vi.fn(async () => [
      {
        typeCode: 'TST-TIPO',
        internalDeadlineHours: 100,
        regulatorDeadlineHours: 100,
        riskThresholdPercent: 80,
        escalationThresholdsPercent: [80, 100, 150],
      },
    ]),
    orders: vi.fn(async () => [
      {
        orderId: 'os-1',
        typeCode: 'TST-TIPO',
        status: 'OPEN' as const,
        // Meio-dia UTC: o mesmo dia civil em UTC e em America/Sao_Paulo.
        openedAt: '2026-12-01T12:00:00Z',
        assessment: {
          internal: { kind: 'INTERNAL' as const, state: 'AT_RISK' as const, dueAtOrNull: '2026-12-05T12:00:00Z', elapsedSeconds: 3600 },
          regulator: { kind: 'REGULATOR' as const, state: 'WITHIN' as const, dueAtOrNull: null, elapsedSeconds: 3600 },
        },
        escalationLevel: 0,
        events: [{ kind: 'RISK_ALERT' as const, deadlineOrNull: 'INTERNAL' as const, escalationLevel: 0, at: '2026-12-02T00:00:00Z' }],
      },
    ]),
    pcrCatalogState: vi.fn(async () => ({ configured: false })),
    evaluateCloseGate: vi.fn(async () => [
      { kind: 'MISSING_PROBLEM_CAUSE_REMEDY' as const, detail: 'codificação obrigatória' },
    ]),
  }
}

describe('P-II4-TELA — SlaOrders', () => {
  it('sem porta, declara a fiação pendente e o catálogo por configurar', () => {
    render(<SlaOrders />)
    // P-II4-FIACAO aparece no título e no corpo do alerta: consulta *AllBy*.
    expect(screen.getAllByText(/P-II4-FIACAO/).length).toBeGreaterThan(0)
    expect(screen.getByText(/Catálogo por configurar/)).toBeTruthy()
    expect(screen.getByText(/P-II4-CATALOGO/)).toBeTruthy()
  })

  it('com porta, mostra a configuração TST-TIPO, os prazos duplos e as recusas do fecho', async () => {
    const user = userEvent.setup()
    render(<SlaOrders port={fakePort()} />)
    // TST-TIPO aparece duas vezes (configuração e ordem): getAllByText.
    await waitFor(() => expect(screen.getAllByText(/TST-TIPO/).length).toBeGreaterThan(0))
    // R41-4-12: os códigos de enumeração aparecem com rótulo pt-BR, nunca crus.
    expect(screen.getByText('Em risco')).toBeTruthy()
    expect(screen.getByText('Dentro do prazo')).toBeTruthy()
    expect(screen.getByText(/Aberta/)).toBeTruthy()
    expect(screen.getByText(/Alerta de risco/)).toBeTruthy()
    expect(screen.queryByText('AT_RISK')).toBeNull()
    expect(screen.queryByText('WITHIN')).toBeNull()
    expect(screen.queryByText('RISK_ALERT')).toBeNull()
    // R41-4-13: a abertura e os vencimentos são exibidos junto a cada OS.
    expect(screen.getByText(/aberta em 01\/12\/2026/)).toBeTruthy()
    expect(screen.getByText(/vencimento 05\/12\/2026/)).toBeTruthy()
    expect(screen.getAllByText(/decorrido 1h 0min/).length).toBeGreaterThan(0)
    await user.click(screen.getByRole('button', { name: 'Avaliar encerramento' }))
    // MISSING_PROBLEM_CAUSE_REMEDY continua citado no texto do catálogo
    // (nome do tipo de recusa do mecanismo); a recusa da porta distingue-se
    // pelo rótulo pt-BR e pelo detalhe devolvido.
    await waitFor(() => expect(screen.getByText(/codificação obrigatória/)).toBeTruthy())
    expect(screen.getByText(/Problema, causa e remédio em falta/)).toBeTruthy()
    expect(screen.getAllByText(/MISSING_PROBLEM_CAUSE_REMEDY/).length).toBeGreaterThan(0)
  })

  it('a tripla TST-* das suites Java marca-se completa quando preenchida', async () => {
    const user = userEvent.setup()
    render(<SlaOrders port={fakePort()} />)
    await user.type(screen.getByLabelText('Código do problema'), 'TST-P1')
    await user.type(screen.getByLabelText('Código da causa'), 'TST-C1')
    await user.type(screen.getByLabelText('Código do remédio'), 'TST-R1')
    expect(screen.getByText('completa')).toBeTruthy()
  })
})

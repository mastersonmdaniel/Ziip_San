import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { DmaPanels } from './DmaPanels'
import type { DmaPanelsPort } from '@services/losses/dmaPanels'

/**
 * Porta falsa com as fixtures das suites Java do núcleo II-6
 * (DmaFixtures/ExactWaterBalanceTest/MinimumNightFlowTest/
 * DmcReturnRankingTest) — nenhum valor inventado.
 */
const fixturePort: DmaPanelsPort = {
  balancePanel: () =>
    Promise.resolve({
      definition: { dmaCode: 'DMA-1', sectorCode: 'SETOR-1', memberDmcCodes: ['DMC-A', 'DMC-B'] },
      memberInputs: [
        {
          dmcId: 'fixture-DMC-A', dmcCode: 'DMC-A', periodStart: '2026-10-01', periodEnd: '2026-10-31',
          systemInputM3: '10000.50', exportedM3: '500.25', billedMeteredM3: '6000.10', billedUnmeteredM3: '300',
          authorizedUnbilledM3: '200.05', apparentLossesM3: '500.05',
        },
        {
          dmcId: 'fixture-DMC-B', dmcCode: 'DMC-B', periodStart: '2026-10-01', periodEnd: '2026-10-31',
          systemInputM3: '2000', exportedM3: '0', billedMeteredM3: '1000', billedUnmeteredM3: '0',
          authorizedUnbilledM3: '100', apparentLossesM3: '50',
        },
      ],
    }),
  nightFlowPanel: () =>
    Promise.resolve({
      series: [
        { instant: '2026-10-15T02:00:00', flowLitresPerHour: '5000.50' },
        { instant: '2026-10-15T02:30:00', flowLitresPerHour: '4600' },
        { instant: '2026-10-15T03:00:00', flowLitresPerHour: '4200.00' },
        { instant: '2026-10-15T03:30:00', flowLitresPerHour: '4800' },
      ],
      window: { start: '02:00', end: '04:00' },
      sampleIntervalMinutes: 30,
      legitNightUseLPerConnH: '1.70',
      connections: 1000,
    }),
  rankingPanel: () =>
    Promise.resolve({
      entries: [
        { dmcCode: 'DMC-B', totalLossesM3: '900', realLossesM3: '800' },
        { dmcCode: 'DMC-A', totalLossesM3: '1200', realLossesM3: '800' },
        { dmcCode: 'DMC-C', totalLossesM3: '2000', realLossesM3: null },
        { dmcCode: 'DMC-D', totalLossesM3: '100', realLossesM3: '50' },
      ],
      tariffPerM3: null,
    }),
  districts: () => Promise.resolve(null),
}

describe('DmaPanels (P-II6-TELA)', () => {
  it('sem fiação (porta por omissão), mostra o estado vazio honesto com a pendência nomeada', async () => {
    render(<DmaPanels />)
    expect(await screen.findByText(/P-II6-FIACAO/)).toBeInTheDocument()
    expect(screen.getByText(/nenhuma rota HTTP/i)).toBeInTheDocument()
  })

  it('com a porta fiada às fixtures Java, mostra o balanço exacto e o agregado do DMA', async () => {
    render(<DmaPanels port={fixturePort} />)
    expect(await screen.findByText(/DMA-1/)).toBeInTheDocument()
    // Agregado: entrada líquida 11500.25; perdas totais 3900.10; reais 3350.05 (suite Java).
    expect(screen.getAllByText('11500.25').length).toBeGreaterThan(0)
    expect(screen.getAllByText('3900.10').length).toBeGreaterThan(0)
    expect(screen.getAllByText('3350.05').length).toBeGreaterThan(0)
    // Por DMC: DMC-A entrada líquida 9500.25.
    expect(screen.getAllByText('9500.25').length).toBeGreaterThan(0)
  })

  it('VMN: desfecho COMPLETE com a líquida exacta das fixtures Java (2500.00 L/h)', async () => {
    const user = userEvent.setup()
    render(<DmaPanels port={fixturePort} />)
    await screen.findByText(/DMA-1/)
    await user.click(screen.getByRole('tab', { name: 'Vazão mínima nocturna' }))
    expect(await screen.findByText('COMPLETE')).toBeInTheDocument()
    expect(screen.getByText(/2500\.00 L\/h/)).toBeInTheDocument()
  })

  it('ranking: modo LOSS_VOLUME_ONLY declarado (tarifa [A DEFINIR]) e DMC-C primeiro', async () => {
    const user = userEvent.setup()
    render(<DmaPanels port={fixturePort} />)
    await screen.findByText(/DMA-1/)
    await user.click(screen.getByRole('tab', { name: 'Ranking DMC' }))
    expect(await screen.findByText('LOSS_VOLUME_ONLY')).toBeInTheDocument()
    expect(screen.getByText(/P-II6-TARIFA/)).toBeInTheDocument()
    const rows = screen.getAllByRole('row')
    const rankingRowTexts = rows.map((r) => r.textContent ?? '')
    const firstDataRow = rankingRowTexts.find((t) => t.includes('DMC-C'))
    expect(firstDataRow).toBeDefined()
    expect(rankingRowTexts.findIndex((t) => t.includes('DMC-C'))).toBeLessThan(
      rankingRowTexts.findIndex((t) => t.includes('DMC-D')),
    )
  })
})

import { describe, expect, it, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Central } from './Central'

const get = vi.fn()
const post = vi.fn()

vi.mock('@services/api', () => ({
  api: { get: (...args: unknown[]) => get(...args), post: (...args: unknown[]) => post(...args) },
}))

vi.mock('@services/registry/errors', () => ({
  errorMessage: () => 'erro de teste',
}))

describe('Central (P1.17)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    get.mockImplementation((path: string) => {
      if (path.endsWith('/overview')) {
        return Promise.resolve({
          data: {
            healthScores: [{ tenantRef: null, score: 100, status: 'HEALTHY',
              factors: [{ key: 'outbox', weight: 25, points: 100, reason: 'Outbox sem mensagens DEAD', measured: true }],
              unmeasured: ['fiscal'] }],
            todayFocus: [{ kind: 'ok', detail: 'Nenhum problema aberto na Central' }],
            watchdog: { lastRunAt: null, stale: true, ageSeconds: null },
          },
        })
      }
      if (path.endsWith('/alerts')) {
        return Promise.resolve({
          data: {
            open: [{ id: 'a1', ruleKey: 'r1', tenantRef: null, severity: 'CRITICAL', status: 'OPEN', openedAt: null }],
            recentResolved: [],
            rules: [{ ruleKey: 'r1', metricKey: 'platform.outbox.dead', severity: 'CRITICAL', enabled: true }],
          },
        })
      }
      if (path.endsWith('/contracts')) {
        return Promise.resolve({
          data: [{
            tenantId: 't1', contractId: 'c1', contractNumber: 'CTR-TESTE', status: 'ACTIVE',
            startsAt: '2026-01-01', endsAt: '2026-12-31', daysToExpiry: 20, commitmentBalance: 40000,
            openInvoices: 1, openInvoicesTotal: 10000, overdueInvoices: 0, providerCostMonth: 6000,
            invoicedMonth: 10000, margin: 0.4,
          }],
        })
      }
      if (path.includes('/sla-report')) {
        return Promise.resolve({
          data: {
            tenantId: 't1', month: '2026-01-01', opened: 3, resolved: 2, adherencePct: 50,
            csatAvg: 3.5, csatCount: 2, penaltyEstimateTotal: 1000,
            bySeverity: [
              { severity: 'S1', opened: 1, resolved: 0, resolvedWithinDue: 0, breachedResolution: 0,
                adherencePct: null, avgFrtMinutes: null, avgMttrMinutes: null, csatAvg: null,
                csatCount: 0, csatReviewCount: 0, penaltyEstimate: 0 },
              { severity: 'S2', opened: 2, resolved: 2, resolvedWithinDue: 1, breachedResolution: 1,
                adherencePct: 50, avgFrtMinutes: 30, avgMttrMinutes: 600, csatAvg: 3.5,
                csatCount: 2, csatReviewCount: 1, penaltyEstimate: 1000 },
            ],
          },
        })
      }
      return Promise.resolve({ data: {} })
    })
  })

  it('mostra a Visão geral e troca para a aba Alertas', async () => {
    const user = userEvent.setup()
    render(<Central />)
    expect(await screen.findByText(/Nenhum problema aberto na Central/)).toBeInTheDocument()
    await user.click(screen.getByRole('tab', { name: 'Alertas' }))
    await waitFor(() => expect(get).toHaveBeenCalledWith('/platform/central/alerts'))
    // 'r1' aparece duas vezes: no alerta aberto e na regra — findByText
    // (singular) falharia por multiplos matches; conferir os dois.
    expect(await screen.findAllByText(/r1/)).toHaveLength(2)
  })

  it('aba Contratos mostra a linha do contrato com margem', async () => {
    const user = userEvent.setup()
    render(<Central />)
    await user.click(screen.getByRole('tab', { name: 'Contratos' }))
    await waitFor(() => expect(get).toHaveBeenCalledWith('/platform/central/contracts'))
    expect(await screen.findByText(/CTR-TESTE/)).toBeInTheDocument()
    expect(await screen.findByText(/40\.0%/)).toBeInTheDocument()
  })

  it('aba SLA de suporte consulta o relatorio por tenant e mes', async () => {
    const user = userEvent.setup()
    render(<Central />)
    await user.click(screen.getByRole('tab', { name: 'SLA de suporte' }))
    await user.type(screen.getByLabelText('Tenant (UUID)'), 't1')
    await user.click(screen.getByRole('button', { name: 'Consultar relatório' }))
    await waitFor(() => expect(get).toHaveBeenCalledWith(expect.stringContaining('/platform/central/sla-report?tenantId=t1')))
    expect(await screen.findByText(/Abertos 3 — resolvidos 2/)).toBeInTheDocument()
    expect(await screen.findByText(/S2 — abertos 2/)).toBeInTheDocument()
  })

  it('reconhecer um alerta chama o POST e recarrega', async () => {
    const user = userEvent.setup()
    post.mockResolvedValue({})
    render(<Central />)
    await user.click(screen.getByRole('tab', { name: 'Alertas' }))
    await user.click(await screen.findByText('Reconhecer'))
    await waitFor(() => expect(post).toHaveBeenCalledWith('/platform/central/alerts/a1/acknowledge'))
  })
})

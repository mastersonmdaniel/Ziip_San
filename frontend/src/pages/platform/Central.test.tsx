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

  it('reconhecer um alerta chama o POST e recarrega', async () => {
    const user = userEvent.setup()
    post.mockResolvedValue({})
    render(<Central />)
    await user.click(screen.getByRole('tab', { name: 'Alertas' }))
    await user.click(await screen.findByText('Reconhecer'))
    await waitFor(() => expect(post).toHaveBeenCalledWith('/platform/central/alerts/a1/acknowledge'))
  })
})

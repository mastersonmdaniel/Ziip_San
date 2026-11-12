import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Observability } from './Observability'
import type { ObservabilityPort } from '@services/observability/observabilityPanels'

describe('Observability (P-II8-TELA)', () => {
  it('SLOs do go-live: os 4 aparecem com "alvo [A DEFINIR]" — nenhum alvo inventado', async () => {
    render(<Observability />)
    expect(await screen.findByText(/Disponibilidade da API/)).toBeInTheDocument()
    expect(screen.getByText(/Latência da API/)).toBeInTheDocument()
    expect(screen.getByText(/Conclusão do lote de faturação/)).toBeInTheDocument()
    expect(screen.getByText(/Sincronização de campo/)).toBeInTheDocument()
    expect(screen.getAllByText('alvo [A DEFINIR]')).toHaveLength(4)
    expect(screen.getByText(/P-II8-ALVOS/)).toBeInTheDocument()
  })

  it('catálogo herdado P3.11 mantém as marcas PROPOSTA tal como documentadas', async () => {
    render(<Observability />)
    await screen.findByText(/Disponibilidade da API/)
    expect(screen.getByText('api.availability')).toBeInTheDocument()
    expect(screen.getByText(/PROPOSTA local/)).toBeInTheDocument()
  })

  it('alertas: só as 2 regras executáveis de alert-rules.json, com runbook', async () => {
    const user = userEvent.setup()
    render(<Observability />)
    await screen.findByText(/Disponibilidade da API/)
    await user.click(screen.getByRole('tab', { name: 'Alertas e burn rate' }))
    expect(await screen.findByText('central.collector.stale')).toBeInTheDocument()
    expect(screen.getByText('platform.outbox.oldest_pending_age_s.high')).toBeInTheDocument()
    expect(screen.getAllByText(/RB-02-fila-parada/)).toHaveLength(2)
  })

  it('rótulos: uc_id consta dos proibidos e a allowlist de tenant é a da P0.8', async () => {
    const user = userEvent.setup()
    render(<Observability />)
    await screen.findByText(/Disponibilidade da API/)
    await user.click(screen.getByRole('tab', { name: 'Política de rótulos' }))
    expect(await screen.findByText(/uc_id NUNCA é rótulo/)).toBeInTheDocument()
    expect(screen.getByText(/ziipsan_business_requests_total/)).toBeInTheDocument()
  })

  it('séries vivas sem fiação: estado vazio honesto com P-II8-FIACAO nomeada', async () => {
    const user = userEvent.setup()
    render(<Observability />)
    await screen.findByText(/Disponibilidade da API/)
    await user.click(screen.getByRole('tab', { name: 'Séries vivas' }))
    expect(await screen.findByText(/P-II8-FIACAO/)).toBeInTheDocument()
  })

  it('séries vivas com a porta fiada mostram o valor entregue, sem o inventar', async () => {
    const user = userEvent.setup()
    const port: ObservabilityPort = {
      liveSeries: () =>
        Promise.resolve([
          { metricName: 'ziipsan_billing_batch_items_total', labels: { outcome: 'SUCCESS' }, currentValue: 42 },
        ]),
    }
    render(<Observability port={port} />)
    await screen.findByText(/Disponibilidade da API/)
    await user.click(screen.getByRole('tab', { name: 'Séries vivas' }))
    expect(await screen.findByText('ziipsan_billing_batch_items_total')).toBeInTheDocument()
    expect(screen.getByText('42')).toBeInTheDocument()
    expect(screen.getByText(/outcome=SUCCESS/)).toBeInTheDocument()
  })
})

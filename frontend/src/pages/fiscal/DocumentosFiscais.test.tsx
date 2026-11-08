import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { DocumentosFiscais } from './DocumentosFiscais'
import { fiscalDocuments, type FiscalQueueItem } from '@services/fiscal/fiscalDocuments'

/**
 * P2.33 — Correção RG-Lote3 (08/11/2026): estado vazio explícito da
 * fila de documentos fiscais. No padrão do `GisStyles.test.tsx`
 * (vitest + @testing-library, serviço mockado, QueryClientProvider):
 * fila resolvida vazia mostra a mensagem; fila com documento mostra
 * a linha e NÃO mostra a mensagem.
 */
vi.mock('@services/fiscal/fiscalDocuments', () => ({
  fiscalDocuments: {
    queue: vi.fn(),
    resend: vi.fn(),
    requestCancellation: vi.fn(),
  },
}))

afterEach(() => cleanup())

const ITEM: FiscalQueueItem = {
  document: {
    id: 'doc-1',
    tenantId: 'tenant-1',
    kind: 'NFAGE',
    nfageStatus: 'AUTHORIZED',
    accessKey: null,
    serie: 1,
    numero: 42,
    competence: '2026-11',
    billingGroupId: null,
    contingency: false,
    rejectionCode: null,
    sourceType: null,
    sourceId: null,
    issuedAt: null,
  },
  queueState: 'AUTHORIZED',
  rejectionExplanation: null,
  contingencyElapsed: null,
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <DocumentosFiscais />
    </QueryClientProvider>,
  )
}

describe('DocumentosFiscais (P2.33) — estado vazio', () => {
  it('fila vazia mostra a mensagem de estado vazio', async () => {
    vi.mocked(fiscalDocuments.queue).mockResolvedValue([])

    renderPage()

    expect(
      await screen.findByText('Nenhum documento fiscal neste filtro.'),
    ).toBeInTheDocument()
  })

  it('fila com documento mostra a linha e não mostra a mensagem de vazio', async () => {
    vi.mocked(fiscalDocuments.queue).mockResolvedValue([ITEM])

    renderPage()

    expect(await screen.findByText(/doc-1 — série 1, nº 42/)).toBeInTheDocument()
    expect(
      screen.queryByText('Nenhum documento fiscal neste filtro.'),
    ).not.toBeInTheDocument()
  })
})

import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '../test/render'
import { Support } from './Support'

const { tickets, detail, createMock, sendMock, cancelMock } = vi.hoisted(() => ({
  tickets: [] as Array<Record<string, unknown>>,
  detail: null as unknown as Record<string, unknown> | null,
  createMock: vi.fn(async (input: unknown) => ({ number: 'SUP-2026-000001', ...(input as object) })),
  sendMock: vi.fn(async () => ({ id: 'm-2' })),
  cancelMock: vi.fn(async () => ({ number: 'SUP-2026-000001', status: 'CANCELLED' })),
}))

vi.mock('@services/support', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@services/support')>()
  return {
    ...actual,
    listTickets: () => Promise.resolve(tickets),
    getTicket: () => Promise.resolve(detail),
    createTicket: createMock,
    sendTicketMessage: sendMock,
    cancelTicket: cancelMock,
    uploadTicketAttachment: vi.fn(async () => ({})),
  }
})

describe('Suporte (P1.23)', () => {
  beforeEach(() => {
    tickets.length = 0
    createMock.mockClear()
    sendMock.mockClear()
    cancelMock.mockClear()
  })

  it('lista os chamados com o termo chamado, nunca protocolo', async () => {
    tickets.push({
      number: 'SUP-2026-000001', subject: 'Dúvida sobre a fatura', category: 'QUESTION',
      status: 'NEW', createdAt: '2026-11-03T10:00:00',
    })
    renderWithProviders(<Support />)
    // A lista vem de query assíncrona; o timeout padrão (1 s) é curto
    // neste ambiente — espera-se até 10 s pelo número.
    expect(await screen.findByText('SUP-2026-000001', {}, { timeout: 10000 })).toBeInTheDocument()
    expect(screen.getByText('Dúvida sobre a fatura')).toBeInTheDocument()
    // 'Novo' aparece também como opção do filtro de estado; a célula da
    // tabela é a que importa aqui.
    expect(screen.getAllByText('Novo').some((el) => el.tagName === 'TD')).toBe(true)
    // O número é "chamado", nunca "protocolo": o cabeçalho explica a distinção,
    // mas nenhuma linha da lista rotula o número como protocolo.
    expect(screen.queryByText(/Protocolo SUP-/)).not.toBeInTheDocument()
  })

  it('o formulário orienta a não incluir dado pessoal e abre o chamado', async () => {
    // Sem delay por tecla e com timeout maior: userEvent + jsdom são lentos
    // neste sandbox, e o teste em si não tem nada de lento.
    const user = userEvent.setup({ delay: null })
    renderWithProviders(<Support />)
    expect(await screen.findByText(/Não inclua dado pessoal desnecessário/)).toBeInTheDocument()
    await user.type(screen.getByLabelText('Assunto'), 'Falha no portal')
    await user.type(screen.getByLabelText('Mensagem inicial'), 'O portal não abre.')
    await user.click(screen.getByRole('button', { name: 'Abrir chamado' }))
    expect(createMock).toHaveBeenCalledWith(expect.objectContaining({
      subject: 'Falha no portal',
      category: 'QUESTION',
      initialMessage: 'O portal não abre.',
    }))
  }, 30000)

  it('sem chamados, explica como abrir o primeiro', async () => {
    renderWithProviders(<Support />)
    expect(await screen.findByText(/Nenhum chamado ainda/)).toBeInTheDocument()
  })
})

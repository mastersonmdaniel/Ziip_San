import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '../../test/render'
import { SavedViews } from './SavedViews'

const { listMock, createMock, removeMock } = vi.hoisted(() => ({
  listMock: vi.fn(),
  createMock: vi.fn(),
  removeMock: vi.fn(),
}))

vi.mock('@services/search', async (importOriginal) => {
  const original = await importOriginal<typeof import('@services/search')>()
  return {
    ...original,
    savedViewsApi: { list: listMock, create: createMock, update: vi.fn(), remove: removeMock },
  }
})

const VIEW = {
  id: 'v1',
  ownerUserId: 'u1',
  title: 'Pendências',
  resource: 'customers',
  filters: '{"q":"TESTE"}',
  columns: '[]',
  sort: null,
  density: 'comfortable',
  visibility: 'PRIVATE',
  sharedOrgUnitId: null,
  isDefault: false,
}

describe('SavedViews (P1.33)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    localStorage.clear()
    listMock.mockResolvedValue([VIEW])
  })

  it('lista as visões do usuário com densidade compacta ao trocar', async () => {
    const user = userEvent.setup()
    renderWithProviders(<SavedViews />, '/busca/visoes-salvas')
    await waitFor(() => expect(screen.getByText('Pendências')).toBeInTheDocument())
    expect(screen.getByRole('row', { name: /Pendências.*Clientes/ })).toBeInTheDocument()
    const selects = screen.getAllByLabelText('Densidade da tabela')
    await user.selectOptions(selects[selects.length - 1], 'compact')
    expect(document.querySelector('table')?.getAttribute('data-density')).toBe('compact')
  })

  it('cria visão com o formulário e exclui da lista', async () => {
    const user = userEvent.setup()
    createMock.mockResolvedValue({ ...VIEW, id: 'v2', title: 'Nova' })
    renderWithProviders(<SavedViews />, '/busca/visoes-salvas')
    await waitFor(() => expect(screen.getByText('Pendências')).toBeInTheDocument())
    await user.type(screen.getByLabelText(/Título/), 'Nova')
    await user.click(screen.getByRole('button', { name: 'Salvar visão' }))
    await waitFor(() =>
      expect(createMock).toHaveBeenCalledWith(
        expect.objectContaining({ title: 'Nova', resource: 'customers', filters: '{}' }),
      ),
    )
    await user.click(screen.getByRole('button', { name: 'Excluir' }))
    await waitFor(() => expect(removeMock).toHaveBeenCalledWith('v1'))
  })

  it('visão ORG_UNIT avisa que só o dono a vê até a P3.30', async () => {
    listMock.mockResolvedValue([{ ...VIEW, visibility: 'ORG_UNIT' }])
    renderWithProviders(<SavedViews />, '/busca/visoes-salvas')
    await waitFor(() =>
      expect(screen.getByText('Unidade organizacional (só eu, até a P3.30)')).toBeInTheDocument(),
    )
  })
})

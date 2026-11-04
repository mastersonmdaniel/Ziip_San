import { describe, expect, it, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'vitest-axe'
import { MemoryRouter } from 'react-router-dom'
import { GlobalSearchDialog } from './GlobalSearchDialog'

const { searchMock, createViewMock } = vi.hoisted(() => ({
  searchMock: vi.fn(),
  createViewMock: vi.fn(),
}))

vi.mock('@services/search', async (importOriginal) => {
  const original = await importOriginal<typeof import('@services/search')>()
  return {
    ...original,
    globalSearch: searchMock,
    savedViewsApi: { ...original.savedViewsApi, create: createViewMock },
  }
})

function renderDialog() {
  return render(
    <MemoryRouter>
      <GlobalSearchDialog
        open
        onClose={vi.fn()}
        shortcuts={{ enabled: true, openSearch: 'ctrl+k' }}
        setShortcutsEnabled={vi.fn()}
        setOpenSearchBinding={vi.fn()}
        resetShortcuts={vi.fn()}
      />
    </MemoryRouter>,
  )
}

describe('GlobalSearchDialog (P1.33)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    localStorage.clear()
  })

  it('mostra resultados por seção, com favorito, e passa no axe', async () => {
    const user = userEvent.setup()
    searchMock.mockResolvedValue({
      q: 'TESTE',
      results: [
        { type: 'CUSTOMER', id: 'c1', title: 'TESTE Cliente', subtitle: 'Cliente · ***.***.247-**', href: '/cadastro/clientes/c1' },
        { type: 'DOCUMENT', id: 'd1', title: 'contrato.pdf', subtitle: 'Documento · CONTRATO · liberado', href: null },
      ],
      deniedTypes: ['BILL'],
      pendingTypes: ['PROTOCOL'],
      documentSearch: false,
    })
    renderDialog()
    await user.type(screen.getByLabelText('Texto da busca'), 'TESTE')
    await waitFor(() => expect(screen.getByText('TESTE Cliente')).toBeInTheDocument())
    expect(screen.getByText('contrato.pdf')).toBeInTheDocument()
    expect(screen.getByText(/Sem permissão para: Conta/)).toBeInTheDocument()
    expect(screen.getByText(/Protocolo ainda não está na busca/)).toBeInTheDocument()
    // O texto da busca vai no corpo do POST (o mock recebe o texto como argumento, não na URL).
    expect(searchMock).toHaveBeenCalledWith('TESTE')
    await user.click(screen.getByRole('button', { name: 'Favoritar TESTE Cliente' }))
    expect(screen.getByRole('button', { name: 'Desfavoritar TESTE Cliente' })).toBeInTheDocument()
    // O diálogo é portal: o axe roda sobre o body, não sobre o container do render.
    expect(await axe(document.body)).toHaveNoViolations()
  }, 30000)

  it('busca por documento avisa o escopo restrito; setas movem entre resultados', async () => {
    const user = userEvent.setup()
    searchMock.mockResolvedValue({
      q: '529.982.247-25',
      results: [
        { type: 'CUSTOMER', id: 'c1', title: 'TESTE Cliente', subtitle: 'Cliente · ***.***.247-**', href: '/cadastro/clientes/c1' },
      ],
      deniedTypes: [],
      pendingTypes: ['PROTOCOL'],
      documentSearch: true,
    })
    renderDialog()
    const input = screen.getByLabelText('Texto da busca')
    await user.type(input, '529.982.247-25')
    await waitFor(() => expect(screen.getByText('TESTE Cliente')).toBeInTheDocument())
    expect(screen.getByText(/abrange só cliente e unidade/)).toBeInTheDocument()
    // Documento nunca entra no histórico da aba.
    expect(sessionStorage.getItem('ziipsan:search-history:v1') ?? '').not.toContain('529')
    await user.keyboard('{ArrowDown}')
    expect(screen.getByRole('button', { name: /^TESTE Cliente/ })).toHaveFocus()
  })

  it('sem texto, mostra o estado vazio sem chamar a API', async () => {
    renderDialog()
    expect(screen.getByText(/Digite para buscar/)).toBeInTheDocument()
    expect(searchMock).not.toHaveBeenCalled()
  })
})

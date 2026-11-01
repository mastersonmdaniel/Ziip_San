import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { axe } from 'vitest-axe'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { AxiosRequestConfig } from 'axios'

const { requestMock } = vi.hoisted(() => ({ requestMock: vi.fn() }))
vi.mock('@services/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@services/api')>()
  return {
    ...actual,
    api: { request: requestMock },
    // authApi usa o axios real por dentro; aqui ele passa pela mesma API falsa dos testes.
    authApi: { ...actual.authApi, getMe: () => requestMock({ method: 'get', url: '/me' }).then((r: { data: never }) => r.data) },
  }
})

import { App } from '../App'
import { AuthProvider } from '@hooks/useAuth'
import { ThemeProvider } from '@hooks/useTheme'
import { ApiError } from '@services/registry/errors'
import { fakeApi, renderWithProviders } from '../test/render'
import {
  page,
  TEST_CUSTOMER,
  TEST_CUSTOMER_SUMMARY,
  TEST_HISTORY,
  TEST_LINKS,
  TEST_UNIT,
  TEST_UNIT_SUMMARY,
  TEST_USER,
} from '../test/fixtures'
import { Login } from './Login'
import { Catalogs } from './cadastro/Catalogs'
import { CustomerDetail, REVEAL_SECONDS } from './cadastro/CustomerDetail'
import { CustomerForm } from './cadastro/CustomerForm'
import { CustomerList } from './cadastro/CustomerList'
import { PropertyDetail } from './cadastro/PropertyDetail'
import { PropertySearch } from './cadastro/PropertySearch'
import { PropertyWizard } from './cadastro/PropertyWizard'
import { Territory } from './cadastro/Territory'

function useApi(routes: Record<string, unknown>) {
  const impl = fakeApi(routes)
  requestMock.mockImplementation((config: AxiosRequestConfig) => impl(config))
  return impl
}

const emptyCatalogs = {
  'GET /registry/water-statuses': page([]),
  'GET /registry/sewer-statuses': page([]),
  'GET /registry/categories': page([]),
  'GET /registry/property-profiles': page([]),
  'GET /registry/customer-types': page([]),
  'GET /registry/activity-branches': page([]),
}

beforeEach(() => {
  requestMock.mockReset()
  localStorage.clear()
})

afterEach(() => {
  vi.useRealTimers()
})

/** Páginas sem o shell do Layout: o landmark "region" do axe só faz sentido na aplicação inteira. */
const PAGE_AXE = { rules: { region: { enabled: false } } }

describe('acessibilidade (axe) das telas principais', () => {
  it('S1 Pesquisa de imóveis', async () => {
    useApi({ ...emptyCatalogs, 'POST /registry/consumer-units/search': page([TEST_UNIT_SUMMARY]) })
    const { container } = renderWithProviders(<PropertySearch />)
    expect(await screen.findByText('1234567-9')).toBeInTheDocument()
    expect(await axe(container, PAGE_AXE)).toHaveNoViolations()
  })

  it('S2 Assistente de imóvel, etapa 1', async () => {
    useApi({ 'GET /registry/settings': { useBlockFace: false, requireReadingSequence: false } })
    const { container } = renderWithProviders(
      <Routes>
        <Route path="/cadastro/imoveis/novo" element={<PropertyWizard />} />
      </Routes>,
      '/cadastro/imoveis/novo',
    )
    expect(await screen.findByRole('heading', { name: /Etapa 1 de 6: Localização/ })).toBeInTheDocument()
    expect(await axe(container, PAGE_AXE)).toHaveNoViolations()
  })

  it('S3 Ficha do imóvel (aba Cadastro e aba Clientes)', async () => {
    const user = userEvent.setup()
    useApi({
      'GET /registry/consumer-units/unit-1': TEST_UNIT,
      'GET /registry/consumer-units/unit-1/customers': TEST_LINKS,
      'GET /registry/consumer-units/unit-1/history': TEST_HISTORY,
    })
    const { container } = renderWithProviders(
      <Routes>
        <Route path="/cadastro/imoveis/:id" element={<PropertyDetail />} />
      </Routes>,
      '/cadastro/imoveis/unit-1',
    )
    expect(await screen.findByRole('heading', { name: 'Imóvel 1234567-9' })).toBeInTheDocument()
    expect(await axe(container, PAGE_AXE)).toHaveNoViolations()
    await user.click(screen.getByRole('tab', { name: 'Clientes' }))
    expect(await screen.findByText('TESTE Cliente Dois')).toBeInTheDocument()
    expect(await axe(container, PAGE_AXE)).toHaveNoViolations()
    await user.click(screen.getByRole('tab', { name: 'Histórico' }))
    expect(await screen.findByText('Situação de água alterada')).toBeInTheDocument()
    expect(await axe(container, PAGE_AXE)).toHaveNoViolations()
  })

  it('S4 Cliente: lista, formulário e ficha', async () => {
    useApi({
      ...emptyCatalogs,
      'POST /registry/customers/search': page([TEST_CUSTOMER_SUMMARY]),
      'GET /registry/customers/cus-1': TEST_CUSTOMER,
    })
    const list = renderWithProviders(<CustomerList />)
    expect(await screen.findByText('TESTE Cliente Um')).toBeInTheDocument()
    expect(await axe(list.container, PAGE_AXE)).toHaveNoViolations()
    list.unmount()

    const form = renderWithProviders(
      <Routes>
        <Route path="/cadastro/clientes/novo" element={<CustomerForm />} />
      </Routes>,
      '/cadastro/clientes/novo',
    )
    expect(await screen.findByRole('heading', { name: 'Cadastrar cliente' })).toBeInTheDocument()
    expect(await axe(form.container, PAGE_AXE)).toHaveNoViolations()
    form.unmount()

    const detail = renderWithProviders(
      <Routes>
        <Route path="/cadastro/clientes/:id" element={<CustomerDetail />} />
      </Routes>,
      '/cadastro/clientes/cus-1',
    )
    expect(await screen.findByRole('heading', { name: 'TESTE Cliente Um' })).toBeInTheDocument()
    expect(await axe(detail.container, PAGE_AXE)).toHaveNoViolations()
  })

  it('S5 Hierarquia territorial', async () => {
    useApi({ 'GET /registry/localities': page([{ id: 'loc-1', version: 1, code: 1, name: 'TESTE Localidade', headquarters: true, computerized: false, blocked: false }]) })
    const { container } = renderWithProviders(
      <Routes>
        <Route path="/cadastro/territorio/:nivel" element={<Territory />} />
      </Routes>,
      '/cadastro/territorio/localidades',
    )
    expect(await screen.findByText('TESTE Localidade')).toBeInTheDocument()
    expect(await axe(container, PAGE_AXE)).toHaveNoViolations()
  })

  it('S6 Catálogos (lista e formulário aberto)', async () => {
    const user = userEvent.setup()
    useApi({ 'GET /registry/categories': page([]) })
    const { container } = renderWithProviders(
      <Routes>
        <Route path="/cadastro/catalogos/:tipo" element={<Catalogs />} />
      </Routes>,
      '/cadastro/catalogos/categories',
    )
    expect(await screen.findByText(/Nenhum registro cadastrado em categorias/)).toBeInTheDocument()
    expect(await axe(container, PAGE_AXE)).toHaveNoViolations()
    await user.click(screen.getByRole('button', { name: /Adicionar categoria/ }))
    const dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveAccessibleName('Adicionar categoria')
    expect(await axe(document.body, PAGE_AXE)).toHaveNoViolations()
  })

  it('Login', async () => {
    useApi({})
    const { container } = renderWithProviders(
      <AuthProvider>
        <Login />
      </AuthProvider>,
    )
    expect(await screen.findByRole('button', { name: 'Entrar' })).toBeInTheDocument()
    expect(await axe(container)).toHaveNoViolations()
  })

  it('aplicação inteira com menu e marcos (landmarks)', async () => {
    useApi({
      ...emptyCatalogs,
      'GET /me': TEST_USER,
      'POST /registry/consumer-units/search': page([TEST_UNIT_SUMMARY]),
    })
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { container } = render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/cadastro/imoveis']}>
          <ThemeProvider>
            <AuthProvider>
              <App />
            </AuthProvider>
          </ThemeProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    )
    expect(await screen.findByText('1234567-9')).toBeInTheDocument()
    expect(screen.getByRole('main')).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: 'Principal' })).toBeInTheDocument()
    expect(await axe(container)).toHaveNoViolations()
  })
})

describe('navegação honesta: o menu só aponta para telas que existem', () => {
  const hrefs = ['/cadastro/imoveis', '/cadastro/clientes', '/cadastro/territorio', '/cadastro/catalogos']

  it('cada item do menu abre uma tela real (nenhum cai em "Página não encontrada")', async () => {
    useApi({ ...emptyCatalogs, 'GET /me': TEST_USER })
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const user = userEvent.setup()
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/cadastro/imoveis']}>
          <ThemeProvider>
            <AuthProvider>
              <App />
            </AuthProvider>
          </ThemeProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    )
    const nav = await screen.findByRole('navigation', { name: 'Principal' })
    const links = within(nav).getAllByRole('link')
    expect(links.map((l) => l.getAttribute('href'))).toEqual(hrefs)
    for (const link of links) {
      await user.click(link)
      await waitFor(() => expect(screen.getByRole('heading', { level: 1 })).toBeInTheDocument())
      expect(screen.queryByText('Página não encontrada')).not.toBeInTheDocument()
    }
  })

  it('rotas removidas (dashboard, faturamento...) caem em "Página não encontrada"', async () => {
    useApi({ 'GET /me': TEST_USER })
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/dashboard']}>
          <ThemeProvider>
            <AuthProvider>
              <App />
            </AuthProvider>
          </ThemeProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    )
    expect(await screen.findByRole('heading', { name: 'Página não encontrada' })).toBeInTheDocument()
  })
})

describe('S1: sem dados falsos e LGPD', () => {
  it('mostra o documento só mascarado e envia a busca no corpo (POST), nunca na URL', async () => {
    const user = userEvent.setup()
    const api = useApi({ ...emptyCatalogs, 'POST /registry/consumer-units/search': page([TEST_UNIT_SUMMARY]) })
    renderWithProviders(<PropertySearch />)
    expect(await screen.findByText('***.TST.000-**')).toBeInTheDocument()
    expect(screen.getByText('001.002.003.0004.005')).toBeInTheDocument()
    await user.type(screen.getByLabelText('Busca livre'), 'maria')
    await user.click(screen.getByRole('button', { name: /Pesquisar/ }))
    await waitFor(() => {
      const last = api.mock.calls.filter((c) => c[0].url === '/registry/consumer-units/search').slice(-1)[0]?.[0]
      expect(last?.method).toBe('POST')
      expect(last?.params).toBeUndefined()
      expect(last?.data).toMatchObject({ q: 'maria', page: 0, size: 25 })
    })
  })

  it('inscrição completa tem prioridade e vai formatada em q, no corpo', async () => {
    const user = userEvent.setup()
    const api = useApi({ ...emptyCatalogs, 'POST /registry/consumer-units/search': page([]) })
    renderWithProviders(<PropertySearch />)
    await user.type(await screen.findByLabelText('Inscrição'), '0010020030004005')
    await user.click(screen.getByRole('button', { name: /Pesquisar/ }))
    await waitFor(() => {
      const last = api.mock.calls.filter((c) => c[0].url === '/registry/consumer-units/search').slice(-1)[0]?.[0]
      expect(last?.data).toMatchObject({ q: '001.002.003.0004.005' })
    })
  })

  it('lista vazia mostra o estado vazio, sem linhas inventadas', async () => {
    useApi({ ...emptyCatalogs, 'POST /registry/consumer-units/search': page([]) })
    renderWithProviders(<PropertySearch />)
    expect(await screen.findByText('Nenhum imóvel encontrado com estes filtros.')).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('API fora do ar mostra o erro de rede real, sem tabela', async () => {
    requestMock.mockRejectedValue(new ApiError('network', null, null, 'x'))
    // O cliente converte erros do axios; aqui forçamos o caminho do erro de rede do axios.
    const { AxiosError } = await import('axios')
    requestMock.mockRejectedValue(new AxiosError('Network Error', 'ERR_NETWORK'))
    renderWithProviders(<PropertySearch />)
    const alert = await screen.findByRole('alert')
    expect(within(alert).getByText('Não foi possível carregar os imóveis')).toBeInTheDocument()
    expect(within(alert).getByText(/Não foi possível falar com o servidor/)).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })
})

describe('S4: revelar documento (LGPD)', () => {
  function renderDetail() {
    return renderWithProviders(
      <Routes>
        <Route path="/cadastro/clientes/:id" element={<CustomerDetail />} />
      </Routes>,
      '/cadastro/clientes/cus-1',
    )
  }

  it('começa mascarado; revelar chama o POST, mostra por tempo curto e some sozinho', async () => {
    const setItem = vi.spyOn(Storage.prototype, 'setItem')
    const consoleLog = vi.spyOn(console, 'log')
    const api = useApi({
      'GET /registry/customers/cus-1': TEST_CUSTOMER,
      'POST /registry/customers/cus-1/reveal-document': { document: '52998224725', expiresInSeconds: 60 },
    })
    renderDetail()
    expect(await screen.findByText('***.TST.000-**')).toBeInTheDocument()
    expect(screen.queryByText('529.982.247-25')).not.toBeInTheDocument()

    vi.useFakeTimers({ shouldAdvanceTime: true })
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    expect(screen.getByRole('button', { name: /Revelar documento/ })).toBeDisabled()
    await user.selectOptions(screen.getByLabelText('Finalidade do acesso'), 'Conferência cadastral')
    await user.click(screen.getByRole('button', { name: /Revelar documento/ }))
    expect(await screen.findByText('529.982.247-25')).toBeInTheDocument()
    const revealCall = api.mock.calls.find((c) => c[0].url === '/registry/customers/cus-1/reveal-document')?.[0]
    expect(revealCall?.method).toBe('POST')
    expect(revealCall?.data).toEqual({ purpose: 'Conferência cadastral' })
    // O limite local (15 s) vale mesmo se o servidor sugerir mais.
    expect(screen.getByText(`O documento some em ${REVEAL_SECONDS} s.`)).toBeInTheDocument()

    await act(async () => {
      vi.advanceTimersByTime((REVEAL_SECONDS + 1) * 1000)
    })
    await waitFor(() => expect(screen.queryByText('529.982.247-25')).not.toBeInTheDocument())
    expect(screen.getByText('***.TST.000-**')).toBeInTheDocument()

    // Nunca em storage nem em console.
    expect(setItem.mock.calls.some((c) => String(c[1]).includes('52998224725') || String(c[0]).includes('52998224725'))).toBe(false)
    expect(consoleLog).not.toHaveBeenCalled()
    setItem.mockRestore()
    consoleLog.mockRestore()
  })

  it('"Ocultar agora" esconde na hora', async () => {
    const user = userEvent.setup()
    useApi({
      'GET /registry/customers/cus-1': TEST_CUSTOMER,
      'POST /registry/customers/cus-1/reveal-document': { document: '52998224725' },
    })
    renderDetail()
    await user.selectOptions(await screen.findByLabelText('Finalidade do acesso'), 'Conferência cadastral')
    await user.click(screen.getByRole('button', { name: /Revelar documento/ }))
    expect(await screen.findByText('529.982.247-25')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /Ocultar agora/ }))
    expect(screen.queryByText('529.982.247-25')).not.toBeInTheDocument()
  })

  it('sem permissão (403) mostra mensagem e nunca o documento', async () => {
    const user = userEvent.setup()
    const { AxiosError, AxiosHeaders } = await import('axios')
    const impl = fakeApi({ 'GET /registry/customers/cus-1': TEST_CUSTOMER })
    requestMock.mockImplementation((config: AxiosRequestConfig) => {
      if (config.method === 'POST') {
        return Promise.reject(
          new AxiosError('forbidden', '403', undefined, undefined, {
            data: { title: 'Forbidden' },
            status: 403,
            statusText: '',
            headers: {},
            config: { headers: new AxiosHeaders() },
          }),
        )
      }
      return impl(config)
    })
    renderDetail()
    await user.selectOptions(await screen.findByLabelText('Finalidade do acesso'), 'Conferência cadastral')
    await user.click(screen.getByRole('button', { name: /Revelar documento/ }))
    expect(await screen.findByText('Você não tem permissão para ver o documento completo.')).toBeInTheDocument()
    expect(screen.getByText('***.TST.000-**')).toBeInTheDocument()
  })
})

describe('S2: validação por etapa', () => {
  it('avançar com a etapa vazia mostra os erros ligados aos campos e não chama o servidor', async () => {
    const user = userEvent.setup()
    const api = useApi({ 'GET /registry/settings': { useBlockFace: false, requireReadingSequence: false } })
    renderWithProviders(
      <Routes>
        <Route path="/cadastro/imoveis/novo" element={<PropertyWizard />} />
      </Routes>,
      '/cadastro/imoveis/novo',
    )
    await screen.findByRole('heading', { name: /Etapa 1 de 6: Localização/ })
    await user.click(screen.getByRole('button', { name: 'Próximo' }))
    expect(await screen.findByText(/campos com problema nesta etapa/)).toBeInTheDocument()
    const lot = screen.getByLabelText(/Lote/)
    expect(lot).toHaveAttribute('aria-invalid', 'true')
    expect(api.mock.calls.some((c) => String(c[0].url).includes('validate-step'))).toBe(false)
    // O resumo de erros recebe o foco para quem usa teclado ou leitor de tela.
    await waitFor(() => expect(screen.getByText(/campos com problema nesta etapa/).closest('[tabindex="-1"]')).toHaveFocus())
  })
})

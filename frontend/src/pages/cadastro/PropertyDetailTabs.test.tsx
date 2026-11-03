import type { AxiosRequestConfig } from 'axios'
import { Route, Routes } from 'react-router-dom'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'vitest-axe'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { TEST_HISTORY, TEST_LINKS, TEST_UNIT } from '../../test/fixtures'
import { fakeApi, renderWithProviders } from '../../test/render'
import { PropertyDetail } from './PropertyDetail'

const { requestMock } = vi.hoisted(() => ({ requestMock: vi.fn() }))
vi.mock('@services/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@services/api')>()
  return { ...actual, api: { request: requestMock } }
})

beforeEach(() => {
  requestMock.mockReset()
})

describe('abas da ficha da unidade (P1.12)', () => {
  it('abas declaradas sem fonte mostram o aviso, sem chamar rota inexistente', async () => {
    const user = userEvent.setup()
    const impl = fakeApi({
      'GET /registry/consumer-units/unit-1': TEST_UNIT,
      'GET /registry/consumer-units/unit-1/customers': TEST_LINKS,
      'GET /registry/consumer-units/unit-1/history': TEST_HISTORY,
    })
    requestMock.mockImplementation((config: AxiosRequestConfig) => impl(config))
    const { container } = renderWithProviders(
      <Routes>
        <Route path="/cadastro/imoveis/:id" element={<PropertyDetail />} />
      </Routes>,
      '/cadastro/imoveis/unit-1',
    )
    expect(await screen.findByRole('heading', { name: 'Imóvel 1234567-9' })).toBeInTheDocument()
    for (const label of ['Contas', 'Débitos', 'Documentos', 'Qualidade']) {
      expect(screen.getByRole('tab', { name: label })).toBeInTheDocument()
    }
    await user.click(screen.getByRole('tab', { name: 'Contas' }))
    expect(await screen.findByText(/ainda não tem rota de API para alimentar esta aba/)).toBeInTheDocument()
    expect(await axe(container, { rules: { region: { enabled: false } } })).toHaveNoViolations()
    const urls = requestMock.mock.calls.map((c) => (c[0] as AxiosRequestConfig).url)
    expect(urls.some((u) => u?.includes('bills') || u?.includes('debts'))).toBe(false)
  })
})

import type { AxiosRequestConfig } from 'axios'
import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { page, TEST_CUSTOMER_SUMMARY } from '../../test/fixtures'
import { fakeApi, renderWithProviders } from '../../test/render'
import { CustomerList } from './CustomerList'

const { requestMock } = vi.hoisted(() => ({ requestMock: vi.fn() }))
vi.mock('@services/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@services/api')>()
  return { ...actual, api: { request: requestMock } }
})

function useApi(routes: Record<string, unknown>) {
  const impl = fakeApi(routes)
  requestMock.mockImplementation((config: AxiosRequestConfig) => impl(config))
}

describe('filtro preservado ao voltar da ficha (P1.12)', () => {
  beforeEach(() => {
    requestMock.mockReset()
    sessionStorage.clear()
  })

  it('a lista restaura o filtro salvo em sessionStorage', async () => {
    sessionStorage.setItem('ziipsan:list:customers', JSON.stringify({ applied: { q: 'TESTE Restaurado', nameMatch: 'contains', personType: '', customerTypeId: null, city: '', includeDeleted: false }, page: 0 }))
    useApi({
      'GET /registry/customer-types': page([]),
      'POST /registry/customers/search': page([TEST_CUSTOMER_SUMMARY]),
    })
    renderWithProviders(<CustomerList />)
    expect(await screen.findByText('TESTE Cliente Um')).toBeInTheDocument()
    expect(screen.getByLabelText('Nome ou documento')).toHaveValue('TESTE Restaurado')
  })

  it('sem nada salvo, a lista começa com o filtro vazio', async () => {
    useApi({
      'GET /registry/customer-types': page([]),
      'POST /registry/customers/search': page([TEST_CUSTOMER_SUMMARY]),
    })
    renderWithProviders(<CustomerList />)
    expect(await screen.findByText('TESTE Cliente Um')).toBeInTheDocument()
    expect(screen.getByLabelText('Nome ou documento')).toHaveValue('')
  })
})

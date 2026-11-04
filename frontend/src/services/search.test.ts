import { describe, expect, it, vi, beforeEach } from 'vitest'

vi.mock('@services/api', () => ({ api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() } }))

import { api } from '@services/api'
import { globalSearch, savedViewsApi } from './search'

const post = vi.mocked(api.post)
const get = vi.mocked(api.get)

describe('search service (P1.33)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('busca global vai no corpo do POST, nunca na URL', async () => {
    const payload = {
      q: 'TESTE',
      results: [],
      deniedTypes: [],
      pendingTypes: ['PROTOCOL'],
      documentSearch: false,
    }
    post.mockResolvedValue({ data: payload })
    await expect(globalSearch('TESTE')).resolves.toEqual(payload)
    expect(post).toHaveBeenCalledWith('/search/global', { q: 'TESTE', types: undefined }, { signal: undefined })
    // A URL chamada não carrega o texto: o texto está só no corpo.
    expect(String(post.mock.calls[0]?.[0])).not.toContain('TESTE')
  })

  it('visões salvas: lista, cria e exclui pelas rotas da P1.33', async () => {
    get.mockResolvedValue({ data: [] })
    post.mockResolvedValue({ data: { id: '1', title: 'V' } })
    await savedViewsApi.list()
    expect(get).toHaveBeenCalledWith('/search/saved-views', { signal: undefined })
    await savedViewsApi.create({ title: 'V', resource: 'customers', filters: '{}' })
    expect(post).toHaveBeenCalledWith(
      '/search/saved-views',
      { title: 'V', resource: 'customers', filters: '{}' },
      { signal: undefined },
    )
  })
})

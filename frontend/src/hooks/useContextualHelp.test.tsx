import { describe, expect, it } from 'vitest'
import { renderHook } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import type { ReactNode } from 'react'
import { useContextualHelp } from './useContextualHelp'

function wrapper(route: string) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return <MemoryRouter initialEntries={[route]}>{children}</MemoryRouter>
  }
}

describe('useContextualHelp (P1.21)', () => {
  it('devolve os artigos e o tour da rota atual', () => {
    const { result } = renderHook(() => useContextualHelp(), {
      wrapper: wrapper('/cadastro/imoveis'),
    })
    expect(result.current.articles.map((a) => a.slug)).toEqual(['pesquisar-imoveis'])
    expect(result.current.tour?.id).toBe('cadastro-imoveis')
    expect(result.current.tour?.steps.map((s) => s.target)).toEqual([
      'imoveis-search',
      'imoveis-new',
    ])
  })

  it('rota sem conteúdo devolve listas vazias, sem inventar artigo', () => {
    const { result } = renderHook(() => useContextualHelp(), {
      wrapper: wrapper('/login'),
    })
    expect(result.current.articles).toEqual([])
    expect(result.current.tour).toBeUndefined()
  })

  it('busca em português ignora acento', () => {
    const { result } = renderHook(() => useContextualHelp(), {
      wrapper: wrapper('/login'),
    })
    expect(result.current.search('imovel').map((a) => a.slug)).toContain('pesquisar-imoveis')
    expect(result.current.search('IMÓVEL').map((a) => a.slug)).toContain('pesquisar-imoveis')
    expect(result.current.search('zzz-inexistente')).toEqual([])
    expect(result.current.search('   ')).toEqual([])
  })

  it('getHint devolve a dica da chave e undefined para chave desconhecida', () => {
    const { result } = renderHook(() => useContextualHelp(), {
      wrapper: wrapper('/cadastro/imoveis'),
    })
    expect(result.current.getHint('cadastro.property.search')).toContain('Matrícula')
    expect(result.current.getHint('campo.inexistente')).toBeUndefined()
  })
})

import { describe, expect, it, beforeEach } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { useSearchHistory } from './useSearchHistory'
import type { SearchHit } from '@services/search'

const HIT: SearchHit = {
  type: 'CUSTOMER',
  id: '11111111-1111-1111-1111-111111111111',
  title: 'TESTE Cliente',
  subtitle: 'Cliente · ***.***.247-**',
  href: '/cadastro/clientes/11111111-1111-1111-1111-111111111111',
}

describe('useSearchHistory (P1.33)', () => {
  beforeEach(() => {
    sessionStorage.clear()
  })

  it('guarda buscas recentes, sem duplicata e com teto', () => {
    const { result } = renderHook(() => useSearchHistory())
    act(() => {
      result.current.addRecent('  TESTE  ')
      result.current.addRecent('outra busca')
      result.current.addRecent('TESTE')
    })
    expect(result.current.recents).toEqual(['TESTE', 'outra busca'])
  })

  it('busca com cara de documento nunca entra no histórico', () => {
    const { result } = renderHook(() => useSearchHistory())
    act(() => {
      result.current.addRecent('529.982.247-25')
      result.current.addRecent('52998224725')
      result.current.addRecent('12.345.678/0001-99')
      result.current.addRecent('busca normal')
    })
    expect(result.current.recents).toEqual(['busca normal'])
    expect(sessionStorage.getItem('ziipsan:search-history:v1')).not.toContain('529')
  })

  it('favoritar e desfavoritar o mesmo alvo', () => {
    const { result } = renderHook(() => useSearchHistory())
    expect(result.current.isFavorite('CUSTOMER', HIT.id)).toBe(false)
    act(() => result.current.toggleFavorite(HIT))
    expect(result.current.isFavorite('CUSTOMER', HIT.id)).toBe(true)
    expect(result.current.favorites).toHaveLength(1)
    act(() => result.current.toggleFavorite(HIT))
    expect(result.current.isFavorite('CUSTOMER', HIT.id)).toBe(false)
    expect(result.current.favorites).toHaveLength(0)
  })
})

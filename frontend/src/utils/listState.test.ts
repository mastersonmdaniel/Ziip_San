import { beforeEach, describe, expect, it } from 'vitest'
import { clearListState, loadListState, saveListState } from './listState'

describe('listState (P1.12)', () => {
  beforeEach(() => {
    sessionStorage.clear()
  })

  it('salva e restaura o filtro; sem nada salvo, volta o fallback', () => {
    expect(loadListState('customers', { applied: { q: '' }, page: 0 })).toEqual({ applied: { q: '' }, page: 0 })
    saveListState('customers', { applied: { q: 'TESTE' }, page: 2 })
    expect(loadListState('customers', { applied: { q: '' }, page: 0 })).toEqual({ applied: { q: 'TESTE' }, page: 2 })
    clearListState('customers')
    expect(loadListState('customers', { applied: { q: '' }, page: 0 })).toEqual({ applied: { q: '' }, page: 0 })
  })

  it('JSON inválido não quebra a lista', () => {
    sessionStorage.setItem('ziipsan:list:customers', 'não-json{')
    expect(loadListState('customers', { applied: { q: '' }, page: 0 })).toEqual({ applied: { q: '' }, page: 0 })
  })
})

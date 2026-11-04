import { describe, expect, it, beforeEach } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { useShortcuts, bindingOf, eventKey, isTypingTarget, DEFAULT_SHORTCUTS } from './useShortcuts'

function keyEvent(init: Partial<KeyboardEvent> & { key: string }): KeyboardEvent {
  return new KeyboardEvent('keydown', {
    key: init.key,
    ctrlKey: init.ctrlKey ?? false,
    metaKey: init.metaKey ?? false,
    shiftKey: init.shiftKey ?? false,
    altKey: init.altKey ?? false,
  })
}

describe('useShortcuts (P1.33)', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('o padrão é Ctrl ou Cmd+K, ligado', () => {
    const { result } = renderHook(() => useShortcuts())
    expect(result.current.settings).toEqual(DEFAULT_SHORTCUTS)
    expect(result.current.matchesOpenSearch(keyEvent({ key: 'k', ctrlKey: true }))).toBe(true)
    expect(result.current.matchesOpenSearch(keyEvent({ key: 'k', metaKey: true }))).toBe(true)
    expect(result.current.matchesOpenSearch(keyEvent({ key: 'k' }))).toBe(false)
    expect(result.current.matchesOpenSearch(keyEvent({ key: 'K', ctrlKey: true }))).toBe(true)
  })

  it('desligar bloqueia o atalho; remapear troca a combinação', () => {
    const { result } = renderHook(() => useShortcuts())
    act(() => result.current.setEnabled(false))
    expect(result.current.matchesOpenSearch(keyEvent({ key: 'k', ctrlKey: true }))).toBe(false)
    act(() => result.current.setEnabled(true))
    act(() => result.current.setOpenSearch('ctrl+shift+p'))
    expect(result.current.matchesOpenSearch(keyEvent({ key: 'k', ctrlKey: true }))).toBe(false)
    expect(result.current.matchesOpenSearch(keyEvent({ key: 'p', ctrlKey: true, shiftKey: true }))).toBe(true)
    expect(result.current.matchesOpenSearch(keyEvent({ key: 'p', metaKey: true, shiftKey: true }))).toBe(false)
  })

  it('restaurar volta ao padrão e persiste a preferência', () => {
    const { result } = renderHook(() => useShortcuts())
    act(() => {
      result.current.setOpenSearch('ctrl+shift+p')
      result.current.reset()
    })
    expect(result.current.settings).toEqual(DEFAULT_SHORTCUTS)
    expect(JSON.parse(localStorage.getItem('ziipsan:shortcuts:v1') ?? '{}')).toEqual(DEFAULT_SHORTCUTS)
  })

  it('bindingOf monta a combinação; campo de digitação é alvo de digitação', () => {
    expect(bindingOf(keyEvent({ key: 'p', ctrlKey: true, shiftKey: true }))).toBe('ctrl+shift+p')
    expect(eventKey(keyEvent({ key: ' ' }))).toBe('space')
    const input = document.createElement('input')
    const div = document.createElement('div')
    expect(isTypingTarget(input)).toBe(true)
    expect(isTypingTarget(div)).toBe(false)
    expect(isTypingTarget(null)).toBe(false)
  })
})

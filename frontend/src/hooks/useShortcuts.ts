import { useCallback, useState } from 'react'

/**
 * P1.33: atalhos de teclado com remapeamento e desligamento (WCAG 2.1.4 — atalhos
 * de tecla única têm de poder ser remapeados ou desligados; aqui nenhum atalho de
 * fábrica é de tecla única, mas o desligamento e o remapeamento valem para todos
 * do mesmo jeito).
 *
 * O único atalho de fábrica é abrir a busca global: Ctrl+K no Windows/Linux e
 * Cmd+K no Mac — por isso a ligação padrão `ctrl+k` casa com Ctrl OU Cmd (a
 * plataforma decide a tecla). O usuário pode gravar outra combinação (ex.:
 * `ctrl+shift+p`) ou desligar todos os atalhos; a preferência é do dispositivo
 * (localStorage) e nunca é dado pessoal: só a combinação de teclas.
 *
 * Formato da ligação: partes separadas por `+`, em minúsculas (`ctrl`, `meta`,
 * `shift`, `alt` + a tecla). `ctrl` na ligação gravada casa com Ctrl ou Cmd
 * quando a ligação é exatamente `ctrl+k` (o padrão multiplataforma); numa
 * ligação gravada pelo usuário, cada modificador casa exatamente.
 */
export interface ShortcutSettings {
  enabled: boolean
  openSearch: string
}

export const DEFAULT_SHORTCUTS: ShortcutSettings = { enabled: true, openSearch: 'ctrl+k' }

const KEY = 'ziipsan:shortcuts:v1'

function read(): ShortcutSettings {
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) return DEFAULT_SHORTCUTS
    const parsed = JSON.parse(raw) as Partial<ShortcutSettings>
    if (typeof parsed.enabled !== 'boolean' || typeof parsed.openSearch !== 'string' || !parsed.openSearch) {
      return DEFAULT_SHORTCUTS
    }
    return { enabled: parsed.enabled, openSearch: parsed.openSearch.toLowerCase() }
  } catch {
    return DEFAULT_SHORTCUTS
  }
}

function persist(next: ShortcutSettings) {
  try {
    localStorage.setItem(KEY, JSON.stringify(next))
  } catch {
    /* storage indisponível: a preferência vale só nesta sessão */
  }
}

/** A tecla do evento, normalizada (espaço vira `space`, para a ligação ser legível). */
export function eventKey(event: KeyboardEvent): string {
  if (event.key === ' ') return 'space'
  return event.key.toLowerCase()
}

/** A ligação que o evento representa (ex.: `ctrl+shift+p`), ou só a tecla, sem modificador. */
export function bindingOf(event: KeyboardEvent): string {
  const parts: string[] = []
  if (event.ctrlKey) parts.push('ctrl')
  if (event.metaKey) parts.push('meta')
  if (event.shiftKey) parts.push('shift')
  if (event.altKey) parts.push('alt')
  parts.push(eventKey(event))
  return parts.join('+')
}

function matchesBinding(binding: string, event: KeyboardEvent): boolean {
  // Padrão multiplataforma: `ctrl+k` aceita Ctrl (Windows/Linux) ou Cmd (Mac).
  if (binding === 'ctrl+k') {
    return (event.ctrlKey || event.metaKey) && !event.shiftKey && !event.altKey && eventKey(event) === 'k'
  }
  return bindingOf(event) === binding
}

/** O evento é de digitação (campo, área de texto ou conteúdo editável)? Atalhos não roubam a digitação. */
export function isTypingTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false
  if (target.isContentEditable) return true
  const tag = target.tagName
  return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT'
}

export function useShortcuts() {
  const [settings, setSettings] = useState<ShortcutSettings>(read)

  const update = useCallback((next: ShortcutSettings) => {
    setSettings(next)
    persist(next)
  }, [])

  const setEnabled = useCallback(
    (enabled: boolean) => update({ ...settings, enabled }),
    [settings, update],
  )

  const setOpenSearch = useCallback(
    (openSearch: string) => update({ ...settings, openSearch: openSearch.toLowerCase() }),
    [settings, update],
  )

  const reset = useCallback(() => update(DEFAULT_SHORTCUTS), [update])

  const matchesOpenSearch = useCallback(
    (event: KeyboardEvent) => settings.enabled && matchesBinding(settings.openSearch, event),
    [settings],
  )

  return { settings, setEnabled, setOpenSearch, reset, matchesOpenSearch }
}

import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { readPreference, writePreference } from '@utils/preferences'
import { PALETTES, PALETTE_IDS, type PaletteId } from '@theme/palettes.generated'
import { resolveApiBaseUrl } from '@services/runtimeConfig'
import { savePreferences } from '@services/preferences'

/**
 * P0.36 (MARCA-E-TEMAS §4.3–§4.5): modo light | dark | system e padrao
 * (Azul Agua | Verde Agua), resolvidos para data-mode/data-palette no
 * <html> — o que o Tailwind (darkMode por seletor) e o palettes.css leem.
 * O script inline do index.html ja aplicou o mesmo estado antes da primeira
 * pintura; aqui o React so o mantem (e corrige se o cache divergir) e
 * acompanha prefers-color-scheme sem recarregar quando o modo e system.
 * Chave legada 'theme' (so light/dark) continua lida na migracao.
 *
 * P1.18: o servidor (GET/PUT /api/v1/me/preferences) e a fonte; o
 * localStorage e so o cache (MARCA-E-TEMAS §4.5 passo 3). Ao montar, o
 * provider busca a preferencia do servidor e corrige o tema se o cache
 * divergir; ao trocar modo/padrao, persiste a mudanca com PUT parcial
 * (só o campo trocado — a escolha guardada do outro campo nao e tocada).
 * O GET usa fetch() puro, nao o cliente api: na tela de login nao ha
 * sessao, e um 401 pelo cliente api redirecionaria para o login (o
 * interceptor so isenta URLs terminadas em '/me'); aqui o 401 so significa
 * "sem sessao, fica o cache". Falha de rede tambem so mantem o cache.
 */
export type ThemeMode = 'light' | 'dark' | 'system'
type ResolvedTheme = 'light' | 'dark'

interface ThemeContextType {
  /** Tema resolvido (o que esta pintado): 'system' nunca aparece aqui. */
  theme: ResolvedTheme
  mode: ThemeMode
  palette: PaletteId
  toggleTheme: () => void
  setTheme: (theme: ResolvedTheme) => void
  setMode: (mode: ThemeMode) => void
  setPalette: (palette: PaletteId) => void
}

const ThemeContext = createContext<ThemeContextType | undefined>(undefined)

function prefersDark(): boolean {
  return typeof window !== 'undefined' && typeof window.matchMedia === 'function'
    ? window.matchMedia('(prefers-color-scheme: dark)').matches
    : false
}

function initialMode(): ThemeMode {
  const saved = readPreference('theme-mode') ?? readPreference('theme')
  if (saved === 'light' || saved === 'dark' || saved === 'system') return saved
  return 'system'
}

function initialPalette(): PaletteId {
  const saved = readPreference('theme-palette')
  if (saved && (PALETTE_IDS as string[]).includes(saved)) return saved as PaletteId
  return 'aqua-blue'
}

function isThemeMode(value: unknown): value is ThemeMode {
  return value === 'light' || value === 'dark' || value === 'system'
}

function isPaletteId(value: unknown): value is PaletteId {
  return typeof value === 'string' && (PALETTE_IDS as string[]).includes(value)
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [mode, setModeState] = useState<ThemeMode>(initialMode)
  const [palette, setPaletteState] = useState<PaletteId>(initialPalette)
  const [systemDark, setSystemDark] = useState<boolean>(prefersDark)

  // P1.18: preferencia do servidor corrige o cache ao chegar. Sem sessao
  // (login) ou sem rede, o fetch falha e o cache continua valendo.
  useEffect(() => {
    if (typeof fetch !== 'function') return undefined
    let active = true
    void fetch(`${resolveApiBaseUrl()}/me/preferences`, { credentials: 'include' })
      .then((response) => (response.ok ? response.json() : null))
      .then((data: unknown) => {
        if (!active || typeof data !== 'object' || data === null) return
        const view = data as { effectiveMode?: unknown; effectivePalette?: unknown }
        if (isThemeMode(view.effectiveMode)) setModeState(view.effectiveMode)
        if (isPaletteId(view.effectivePalette)) setPaletteState(view.effectivePalette)
      })
      .catch(() => {
        // Cache continua valendo; nada a fazer.
      })
    return () => {
      active = false
    }
  }, [])

  useEffect(() => {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return undefined
    const query = window.matchMedia('(prefers-color-scheme: dark)')
    const onChange = (event: MediaQueryListEvent) => setSystemDark(event.matches)
    query.addEventListener('change', onChange)
    return () => query.removeEventListener('change', onChange)
  }, [])

  const theme: ResolvedTheme = mode === 'system' ? (systemDark ? 'dark' : 'light') : mode

  useEffect(() => {
    const root = document.documentElement
    root.setAttribute('data-mode', theme)
    root.setAttribute('data-palette', palette)
    root.style.colorScheme = theme
    const meta = document.querySelector('meta[name="theme-color"]')
    const themeColor = PALETTES[palette]?.themeColor?.[theme]
    if (meta && themeColor) meta.setAttribute('content', themeColor)
    writePreference('theme-mode', mode)
    writePreference('theme-palette', palette)
    writePreference('theme', theme)
  }, [theme, mode, palette])

  const value = useMemo<ThemeContextType>(
    () => ({
      theme,
      mode,
      palette,
      // PUT parcial em cada troca; falha de rede nao desfaz a troca local
      // (o cache continua valendo e o servidor corrige no proximo GET).
      setMode: (next) => {
        setModeState(next)
        void savePreferences({ mode: next }).catch(() => undefined)
      },
      setPalette: (next) => {
        setPaletteState(next)
        void savePreferences({ palette: next }).catch(() => undefined)
      },
      setTheme: (next) => {
        setModeState(next)
        void savePreferences({ mode: next }).catch(() => undefined)
      },
      toggleTheme: () => {
        const next = mode === 'system' ? (theme === 'dark' ? 'light' : 'dark') : mode === 'light' ? 'dark' : 'light'
        setModeState(next)
        void savePreferences({ mode: next }).catch(() => undefined)
      },
    }),
    [theme, mode, palette],
  )

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}

export function useTheme(): ThemeContextType {
  const context = useContext(ThemeContext)
  if (!context) {
    throw new Error('useTheme deve ser usado dentro de um ThemeProvider')
  }
  return context
}

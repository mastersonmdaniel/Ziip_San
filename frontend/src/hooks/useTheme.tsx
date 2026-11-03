import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { readPreference, writePreference } from '@utils/preferences'
import { PALETTES, PALETTE_IDS, type PaletteId } from '@theme/palettes.generated'

/**
 * P0.36 (MARCA-E-TEMAS §4.3–§4.5): modo light | dark | system e padrao
 * (Azul Agua | Verde Agua), resolvidos para data-mode/data-palette no
 * <html> — o que o Tailwind (darkMode por seletor) e o palettes.css leem.
 * O script inline do index.html ja aplicou o mesmo estado antes da primeira
 * pintura; aqui o React so o mantem (e corrige se o cache divergir) e
 * acompanha prefers-color-scheme sem recarregar quando o modo e system.
 * A preferencia no servidor (GET/PUT /api/v1/me/preferences) e P1.18: ate
 * la, localStorage e o cache, nao a fonte — o comentario antigo que dizia
 * o contrario foi corrigido. Chave legada 'theme' (so light/dark) continua
 * lida na migracao.
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

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [mode, setModeState] = useState<ThemeMode>(initialMode)
  const [palette, setPaletteState] = useState<PaletteId>(initialPalette)
  const [systemDark, setSystemDark] = useState<boolean>(prefersDark)

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
      setMode: setModeState,
      setPalette: setPaletteState,
      setTheme: (next) => setModeState(next),
      toggleTheme: () => {
        if (mode === 'system') setModeState(theme === 'dark' ? 'light' : 'dark')
        else setModeState(mode === 'light' ? 'dark' : 'light')
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

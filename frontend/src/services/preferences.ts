import { api } from '@services/api'
import type { ThemeMode } from '@hooks/useTheme'
import type { PaletteId } from '@theme/palettes.generated'

/**
 * P1.18: preferências do usuário no servidor (GET/PUT /api/v1/me/preferences).
 * O `localStorage` continua existindo como cache (MARCA-E-TEMAS §4.5: o
 * script inline pinta antes da primeira pintura a partir dele); o servidor
 * é a fonte — ao chegar, corrige o cache se divergir.
 *
 * PUT parcial: campo ausente mantém o guardado, null limpa a escolha.
 * `stored.*` null = sem escolha guardada (vale o padrão do tenant).
 */
export type ThemePalette = PaletteId

export interface StoredPreferences {
  mode: ThemeMode | null
  palette: ThemePalette | null
  locale: string | null
}

export interface TenantDefaults {
  defaultMode: ThemeMode | null
  defaultPalette: ThemePalette | null
  allowedPalettes: ThemePalette[] | null
  defaultLocale: string | null
}

export interface PreferencesView {
  stored: StoredPreferences
  effectiveMode: ThemeMode
  effectivePalette: ThemePalette
  effectiveLocale: string
  tenantDefaults: TenantDefaults
}

export interface PreferencesUpdate {
  mode?: ThemeMode | null
  palette?: ThemePalette | null
  locale?: string | null
}

interface RawView {
  stored: { mode: string | null; palette: string | null; locale: string | null }
  effectiveMode: string
  effectivePalette: string
  effectiveLocale: string
  tenantDefaults: {
    defaultMode: string | null
    defaultPalette: string | null
    allowedPalettes: string[] | null
    defaultLocale: string | null
  }
}

function toView(raw: RawView): PreferencesView {
  return {
    stored: raw.stored as StoredPreferences,
    effectiveMode: raw.effectiveMode as ThemeMode,
    effectivePalette: raw.effectivePalette as ThemePalette,
    effectiveLocale: raw.effectiveLocale,
    tenantDefaults: raw.tenantDefaults as TenantDefaults,
  }
}

export async function loadPreferences(): Promise<PreferencesView> {
  const response = await api.get<RawView>('/me/preferences')
  return toView(response.data)
}

export async function savePreferences(update: PreferencesUpdate): Promise<PreferencesView> {
  const response = await api.put<RawView>('/me/preferences', update)
  return toView(response.data)
}

/**
 * Resolução do efetivo no cliente — o MESMO algoritmo do backend
 * (UserPreferencesService.resolve*): escolha do usuário se permitida,
 * senão padrão do tenant, senão fábrica (system, aqua-blue, pt-BR).
 * Usada para pintar a partir do cache antes da resposta do servidor;
 * o servidor tem a palavra final quando responde.
 */
export function resolveEffective(
  stored: StoredPreferences,
  tenantDefaults: TenantDefaults,
): { mode: ThemeMode; palette: ThemePalette; locale: string } {
  const modes: string[] = ['light', 'dark', 'system']
  const mode: ThemeMode =
    stored.mode && modes.includes(stored.mode)
      ? stored.mode
      : tenantDefaults.defaultMode && modes.includes(tenantDefaults.defaultMode)
        ? tenantDefaults.defaultMode
        : 'system'
  const allowed = tenantDefaults.allowedPalettes
  const palette: ThemePalette =
    stored.palette && (allowed === null || allowed.includes(stored.palette))
      ? stored.palette
      : tenantDefaults.defaultPalette && (allowed === null || allowed.includes(tenantDefaults.defaultPalette))
        ? tenantDefaults.defaultPalette
        : 'aqua-blue'
  const locale =
    stored.locale === 'pt-BR'
      ? stored.locale
      : tenantDefaults.defaultLocale === 'pt-BR'
        ? tenantDefaults.defaultLocale
        : 'pt-BR'
  return { mode, palette, locale }
}

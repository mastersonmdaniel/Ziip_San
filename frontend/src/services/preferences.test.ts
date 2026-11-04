import { describe, expect, it } from 'vitest'
import { resolveEffective, type StoredPreferences, type TenantDefaults } from './preferences'

/**
 * P1.18: o mesmo algoritmo do backend (UserPreferencesService.resolve*).
 * A persistência e o HTTP ficam na IT do backend; aqui só a resolução.
 */
describe('P1.18 resolução do efetivo no cliente', () => {
  const none: TenantDefaults = { defaultMode: null, defaultPalette: null, allowedPalettes: null, defaultLocale: null }
  const empty: StoredPreferences = { mode: null, palette: null, locale: null }

  it('sem escolha e sem padrão do tenant: fábrica', () => {
    expect(resolveEffective(empty, none)).toEqual({ mode: 'system', palette: 'aqua-blue', locale: 'pt-BR' })
  })

  it('escolha do usuário vence o padrão do tenant', () => {
    const tenant: TenantDefaults = { defaultMode: 'dark', defaultPalette: 'aqua-green', allowedPalettes: null, defaultLocale: 'pt-BR' }
    expect(resolveEffective({ mode: 'light', palette: 'aqua-blue', locale: 'pt-BR' }, tenant)).toEqual({
      mode: 'light',
      palette: 'aqua-blue',
      locale: 'pt-BR',
    })
  })

  it('padrão retirado da lista: efetivo cai no padrão do tenant, sem apagar a escolha', () => {
    const tenant: TenantDefaults = { defaultMode: null, defaultPalette: 'aqua-blue', allowedPalettes: ['aqua-blue'], defaultLocale: null }
    expect(resolveEffective({ mode: null, palette: 'aqua-green', locale: null }, tenant).palette).toBe('aqua-blue')
  })
})

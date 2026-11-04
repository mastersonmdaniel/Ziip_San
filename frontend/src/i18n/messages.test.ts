import { describe, expect, it } from 'vitest'
import { catalogKeys, DEFAULT_LOCALE, SUPPORTED_LOCALES, t } from './messages'

describe('P1.18 catálogo de mensagens', () => {
  it('só pt-BR é publicado', () => {
    expect(SUPPORTED_LOCALES).toEqual(['pt-BR'])
    expect(DEFAULT_LOCALE).toBe('pt-BR')
  })

  it('t() devolve o texto pt-BR do catálogo', () => {
    expect(t('layout.logout')).toBe('Sair')
    expect(t('nav.properties')).toBe('Imóveis')
    expect(t('layout.useDarkTheme')).toBe('Usar tema escuro')
  })

  it('chave ausente devolve a própria chave (buraco visível, não tradução inventada)', () => {
    expect(t('layout.inexistente')).toBe('layout.inexistente')
  })

  it('toda chave usada pelo Layout existe no catálogo', () => {
    const keys = catalogKeys()
    for (const key of [
      'layout.skipToContent',
      'layout.mainMenu',
      'layout.closeMenu',
      'layout.mainNav',
      'layout.openMenu',
      'layout.registrySection',
      'layout.settingsSection',
      'layout.logout',
      'layout.colorScheme',
      'layout.useLightTheme',
      'layout.useDarkTheme',
      'nav.properties',
      'nav.customers',
      'nav.territory',
      'nav.catalogs',
      'nav.gisCatalog',
      'nav.generatedFiles',
      'nav.templates',
    ]) {
      expect(keys).toContain(key)
    }
  })
})

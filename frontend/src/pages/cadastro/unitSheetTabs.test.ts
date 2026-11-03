import verticalRaw from '../../../../packages/sanitation/vertical.json?raw'
import { describe, expect, it } from 'vitest'
import { UNIT_SHEET_TABS } from './unitSheetTabs'

/**
 * P1.12/P0.47: a ficha não pode divergir do manifesto do pacote. As abas
 * declaradas em `unitSheetTabs` no vertical.json têm de ser exatamente as
 * abas não-registry da ficha, na mesma ordem; as da ficha são da própria
 * ficha (registry), não do manifesto.
 */
describe('abas da ficha da unidade (P0.47)', () => {
  it('bate com o vertical.json: bills, debts, quality, documents', () => {
    const vertical = JSON.parse(verticalRaw) as { modules: Array<{ unitSheetTabs?: string[] }> }
    const declared = vertical.modules.flatMap((m) => m.unitSheetTabs ?? [])
    // Ordem dos módulos no manifesto: billing, collection, quality, mtr.
    expect(declared).toEqual(['bills', 'debts', 'quality', 'documents'])
    const manifestTabs = UNIT_SHEET_TABS.filter((t) => t.module !== 'registry').map((t) => t.id)
    expect(manifestTabs).toEqual(declared)
    expect(UNIT_SHEET_TABS.filter((t) => t.module === 'registry').map((t) => t.id)).toEqual([
      'cadastro',
      'clientes',
      'historico',
    ])
  })

  it('aba declarada sem fonte ainda não é dada como disponível', () => {
    for (const tab of UNIT_SHEET_TABS.filter((t) => t.module !== 'registry')) {
      expect(tab.available).toBe(false)
      expect(tab.unavailableText).toBeTruthy()
    }
  })
})

import { describe, expect, it } from 'vitest'
import { render } from '@testing-library/react'
import { IconContactSheet } from './IconContactSheet'
import { ICON_SECTIONS, ICON_SET_SIZES } from './iconSets'
import { ICONS } from './Icon'

/**
 * P1.29 — folha de contato (ICONES-E-SIMBOLOGIA §6): a folha renderiza
 * todas as secoes, cada item com os tres tamanhos conferidos (16, 20 e
 * 24 px), sob cada combinacao de tema (2 paletas x 2 modos, aplicadas
 * nos atributos do documento como o ThemeProvider faz). jsdom nao mede
 * cor, por isso o contraste por combinacao esta em iconContrast.test.ts;
 * aqui confere-se que nada some ou quebra ao trocar de tema.
 */
const TOTAL_ITEMS = ICON_SECTIONS.reduce((sum, section) => sum + Object.keys(section.icons).length, 0)

describe('IconContactSheet (P1.29)', () => {
  it('todas as secoes e itens do inventario estao na folha', () => {
    const { container } = render(<IconContactSheet />)
    for (const section of ICON_SECTIONS) {
      expect(container.textContent).toContain(section.title)
      for (const item of Object.keys(section.icons)) {
        expect(container.textContent).toContain(item)
      }
    }
    expect(TOTAL_ITEMS).toBe(14 + 15 + 7 + 8 + 28)
  })

  it('cada item aparece nos 3 tamanhos: total de graficos = itens x 3', () => {
    const { container } = render(<IconContactSheet />)
    const svgs = container.querySelectorAll('svg')
    expect(svgs.length).toBe(TOTAL_ITEMS * ICON_SET_SIZES.length)
    for (const size of ICON_SET_SIZES) {
      const atSize = [...svgs].filter((svg) => svg.getAttribute('width') === String(size))
      expect(atSize.length).toBe(TOTAL_ITEMS)
    }
  })

  it('folha renderiza sob as 4 combinacoes de tema (2 paletas x 2 modos)', () => {
    for (const palette of ['aqua-blue', 'aqua-green']) {
      for (const mode of ['light', 'dark']) {
        document.documentElement.setAttribute('data-palette', palette)
        document.documentElement.setAttribute('data-mode', mode)
        const { container, unmount } = render(<IconContactSheet />)
        expect(container.querySelectorAll('svg').length).toBe(TOTAL_ITEMS * ICON_SET_SIZES.length)
        unmount()
      }
    }
    document.documentElement.removeAttribute('data-palette')
    document.documentElement.removeAttribute('data-mode')
  })

  it('nenhum nome do registro fica fora da folha, exceto os genericos da P0.46', () => {
    const inSheet = new Set<string>(ICON_SECTIONS.flatMap((section) => Object.values(section.icons)))
    const generic = ['building-2', 'chevron-left', 'chevron-right', 'eye', 'eye-off', 'layers', 'list-checks', 'log-in', 'map-pin', 'menu', 'palette', 'users']
    expect(Object.keys(ICONS).filter((name) => !inSheet.has(name)).sort()).toEqual(generic.sort())
  })
})

import { describe, expect, it } from 'vitest'
import { render } from '@testing-library/react'
import { ICONS, Icon, type IconName } from './Icon'
import { CUSTOM_ICONS } from './customIcons'
import { ICON_LABELS } from '../../i18n/iconLabels'
import {
  ACTION_ICONS,
  CUSTOM_ICON_NAMES,
  GENERIC_CORE_ICONS,
  ICON_SECTIONS,
  NAV_ICONS,
  SANITATION_ICONS,
  STATE_ICONS,
  SYSTEM_ICONS,
} from './iconSets'

/**
 * P1.29 — cobertura do inventario (ICONES-E-SIMBOLOGIA §4.1 e §4.2):
 * cada item do inventario tem icone no registro; cada icone de
 * saneamento tem prefixo sanitation-; cada icone proprio esta declarado
 * em customIcons.ts; nenhum icone do registro fica fora de categoria
 * ou da lista generica; todo icone proprio segue o guia de estilo
 * (grade 24, traco 2, currentColor, sem cor literal) e renderiza nos
 * tres tamanhos conferidos (16, 20 e 24 px).
 */
describe('IconSets (P1.29)', () => {
  it('inventario §4.1 completo: 14 navegacao, 15 acoes, 7 estados, 8 sistema', () => {
    expect(Object.keys(NAV_ICONS)).toHaveLength(14)
    expect(Object.keys(ACTION_ICONS)).toHaveLength(15)
    expect(Object.keys(STATE_ICONS)).toHaveLength(7)
    expect(Object.keys(SYSTEM_ICONS)).toHaveLength(8)
    for (const map of [NAV_ICONS, ACTION_ICONS, STATE_ICONS, SYSTEM_ICONS]) {
      for (const [item, name] of Object.entries(map)) {
        expect(ICONS[name], `item sem icone: ${item}`).toBeTruthy()
        expect(ICON_LABELS[name], `icone sem rotulo: ${name}`).toBeTruthy()
      }
    }
  })

  it('inventario §4.2 completo: 28 entidades de saneamento, todas com prefixo sanitation-', () => {
    expect(Object.keys(SANITATION_ICONS)).toHaveLength(28)
    for (const [item, name] of Object.entries(SANITATION_ICONS)) {
      expect(name.startsWith('sanitation-'), `sem prefixo: ${name}`).toBe(true)
      expect(ICONS[name], `item sem icone: ${item}`).toBeTruthy()
      expect(ICON_LABELS[name], `icone sem rotulo: ${name}`).toBeTruthy()
    }
  })

  it('8 icones proprios, todos em customIcons.ts, nenhum outro com prefixo sanitation- fora dele', () => {
    expect(CUSTOM_ICON_NAMES).toHaveLength(8)
    for (const name of CUSTOM_ICON_NAMES) {
      expect(ICONS[name]).toBe(CUSTOM_ICONS[name])
    }
    const sanitation = (Object.keys(ICONS) as IconName[]).filter((n) => n.startsWith('sanitation-'))
    expect(sanitation).toHaveLength(28)
    for (const name of sanitation) {
      if (!CUSTOM_ICON_NAMES.includes(name as never)) {
        expect(Object.keys(CUSTOM_ICONS)).not.toContain(name)
      }
    }
  })

  it('todo icone do registro esta numa categoria ou na lista generica, sem sobra', () => {
    const covered = new Set<IconName>()
    for (const section of ICON_SECTIONS) {
      for (const name of Object.values(section.icons)) covered.add(name)
    }
    for (const name of GENERIC_CORE_ICONS) covered.add(name)
    expect([...covered].sort()).toEqual(Object.keys(ICONS).sort())
  })

  it('rotulos do i18n cobrem exatamente o registro (sem falta nem sobra)', () => {
    expect(Object.keys(ICON_LABELS).sort()).toEqual(Object.keys(ICONS).sort())
  })

  it('icone proprio segue o guia de estilo: grade 24, traco 2, currentColor, sem cor literal', () => {
    for (const name of CUSTOM_ICON_NAMES) {
      const { container } = render(<Icon name={name as IconName} size={24} />)
      const svg = container.querySelector('svg')
      expect(svg).toBeTruthy()
      expect(svg?.getAttribute('viewBox')).toBe('0 0 24 24')
      expect(svg?.getAttribute('width')).toBe('24')
      expect(svg?.getAttribute('height')).toBe('24')
      expect(svg?.getAttribute('fill')).toBe('none')
      expect(svg?.getAttribute('stroke')).toBe('currentColor')
      expect(svg?.getAttribute('stroke-width')).toBe('2')
      expect(svg?.outerHTML).not.toMatch(/#[0-9a-fA-F]{3,8}/)
    }
  })

  it('todo icone renderiza em 16, 20 e 24 px', () => {
    for (const size of [16, 20, 24]) {
      for (const name of Object.keys(ICONS) as IconName[]) {
        const { container, unmount } = render(<Icon name={name} size={size} />)
        const svg = container.querySelector('svg')
        expect(svg?.getAttribute('width'), `${name} @ ${size}`).toBe(String(size))
        expect(svg?.getAttribute('height'), `${name} @ ${size}`).toBe(String(size))
        unmount()
      }
    }
  })
})

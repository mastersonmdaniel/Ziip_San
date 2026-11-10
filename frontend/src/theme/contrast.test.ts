import { describe, expect, it } from 'vitest'
import { PALETTES, PALETTE_IDS } from './palettes.generated'

/**
 * P0.36/P0.37 — contraste WCAG 2.2 AA (ISO/IEC 40500:2025) por padrao e por
 * modo, sobre os valores do MARCA-E-TEMAS §4.2 (aprovados pelo dono em
 * 12/10/2026). Regras do §4.6: texto e *-foreground contra o fundo, 4,5:1;
 * input, focus, chart-* e map-* contra background e surface, 3:1.
 * O teste tambem trava os valores do §4.2: mudar um valor aqui sem mudar
 * o §4.2 quebra o teste de proposito (ajuste fino so com registro no §4.2).
 * axe em jsdom nao mede contraste — por isso este teste existe separado.
 */
function hslToRgb(hsl: string): [number, number, number] {
  // Tokens no formato "201 96% 32%": parseFloat ignora o '%' final.
  const [h, s, l] = hsl.split(/\s+/).map((part) => parseFloat(part))
  const sat = s / 100
  const light = l / 100
  const c = (1 - Math.abs(2 * light - 1)) * sat
  const x = c * (1 - Math.abs(((h / 60) % 2) - 1))
  const m = light - c / 2
  let r = 0
  let g = 0
  let b = 0
  if (h < 60) [r, g, b] = [c, x, 0]
  else if (h < 120) [r, g, b] = [x, c, 0]
  else if (h < 180) [r, g, b] = [0, c, x]
  else if (h < 240) [r, g, b] = [0, x, c]
  else if (h < 300) [r, g, b] = [x, 0, c]
  else [r, g, b] = [c, 0, x]
  return [r + m, g + m, b + m]
}

function luminance(hsl: string): number {
  const channel = (v: number) => (v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4)
  const [r, g, b] = hslToRgb(hsl).map(channel)
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

function contrast(a: string, b: string): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x)
  return (hi + 0.05) / (lo + 0.05)
}

const TEXT_PAIRS: Array<[string, string]> = [
  ['foreground', 'background'],
  ['primary-foreground', 'primary'],
  ['secondary-foreground', 'secondary'],
  ['accent-foreground', 'accent'],
  ['muted-foreground', 'background'],
  ['muted-foreground', 'muted'],
  ['success-foreground', 'success'],
  ['warning-foreground', 'warning'],
  ['danger-foreground', 'danger'],
  ['info-foreground', 'info'],
]

const GRAPHIC_TOKENS = ['input', 'focus', 'chart-1', 'chart-2', 'chart-3', 'chart-4', 'chart-5', 'chart-6', 'map-water', 'map-sewer', 'map-boundary', 'map-selection', 'map-alert']

describe('contraste WCAG 2.2 AA (MARCA-E-TEMAS §4.6)', () => {
  it('valores do §4.2 estao no registro (7 padroes: 2 originais + 5 da decisao 65)', () => {
    expect(PALETTE_IDS).toEqual(['amber', 'aqua-blue', 'aqua-green', 'executive-navy', 'graphite', 'sanitation-green', 'violet'])
    expect(PALETTES['aqua-blue'].light.primary).toBe('201 96% 32%')
    expect(PALETTES['aqua-blue'].dark.primary).toBe('199 89% 58%')
    expect(PALETTES['aqua-green'].light.primary).toBe('176 84% 24%')
    expect(PALETTES['aqua-green'].dark.primary).toBe('172 66% 50%')
    // Verde Agua claro: 5,9:1 contra branco; escuro: 10,1:1 contra o fundo escuro.
    expect(contrast(PALETTES['aqua-green'].light.primary, '0 0% 100%')).toBeGreaterThanOrEqual(4.5)
    expect(contrast(PALETTES['aqua-green'].dark.primary, PALETTES['aqua-green'].dark.background)).toBeGreaterThanOrEqual(4.5)
  })

  for (const id of PALETTE_IDS) {
    for (const mode of ['light', 'dark'] as const) {
      it(`${id} / ${mode}: texto e -foreground contra o fundo >= 4,5:1`, () => {
        const tokens = PALETTES[id][mode] as Record<string, string>
        for (const [fg, bg] of TEXT_PAIRS) {
          expect(contrast(tokens[fg], tokens[bg]), `${fg} sobre ${bg}`).toBeGreaterThanOrEqual(4.5)
        }
      })
      it(`${id} / ${mode}: input, focus, chart-* e map-* contra background e surface >= 3:1`, () => {
        const tokens = PALETTES[id][mode] as Record<string, string>
        for (const token of GRAPHIC_TOKENS) {
          expect(contrast(tokens[token], tokens.background), `${token} sobre background`).toBeGreaterThanOrEqual(3)
          expect(contrast(tokens[token], tokens.surface), `${token} sobre surface`).toBeGreaterThanOrEqual(3)
        }
      })
    }
  }
})

import { describe, expect, it } from 'vitest'
import { PALETTES, PALETTE_IDS } from '../../theme/palettes.generated'

/**
 * P1.29 (ICONES-E-SIMBOLOGIA §2.1 e §6) — contraste dos icones por tema:
 * o icone usa currentColor, entao o contraste e o da cor do texto que o
 * acompanha. Confere, para as 2 paletas (Azul Agua, Verde Agua) x 2 modos
 * (claro, escuro), que toda cor usada em icone (texto, primaria, estado
 * e texto atenuado) contra fundo e superficie atinge 3:1 (WCAG 2.2,
 * graficos e componentes de interface). Texto corrido ja e 4,5:1 pelo
 * teste de contraste da P0.36; aqui o piso e o de grafico, porque o
 * icone nunca e o unico meio de transmitir a informacao (ha rotulo).
 * Duplica o calculo de luminancia do teste da P0.36 de proposito: este
 * arquivo nao importa helper de teste alheio.
 */
function hslToRgb(hsl: string): [number, number, number] {
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

const ICON_COLORS = ['foreground', 'primary', 'success', 'warning', 'danger', 'info', 'muted-foreground']
const BACKGROUNDS = ['background', 'surface']

describe('contraste dos icones por tema (P1.29)', () => {
  for (const id of PALETTE_IDS) {
    for (const mode of ['light', 'dark'] as const) {
      it(`${id} / ${mode}: cores de icone contra fundo e superficie >= 3:1`, () => {
        const tokens = PALETTES[id][mode] as Record<string, string>
        for (const fg of ICON_COLORS) {
          for (const bg of BACKGROUNDS) {
            expect(contrast(tokens[fg], tokens[bg]), `${fg} sobre ${bg}`).toBeGreaterThanOrEqual(3)
          }
        }
      })
    }
  }
})

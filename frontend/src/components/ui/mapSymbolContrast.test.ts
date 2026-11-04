import statesRaw from '../../../../packages/sanitation/icons/map/states.json?raw'
import { describe, expect, it } from 'vitest'
import { PALETTES, PALETTE_IDS } from '../../theme/palettes.generated'

/**
 * P1.30 (ADR-0017 ajuste v2.13; ADR-0036 §5; ICONES-E-SIMBOLOGIA §2.2) —
 * contraste dos símbolos de mapa sobre o mapa base claro e escuro.
 * Piso 3:1 (WCAG 2.2, gráficos), o mesmo piso do teste de contraste da
 * P0.36 para tokens map-*: o estado nunca depende só de cor (há traço e
 * halo), mas o símbolo tem de se destacar do fundo nos dois temas.
 * Cópia local do cálculo de luminância, como nos demais testes de tema:
 * este arquivo não importa helper de teste alheio.
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
function hexToRgb(hex: string): [number, number, number] {
  const n = parseInt(hex.slice(1), 16)
  return [((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255]
}
function luminance(rgb: [number, number, number]): number {
  const channel = (v: number) => (v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4)
  const [r, g, b] = rgb.map(channel)
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}
function contrastRgb(a: [number, number, number], b: [number, number, number]): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x)
  return (hi + 0.05) / (lo + 0.05)
}
function contrastHsl(a: string, b: string): number {
  return contrastRgb(hslToRgb(a), hslToRgb(b))
}

// Cinza de abandonado: vem do states.json do pipeline (nunca duplicado aqui
// em literal — o lint P0.36 proíbe cor literal, e o valor certo é o da fonte).
const ABANDONED_GREY: string = (JSON.parse(statesRaw) as { abandonedGrey: string }).abandonedGrey

describe('contraste dos símbolos de mapa por tema (P1.30)', () => {
  for (const id of PALETTE_IDS) {
    for (const mode of ['light', 'dark'] as const) {
      const tokens = PALETTES[id][mode] as Record<string, string>
      it(`${id} / ${mode}: map-water e map-sewer contra fundo e superfície >= 3:1`, () => {
        for (const fg of ['map-water', 'map-sewer']) {
          for (const bg of ['background', 'surface']) {
            expect(contrastHsl(tokens[fg], tokens[bg]), `${fg} sobre ${bg}`).toBeGreaterThanOrEqual(3)
          }
        }
      })
      it(`${id} / ${mode}: cinza de abandonado contra fundo e superfície >= 3:1`, () => {
        for (const bg of ['background', 'surface']) {
          expect(contrastRgb(hexToRgb(ABANDONED_GREY), hslToRgb(tokens[bg])), `abandonado sobre ${bg}`).toBeGreaterThanOrEqual(3)
        }
      })
      it(`${id} / ${mode}: halo separa o símbolo do fundo (halo contra map-* >= 3:1)`, () => {
        const halo = hslToRgb(tokens.background)
        for (const fg of ['map-water', 'map-sewer']) {
          expect(contrastRgb(halo, hslToRgb(tokens[fg])), `halo contra ${fg}`).toBeGreaterThanOrEqual(3)
        }
      })
    }
  }
})

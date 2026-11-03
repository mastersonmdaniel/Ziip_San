#!/usr/bin/env node
/**
 * P0.36/P0.37 — gera, a partir do registro de padroes, o CSS de variaveis,
 * o modulo TypeScript dos padroes e a lista de ids (que o backend usa para
 * validar preferencias na P1.18). Valores do MARCA-E-TEMAS §4.2, aprovados
 * pelo dono em 12/10/2026: este script nao inventa valores, so os aplica.
 * Ajuste fino futuro entra no §4.2 primeiro e depois aqui, com o teste de
 * contraste verde — nunca o contrario.
 * Uso: node scripts/theme/generate-palettes.mjs
 */
import { readFileSync, writeFileSync, readdirSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..')
const themeDir = join(root, 'frontend', 'src', 'theme')
const common = JSON.parse(readFileSync(join(themeDir, 'common.json'), 'utf8'))

// Aliases que o Tailwind e os componentes ja usam (MARCA-E-TEMAS §4.1):
// card/popover = surface/surface-raised, destructive = danger, ring = focus.
function expand(mode, palette) {
  const merged = { ...common[mode], ...palette[mode] }
  const tokens = {
    ...merged,
    focus: merged.primary,
    ring: merged.primary,
    card: merged.surface,
    'card-foreground': merged.foreground,
    popover: merged['surface-raised'],
    'popover-foreground': merged.foreground,
    destructive: merged.danger,
    'destructive-foreground': merged['danger-foreground'],
  }
  delete tokens.themeColor
  return tokens
}

const palettes = {}
for (const file of readdirSync(join(themeDir, 'palettes')).filter((f) => f.endsWith('.json')).sort()) {
  const palette = JSON.parse(readFileSync(join(themeDir, 'palettes', file), 'utf8'))
  palettes[palette.id] = {
    id: palette.id,
    label: palette.label,
    light: expand('light', palette),
    dark: expand('dark', palette),
    themeColor: palette.light.themeColor && palette.dark.themeColor
      ? { light: palette.light.themeColor, dark: palette.dark.themeColor }
      : undefined,
  }
}

const header = `/* GERADO por scripts/theme/generate-palettes.mjs a partir de src/theme/palettes/*.json (MARCA-E-TEMAS §4.2).\n   Nao edite a mao. */\n`
let css = header
// Base sem JS: Azul Agua claro (o script inline do index.html corrige antes da pintura).
const base = palettes['aqua-blue'].light
css += `:root {\n${Object.entries(base).map(([k, v]) => `  --${k}: ${v};`).join('\n')}\n  --radius: 0.5rem;\n}\n`
for (const palette of Object.values(palettes)) {
  for (const mode of ['light', 'dark']) {
    css += `:root[data-palette="${palette.id}"][data-mode="${mode}"] {\n${Object.entries(palette[mode]).map(([k, v]) => `  --${k}: ${v};`).join('\n')}\n  --radius: 0.5rem;\n}\n`
  }
}
writeFileSync(join(themeDir, 'palettes.css'), css)

const tsHeader = `// GERADO por scripts/theme/generate-palettes.mjs a partir de src/theme/palettes/*.json (MARCA-E-TEMAS §4.2).\n// Nao edite a mao.\n`
const ts = `${tsHeader}export type ThemeMode = 'light' | 'dark'\nexport type PaletteId = ${Object.keys(palettes).map((id) => `'${id}'`).join(' | ')}\nexport const PALETTE_IDS = ${JSON.stringify(Object.keys(palettes))} as PaletteId[]\nexport const PALETTES = ${JSON.stringify(palettes, null, 2)} as const\n`
writeFileSync(join(themeDir, 'palettes.generated.ts'), ts)
writeFileSync(join(themeDir, 'palette-ids.json'), `${JSON.stringify(Object.keys(palettes), null, 2)}\n`)
console.log(`OK: padroes gerados (${Object.keys(palettes).join(', ')})`)

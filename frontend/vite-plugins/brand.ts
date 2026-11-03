import { readFileSync, existsSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import type { Plugin } from 'vite'

/**
 * P0.34: plugin do Vite que aplica o pacote de marca a casca do frontend
 * (<title>, description, theme-color) no build. Le frontend/brand.json
 * (copia gerada do pacote, presente mesmo quando o frontend e copiado
 * sozinho no CI) e, na falta dele, branding/ziipsan/brand.json.
 */
export function brandPlugin(): Plugin {
  const dir = dirname(fileURLToPath(import.meta.url))
  const frontendDir = join(dir, '..')
  const fromFrontend = join(frontendDir, 'brand.json')
  const fromPackage = join(frontendDir, '..', 'branding', process.env.ZIIPSAN_BRAND || 'ziipsan', 'brand.json')
  const path = existsSync(fromFrontend) ? fromFrontend : fromPackage
  const brand = JSON.parse(readFileSync(path, 'utf8')) as {
    productName: string
    description: string
    themeColor: { light: string; dark: string }
  }
  return {
    name: 'ziipsan-brand',
    transformIndexHtml(html) {
      return html
        .replaceAll('%BRAND_TITLE%', `${brand.productName} - ${brand.description}`)
        .replaceAll('%BRAND_DESCRIPTION%', brand.description)
        .replaceAll('%BRAND_THEME_COLOR_LIGHT%', brand.themeColor.light)
    },
  }
}

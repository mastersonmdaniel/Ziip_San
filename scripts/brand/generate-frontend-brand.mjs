#!/usr/bin/env node
/**
 * P0.34 — gera, a partir do pacote de marca, o modulo do frontend e os
 * ativos publicos. Nunca edite a saida a mao: rode este script.
 * Uso: node scripts/brand/generate-frontend-brand.mjs [--brand-dir DIR] [--frontend-dir DIR]
 * Padroes: branding/<$ZIIPSAN_BRAND|ziipsan> e frontend/.
 */
import { readFileSync, writeFileSync, copyFileSync, mkdirSync, readdirSync } from 'node:fs'
import { join, dirname, relative } from 'node:path'
import { fileURLToPath } from 'node:url'

const args = Object.fromEntries(
  process.argv.slice(2).map((a) => {
    const [k, v] = a.split('=')
    return [k.replace(/^--/, ''), v]
  }),
)
const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..')
const brandDir = args['brand-dir'] || join(root, 'branding', process.env.ZIIPSAN_BRAND || 'ziipsan')
const frontendDir = args['frontend-dir'] || join(root, 'frontend')
const brand = JSON.parse(readFileSync(join(brandDir, 'brand.json'), 'utf8'))
const texts = JSON.parse(readFileSync(join(brandDir, 'texts', 'pt-BR.json'), 'utf8'))

// Correcao da integracao resto-do-P0: o cabecalho usava o caminho ABSOLUTO do
// pacote (brandDir), entao a saida mudava a cada checkout (ex.: worktree da F34
// vs clone main) e o check (c2) do ci-linux acusava o arquivo como desatualizado
// mesmo com o conteudo identico. Caminho relativo ao repo quando possivel (o caso
// normal); absoluto so quando --brand-dir aponta para fora do repo.
const headerSource = brandDir.startsWith(root) ? relative(root, brandDir) : brandDir
const header = `// GERADO por scripts/brand/generate-frontend-brand.mjs a partir de ${headerSource}.\n// Nao edite a mao: troque o pacote branding/ e regenere.\n`
const generated = `${header}export const BRAND = ${JSON.stringify(
  {
    productName: brand.productName,
    shortName: brand.shortName,
    description: brand.description,
    brandPrimary: brand.brandPrimary,
    themeColor: brand.themeColor,
    attribution: brand.attribution,
    footer: texts.footer,
    emailSignature: texts.emailSignature,
    aboutText: texts.aboutText,
  },
  null,
  2,
)} as const\n`
mkdirSync(join(frontendDir, 'src', 'brand'), { recursive: true })
writeFileSync(join(frontendDir, 'src', 'brand', 'brand.generated.ts'), generated)
// Copia JSON simples para o plugin do Vite: no CI o frontend e copiado
// sozinho (sem branding/), e o plugin le este arquivo em vez do pacote.
writeFileSync(
  join(frontendDir, 'brand.json'),
  JSON.stringify({ productName: brand.productName, description: brand.description, themeColor: brand.themeColor }, null, 2),
)

const publicBrand = join(frontendDir, 'public', 'brand')
mkdirSync(publicBrand, { recursive: true })
for (const file of readdirSync(join(brandDir, 'assets'))) {
  copyFileSync(join(brandDir, 'assets', file), join(publicBrand, file))
}
console.log(`OK: marca gerada para o frontend a partir de ${brandDir}`)

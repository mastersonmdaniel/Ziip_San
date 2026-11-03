#!/usr/bin/env node
/**
 * P0.34 — valida o pacote de marca sem dependencias externas.
 * Uso: node scripts/brand/check-brand.mjs [pasta-do-pacote]
 * Padrao: branding/ziipsan (ou $ZIIPSAN_BRAND). Falha se:
 *  - brand.json invalido, sem campo obrigatorio, ou cor fora de #RRGGBB;
 *  - texts/pt-BR.json ausente ou sem os textos obrigatorios;
 *  - algum ativo listado ausente, com dimensao errada (PNG via cabecalho IHDR,
 *    SVG via width/height/viewBox) ou SVG proibido (script, foreignObject,
 *    atributo on*, referencia externa, javascript:).
 * Nao valida PNG pixel a pixel nem ICO (o ICO e derivado do favicon.png).
 */
import { readFileSync, existsSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..')
const brandDir = process.argv[2] || join(root, 'branding', process.env.ZIIPSAN_BRAND || 'ziipsan')
const failures = []
const fail = (msg) => failures.push(msg)

function readJson(path) {
  try {
    return JSON.parse(readFileSync(path, 'utf8'))
  } catch (e) {
    fail(`${path}: JSON invalido (${e.message})`)
    return null
  }
}

const brand = readJson(join(brandDir, 'brand.json'))
if (brand) {
  for (const key of ['productName', 'shortName', 'description', 'brandPrimary', 'themeColor', 'attribution']) {
    if (brand[key] === undefined || brand[key] === null || brand[key] === '') fail(`brand.json: campo obrigatorio ausente: ${key}`)
  }
  if (typeof brand.productName === 'string' && brand.productName.length > 60) fail('brand.json: productName acima de 60 caracteres')
  if (typeof brand.shortName === 'string' && brand.shortName.length > 20) fail('brand.json: shortName acima de 20 caracteres')
  for (const [label, value] of [
    ['brandPrimary', brand.brandPrimary],
    ['themeColor.light', brand.themeColor?.light],
    ['themeColor.dark', brand.themeColor?.dark],
  ]) {
    if (value !== undefined && !/^#[0-9a-fA-F]{6}$/.test(value)) fail(`brand.json: ${label} fora de #RRGGBB: ${value}`)
  }
  if (brand.attribution && (typeof brand.attribution.enabled !== 'boolean' || typeof brand.attribution.text !== 'string')) {
    fail('brand.json: attribution precisa de enabled (boolean) e text (string)')
  }
  if (brand.legal?.cnpj !== undefined && !/^[0-9./-]+$/.test(brand.legal.cnpj)) fail('brand.json: legal.cnpj com caracteres invalidos')
}

const texts = readJson(join(brandDir, 'texts', 'pt-BR.json'))
if (texts) {
  for (const key of ['description', 'footer', 'emailSignature', 'aboutText']) {
    if (typeof texts[key] !== 'string' || texts[key] === '') fail(`texts/pt-BR.json: texto obrigatorio ausente: ${key}`)
  }
}

function pngSize(path) {
  const buf = readFileSync(path)
  if (buf.length < 24 || buf.toString('ascii', 1, 4) !== 'PNG') return null
  return { width: buf.readUInt32BE(16), height: buf.readUInt32BE(20) }
}

const expectedPng = {
  'icon-192.png': [192, 192],
  'icon-512.png': [512, 512],
  'icon-maskable-512.png': [512, 512],
  'pdf-logo.png': null, // proporcao livre; so existencia
  'email-header.png': null,
}
for (const [file, size] of Object.entries(expectedPng)) {
  const path = join(brandDir, 'assets', file)
  if (!existsSync(path)) {
    fail(`assets/${file}: ausente`)
    continue
  }
  if (size) {
    const got = pngSize(path)
    if (!got || got.width !== size[0] || got.height !== size[1]) fail(`assets/${file}: dimensao ${got ? `${got.width}x${got.height}` : 'ilegivel'}, esperada ${size[0]}x${size[1]}`)
  }
}

const forbiddenSvg = [/<script[\s>]/i, /<foreignObject[\s>]/i, /\son\w+\s*=/i, /javascript:/i, /xlink:href\s*=\s*"https?:/i, /href\s*=\s*"https?:/i]
for (const file of ['logo-light.svg', 'logo-dark.svg', 'mark.svg', 'favicon.svg']) {
  const path = join(brandDir, 'assets', file)
  if (!existsSync(path)) {
    fail(`assets/${file}: ausente`)
    continue
  }
  const svg = readFileSync(path, 'utf8')
  for (const pattern of forbiddenSvg) {
    if (pattern.test(svg)) fail(`assets/${file}: SVG proibido pelo padrao ${pattern}`)
  }
  if (!/viewBox\s*=\s*"[^"]+"/.test(svg)) fail(`assets/${file}: sem viewBox`)
}

if (failures.length > 0) {
  for (const failure of failures) console.error(`ERRO: ${failure}`)
  process.exit(1)
}
console.log(`OK: pacote de marca valido (${brandDir})`)

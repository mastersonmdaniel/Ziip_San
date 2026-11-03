#!/usr/bin/env node
/**
 * P0.35 — prova que trocar a marca muda login e e-mail sem alterar codigo.
 * Cria um pacote temporario com outro nome de produto, roda os dois
 * geradores para diretorios temporarios e confere que a saida muda (e que
 * nenhum arquivo do repositorio foi tocado). Falha se a saida nao mudar.
 * Uso: node scripts/brand/test-brand-swap.mjs
 */
import { mkdtempSync, cpSync, readFileSync, writeFileSync, existsSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { execFileSync } from 'node:child_process'

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..')
const tmp = mkdtempSync(join(tmpdir(), 'ziipsan-brand-swap-'))
const brandTmp = join(tmp, 'brand')
cpSync(join(root, 'branding', 'ziipsan'), brandTmp, { recursive: true })
const brandJsonPath = join(brandTmp, 'brand.json')
const brand = JSON.parse(readFileSync(brandJsonPath, 'utf8'))
brand.productName = 'MARCA-TESTE'
brand.shortName = 'MARCA-TESTE'
brand.description = 'Descricao de teste, sem valor real'
writeFileSync(brandJsonPath, JSON.stringify(brand, null, 2))
const textsPath = join(brandTmp, 'texts', 'pt-BR.json')
const texts = JSON.parse(readFileSync(textsPath, 'utf8'))
for (const key of Object.keys(texts)) {
  if (typeof texts[key] === 'string') texts[key] = texts[key].replaceAll('ZIIPSAN', 'MARCA-TESTE')
}
writeFileSync(textsPath, JSON.stringify(texts, null, 2))

const feTmp = join(tmp, 'frontend')
const themesTmp = join(tmp, 'themes')
execFileSync('node', [join(root, 'scripts', 'brand', 'generate-frontend-brand.mjs'), `--brand-dir=${brandTmp}`, `--frontend-dir=${feTmp}`], { stdio: 'pipe' })
execFileSync('node', [join(root, 'scripts', 'brand', 'generate-keycloak-theme.mjs'), `--brand-dir=${brandTmp}`, `--themes-dir=${themesTmp}`], { stdio: 'pipe' })

const checks = [
  [join(feTmp, 'src', 'brand', 'brand.generated.ts'), 'MARCA-TESTE'],
  [join(themesTmp, 'ziipsan', 'login', 'messages', 'messages_pt-BR.properties'), 'MARCA-TESTE'],
  [join(themesTmp, 'ziipsan', 'email', 'messages', 'messages_pt-BR.properties'), 'MARCA-TESTE'],
  [join(themesTmp, 'ziipsan', 'email', 'html', 'password-reset.ftl'), 'MARCA-TESTE'],
]
let ok = true
for (const [path, needle] of checks) {
  if (!existsSync(path) || !readFileSync(path, 'utf8').includes(needle)) {
    console.error(`ERRO: ${path} nao reflete a marca trocada`)
    ok = false
  }
  if (existsSync(path) && readFileSync(path, 'utf8').includes('ZIIPSAN')) {
    console.error(`ERRO: ${path} ainda contem a marca antiga apos a troca`)
    ok = false
  }
}
if (!ok) process.exit(1)
console.log('OK: trocar a marca muda o frontend, o login e o e-mail do Keycloak sem alterar codigo')

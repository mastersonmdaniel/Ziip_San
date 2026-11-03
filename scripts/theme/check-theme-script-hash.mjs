#!/usr/bin/env node
/**
 * P0.36 — hash SHA-256 do script inline de tema do index.html, para a CSP.
 * O script aplica o tema antes da primeira pintura (MARCA-E-TEMAS §4.5); a
 * CSP do documento precisa permitir exatamente este script
 * (script-src 'self' 'sha256-<hash>'). Sem o hash conferido, o deploy pode
 * servir uma CSP que bloqueia o script (tela pisca) ou um script divergente.
 * Uso: node scripts/theme/check-theme-script-hash.mjs [--write]
 *   padrao: confere o hash gravado em frontend/src/theme/inline-theme-script.sha256;
 *   --write: regrava o hash apos uma mudanca legitima do script.
 */
import { readFileSync, writeFileSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createHash } from 'node:crypto'

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..')
const indexHtml = readFileSync(join(root, 'frontend', 'index.html'), 'utf8')
const match = indexHtml.match(/<script id="theme-init">([\s\S]*?)<\/script>/)
if (!match) {
  console.error('ERRO: script inline de tema (id="theme-init") nao encontrado em frontend/index.html')
  process.exit(1)
}
const hash = createHash('sha256').update(match[1], 'utf8').digest('base64')
const hashPath = join(root, 'frontend', 'src', 'theme', 'inline-theme-script.sha256')
if (process.argv.includes('--write')) {
  writeFileSync(hashPath, `${hash}\n`)
  console.log(`OK: hash do script inline gravado: sha256-${hash}`)
} else {
  const stored = readFileSync(hashPath, 'utf8').trim()
  if (stored !== hash) {
    console.error(`ERRO: hash do script inline divergente (gravado ${stored}, atual ${hash}). Regenere com --write apos revisar a mudanca.`)
    process.exit(1)
  }
  console.log(`OK: hash do script inline conferido (sha256-${hash})`)
}

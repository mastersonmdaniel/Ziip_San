#!/usr/bin/env node
/**
 * GIS-06 (P3.25): medição das metas de tile do §10.4 para a linha
 * de base do RNF — p95 ≤ 300 ms com cache quente e ≤ 1 s sem cache,
 * em tenant sintético com 1 milhão de feições; ficha da feição
 * ≤ 500 ms. As metas são PROPOSTAS (docs/gis/RNF-TILES-PROPOSTA.md):
 * este script mede e registra; não aprova nem reprova o RNF.
 *
 * Uso (contra a stack local, ADR-0033):
 *   ZIIPSAN_API_URL=http://localhost:8090 \
 *   ZIIPSAN_SESSION_COOKIE='ziipsan-session=...' \
 *   ZIIPSAN_TILES='water-arcs:15:12196:18540,water-nodes:15:12196:18540' \
 *   ZIIPSAN_FEATURE='gis_node:<uuid-de-uma-feicao-da-massa>' \
 *   node scripts/gis/measure-tiles.mjs --out baseline-tiles.json
 *
 * Sem dependências (só fetch do Node 18+). Cada tile é pedido uma
 * vez a frio e N vezes a quente; o relatório guarda p50/p95/máx
 * dos dois conjuntos e o tamanho dos corpos. Com ZIIPSAN_FEATURE
 * definido, a ficha da feição (GET /gis/map/features/...) também é
 * medida N vezes contra a meta de 500 ms (correção pós-revisão
 * B2: a meta era declarada no relatório mas nenhum pedido de
 * ficha era feito).
 *
 * Ressalva honesta do "a frio": é o primeiro pedido DESTE
 * processo — se o cache do servidor já estiver quente de um
 * ensaio anterior, a medida "fria" é quente. Para uma linha de
 * base fria de verdade, reinicie a api (ou suba a versão da
 * camada aprovando um pacote) antes de rodar.
 */
const apiUrl = process.env.ZIIPSAN_API_URL ?? 'http://localhost:8090'
const cookie = process.env.ZIIPSAN_SESSION_COOKIE ?? ''
const tilesSpec = process.env.ZIIPSAN_TILES ?? 'water-arcs:15:12196:18540'
const warmRounds = Number(process.env.ZIIPSAN_WARM_ROUNDS ?? '20')
const outArg = process.argv.indexOf('--out')
const outPath = outArg >= 0 ? process.argv[outArg + 1] : null

function percentile(sorted, p) {
  if (sorted.length === 0) return null
  const idx = Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1)
  return sorted[idx]
}

function summarize(samplesMs) {
  const sorted = [...samplesMs].sort((a, b) => a - b)
  return {
    n: sorted.length,
    p50Ms: percentile(sorted, 50),
    p95Ms: percentile(sorted, 95),
    maxMs: sorted.length ? sorted[sorted.length - 1] : null,
  }
}

async function timedFetch(url) {
  const started = performance.now()
  const response = await fetch(url, { headers: cookie ? { Cookie: cookie } : {} })
  const body = await response.arrayBuffer()
  return { ms: performance.now() - started, status: response.status, bytes: body.byteLength }
}

const featureSpec = process.env.ZIIPSAN_FEATURE ?? ''

const report = {
  generatedAt: new Date().toISOString(),
  apiUrl,
  targets: { tileP95HotMs: 300, tileP95ColdMs: 1000, featureDetailP95Ms: 500 },
  tiles: [],
  featureDetail: null,
}

for (const spec of tilesSpec.split(',').map((s) => s.trim()).filter(Boolean)) {
  const [layer, z, x, y] = spec.split(':')
  const url = `${apiUrl}/api/v1/gis/tiles/${layer}/${z}/${x}/${y}.mvt`
  const cold = await timedFetch(url)
  const warm = []
  let bytes = cold.bytes
  for (let i = 0; i < warmRounds; i++) {
    const sample = await timedFetch(url)
    warm.push(sample.ms)
    bytes = sample.bytes
  }
  report.tiles.push({
    layer, z: Number(z), x: Number(x), y: Number(y),
    coldStatus: cold.status, bytes,
    cold: summarize([cold.ms]),
    warm: summarize(warm),
    meetsHotTarget: (summarize(warm).p95Ms ?? Infinity) <= 300,
    meetsColdTarget: cold.ms <= 1000,
  })
}

if (featureSpec) {
  const [featureTable, featureId] = featureSpec.split(':')
  const url = `${apiUrl}/api/v1/gis/map/features/${featureTable}/${featureId}`
  const samples = []
  let status = null
  for (let i = 0; i < warmRounds; i++) {
    const sample = await timedFetch(url)
    samples.push(sample.ms)
    status = sample.status
  }
  const summary = summarize(samples)
  report.featureDetail = {
    featureTable,
    featureId,
    status,
    ...summary,
    meetsDetailTarget: (summary.p95Ms ?? Infinity) <= 500,
  }
}

const text = JSON.stringify(report, null, 2)
if (outPath) {
  const { writeFile } = await import('node:fs/promises')
  await writeFile(outPath, text + '\n', 'utf-8')
  console.log(`linha de base escrita em ${outPath}`)
} else {
  console.log(text)
}

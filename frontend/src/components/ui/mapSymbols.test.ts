/// <reference types="node" />
import { createHash } from 'node:crypto'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { inflateSync } from 'node:zlib'
import { describe, expect, it } from 'vitest'

/**
 * P1.30 (ADR-0036 §5; ADR-0034 decisão 6) — cobertura e regressão entre canais
 * dos símbolos de mapa de água e esgoto.
 *
 * O inventário oficial NÃO é a lista do source.json: o teste extrai os tipos
 * de fábrica do próprio SQL (seed V13_2 + R__ da P1.27) e recusa qualquer
 * divergência — tipo de fábrica ativo sem símbolo quebra o teste, e símbolo
 * sem tipo de fábrica também (nada inventado). A regressão entre canais é
 * por originHash: sprite, QGIS, SLD e legenda têm de referenciar o mesmo
 * símbolo da mesma fonte, com as 435 entradas (87 tipos × 5 estados).
 * Saídas desatualizadas (fonte editada sem regenerar) quebram aqui, porque o
 * manifesto carrega o hash da fonte e os hashes dos arquivos.
 */
const HERE = path.dirname(fileURLToPath(import.meta.url))
const ROOT = path.resolve(HERE, '..', '..', '..', '..')
const MAP_DIR = path.join(ROOT, 'packages', 'sanitation', 'icons', 'map')
const GEN_DIR = path.join(MAP_DIR, 'generated')
const TENANT_SQL = path.join(ROOT, 'intelligence', 'ziipsan-gis', 'src', 'main', 'resources', 'db', 'migration', 'tenant')

const sha256 = (data: Buffer | string) => createHash('sha256').update(data).digest('hex')
const stable = (v: unknown): string => {
  if (v === null || typeof v !== 'object') return JSON.stringify(v)
  if (Array.isArray(v)) return `[${v.map(stable).join(',')}]`
  return `{${Object.keys(v as Record<string, unknown>).sort().map((k) => `${JSON.stringify(k)}:${stable((v as Record<string, unknown>)[k])}`).join(',')}}`
}
const read = (p: string) => fs.readFileSync(p, 'utf-8')
const source = JSON.parse(read(path.join(MAP_DIR, 'source.json')))
const statesDoc = JSON.parse(read(path.join(MAP_DIR, 'states.json')))
const manifest = JSON.parse(read(path.join(GEN_DIR, 'manifest.json')))
const STATE_IDS = statesDoc.states.map((s: { id: string }) => s.id)

function sqlTypeBlock(file: string): string {
  const sql = read(path.join(TENANT_SQL, file))
  const start = sql.indexOf('JOIN (VALUES')
  const end = sql.indexOf(') AS t(', start)
  expect(start).toBeGreaterThan(-1)
  expect(end).toBeGreaterThan(start)
  return sql.slice(start, end)
}
function sqlTypes(file: string): Array<{ classCode: string; typeCode: string; label: string }> {
  const out: Array<{ classCode: string; typeCode: string; label: string }> = []
  for (const m of sqlTypeBlock(file).matchAll(/\('([A-Z_]+)', '([A-Z_]+)', '([^']+)'/g)) {
    // O sufixo ' [*]' do SQL marca tipo acrescido do manual da ARSESP /
    // glossário do SINISA (proveniência do catálogo, P1.27); não faz parte
    // do nome de exibição, por isso a comparação normaliza esse sufixo.
    out.push({ classCode: m[1], typeCode: m[2], label: m[3].replace(/ \[\*\]$/, '') })
  }
  return out
}
function sqlClassDomains(file: string): Record<string, string> {
  const sql = read(path.join(TENANT_SQL, file))
  const start = sql.indexOf('INSERT INTO gis_feature_class')
  const end = sql.indexOf('ON CONFLICT', start)
  const block = sql.slice(start, end)
  const out: Record<string, string> = {}
  for (const m of block.matchAll(/\('\$\{tenant_id\}', '([A-Z_]+)', '([A-Z_]+)',/g)) {
    out[m[1]] = m[2]
  }
  return out
}
const CLASSES: Record<string, { domain: string; geometry: string }> = Object.fromEntries(
  source.classes.map((c: { code: string; domain: string; geometry: string }) => [c.code, { domain: c.domain, geometry: c.geometry }]),
)
const fullGlyph = (t: { class: string; mark?: unknown[]; overrideBase?: boolean }) => {
  const base = t.overrideBase ? [] : source.classes.find((c: { code: string }) => c.code === t.class).base
  return JSON.stringify([...base, ...(t.mark ?? [])])
}

describe('símbolos de mapa — cobertura do catálogo de fábrica (P1.30)', () => {
  it('o SQL tem 41 tipos gis01 (V13_2) + 46 tipos gis02 (R__ P1.27) = 87, sem duplicata', () => {
    const v13 = sqlTypes('V13_2__gis_features_history_migration.sql')
    const r = sqlTypes('R__gis_factory_catalog.sql')
    expect(v13.length).toBe(41)
    expect(r.length).toBe(46)
    const keys = [...v13, ...r].map((t) => `${t.classCode}:${t.typeCode}`)
    expect(new Set(keys).size).toBe(87)
  })

  it('o manifesto cobre exatamente os 87 tipos do SQL, com o rótulo do SQL', () => {
    const sql = [...sqlTypes('V13_2__gis_features_history_migration.sql'), ...sqlTypes('R__gis_factory_catalog.sql')]
    const byKey = new Map(sql.map((t) => [`${t.classCode}:${t.typeCode}`, t]))
    expect(manifest.symbols.length).toBe(87)
    for (const s of manifest.symbols) {
      const t = byKey.get(`${s.classCode}:${s.typeCode}`)
      expect(t, `${s.classCode}:${s.typeCode} sem tipo de fábrica (símbolo inventado?)`).toBeDefined()
      expect(s.label).toBe(t!.label)
      byKey.delete(`${s.classCode}:${s.typeCode}`)
    }
    expect([...byKey.keys()], 'tipos de fábrica ativos sem símbolo').toEqual([])
  })

  it('domínio do tipo = domínio da classe, exceto os 6 overrides documentados', () => {
    const classDomains = { ...sqlClassDomains('V13_2__gis_features_history_migration.sql'), ...sqlClassDomains('R__gis_factory_catalog.sql') }
    const expectedOverrides: Record<string, string> = {
      GRAVITY_SEWER: 'SEWER', FORCE_MAIN: 'SEWER', INVERTED_SIPHON: 'SEWER',
      SEWAGE_LIFT_STATION: 'SEWER', WWTP: 'SEWER', SEWER_SERVICE_LINE: 'SEWER',
    }
    expect(source.domainOverrides).toEqual(expectedOverrides)
    for (const s of manifest.symbols) {
      const expected = expectedOverrides[s.typeCode] ?? classDomains[s.classCode]
      expect(s.domain, `${s.typeCode}`).toBe(expected)
    }
  })

  it('todo símbolo tem os 5 estados, com arquivos de variante, e o estado muda o arquivo', () => {
    expect(STATE_IDS).toEqual(['in-service', 'planned', 'under-construction', 'abandoned', 'out-of-target'])
    for (const s of manifest.symbols) {
      expect(s.states).toEqual(STATE_IDS)
      if (s.geometry === 'point') {
        const inService = read(path.join(GEN_DIR, s.files.svg['in-service']))
        const planned = read(path.join(GEN_DIR, s.files.svg.planned))
        expect(planned).not.toBe(inService)
        expect(read(path.join(GEN_DIR, s.files.svgBase))).toBe(inService)
        for (const st of STATE_IDS) {
          expect(fs.existsSync(path.join(GEN_DIR, s.files.svg[st])), `${s.styleKey}--${st}`).toBe(true)
          for (const themeName of ['light', 'dark']) {
            expect(fs.existsSync(path.join(GEN_DIR, s.files.svgColored[themeName][st]))).toBe(true)
          }
        }
      } else if (s.geometry === 'line') {
        expect(s.lineStyle['in-service'].dash).toBeNull()
        expect(s.lineStyle.planned.dash).toEqual([6, 3])
        expect(s.lineStyle['out-of-target'].halo).not.toBeNull()
      } else {
        expect(s.zoneStyle['in-service'].fillOpacity).toBe(0.15)
        expect(s.zoneStyle['out-of-target'].hatch).toBe(true)
      }
    }
  })

  it('nenhum glifo de ponto se repete, e a marca é distinta dentro da classe', () => {
    const pointTypes = source.types.filter((t: { class: string }) => CLASSES[t.class].geometry === 'point')
    expect(pointTypes.length).toBe(73)
    expect(new Set(pointTypes.map(fullGlyph)).size).toBe(73)
    for (const cls of Object.keys(CLASSES)) {
      const marks = pointTypes.filter((t: { class: string }) => t.class === cls).map((t: { mark?: unknown[] }) => JSON.stringify(t.mark ?? []))
      expect(new Set(marks).size, `marcas duplicadas em ${cls}`).toBe(marks.length)
    }
  })

  it('primitivas dentro da grade 32 e glifo de ponto não vazio', () => {
    const check = (p: unknown[], where: string) => {
      const [kind, ...rest] = p as [string, ...unknown[]]
      expect(['circle', 'circle-fill', 'rect', 'rect-fill', 'line', 'polyline', 'polygon', 'polygon-fill']).toContain(kind)
      const nums = (v: unknown): number[] => (Array.isArray(v) ? (v as unknown[]).flatMap(nums) : [v as number])
      for (const n of nums(rest)) expect(n, `${where} ${kind}`).toBeGreaterThanOrEqual(0)
      for (const n of nums(rest)) expect(n, `${where} ${kind}`).toBeLessThanOrEqual(32)
    }
    for (const c of source.classes) for (const p of c.base) check(p, c.code)
    for (const t of source.types) {
      const prims = fullGlyph(t) === '[]' ? [] : JSON.parse(fullGlyph(t))
      for (const p of prims) check(p, t.code)
      if (CLASSES[t.class].geometry === 'point') expect(prims.length, t.code).toBeGreaterThan(0)
    }
  })
})

describe('símbolos de mapa — sanitização dos SVG (P1.30, ADR-0036 §5)', () => {
  const svgFiles = (dir: string): string[] => {
    const out: string[] = []
    const walk = (d: string) => {
      for (const e of fs.readdirSync(d, { withFileTypes: true })) {
        if (e.isDirectory()) walk(path.join(d, e.name))
        else if (e.name.endsWith('.svg')) out.push(path.join(d, e.name))
      }
    }
    walk(dir)
    return out
  }
  it('nenhum SVG tem script, foreignObject, on*, referência externa ou javascript:', () => {
    const bad = /<script|foreignObject|on[a-z]+\s*=|href\s*=\s*"https?:|javascript:/i
    for (const f of [...svgFiles(path.join(GEN_DIR, 'svg')), ...svgFiles(path.join(GEN_DIR, 'svg-colored'))]) {
      expect(read(f), f).not.toMatch(bad)
    }
  })
  it('SVG de base usa só currentColor (a cor vem do token map-* no renderizador)', () => {
    for (const f of svgFiles(path.join(GEN_DIR, 'svg'))) {
      expect(read(f), f).not.toMatch(/#[0-9a-fA-F]{3,6}\b/)
      expect(read(f), f).toMatch(/currentColor/)
    }
  })
})

describe('símbolos de mapa — regressão entre canais (P1.30)', () => {
  it('manifesto: hash da fonte, contagens e aceite pendente', () => {
    const sourceHash = sha256(Buffer.concat([fs.readFileSync(path.join(MAP_DIR, 'source.json')), fs.readFileSync(path.join(MAP_DIR, 'states.json'))]))
    expect(manifest.source.sourceHash).toBe(sourceHash)
    expect(manifest.counts).toEqual({ types: 87, pointTypes: 73, lineTypes: 12, zoneTypes: 2, states: 5, spriteIcons: 365, channelEntries: 435 })
    expect(manifest.ownerAcceptance).toMatch(/^PENDENTE/)
  })

  it('originHash de cada símbolo corresponde à entrada da fonte', () => {
    type SourceType = { class: string; code: string; label: string; mark?: unknown[]; overrideBase?: boolean }
    const byKey = new Map<string, SourceType>(source.types.map((t: SourceType) => [`${t.class}:${t.code}`, t]))
    for (const s of manifest.symbols) {
      const t = byKey.get(`${s.classCode}:${s.typeCode}`)!
      const cls = CLASSES[s.classCode]
      const prims = t.overrideBase ? [...(t.mark ?? [])] : [...source.classes.find((c: { code: string }) => c.code === t.class).base, ...(t.mark ?? [])]
      expect(s.originHash).toBe(sha256(stable({ class: s.classCode, code: s.typeCode, label: s.label, domain: s.domain, geometry: cls.geometry, prims })))
    }
  })

  it('sprite MapLibre: 365 ícones SDF, geometria dentro do PNG, 1x e 2x', () => {
    const sprite = JSON.parse(read(path.join(GEN_DIR, 'sprite.json')))
    const sprite2x = JSON.parse(read(path.join(GEN_DIR, 'sprite@2x.json')))
    expect(Object.keys(sprite).length).toBe(365)
    expect(Object.keys(sprite2x).length).toBe(365)
    const pngSize = (buf: Buffer) => ({ w: buf.readUInt32BE(16), h: buf.readUInt32BE(20) })
    expect(pngSize(fs.readFileSync(path.join(GEN_DIR, 'sprite.png')))).toEqual({ w: 960, h: 912 })
    expect(pngSize(fs.readFileSync(path.join(GEN_DIR, 'sprite@2x.png')))).toEqual({ w: 1920, h: 1824 })
    for (const [name, e] of Object.entries<{ x: number; y: number; width: number; height: number; pixelRatio: number; content: number[]; sdf: boolean }>(sprite)) {
      expect(e.sdf).toBe(true)
      expect(e.pixelRatio).toBe(1)
      expect([e.width, e.height]).toEqual([48, 48])
      expect(e.content).toEqual([8, 8, 40, 40])
      expect(e.x + e.width).toBeLessThanOrEqual(960)
      expect(e.y + e.height).toBeLessThanOrEqual(912)
      const e2 = sprite2x[name]
      expect(e2.pixelRatio).toBe(2)
      expect([e2.width, e2.height]).toEqual([96, 96])
      expect(e2.content).toEqual([16, 16, 80, 80])
    }
    for (const s of manifest.symbols.filter((x: { geometry: string }) => x.geometry === 'point')) {
      for (const st of STATE_IDS) expect(sprite[`${s.styleKey}--${st}`]).toBeDefined()
    }
  })

  it('sprite PNG decodificado: toda célula tem interior profundo e exterior vazio (SDF válido)', () => {
    const buf = fs.readFileSync(path.join(GEN_DIR, 'sprite.png'))
    const W = buf.readUInt32BE(16), H = buf.readUInt32BE(20)
    let off = 8
    const idats: Buffer[] = []
    while (off < buf.length) {
      const len = buf.readUInt32BE(off), type = buf.toString('ascii', off + 4, off + 8)
      if (type === 'IDAT') idats.push(buf.subarray(off + 8, off + 8 + len))
      off += 12 + len
    }
    const raw = inflateSync(Buffer.concat(idats))
    const stride = W * 4 + 1
    expect(raw.length).toBe(stride * H)
    // Filtro: cada linha do PNG tem filtro 0 — verificado uma vez por linha
    // (antes, uma vez por célula × linha: 17.520 expects redundantes, o que
    // estourava o timeout de 10 s do vitest sob contenção da VM sem que
    // nenhuma asserção tivesse falhado; a propriedade checada é a mesma).
    for (let y = 0; y < H; y++) expect(raw[y * stride], `filtro PNG diferente de 0 na linha ${y}`).toBe(0)
    const alphaAt = (x: number, y: number) => raw[y * stride + 1 + x * 4 + 3]
    const sprite = JSON.parse(read(path.join(GEN_DIR, 'sprite.json')))
    for (const [name, e] of Object.entries<{ x: number; y: number; width: number; height: number }>(sprite)) {
      let mn = 255, mx = 0
      for (let y = e.y; y < e.y + e.height; y++) {
        for (let x = e.x; x < e.x + e.width; x++) {
          const a = alphaAt(x, y)
          if (a < mn) mn = a
          if (a > mx) mx = a
        }
      }
      expect(mn, `${name} sem exterior vazio`).toBeLessThanOrEqual(55)
      expect(mx, `${name} sem interior profundo`).toBeGreaterThanOrEqual(200)
    }
  }, 60000)

  it('QGIS, SLD claro/escuro e legenda têm as 435 entradas com o mesmo nome', () => {
    const qgis = read(path.join(GEN_DIR, 'qgis-style.xml'))
    const sldLight = read(path.join(GEN_DIR, 'sld-light.xml'))
    const sldDark = read(path.join(GEN_DIR, 'sld-dark.xml'))
    const legend = read(path.join(GEN_DIR, 'legend.svg'))
    expect((qgis.match(/<symbol name="/g) ?? []).length).toBe(435)
    expect((sldLight.match(/<Rule><Name>/g) ?? []).length).toBe(435)
    expect((sldDark.match(/<Rule><Name>/g) ?? []).length).toBe(435)
    for (const s of manifest.symbols) {
      for (const st of STATE_IDS) {
        const name = `${s.styleKey}--${st}`
        expect(qgis, name).toContain(`name="${name}"`)
        expect(sldLight, name).toContain(`<Name>${name}</Name>`)
        expect(sldDark, name).toContain(`<Name>${name}</Name>`)
      }
      expect(legend, s.styleKey).toContain(s.styleKey)
    }
  })

  it('folha de contato: todos os símbolos, 5 estados, 3 tamanhos, 2 temas, aceite pendente', () => {
    const sheet = read(path.join(GEN_DIR, 'contact-sheet.html'))
    expect(sheet).toContain('Tema claro')
    expect(sheet).toContain('Tema escuro')
    expect(sheet).toContain('Aceite do dono: pendente')
    for (const size of ['12', '16', '24']) expect(sheet).toContain(`width="${size}"`)
    for (const s of manifest.symbols) {
      const sid = `s-${s.styleKey.replace(/[^a-z0-9]+/gi, '-')}--`
      for (const st of STATE_IDS) expect(sheet, `${s.styleKey}--${st}`).toContain(`id="${sid}${st}"`)
      expect(sheet).toContain(s.label)
    }
  })
})

import { describe, expect, it } from 'vitest'
import {
  centroid,
  dist,
  insertNodeAt,
  mergeLines,
  moveVertex,
  rotate,
  scale,
  splitLineAt,
  toLineWkt,
  toPointWkt,
  toPolygonWkt,
  translate,
  type Pt,
} from './geometry'

// Caracterização do comportamento EXISTENTE de utils/geometry.ts
// (espelho TS de GisGeom, Java) — Ciclo 1 do PDCA GIS: o módulo era
// usado pelo editor web (GIS-09) sem nenhum teste no front.
const line: Pt[] = [
  { x: 0, y: 0 },
  { x: 10, y: 0 },
  { x: 20, y: 0 },
]

describe('geometry — transformações', () => {
  it('translate desloca todos os pontos sem mutar a entrada', () => {
    const out = translate(line, 1.5, -2)
    expect(out).toEqual([
      { x: 1.5, y: -2 },
      { x: 11.5, y: -2 },
      { x: 21.5, y: -2 },
    ])
    expect(line[0]).toEqual({ x: 0, y: 0 })
  })

  it('rotate de 90° em torno da origem troca os eixos', () => {
    const [p] = rotate([{ x: 1, y: 0 }], { x: 0, y: 0 }, 90)
    expect(p.x).toBeCloseTo(0, 10)
    expect(p.y).toBeCloseTo(1, 10)
  })

  it('scale dobra as distâncias ao pivô e recusa fator não-positivo', () => {
    expect(scale([{ x: 2, y: 3 }], { x: 0, y: 0 }, 2)).toEqual([{ x: 4, y: 6 }])
    expect(() => scale(line, { x: 0, y: 0 }, 0)).toThrow('fator de escala deve ser positivo')
    expect(() => scale(line, { x: 0, y: 0 }, -1)).toThrow('fator de escala deve ser positivo')
  })

  it('centroid ignora o ponto de fecho repetido do anel', () => {
    const ring: Pt[] = [
      { x: 0, y: 0 },
      { x: 2, y: 0 },
      { x: 2, y: 2 },
      { x: 0, y: 2 },
      { x: 0, y: 0 },
    ]
    expect(centroid(ring)).toEqual({ x: 1, y: 1 })
  })

  it('dist é euclidiana', () => {
    expect(dist({ x: 0, y: 0 }, { x: 3, y: 4 })).toBe(5)
  })
})

describe('geometry — dividir/inserir nó (regras D6 do espelho)', () => {
  it('divisão a meio de um segmento cria o nó projectado nos dois trechos', () => {
    const r = splitLineAt(line, { x: 5, y: 0.4 }, 1)
    expect(r.node).toEqual({ x: 5, y: 0 })
    expect(r.first).toEqual([
      { x: 0, y: 0 },
      { x: 5, y: 0 },
    ])
    expect(r.second).toEqual([
      { x: 5, y: 0 },
      { x: 10, y: 0 },
      { x: 20, y: 0 },
    ])
  })

  it('divisão exactamente num vértice existente não duplica o ponto', () => {
    const r = splitLineAt(line, { x: 10, y: 0 }, 0.5)
    expect(r.node).toEqual({ x: 10, y: 0 })
    expect(r.first).toEqual([
      { x: 0, y: 0 },
      { x: 10, y: 0 },
    ])
    expect(r.second).toEqual([
      { x: 10, y: 0 },
      { x: 20, y: 0 },
    ])
  })

  it('ponto longe do arco (fora da tolerância) é recusado', () => {
    expect(() => splitLineAt(line, { x: 5, y: 9 }, 1)).toThrow('ponto de divisao fora do arco')
  })

  it('divisão colada a uma extremidade é recusada (arco degenerado)', () => {
    expect(() => splitLineAt(line, { x: 0, y: 0 }, 0.5)).toThrow(
      'ponto de divisao na extremidade do arco',
    )
  })

  it('insertNodeAt é a mesma operação que splitLineAt', () => {
    const r = insertNodeAt(line, { x: 15, y: 0 }, 0.5)
    expect(r.node).toEqual({ x: 15, y: 0 })
    expect(r.first[r.first.length - 1]).toEqual({ x: 15, y: 0 })
  })

  it('moveVertex troca só o vértice indicado e recusa índice fora', () => {
    expect(moveVertex(line, 1, { x: 10, y: 7 })[1]).toEqual({ x: 10, y: 7 })
    expect(() => moveVertex(line, 3, { x: 0, y: 0 })).toThrow('vertice fora da geometria')
    expect(() => moveVertex(line, -1, { x: 0, y: 0 })).toThrow('vertice fora da geometria')
  })
})

describe('geometry — unir arcos', () => {
  it('une quando o fim de A toca o início de B, sem duplicar o vértice comum', () => {
    const b: Pt[] = [
      { x: 20, y: 0 },
      { x: 30, y: 0 },
    ]
    expect(mergeLines(line, b, 0.01)).toEqual([
      { x: 0, y: 0 },
      { x: 10, y: 0 },
      { x: 20, y: 0 },
      { x: 30, y: 0 },
    ])
  })

  it('une com B invertido quando o fim de A toca o fim de B', () => {
    const b: Pt[] = [
      { x: 30, y: 0 },
      { x: 20, y: 0 },
    ]
    expect(mergeLines(line, b, 0.01)).toEqual([
      { x: 0, y: 0 },
      { x: 10, y: 0 },
      { x: 20, y: 0 },
      { x: 30, y: 0 },
    ])
  })

  it('arcos sem extremidade comum são recusados', () => {
    const b: Pt[] = [
      { x: 100, y: 100 },
      { x: 110, y: 100 },
    ]
    expect(() => mergeLines(line, b, 0.01)).toThrow('arcos sem extremidade comum')
  })
})

describe('geometry — WKT de pré-visualização (7 casas decimais)', () => {
  it('POINT e LINESTRING no formato do espelho', () => {
    expect(toPointWkt({ x: 1.5, y: -2.25 })).toBe('POINT(1.5000000 -2.2500000)')
    expect(toLineWkt(line)).toBe(
      'LINESTRING(0.0000000 0.0000000, 10.0000000 0.0000000, 20.0000000 0.0000000)',
    )
  })

  it('linha com menos de 2 vértices é recusada', () => {
    expect(() => toLineWkt([{ x: 0, y: 0 }])).toThrow('linha exige ao menos 2 vertices')
  })

  it('polígono exige anel fechado com pelo menos 4 pontos', () => {
    const ring: Pt[] = [
      { x: 0, y: 0 },
      { x: 1, y: 0 },
      { x: 1, y: 1 },
      { x: 0, y: 0 },
    ]
    expect(toPolygonWkt(ring)).toBe(
      'POLYGON((0.0000000 0.0000000, 1.0000000 0.0000000, 1.0000000 1.0000000, 0.0000000 0.0000000))',
    )
    expect(() => toPolygonWkt(ring.slice(0, 3))).toThrow('anel de poligono')
    expect(() =>
      toPolygonWkt([
        { x: 0, y: 0 },
        { x: 1, y: 0 },
        { x: 1, y: 1 },
        { x: 0, y: 1 },
      ]),
    ).toThrow('anel de poligono')
  })
})

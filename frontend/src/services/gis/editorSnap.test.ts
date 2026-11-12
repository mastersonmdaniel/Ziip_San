import { describe, expect, it } from 'vitest'
import {
  polylinesFromGeoJsonGeometry,
  snapPoint,
  type SnapTarget,
} from './editorSnap'
import type { Pt } from '../../utils/geometry'

// Alvos de snap: um arco horizontal de (0,0) a (10,0) e um vertice
// isolado em (20,5) (ponto de outra feicao).
const TARGETS: SnapTarget[] = [
  { points: [{ x: 0, y: 0 }, { x: 10, y: 0 }] },
  { points: [{ x: 20, y: 5 }] },
]

describe('snapPoint — snap a vertices (E2-SNAP)', () => {
  it('clique perto de um vertice encaixa exactamente no vertice', () => {
    const r = snapPoint({ x: 0.4, y: 0.3 }, TARGETS, 0.75)
    expect(r.kind).toBe('vertex')
    expect(r.point).toEqual({ x: 0, y: 0 })
  })

  it('vertice ganha a borda quando ambos estao dentro da tolerancia', () => {
    // (9.8, 0.2) esta a ~0.28 do vertice (10,0) e a 0.2 da borda:
    // a prioridade e do vertice, nao da projecao mais curta.
    const r = snapPoint({ x: 9.8, y: 0.2 }, TARGETS, 0.75)
    expect(r.kind).toBe('vertex')
    expect(r.point).toEqual({ x: 10, y: 0 })
  })

  it('vertice isolado (ponto de outra feicao) tambem e alvo', () => {
    const r = snapPoint({ x: 20.3, y: 4.8 }, TARGETS, 0.75)
    expect(r.kind).toBe('vertex')
    expect(r.point).toEqual({ x: 20, y: 5 })
  })
})

describe('snapPoint — snap a bordas (E2-SNAP)', () => {
  it('clique perto do meio de um segmento encaixa na projecao sobre o segmento', () => {
    const r = snapPoint({ x: 5, y: 0.5 }, TARGETS, 0.75)
    expect(r.kind).toBe('edge')
    expect(r.point).toEqual({ x: 5, y: 0 })
  })

  it('projecao cai dentro do segmento (nao na extensao da recta)', () => {
    const r = snapPoint({ x: 12, y: 0.4 }, TARGETS, 3)
    expect(r.kind).toBe('vertex')
    expect(r.point).toEqual({ x: 10, y: 0 })
  })
})

describe('snapPoint — sem snap', () => {
  it('fora da tolerancia devolve o ponto original e kind null', () => {
    const at: Pt = { x: 5, y: 4 }
    const r = snapPoint(at, TARGETS, 0.75)
    expect(r.kind).toBeNull()
    expect(r.point).toEqual(at)
    expect(r.distance).toBeNull()
  })

  it('sem alvos nao ha snap', () => {
    const r = snapPoint({ x: 1, y: 1 }, [], 10)
    expect(r.kind).toBeNull()
    expect(r.point).toEqual({ x: 1, y: 1 })
  })

  it('tolerancia zero ou negativa nunca encaixa (nem no proprio vertice)', () => {
    expect(snapPoint({ x: 0, y: 0 }, TARGETS, 0).kind).toBeNull()
    expect(snapPoint({ x: 0, y: 0 }, TARGETS, -1).kind).toBeNull()
  })
})

describe('polylinesFromGeoJsonGeometry — alvos das feicoes renderizadas', () => {
  it('Point vira polilinha de um vertice', () => {
    expect(
      polylinesFromGeoJsonGeometry({ type: 'Point', coordinates: [-48.5, -15.8] }),
    ).toEqual([[{ x: -48.5, y: -15.8 }]])
  })

  it('LineString vira uma polilinha com os vertices na ordem', () => {
    expect(
      polylinesFromGeoJsonGeometry({
        type: 'LineString',
        coordinates: [[0, 0], [3, 4], [6, 4]],
      }),
    ).toEqual([[{ x: 0, y: 0 }, { x: 3, y: 4 }, { x: 6, y: 4 }]])
  })

  it('Polygon devolve o anel exterior como polilinha', () => {
    const out = polylinesFromGeoJsonGeometry({
      type: 'Polygon',
      coordinates: [[[0, 0], [4, 0], [4, 4], [0, 0]]],
    })
    expect(out).toEqual([[{ x: 0, y: 0 }, { x: 4, y: 0 }, { x: 4, y: 4 }, { x: 0, y: 0 }]])
  })

  it('MultiLineString devolve uma polilinha por linha', () => {
    const out = polylinesFromGeoJsonGeometry({
      type: 'MultiLineString',
      coordinates: [[[0, 0], [1, 1]], [[2, 2], [3, 3]]],
    })
    expect(out).toHaveLength(2)
    expect(out[1]).toEqual([{ x: 2, y: 2 }, { x: 3, y: 3 }])
  })

  it('geometria nula ou desconhecida devolve vazio (sem inventar alvos)', () => {
    expect(polylinesFromGeoJsonGeometry(null)).toEqual([])
    expect(polylinesFromGeoJsonGeometry(undefined)).toEqual([])
    expect(polylinesFromGeoJsonGeometry({ type: 'Circle', coordinates: [0, 0] })).toEqual([])
  })

  it('coordenadas malformadas sao ignoradas, nao corrigidas', () => {
    expect(
      polylinesFromGeoJsonGeometry({
        type: 'LineString',
        coordinates: [[0, 0], ['x', 1], [2, 2]],
      }),
    ).toEqual([[{ x: 0, y: 0 }, { x: 2, y: 2 }]])
  })
})

import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@services/api', () => ({ api: { get: vi.fn(), post: vi.fn() } }))

import { api } from '@services/api'
import { changeSets, geometryItemInput } from './changeSets'

const post = vi.mocked(api.post)

beforeEach(() => {
  vi.clearAllMocks()
  post.mockResolvedValue({ data: {} } as never)
})

describe('geometryItemInput — regra da geometria proposta (V13_9)', () => {
  it('linha de gis_arc vai em proposedGeomWkt', () => {
    const input = geometryItemInput({
      featureTable: 'gis_arc',
      featureId: 'a1',
      operation: 'UPDATE',
      geomWkt: 'LINESTRING(0 0, 1 1)',
    })
    expect(input).toEqual({
      operation: 'UPDATE',
      featureTable: 'gis_arc',
      featureId: 'a1',
      proposedState: {},
      proposedGeomWkt: 'LINESTRING(0 0, 1 1)',
      proposedGeomPointWkt: null,
    })
  })

  it('ponto de gis_structure vai em proposedGeomPointWkt (coluna geom_point)', () => {
    const input = geometryItemInput({
      featureTable: 'gis_structure',
      featureId: 's1',
      operation: 'UPDATE',
      geomWkt: 'POINT(1 2)',
    })
    expect(input.proposedGeomWkt).toBeNull()
    expect(input.proposedGeomPointWkt).toBe('POINT(1 2)')
  })

  it('ponto de gis_node vai em proposedGeomWkt (a geom do no e o ponto)', () => {
    const input = geometryItemInput({
      featureTable: 'gis_node',
      featureId: 'n1',
      operation: 'UPDATE',
      geomWkt: 'POINT(1 2)',
    })
    expect(input.proposedGeomWkt).toBe('POINT(1 2)')
    expect(input.proposedGeomPointWkt).toBeNull()
  })

  it('estado proposto do utilizador atravessa intacto', () => {
    const input = geometryItemInput({
      featureTable: 'gis_arc',
      featureId: 'a1',
      operation: 'UPDATE',
      geomWkt: 'LINESTRING(0 0, 1 1)',
      proposedState: { material: 'PVC' },
    })
    expect(input.proposedState).toEqual({ material: 'PVC' })
  })

  it('tabela fora das tabelas da GIS-04 e recusada no cliente', () => {
    expect(() =>
      geometryItemInput({
        featureTable: 'gis_hydrant',
        featureId: 'x1',
        operation: 'UPDATE',
        geomWkt: 'POINT(1 2)',
      }),
    ).toThrow(/tabela/i)
  })

  it('operacao fora de INSERT/UPDATE/RETIRE e recusada no cliente', () => {
    expect(() =>
      geometryItemInput({
        featureTable: 'gis_arc',
        featureId: 'a1',
        operation: 'DELETE',
        geomWkt: 'LINESTRING(0 0, 1 1)',
      }),
    ).toThrow(/operação/i)
  })
})

describe('changeSets — lado do autor (GIS-04)', () => {
  it('create posta para a coleccao com o corpo dado', async () => {
    post.mockResolvedValueOnce({ data: { id: 'cs1' } } as never)
    const out = await changeSets.create({ type: 'CORRECTION', channel: 'WEB' })
    expect(out).toEqual({ id: 'cs1' })
    expect(post).toHaveBeenCalledWith('/gis/change-sets', {
      type: 'CORRECTION',
      channel: 'WEB',
    })
  })

  it('addItem posta o item no pacote certo', async () => {
    const input = geometryItemInput({
      featureTable: 'gis_arc',
      featureId: 'a1',
      operation: 'UPDATE',
      geomWkt: 'LINESTRING(0 0, 1 1)',
    })
    await changeSets.addItem('cs1', input)
    expect(post).toHaveBeenCalledWith('/gis/change-sets/cs1/items', input)
  })

  it('submit posta a submissao (revisao por outra pessoa)', async () => {
    await changeSets.submit('cs1')
    expect(post).toHaveBeenCalledWith('/gis/change-sets/cs1/submit', {})
  })
})

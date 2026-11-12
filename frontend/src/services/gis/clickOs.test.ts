import { describe, expect, it } from 'vitest'
import { clickTargetForFeature } from '@services/gis/clickOs'

describe('clickTargetForFeature — alvo do clique II-1', () => {
  it('nó da rede vira alvo por nodeId', () => {
    expect(clickTargetForFeature('gis_node', 'n1')).toEqual({ arcId: null, nodeId: 'n1' })
  })

  it('arco da rede vira alvo por arcId', () => {
    expect(clickTargetForFeature('gis_arc', 'a1')).toEqual({ arcId: 'a1', nodeId: null })
  })

  it('outras tabelas com ficha não são alvo de clique', () => {
    for (const table of ['gis_connection', 'gis_structure', 'gis_zone', 'gis_poi']) {
      expect(clickTargetForFeature(table, 'x1')).toBeNull()
    }
  })

  it('id vazio não é alvo', () => {
    expect(clickTargetForFeature('gis_node', '')).toBeNull()
  })
})

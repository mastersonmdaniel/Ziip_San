import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@services/api', () => ({ api: { request: vi.fn() } }))

import { api } from '@services/api'
import { buildAttributesSchema, gisCatalogApi } from './catalog'

const request = vi.mocked(api.request)

beforeEach(() => {
  vi.clearAllMocks()
  request.mockResolvedValue({ data: [], status: 200, statusText: '', headers: {}, config: { headers: {} } } as never)
})

describe('buildAttributesSchema', () => {
  it('monta properties, required e ignora linha sem nome', () => {
    expect(
      buildAttributesSchema([
        { name: 'diametro', type: 'integer', required: true },
        { name: 'material', type: 'string', required: false },
        { name: '  ', type: 'number', required: true },
      ]),
    ).toBe(
      '{"type":"object","properties":{"diametro":{"type":"integer"},"material":{"type":"string"}},"required":["diametro"]}',
    )
  })

  it('schema vazio vira objeto vazio', () => {
    expect(buildAttributesSchema([])).toBe('{"type":"object","properties":{},"required":[]}')
  })
})

describe('gisCatalogApi', () => {
  it('lista tipos com filtros e cria tipo com o schema montado', async () => {
    request.mockResolvedValueOnce({ data: [], status: 200, statusText: '', headers: {}, config: { headers: {} } } as never)
    await gisCatalogApi.listTypes({ q: 'UTS', usable: true })
    expect(request).toHaveBeenCalledWith({
      method: 'get',
      url: '/gis/catalog/types',
      params: { q: 'UTS', usable: 'true' },
    })

    request.mockResolvedValueOnce({ data: { id: '1' }, status: 201, statusText: '', headers: {}, config: { headers: {} } } as never)
    await gisCatalogApi.createType({
      classId: 'c1',
      code: 'NOVO_TIPO',
      label: 'Tipo novo',
      attributesSchema: buildAttributesSchema([{ name: 'x', type: 'number', required: false }]),
    })
    expect(request).toHaveBeenCalledWith({
      method: 'post',
      url: '/gis/catalog/types',
      data: expect.objectContaining({ code: 'NOVO_TIPO', attributesSchema: expect.stringContaining('"x"') }),
    })
  })

  it('aprova, recusa e ativa pela rota certa', async () => {
    await gisCatalogApi.approveType('t1')
    expect(request).toHaveBeenCalledWith({ method: 'post', url: '/gis/catalog/types/t1/approve', data: undefined })
    await gisCatalogApi.rejectItem('i1')
    expect(request).toHaveBeenCalledWith({ method: 'post', url: '/gis/catalog/items/i1/reject', data: undefined })
    await gisCatalogApi.setItemActive('i1', false)
    expect(request).toHaveBeenCalledWith({ method: 'patch', url: '/gis/catalog/items/i1/active', data: { active: false } })
  })

  it('relatório aponta para a rota de referência a validar', async () => {
    request.mockResolvedValueOnce({
      data: { factoryItemsStandard: 0, normsPurchasePending: true },
      status: 200, statusText: '', headers: {}, config: { headers: {} },
    } as never)
    const report = await gisCatalogApi.referenceReport()
    expect(request).toHaveBeenCalledWith({
      method: 'get',
      url: '/gis/catalog/report/reference-to-validate',
      params: undefined,
    })
    expect(report.factoryItemsStandard).toBe(0)
    expect(report.normsPurchasePending).toBe(true)
  })
})


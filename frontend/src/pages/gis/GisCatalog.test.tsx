import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({
  listClasses: vi.fn(),
  listTypes: vi.fn(),
  listItems: vi.fn(),
  listMaterials: vi.fn(),
  referenceReport: vi.fn(),
  createType: vi.fn(),
  approveType: vi.fn(),
  rejectType: vi.fn(),
  setTypeActive: vi.fn(),
  createItem: vi.fn(),
  approveItem: vi.fn(),
  rejectItem: vi.fn(),
  setItemActive: vi.fn(),
}))

vi.mock('@services/gis/catalog', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@services/gis/catalog')>()
  return { ...actual, gisCatalogApi: api }
})

import { GisCatalog } from './GisCatalog'

const PIPE = { id: 'c1', code: 'PIPE', domain: 'WATER', topologyRole: 'ARC', geometryType: 'LineStringZ', origin: 'FACTORY', active: true }
const PENDING_TYPE = {
  id: 't1', classId: 'c1', classCode: 'PIPE', code: 'TIPO_NOVO', label: 'Tipo novo',
  attributesSchema: '{}', origin: 'TENANT', factoryCode: null, factoryRetiredOn: null,
  active: false, approvalStatus: 'PENDING', proposedBy: 'u1', approvedBy: null, approvedAt: null,
  requiresCatalogItem: false,
}
const APPROVED_TYPE = { ...PENDING_TYPE, id: 't2', code: 'UTS', label: 'Unidade de tratamento simplificado (UTS)', origin: 'FACTORY', active: true, approvalStatus: 'APPROVED' }

beforeEach(() => {
  vi.clearAllMocks()
  api.listClasses.mockResolvedValue([PIPE])
  api.listTypes.mockResolvedValue([PENDING_TYPE, APPROVED_TYPE])
  api.listItems.mockResolvedValue([])
  api.listMaterials.mockResolvedValue([])
  api.referenceReport.mockResolvedValue({
    factoryTypes: 60, factoryItems: 261, factoryItemsToValidate: 261, factoryItemsStandard: 0,
    factoryTypesRetired: 0, factoryItemsRetired: 0, tenantTypesPending: 1, tenantItemsPending: 0,
    normsPurchasePending: true, note: 'Compra P1.28 pendente.',
  })
  api.createType.mockImplementation(async (input: unknown) => ({ ...PENDING_TYPE, ...(input as object) }))
  api.approveType.mockResolvedValue({ ...PENDING_TYPE, approvalStatus: 'APPROVED', active: true })
})

describe('GisCatalog', () => {
  it('lista tipos com situação e aprova o pendente', async () => {
    const user = userEvent.setup()
    render(<GisCatalog />)
    await waitFor(() => expect(screen.getByText('TIPO_NOVO')).toBeInTheDocument())
    expect(screen.getByText('UTS')).toBeInTheDocument()
    const approve = screen.getByRole('button', { name: 'Aprovar' })
    await user.click(approve)
    expect(api.approveType).toHaveBeenCalledWith('t1')
  })

  it('cria tipo com o schema montado pelo formulário', async () => {
    const user = userEvent.setup()
    render(<GisCatalog />)
    await waitFor(() => expect(screen.getByText('TIPO_NOVO')).toBeInTheDocument())
    await user.selectOptions(screen.getByLabelText('Classe'), 'c1')
    await user.type(screen.getByLabelText('Código do tipo'), 'novo_tipo')
    await user.type(screen.getByLabelText('Rótulo do tipo'), 'Tipo de teste')
    await user.type(screen.getByLabelText('Nome do atributo 1'), 'diametro')
    await user.selectOptions(screen.getByLabelText('Tipo do atributo 1'), 'integer')
    await user.click(screen.getByRole('button', { name: 'Criar tipo (fica pendente)' }))
    await waitFor(() =>
      expect(api.createType).toHaveBeenCalledWith(expect.objectContaining({
        code: 'NOVO_TIPO',
        attributesSchema: expect.stringContaining('"diametro":{"type":"integer"}'),
      })),
    )
  })

  it('relatório mostra zero STANDARD e a ressalva da compra pendente', async () => {
    const user = userEvent.setup()
    render(<GisCatalog />)
    await waitFor(() => expect(screen.getByText('TIPO_NOVO')).toBeInTheDocument())
    await user.click(screen.getByRole('tab', { name: 'Relatório' }))
    await waitFor(() => expect(screen.getByText('Compra P1.28 pendente.')).toBeInTheDocument())
    const section = screen.getByLabelText('Relatório de referência a validar')
    // Itens de fábrica e itens a validar: ambos 261 (nenhum STANDARD).
    expect(within(section).getAllByText('261')).toHaveLength(2)
  })
})

import { describe, expect, it } from 'vitest'
import { EMPTY_ADDRESS } from '@components/registry/addressForm'
import {
  apiPathToFormPath,
  emptyWizardValues,
  stepOfField,
  stepPayload,
  stepSchema,
  stepSlice,
  toConsumerUnitInput,
  type StepContext,
  type WizardValues,
} from './propertyForm'

const ctx: StepContext = { useBlockFace: false, requireReadingSequence: false, sewerRequiresDischarge: false }
const lv = (id: string, code?: number) => ({ id, label: id, code: code ?? null })

function validate(step: number, values: WizardValues, c: StepContext = ctx) {
  return stepSchema(step, c).safeParse(stepSlice(step, values))
}

function messages(step: number, values: WizardValues, c: StepContext = ctx): string[] {
  const r = validate(step, values, c)
  return r.success ? [] : r.error.issues.map((i) => `${i.path.join('.')}: ${i.message}`)
}

function filled(): WizardValues {
  return {
    ...emptyWizardValues(),
    localityId: lv('loc', 1),
    sectorId: lv('sec', 2),
    blockId: lv('blk', 3),
    lot: '4',
    sublot: '5',
    address: { ...EMPTY_ADDRESS, streetName: 'TESTE Rua', number: 'S/N' },
    customers: [{ customerId: lv('cus'), relation: 'USER', startDate: '2024-01-10', showOnBill: true }],
    subcategories: [{ subcategoryId: lv('sub'), economies: '2', privateUnits: '', collectiveUnits: '', activityBranchIds: [] }],
    builtAreaM2: '80,5',
    waterStatusId: lv('ws'),
    sewerStatusId: lv('ss'),
    profileId: lv('pf'),
  }
}

describe('validação por etapa do assistente (regras do relatório 07, S2)', () => {
  it('etapa 1: campos obrigatórios e faixas de lote e sublote', () => {
    const empty = messages(0, emptyWizardValues())
    expect(empty.some((m) => m.startsWith('localityId'))).toBe(true)
    expect(empty.some((m) => m.startsWith('lot'))).toBe(true)
    expect(messages(0, { ...filled(), lot: '10000' })).toContain('lot: O máximo é 9999.')
    expect(messages(0, { ...filled(), lot: '0' })).toContain('lot: O mínimo é 1.')
    expect(messages(0, { ...filled(), sublot: '1000' })).toContain('sublot: O máximo é 999.')
    expect(messages(0, { ...filled(), sublot: '0' })).toEqual([]) // sublote 0 é permitido
    expect(messages(0, filled())).toEqual([])
  })

  it('etapa 1: sequência na rota e face da quadra obrigatórias só quando o tenant exige', () => {
    expect(messages(0, filled())).toEqual([])
    const strict: StepContext = { ...ctx, requireReadingSequence: true, useBlockFace: true }
    const m = messages(0, filled(), strict)
    expect(m.some((x) => x.startsWith('readingSequence'))).toBe(true)
    expect(m.some((x) => x.startsWith('blockFaceId'))).toBe(true)
  })

  it('etapa 2: número aceita "S/N"; logradouro e número são obrigatórios; coordenada exige o par', () => {
    expect(messages(1, filled())).toEqual([])
    expect(messages(1, { ...filled(), address: { ...filled().address, number: '' } })).toContain('address.number: Campo obrigatório.')
    expect(messages(1, { ...filled(), address: { ...filled().address, cep: '1234' } })).toContain('address.cep: CEP inválido: informe 8 dígitos.')
    expect(
      messages(1, { ...filled(), address: { ...filled().address, coordinates: { lat: '-9,97', lng: '' } } }).some((m) => m.startsWith('address.coordinates.lat')),
    ).toBe(true)
    expect(
      messages(1, { ...filled(), address: { ...filled().address, coordinates: { lat: '48,85', lng: '2,35' } } }).some((m) => m.includes('fora do Brasil')),
    ).toBe(true)
  })

  it('etapa 3: precisa de um Usuário e de exatamente um nome na conta', () => {
    const noUser = { ...filled(), customers: [{ customerId: lv('c'), relation: 'OWNER' as const, startDate: '2024-01-10', showOnBill: true }] }
    expect(messages(2, noUser).join('|')).toContain('ao menos um cliente com a relação Usuário')
    const twoOnBill = {
      ...filled(),
      customers: [
        { customerId: lv('a'), relation: 'USER' as const, startDate: '2024-01-10', showOnBill: true },
        { customerId: lv('b'), relation: 'OWNER' as const, startDate: '2024-01-10', showOnBill: true },
      ],
    }
    expect(messages(2, twoOnBill).join('|')).toContain('exatamente um cliente como o nome na conta')
    expect(messages(2, filled())).toEqual([])
  })

  it('etapa 3: Responsável exige o modo de envio da conta; data futura é recusada', () => {
    const responsible = {
      ...filled(),
      customers: [
        { customerId: lv('a'), relation: 'USER' as const, startDate: '2024-01-10', showOnBill: true },
        { customerId: lv('b'), relation: 'RESPONSIBLE' as const, startDate: '2024-01-10', showOnBill: false },
      ],
    }
    expect(messages(2, responsible).join('|')).toContain('modo de envio da conta é obrigatório')
    expect(messages(2, { ...responsible, deliveryModeId: lv('mode') })).toEqual([])
    const future = { ...filled(), customers: [{ customerId: lv('a'), relation: 'USER' as const, startDate: '2999-01-01', showOnBill: true }] }
    expect(messages(2, future).join('|')).toContain('A data não pode ser futura.')
  })

  it('etapa 4: subcategoria obrigatória, sem repetir, economias >= 1', () => {
    const dup = {
      ...filled(),
      subcategories: [
        { subcategoryId: lv('sub'), economies: '1', privateUnits: '', collectiveUnits: '', activityBranchIds: [] },
        { subcategoryId: lv('sub'), economies: '1', privateUnits: '', collectiveUnits: '', activityBranchIds: [] },
      ],
    }
    expect(messages(3, dup)).toContain('subcategories.1.subcategoryId: Subcategoria repetida.')
    expect(messages(3, { ...filled(), subcategories: [{ subcategoryId: lv('s'), economies: '0', privateUnits: '', collectiveUnits: '', activityBranchIds: [] }] })).toContain('subcategories.0.economies: O mínimo é 1.')
  })

  it('etapa 5: esgotamento obrigatório só se a situação de esgoto fatura', () => {
    expect(messages(4, filled())).toEqual([])
    expect(messages(4, filled(), { ...ctx, sewerRequiresDischarge: true }).some((m) => m.startsWith('sewerDischargeId'))).toBe(true)
    expect(messages(4, { ...filled(), builtAreaM2: '' }).some((m) => m.startsWith('builtAreaM2'))).toBe(true)
    expect(messages(4, { ...filled(), builtAreaM2: 'abc' })).toContain('builtAreaM2: Informe um número, por exemplo 12,5.')
  })

  it('etapa 6: observações até 750 caracteres', () => {
    expect(messages(5, { ...filled(), notes: 'x'.repeat(751) })).toContain('notes: No máximo 750 caracteres.')
    expect(messages(5, { ...filled(), notes: 'x'.repeat(750) })).toEqual([])
  })
})

describe('payload da API', () => {
  it('monta ids, números e coordenadas no contrato (zeros à esquerda não existem no payload)', () => {
    const values: WizardValues = {
      ...filled(),
      lot: '0004',
      readingSequence: '12',
      lotFrontageM: '8,5',
      address: { ...filled().address, cep: '01310100', coordinates: { lat: '-23,550000', lng: '-46,630000' } },
      iptu: ' 123 ',
    }
    const input = toConsumerUnitInput(values)
    expect(input.localityId).toBe('loc')
    expect(input.lot).toBe(4)
    expect(input.sublot).toBe(5)
    expect(input.readingSequence).toBe(12)
    expect(input.lotFrontageM).toBe(8.5)
    expect(input.address.cep).toBe('01310100')
    expect(input.address.coordinates).toEqual({ lat: -23.55, lng: -46.63 })
    expect(input.builtAreaM2).toBe(80.5)
    expect(input.externalIds).toEqual({ iptu: '123' })
    expect(input.physical).toBeNull()
    expect(stepPayload(2, values)).toEqual({
      customers: [{ customerId: 'cus', relation: 'USER', startDate: '2024-01-10', showOnBill: true }],
      deliveryModeId: null,
    })
  })

  it('leva erros do servidor à etapa certa e converte o caminho', () => {
    expect(stepOfField('lot')).toBe(0)
    expect(stepOfField('address.number')).toBe(1)
    expect(stepOfField('customers[0].customerId')).toBe(2)
    expect(stepOfField('subcategories[1].economies')).toBe(3)
    expect(stepOfField('externalIds.iptu')).toBe(5)
    expect(stepOfField('campoDesconhecido')).toBeNull()
    expect(apiPathToFormPath('customers[0].customerId')).toBe('customers.0.customerId')
    expect(apiPathToFormPath('externalIds.iptu')).toBe('iptu')
  })
})

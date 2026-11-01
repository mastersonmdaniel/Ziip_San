/**
 * FIXTURES DE TESTE. Nada deste arquivo é dado real nem é usado fora dos testes.
 * Os valores são obviamente falsos (prefixo "TESTE") e os documentos já estão mascarados.
 */
import type {
  ConsumerUnit,
  ConsumerUnitSummary,
  Customer,
  CustomerSummary,
  HistoryEvent,
  Page,
  UnitCustomerLink,
} from '@services/registry/types'

export function page<T>(content: T[]): Page<T> {
  return { content, page: 0, size: 25, totalElements: content.length, totalPages: content.length === 0 ? 0 : 1 }
}

export const TEST_USER = {
  id: 'u-test',
  username: 'teste',
  email: 'teste@example.invalid',
  fullName: 'TESTE Usuário',
  roles: [],
  tenantId: 'tenant_teste',
}

export const TEST_UNIT_SUMMARY: ConsumerUnitSummary = {
  id: 'unit-1',
  registration: '1234567',
  registrationDv: '9',
  inscription: { locality: 1, sector: 2, block: 3, lot: 4, sublot: 5 },
  userName: 'TESTE Cliente Um',
  userDocumentMasked: '***.TST.000-**',
  addressText: 'TESTE Rua Um, 10',
  mainCategoryName: 'TESTE Categoria',
  waterStatusName: 'TESTE Água Ligada',
  sewerStatusName: 'TESTE Esgoto Ligado',
  economies: 1,
  deleted: false,
}

export const TEST_UNIT: ConsumerUnit = {
  id: 'unit-1',
  version: 3,
  registration: '1234567',
  registrationDv: '9',
  inscription: { locality: 1, sector: 2, block: 3, lot: 4, sublot: 5 },
  status: 'ACTIVE',
  deletedAt: null,
  deletedReason: null,
  locality: { id: 'loc-1', code: 1, name: 'TESTE Localidade' },
  sector: { id: 'sec-1', code: 2, name: 'TESTE Setor' },
  block: { id: 'blk-1', code: 3, name: 'TESTE Quadra' },
  blockFace: null,
  lot: 4,
  sublot: 5,
  readingSequence: null,
  lotFrontageM: null,
  address: { cep: '00000000', streetName: 'TESTE Rua Um', number: '10', complement: null, neighborhood: 'TESTE Bairro', city: 'TESTE Cidade', state: 'SP', coordinates: null },
  addressText: 'TESTE Rua Um, 10',
  subcategories: [{ subcategoryId: 'sub-1', subcategory: { id: 'sub-1', name: 'TESTE Subcategoria' }, category: { id: 'cat-1', name: 'TESTE Categoria' }, economies: 1, privateUnits: null, collectiveUnits: null, activityBranchIds: [] }],
  mainCategory: { id: 'cat-1', name: 'TESTE Categoria' },
  mainSubcategory: { id: 'sub-1', name: 'TESTE Subcategoria' },
  economies: 1,
  builtAreaM2: 80,
  builtAreaRange: null,
  waterStatus: { id: 'ws-1', name: 'TESTE Água Ligada' },
  sewerStatus: { id: 'ss-1', name: 'TESTE Esgoto Ligado' },
  sewerDischarge: null,
  profile: { id: 'pf-1', name: 'TESTE Perfil' },
  waterSource: null,
  wellType: null,
  pavementStreet: null,
  pavementSidewalk: null,
  physical: null,
  usagePoints: null,
  residents: null,
  externalIds: null,
  deliveryMode: null,
  deliveryRoute: null,
  deliverySequence: null,
  altRoute: null,
  parentUnit: null,
  notes: null,
}

export const TEST_LINKS: UnitCustomerLink[] = [
  { id: 'l-1', customerId: 'cus-1', customerName: 'TESTE Cliente Um', customerDocumentMasked: '***.TST.000-**', relation: 'USER', startDate: '2024-01-10', endDate: null, endReason: null, showOnBill: true },
  { id: 'l-2', customerId: 'cus-2', customerName: 'TESTE Cliente Dois', customerDocumentMasked: null, relation: 'OWNER', startDate: '2020-01-10', endDate: '2023-12-31', endReason: 'TESTE motivo', showOnBill: false },
]

export const TEST_HISTORY: HistoryEvent[] = [
  { id: 'h-1', occurredAt: '2024-03-01T10:30:00', type: 'STATUS', title: 'Situação de água alterada', actor: 'TESTE Operador', changes: [{ field: 'Situação de água', from: 'TESTE A', to: 'TESTE B' }] },
]

export const TEST_CUSTOMER_SUMMARY: CustomerSummary = {
  id: 'cus-1',
  name: 'TESTE Cliente Um',
  personType: 'PF',
  customerTypeName: 'TESTE Tipo',
  documentMasked: '***.TST.000-**',
  city: 'TESTE Cidade',
  unitCount: 1,
  deleted: false,
}

export const TEST_CUSTOMER: Customer = {
  id: 'cus-1',
  version: 2,
  personType: 'PF',
  name: 'TESTE Cliente Um',
  tradeName: null,
  customerType: { id: 'ct-1', name: 'TESTE Tipo' },
  documentMasked: '***.TST.000-**',
  birthDate: null,
  activityBranch: null,
  parentCustomer: null,
  emails: [],
  phones: [],
  addresses: [{ address: { cep: null, streetName: 'TESTE Rua Um', number: '10', complement: null, neighborhood: null, city: 'TESTE Cidade', state: 'SP', coordinates: null }, correspondence: true }],
  billing: { dueDay: null, nextMonthDue: false, advanceInvoice: false, blockNegativation: false },
  unitCount: 1,
  deletedAt: null,
}

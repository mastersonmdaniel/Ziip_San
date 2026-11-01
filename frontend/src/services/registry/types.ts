/**
 * Tipos do contrato REST do cadastro comercial (relatório 07, seções 6.2 e 6.3).
 * Nomes de campo em inglês camelCase, como nas tabelas S1-S6. Onde o relatório não define
 * o nome ou o formato, a suposição está marcada com "SUPOSIÇÃO" e listada em
 * docs/frontend-cadastro.md para o backend confirmar.
 */
import type { Inscription } from '@utils/validators/inscription'

export type Uuid = string

/** Toda entidade editável carrega `version` (@Version): vai de volta no PUT. */
export interface Versioned {
  id: Uuid
  version: number
}

/** Referência resumida a outra entidade (para exibir sem nova consulta). */
export interface Ref {
  id: Uuid
  code?: string | number | null
  name: string
}

/** SUPOSIÇÃO: formato da página (estilo Spring Data). O cliente aceita `number` no lugar de `page`. */
export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type FilterValue = string | number | boolean | null | undefined

export interface ListParams {
  q?: string
  page?: number
  size?: number
  /** Ex.: "name,asc" */
  sort?: string
  [filter: string]: FilterValue
}

// ---------------------------------------------------------------------------------------------
// Parâmetros do tenant (SUPOSIÇÃO: GET /api/registry/settings)
// ---------------------------------------------------------------------------------------------

export interface RegistrySettings {
  /** Tenant usa face de quadra (S2 etapa 1: blockFaceId passa a ser obrigatório). */
  useBlockFace: boolean
  /** Sequência na rota é obrigatória (S2 etapa 1: readingSequence). */
  requireReadingSequence: boolean
}

// ---------------------------------------------------------------------------------------------
// Endereço e geolocalização
// ---------------------------------------------------------------------------------------------

export interface Coordinates {
  /** Graus decimais, SIRGAS 2000 (EPSG:4674). */
  lat: number
  lng: number
}

/** Endereço em texto livre + CEP (normalização com logradouro/bairro/município é P1). */
export interface Address {
  cep: string | null
  streetName: string
  number: string
  complement: string | null
  neighborhood: string | null
  city: string | null
  /** UF (2 letras). */
  state: string | null
  referenceTypeId?: Uuid | null
  coordinates?: Coordinates | null
}

/** SUPOSIÇÃO: GET /api/registry/addresses/cep/{cep}. 404 = CEP desconhecido. */
export interface CepLookupResult {
  cep: string
  streetName: string | null
  neighborhood: string | null
  city: string | null
  state: string | null
}

// ---------------------------------------------------------------------------------------------
// Catálogos (S6)
// ---------------------------------------------------------------------------------------------

/** Item genérico para o CRUD dirigido por metadados (catálogos e hierarquia territorial). */
export interface CrudItem extends Versioned {
  [field: string]: unknown
}

/** Item mínimo de catálogo usado em buscas e seletores. */
export interface CatalogLookupItem extends Versioned {
  code?: string | number | null
  name: string
  active?: boolean
  /** Presentes só em alguns catálogos (situações de água/esgoto, subcategorias). */
  billable?: boolean
  category?: Ref
}

/** Campos comuns de um item de catálogo. Os demais campos dependem do catálogo (ver catalogs.ts). */
export interface CatalogItem extends Versioned {
  code?: string | number | null
  name: string
  active: boolean
  [field: string]: unknown
}

export interface Category extends CatalogItem {
  abbreviation: string
  /** Faixa de consumo (parâmetros do módulo de consumo, relatório 02). */
  minimumConsumption: number
  /** Limite de economias acima do qual o servidor pede confirmação. */
  economiesSoftLimit: number | null
  requiresSpecialPermission: boolean
}

export interface Subcategory extends CatalogItem {
  categoryId: Uuid
  category?: Ref
  abbreviation: string | null
  inspectionFactor: number
  seasonalSupply: boolean
}

export interface PropertyProfile extends CatalogItem {
  automaticGeneration: boolean
  blockRectification: boolean
  largeConsumer: boolean
}

export interface WaterStatus extends CatalogItem {
  abbreviation: string
  minimumBillingConsumption: number
  billable: boolean
  hasNetwork: boolean
  hasConnection: boolean
  supplies: boolean
}

export interface SewerStatus extends CatalogItem {
  abbreviation: string
  minimumBillingVolume: number
  billable: boolean
  hasNetwork: boolean
  hasConnection: boolean
}

// ---------------------------------------------------------------------------------------------
// Hierarquia territorial (S5)
// ---------------------------------------------------------------------------------------------

export type NetworkCoverage = 'NONE' | 'PARTIAL' | 'FULL'

export interface Locality extends Versioned {
  code: number
  name: string
  headquarters: boolean
  computerized: boolean
  blocked: boolean
}

export interface CommercialSector extends Versioned {
  localityId: Uuid
  locality?: Ref
  code: number
  name: string
  /** Setor alternativo ativo não aceita imóvel novo. */
  alternative: boolean
  blocked: boolean
}

export interface Block extends Versioned {
  sectorId: Uuid
  sector?: Ref
  code: number
  name?: string | null
  readingRouteId: Uuid | null
  readingRoute?: Ref | null
  waterNetwork: NetworkCoverage
  sewerNetwork: NetworkCoverage
  censusSector: string | null
  autoIncrementLot: boolean
  blocked: boolean
}

export interface BlockFace extends Versioned {
  blockId: Uuid
  code: number
  name?: string | null
}

export interface ReadingRoute extends Versioned {
  localityId: Uuid
  locality?: Ref
  sectorId: Uuid
  sector?: Ref
  code: number
  name: string | null
  /** Limite de imóveis por rota. */
  maxUnits: number
  active: boolean
}

// ---------------------------------------------------------------------------------------------
// Cliente (S4)
// ---------------------------------------------------------------------------------------------

export type PersonType = 'PF' | 'PJ'
export type PhoneType = 'RESIDENTIAL' | 'COMMERCIAL' | 'MOBILE' | 'FAX'

export interface CustomerEmail {
  address: string
  /** Consentimento registrado no módulo LGPD; a tela só preserva o vínculo. */
  consentId?: Uuid | null
}

export interface CustomerPhone {
  type: PhoneType
  /** E.164 (+5511987654321). */
  number: string
  extension: string | null
  contactName: string | null
  primary: boolean
}

export interface CustomerAddress {
  address: Address
  correspondence: boolean
}

export interface CustomerBilling {
  /** 1 a 31. */
  dueDay: number | null
  nextMonthDue: boolean
  advanceInvoice: boolean
  /** Só com permissão (a API decide). */
  blockNegativation: boolean
}

export interface CustomerSummary {
  id: Uuid
  name: string
  personType: PersonType
  customerTypeName: string | null
  /** SEMPRE mascarado pela API: ***.123.456-** */
  documentMasked: string | null
  city: string | null
  unitCount: number
  deleted: boolean
}

export interface Customer extends Versioned {
  personType: PersonType
  name: string
  tradeName: string | null
  customerType: Ref | null
  documentMasked: string | null
  birthDate: string | null
  activityBranch: Ref | null
  parentCustomer: Ref | null
  emails: CustomerEmail[]
  phones: CustomerPhone[]
  addresses: CustomerAddress[]
  billing: CustomerBilling
  unitCount: number
  deletedAt: string | null
}

export interface CustomerInput {
  personType: PersonType
  name: string
  tradeName: string | null
  customerTypeId: Uuid | null
  /** CPF ou CNPJ SEM máscara. Em edição: omitido/vazio mantém o documento atual. */
  document?: string
  /** Motivo para cadastrar sem documento (a API decide se há permissão). */
  documentExemptionReason?: string | null
  birthDate: string | null
  activityBranchId: Uuid | null
  parentCustomerId: Uuid | null
  emails: CustomerEmail[]
  phones: CustomerPhone[]
  addresses: CustomerAddress[]
  billing: CustomerBilling
}

/** SUPOSIÇÃO: POST /api/registry/customers/{id}/reveal-document -> 200. 403 sem permissão. */
export interface RevealDocumentResult {
  document: string
  /** Tempo sugerido pelo servidor; a tela usa o menor entre este e o limite local. */
  expiresInSeconds?: number
}

// ---------------------------------------------------------------------------------------------
// Imóvel / unidade consumidora (S1, S2, S3)
// ---------------------------------------------------------------------------------------------

export type UnitRelation = 'OWNER' | 'USER' | 'RESPONSIBLE'
export type UnitStatus = 'ACTIVE' | 'PROVISIONAL'

export interface ConsumerUnitSummary {
  id: Uuid
  /** Base da matrícula (sem DV) e DV, separados. */
  registration: string
  registrationDv: string
  inscription: Inscription
  userName: string | null
  /** SEMPRE mascarado pela API. */
  userDocumentMasked: string | null
  addressText: string
  mainCategoryName: string | null
  waterStatusName: string | null
  sewerStatusName: string | null
  economies: number
  deleted: boolean
}

export interface UnitSubcategory {
  subcategoryId: Uuid
  subcategory?: Ref
  /** Derivada pelo servidor a partir da subcategoria. */
  category?: Ref
  economies: number
  privateUnits: number | null
  collectiveUnits: number | null
  activityBranchIds: Uuid[]
}

export interface PhysicalCharacteristics {
  garden: boolean | null
  lowerReservoirM3: number | null
  upperReservoirM3: number | null
  poolM3: number | null
}

export interface ConsumerUnit extends Versioned {
  registration: string
  registrationDv: string
  inscription: Inscription
  status: UnitStatus
  deletedAt: string | null
  deletedReason: string | null

  locality: Ref
  sector: Ref
  block: Ref
  blockFace: Ref | null
  lot: number
  sublot: number
  readingSequence: number | null
  lotFrontageM: number | null

  address: Address
  addressText: string

  subcategories: UnitSubcategory[]
  mainCategory: Ref | null
  mainSubcategory: Ref | null
  economies: number

  builtAreaM2: number | null
  /** Faixa derivada pelo servidor. */
  builtAreaRange: string | null
  waterStatus: Ref | null
  sewerStatus: Ref | null
  sewerDischarge: Ref | null
  profile: Ref | null
  waterSource: Ref | null
  wellType: Ref | null
  pavementStreet: Ref | null
  pavementSidewalk: Ref | null
  physical: PhysicalCharacteristics | null

  usagePoints: number | null
  residents: number | null
  externalIds: { iptu?: string | null } | null
  deliveryMode: Ref | null
  deliveryRoute: Ref | null
  deliverySequence: number | null
  altRoute: Ref | null
  parentUnit: Ref | null
  notes: string | null
}

/** Payload de POST/PUT /consumer-units (S2). IDs, nunca objetos. */
export interface ConsumerUnitInput {
  localityId: Uuid
  sectorId: Uuid
  blockId: Uuid
  blockFaceId: Uuid | null
  lot: number
  sublot: number
  readingSequence: number | null
  lotFrontageM: number | null
  address: Address
  customers: UnitCustomerInput[]
  deliveryModeId: Uuid | null
  subcategories: Array<{
    subcategoryId: Uuid
    economies: number
    privateUnits: number | null
    collectiveUnits: number | null
    activityBranchIds: Uuid[]
  }>
  builtAreaM2: number | null
  waterStatusId: Uuid
  sewerStatusId: Uuid
  sewerDischargeId: Uuid | null
  profileId: Uuid
  waterSourceId: Uuid | null
  wellTypeId: Uuid | null
  pavementStreetId: Uuid | null
  pavementSidewalkId: Uuid | null
  physical: PhysicalCharacteristics | null
  usagePoints: number | null
  residents: number | null
  externalIds: { iptu: string | null }
  deliveryRouteId: Uuid | null
  deliverySequence: number | null
  altRouteId: Uuid | null
  parentUnitId: Uuid | null
  notes: string | null
  /** Rascunho a descartar quando o imóvel for gravado. */
  draftId?: Uuid | null
}

export interface UnitCustomerInput {
  customerId: Uuid
  relation: UnitRelation
  /** yyyy-MM-dd */
  startDate: string
  showOnBill: boolean
}

/** Vínculo cliente-imóvel com vigência (aba Clientes da ficha). */
export interface UnitCustomerLink {
  id: Uuid
  customerId: Uuid
  customerName: string
  /** Mascarado pela API. */
  customerDocumentMasked: string | null
  relation: UnitRelation
  startDate: string
  endDate: string | null
  endReason: string | null
  showOnBill: boolean
}

export type HistoryEventType = 'STATUS' | 'INSCRIPTION' | 'AUDIT'

export interface HistoryChange {
  field: string
  from: string | null
  to: string | null
}

/** Evento da aba Histórico (situação, inscrição, auditoria campo a campo). */
export interface HistoryEvent {
  id: Uuid
  occurredAt: string
  type: HistoryEventType
  title: string
  /** Nome do operador (dado de funcionário: acesso restrito pela API). */
  actor: string | null
  changes: HistoryChange[]
}

// ---------------------------------------------------------------------------------------------
// Rascunho do assistente (S2): JSON opaco guardado no servidor
// ---------------------------------------------------------------------------------------------

export interface Draft<TData> {
  id: Uuid
  version: number
  /** Etapa (0-based) em que o usuário parou. */
  step: number
  /** Imóvel que está sendo editado; null na inclusão. */
  targetId: Uuid | null
  data: TData
  updatedAt: string
}

export interface DraftInput<TData> {
  step: number
  targetId: Uuid | null
  data: TData
}

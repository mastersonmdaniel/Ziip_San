import { z } from 'zod'
import { addressSchema, addressToApi, addressToForm, EMPTY_ADDRESS, type AddressFormValue } from '@components/registry/addressForm'
import { refToLookupValue, type LookupValue } from '@components/registry/LookupField'
import type {
  ConsumerUnit,
  ConsumerUnitInput,
  UnitCustomerLink,
  UnitRelation,
} from '@services/registry/types'
import { decimalField, dateField, integerField, lookupField, lookupValueSchema, textField, toDecimalOrNull, toIntOrNull, toTextOrNull } from '@utils/zodFields'

export interface WizardCustomer {
  customerId: LookupValue | null
  relation: UnitRelation
  startDate: string
  showOnBill: boolean
}

export interface WizardSubcategory {
  subcategoryId: LookupValue | null
  economies: string
  privateUnits: string
  collectiveUnits: string
  activityBranchIds: LookupValue[]
}

export interface WizardValues {
  // Etapa 1: Localização
  localityId: LookupValue | null
  sectorId: LookupValue | null
  blockId: LookupValue | null
  blockFaceId: LookupValue | null
  lot: string
  sublot: string
  readingSequence: string
  lotFrontageM: string
  // Etapa 2: Endereço
  address: AddressFormValue
  // Etapa 3: Clientes
  customers: WizardCustomer[]
  deliveryModeId: LookupValue | null
  // Etapa 4: Categorias
  subcategories: WizardSubcategory[]
  // Etapa 5: Características
  builtAreaM2: string
  waterStatusId: LookupValue | null
  sewerStatusId: LookupValue | null
  sewerDischargeId: LookupValue | null
  profileId: LookupValue | null
  waterSourceId: LookupValue | null
  wellTypeId: LookupValue | null
  pavementStreetId: LookupValue | null
  pavementSidewalkId: LookupValue | null
  physical: { garden: boolean; lowerReservoirM3: string; upperReservoirM3: string; poolM3: string }
  // Etapa 6: Conclusão
  usagePoints: string
  residents: string
  iptu: string
  deliveryRouteId: LookupValue | null
  deliverySequence: string
  altRouteId: LookupValue | null
  parentUnitId: LookupValue | null
  notes: string
}

export const STEP_LABELS = ['Localização', 'Endereço', 'Clientes', 'Categorias', 'Características', 'Conclusão'] as const
export const LAST_STEP = STEP_LABELS.length - 1

export function emptyWizardValues(): WizardValues {
  return {
    localityId: null,
    sectorId: null,
    blockId: null,
    blockFaceId: null,
    lot: '',
    sublot: '',
    readingSequence: '',
    lotFrontageM: '',
    address: EMPTY_ADDRESS,
    customers: [{ customerId: null, relation: 'USER', startDate: '', showOnBill: true }],
    deliveryModeId: null,
    subcategories: [{ subcategoryId: null, economies: '1', privateUnits: '', collectiveUnits: '', activityBranchIds: [] }],
    builtAreaM2: '',
    waterStatusId: null,
    sewerStatusId: null,
    sewerDischargeId: null,
    profileId: null,
    waterSourceId: null,
    wellTypeId: null,
    pavementStreetId: null,
    pavementSidewalkId: null,
    physical: { garden: false, lowerReservoirM3: '', upperReservoirM3: '', poolM3: '' },
    usagePoints: '',
    residents: '',
    iptu: '',
    deliveryRouteId: null,
    deliverySequence: '',
    altRouteId: null,
    parentUnitId: null,
    notes: '',
  }
}

/** Campo (primeiro segmento do caminho) -> etapa. Usado para levar o usuário à etapa do erro. */
export const FIELD_STEP: Record<string, number> = {
  localityId: 0,
  sectorId: 0,
  blockId: 0,
  blockFaceId: 0,
  lot: 0,
  sublot: 0,
  readingSequence: 0,
  lotFrontageM: 0,
  address: 1,
  customers: 2,
  deliveryModeId: 2,
  subcategories: 3,
  builtAreaM2: 4,
  waterStatusId: 4,
  sewerStatusId: 4,
  sewerDischargeId: 4,
  profileId: 4,
  waterSourceId: 4,
  wellTypeId: 4,
  pavementStreetId: 4,
  pavementSidewalkId: 4,
  physical: 4,
  usagePoints: 5,
  residents: 5,
  iptu: 5,
  externalIds: 5,
  deliveryRouteId: 5,
  deliverySequence: 5,
  altRouteId: 5,
  parentUnitId: 5,
  notes: 5,
}

/** Caminho da API -> caminho do formulário (poucas chaves diferem). */
export function apiPathToFormPath(path: string): string {
  return path.replace(/\[(\d+)\]/g, '.$1').replace(/^externalIds\.iptu$/, 'iptu')
}

export function stepOfField(path: string): number | null {
  const head = path.replace(/\[.*$/, '').split('.')[0] ?? ''
  return FIELD_STEP[head] ?? null
}

// ---------------------------------------------------------------------------------------------
// Esquemas por etapa
// ---------------------------------------------------------------------------------------------

export interface StepContext {
  /** Tenant usa face de quadra. */
  useBlockFace: boolean
  /** Sequência na rota obrigatória. */
  requireReadingSequence: boolean
  /** A situação de esgoto escolhida fatura: esgotamento passa a ser obrigatório. */
  sewerRequiresDischarge: boolean
}

export function stepSchema(step: number, ctx: StepContext): z.ZodTypeAny {
  switch (step) {
    case 0:
      return z.object({
        localityId: lookupField(true),
        sectorId: lookupField(true),
        blockId: lookupField(true),
        blockFaceId: lookupField(ctx.useBlockFace),
        lot: integerField({ required: true, min: 1, max: 9999 }),
        sublot: integerField({ required: true, min: 0, max: 999 }),
        readingSequence: integerField({ required: ctx.requireReadingSequence, min: 1 }),
        lotFrontageM: decimalField({ min: 0 }),
      })
    case 1:
      return z.object({ address: addressSchema })
    case 2:
      return z
        .object({
          customers: z
            .array(
              z.object({
                customerId: lookupField(true),
                relation: z.enum(['OWNER', 'USER', 'RESPONSIBLE']),
                startDate: dateField({ required: true, notFuture: true }),
                showOnBill: z.boolean(),
              }),
            )
            .min(1, 'Informe ao menos um cliente.'),
          deliveryModeId: lookupValueSchema.nullable(),
        })
        .superRefine((v, ctx2) => {
          if (!v.customers.some((c) => c.relation === 'USER')) {
            ctx2.addIssue({ code: 'custom', path: ['customers', 0, 'relation'], message: 'O imóvel precisa de ao menos um cliente com a relação Usuário.' })
          }
          const onBill = v.customers.filter((c) => c.showOnBill).length
          if (onBill !== 1) {
            ctx2.addIssue({ code: 'custom', path: ['customers', 0, 'showOnBill'], message: 'Marque exatamente um cliente como o nome na conta.' })
          }
          if (v.customers.some((c) => c.relation === 'RESPONSIBLE') && v.deliveryModeId === null) {
            ctx2.addIssue({ code: 'custom', path: ['deliveryModeId'], message: 'Com um cliente Responsável, o modo de envio da conta é obrigatório.' })
          }
        })
    case 3:
      return z
        .object({
          subcategories: z
            .array(
              z.object({
                subcategoryId: lookupField(true),
                economies: integerField({ required: true, min: 1 }),
                privateUnits: integerField({ min: 0 }),
                collectiveUnits: integerField({ min: 0 }),
                activityBranchIds: z.array(lookupValueSchema),
              }),
            )
            .min(1, 'Informe ao menos uma subcategoria.'),
        })
        .superRefine((v, ctx2) => {
          const seen = new Set<string>()
          v.subcategories.forEach((s, i) => {
            const id = s.subcategoryId?.id
            if (!id) return
            if (seen.has(id)) {
              ctx2.addIssue({ code: 'custom', path: ['subcategories', i, 'subcategoryId'], message: 'Subcategoria repetida.' })
            }
            seen.add(id)
          })
        })
    case 4:
      return z.object({
        builtAreaM2: decimalField({ required: true, min: 0 }),
        waterStatusId: lookupField(true),
        sewerStatusId: lookupField(true),
        sewerDischargeId: lookupField(ctx.sewerRequiresDischarge),
        profileId: lookupField(true),
        waterSourceId: lookupValueSchema.nullable(),
        wellTypeId: lookupValueSchema.nullable(),
        pavementStreetId: lookupValueSchema.nullable(),
        pavementSidewalkId: lookupValueSchema.nullable(),
        physical: z.object({
          garden: z.boolean(),
          lowerReservoirM3: decimalField({ min: 0 }),
          upperReservoirM3: decimalField({ min: 0 }),
          poolM3: decimalField({ min: 0 }),
        }),
      })
    case 5:
      return z.object({
        usagePoints: integerField({ min: 0 }),
        residents: integerField({ min: 0 }),
        iptu: textField({ max: 30 }),
        deliveryRouteId: lookupValueSchema.nullable(),
        deliverySequence: integerField({ min: 1 }),
        altRouteId: lookupValueSchema.nullable(),
        parentUnitId: lookupValueSchema.nullable(),
        notes: textField({ max: 750 }),
      })
    default:
      return z.object({})
  }
}

/** Valores relevantes da etapa (recorte do formulário) para validar. */
export function stepSlice(step: number, v: WizardValues): Record<string, unknown> {
  switch (step) {
    case 0:
      return { localityId: v.localityId, sectorId: v.sectorId, blockId: v.blockId, blockFaceId: v.blockFaceId, lot: v.lot, sublot: v.sublot, readingSequence: v.readingSequence, lotFrontageM: v.lotFrontageM }
    case 1:
      return { address: v.address }
    case 2:
      return { customers: v.customers, deliveryModeId: v.deliveryModeId }
    case 3:
      return { subcategories: v.subcategories }
    case 4:
      return {
        builtAreaM2: v.builtAreaM2,
        waterStatusId: v.waterStatusId,
        sewerStatusId: v.sewerStatusId,
        sewerDischargeId: v.sewerDischargeId,
        profileId: v.profileId,
        waterSourceId: v.waterSourceId,
        wellTypeId: v.wellTypeId,
        pavementStreetId: v.pavementStreetId,
        pavementSidewalkId: v.pavementSidewalkId,
        physical: v.physical,
      }
    case 5:
      return { usagePoints: v.usagePoints, residents: v.residents, iptu: v.iptu, deliveryRouteId: v.deliveryRouteId, deliverySequence: v.deliverySequence, altRouteId: v.altRouteId, parentUnitId: v.parentUnitId, notes: v.notes }
    default:
      return {}
  }
}

// ---------------------------------------------------------------------------------------------
// Conversões formulário <-> API
// ---------------------------------------------------------------------------------------------

const id = (v: LookupValue | null): string | null => v?.id ?? null

function physicalToApi(p: WizardValues['physical']): ConsumerUnitInput['physical'] {
  const lower = toDecimalOrNull(p.lowerReservoirM3)
  const upper = toDecimalOrNull(p.upperReservoirM3)
  const pool = toDecimalOrNull(p.poolM3)
  if (!p.garden && lower === null && upper === null && pool === null) return null
  return { garden: p.garden, lowerReservoirM3: lower, upperReservoirM3: upper, poolM3: pool }
}

/** Fatia do payload da API correspondente a uma etapa (usada em validate-step). */
export function stepPayload(step: number, v: WizardValues): Partial<ConsumerUnitInput> {
  switch (step) {
    case 0:
      return {
        localityId: id(v.localityId) ?? '',
        sectorId: id(v.sectorId) ?? '',
        blockId: id(v.blockId) ?? '',
        blockFaceId: id(v.blockFaceId),
        lot: Number(v.lot),
        sublot: Number(v.sublot),
        readingSequence: toIntOrNull(v.readingSequence),
        lotFrontageM: toDecimalOrNull(v.lotFrontageM),
      }
    case 1:
      return { address: addressToApi(v.address) }
    case 2:
      return {
        customers: v.customers.map((c) => ({ customerId: id(c.customerId) ?? '', relation: c.relation, startDate: c.startDate, showOnBill: c.showOnBill })),
        deliveryModeId: id(v.deliveryModeId),
      }
    case 3:
      return {
        subcategories: v.subcategories.map((s) => ({
          subcategoryId: id(s.subcategoryId) ?? '',
          economies: Number(s.economies),
          privateUnits: toIntOrNull(s.privateUnits),
          collectiveUnits: toIntOrNull(s.collectiveUnits),
          activityBranchIds: s.activityBranchIds.map((a) => a.id),
        })),
      }
    case 4:
      return {
        builtAreaM2: toDecimalOrNull(v.builtAreaM2),
        waterStatusId: id(v.waterStatusId) ?? '',
        sewerStatusId: id(v.sewerStatusId) ?? '',
        sewerDischargeId: id(v.sewerDischargeId),
        profileId: id(v.profileId) ?? '',
        waterSourceId: id(v.waterSourceId),
        wellTypeId: id(v.wellTypeId),
        pavementStreetId: id(v.pavementStreetId),
        pavementSidewalkId: id(v.pavementSidewalkId),
        physical: physicalToApi(v.physical),
      }
    case 5:
      return {
        usagePoints: toIntOrNull(v.usagePoints),
        residents: toIntOrNull(v.residents),
        externalIds: { iptu: toTextOrNull(v.iptu) },
        deliveryRouteId: id(v.deliveryRouteId),
        deliverySequence: toIntOrNull(v.deliverySequence),
        altRouteId: id(v.altRouteId),
        parentUnitId: id(v.parentUnitId),
        notes: toTextOrNull(v.notes),
      }
    default:
      return {}
  }
}

/** Payload completo (todas as etapas já validadas). */
export function toConsumerUnitInput(v: WizardValues): ConsumerUnitInput {
  const parts = [0, 1, 2, 3, 4, 5].map((s) => stepPayload(s, v))
  return Object.assign({}, ...parts) as ConsumerUnitInput
}

/** Imóvel carregado da API -> valores do assistente (edição). `links` = vínculos de clientes. */
export function unitToWizardValues(unit: ConsumerUnit, links: UnitCustomerLink[]): WizardValues {
  const base = emptyWizardValues()
  const current = links.filter((l) => l.endDate === null)
  return {
    ...base,
    localityId: refToLookupValue(unit.locality),
    sectorId: refToLookupValue(unit.sector),
    blockId: refToLookupValue(unit.block),
    blockFaceId: refToLookupValue(unit.blockFace),
    lot: String(unit.lot),
    sublot: String(unit.sublot),
    readingSequence: unit.readingSequence === null ? '' : String(unit.readingSequence),
    lotFrontageM: unit.lotFrontageM === null ? '' : String(unit.lotFrontageM).replace('.', ','),
    address: addressToForm(unit.address),
    customers:
      current.length > 0
        ? current.map((l) => ({
            customerId: { id: l.customerId, label: l.customerName },
            relation: l.relation,
            startDate: l.startDate,
            showOnBill: l.showOnBill,
          }))
        : base.customers,
    deliveryModeId: refToLookupValue(unit.deliveryMode),
    subcategories:
      unit.subcategories.length > 0
        ? unit.subcategories.map((s) => ({
            subcategoryId: s.subcategory ? refToLookupValue(s.subcategory) : { id: s.subcategoryId, label: s.subcategoryId },
            economies: String(s.economies),
            privateUnits: s.privateUnits === null ? '' : String(s.privateUnits),
            collectiveUnits: s.collectiveUnits === null ? '' : String(s.collectiveUnits),
            activityBranchIds: [],
          }))
        : base.subcategories,
    builtAreaM2: unit.builtAreaM2 === null ? '' : String(unit.builtAreaM2).replace('.', ','),
    waterStatusId: refToLookupValue(unit.waterStatus),
    sewerStatusId: refToLookupValue(unit.sewerStatus),
    sewerDischargeId: refToLookupValue(unit.sewerDischarge),
    profileId: refToLookupValue(unit.profile),
    waterSourceId: refToLookupValue(unit.waterSource),
    wellTypeId: refToLookupValue(unit.wellType),
    pavementStreetId: refToLookupValue(unit.pavementStreet),
    pavementSidewalkId: refToLookupValue(unit.pavementSidewalk),
    physical: {
      garden: unit.physical?.garden === true,
      lowerReservoirM3: unit.physical?.lowerReservoirM3 == null ? '' : String(unit.physical.lowerReservoirM3).replace('.', ','),
      upperReservoirM3: unit.physical?.upperReservoirM3 == null ? '' : String(unit.physical.upperReservoirM3).replace('.', ','),
      poolM3: unit.physical?.poolM3 == null ? '' : String(unit.physical.poolM3).replace('.', ','),
    },
    usagePoints: unit.usagePoints === null ? '' : String(unit.usagePoints),
    residents: unit.residents === null ? '' : String(unit.residents),
    iptu: unit.externalIds?.iptu ?? '',
    deliveryRouteId: refToLookupValue(unit.deliveryRoute),
    deliverySequence: unit.deliverySequence === null ? '' : String(unit.deliverySequence),
    altRouteId: refToLookupValue(unit.altRoute),
    parentUnitId: refToLookupValue(unit.parentUnit),
    notes: unit.notes ?? '',
  }
}

function isRecord(v: unknown): v is Record<string, unknown> {
  return typeof v === 'object' && v !== null && !Array.isArray(v)
}

/**
 * Restaura um rascunho (JSON opaco do servidor) sobre os valores vazios: chaves que faltarem
 * ficam com o padrão. A validação real acontece de novo a cada etapa.
 */
export function restoreWizardValues(data: unknown): WizardValues {
  const base = emptyWizardValues()
  if (!isRecord(data)) return base
  return { ...base, ...(data as Partial<WizardValues>) }
}

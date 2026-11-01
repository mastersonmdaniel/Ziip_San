import { z } from 'zod'
import { addressSchema, addressToApi, addressToForm, EMPTY_ADDRESS, type AddressFormValue } from '@components/registry/addressForm'
import { refToLookupValue, type LookupValue } from '@components/registry/LookupField'
import type { Customer, CustomerInput, PersonType, PhoneType } from '@services/registry/types'
import { isValidDocument } from '@utils/validators/document'
import { fromE164, isValidPhone, toE164 } from '@utils/validators/phone'
import { dateField, integerField, lookupField, lookupValueSchema, textField, toIntOrNull, toTextOrNull } from '@utils/zodFields'

export interface PhoneFormValue {
  type: PhoneType
  /** Dígitos nacionais. */
  number: string
  extension: string
  contactName: string
  primary: boolean
}

export interface EmailFormValue {
  address: string
  /** Vínculo de consentimento (LGPD) preservado; a tela não o cria. */
  consentId: string | null
}

export interface CustomerAddressFormValue {
  address: AddressFormValue
  correspondence: boolean
}

export interface CustomerFormValues {
  personType: PersonType
  name: string
  tradeName: string
  customerTypeId: LookupValue | null
  /** CPF/CNPJ SEM máscara. Vive só no estado do formulário. */
  document: string
  documentExemptionReason: string
  birthDate: string
  activityBranchId: LookupValue | null
  parentCustomerId: LookupValue | null
  emails: EmailFormValue[]
  phones: PhoneFormValue[]
  addresses: CustomerAddressFormValue[]
  billing: {
    dueDay: string
    nextMonthDue: boolean
    advanceInvoice: boolean
    blockNegativation: boolean
  }
}

export const EMPTY_PHONE: PhoneFormValue = { type: 'MOBILE', number: '', extension: '', contactName: '', primary: false }

export function emptyCustomerForm(): CustomerFormValues {
  return {
    personType: 'PF',
    name: '',
    tradeName: '',
    customerTypeId: null,
    document: '',
    documentExemptionReason: '',
    birthDate: '',
    activityBranchId: null,
    parentCustomerId: null,
    emails: [],
    phones: [],
    addresses: [{ address: EMPTY_ADDRESS, correspondence: true }],
    billing: { dueDay: '', nextMonthDue: false, advanceInvoice: false, blockNegativation: false },
  }
}

/** O documento NÃO volta da API (só mascarado): em edição o campo começa vazio = manter o atual. */
export function customerToForm(c: Customer): CustomerFormValues {
  return {
    personType: c.personType,
    name: c.name,
    tradeName: c.tradeName ?? '',
    customerTypeId: refToLookupValue(c.customerType),
    document: '',
    documentExemptionReason: '',
    birthDate: c.birthDate ?? '',
    activityBranchId: refToLookupValue(c.activityBranch),
    parentCustomerId: refToLookupValue(c.parentCustomer),
    emails: c.emails.map((e) => ({ address: e.address, consentId: e.consentId ?? null })),
    phones: c.phones.map((p) => ({
      type: p.type,
      number: fromE164(p.number),
      extension: p.extension ?? '',
      contactName: p.contactName ?? '',
      primary: p.primary,
    })),
    addresses:
      c.addresses.length > 0
        ? c.addresses.map((a) => ({ address: addressToForm(a.address), correspondence: a.correspondence }))
        : [{ address: EMPTY_ADDRESS, correspondence: true }],
    billing: {
      dueDay: c.billing.dueDay === null ? '' : String(c.billing.dueDay),
      nextMonthDue: c.billing.nextMonthDue,
      advanceInvoice: c.billing.advanceInvoice,
      blockNegativation: c.billing.blockNegativation,
    },
  }
}

export function formToCustomerInput(v: CustomerFormValues): CustomerInput {
  const pf = v.personType === 'PF'
  const input: CustomerInput = {
    personType: v.personType,
    name: v.name.trim(),
    tradeName: pf ? null : toTextOrNull(v.tradeName),
    customerTypeId: v.customerTypeId?.id ?? null,
    birthDate: pf ? toTextOrNull(v.birthDate) : null,
    activityBranchId: pf ? null : (v.activityBranchId?.id ?? null),
    parentCustomerId: pf ? null : (v.parentCustomerId?.id ?? null),
    emails: v.emails.map((e) => ({ address: e.address.trim(), consentId: e.consentId })),
    phones: v.phones.map((p) => ({
      type: p.type,
      number: toE164(p.number),
      extension: toTextOrNull(p.extension),
      contactName: toTextOrNull(p.contactName),
      primary: p.primary,
    })),
    addresses: v.addresses.map((a) => ({ address: addressToApi(a.address), correspondence: a.correspondence })),
    billing: {
      dueDay: toIntOrNull(v.billing.dueDay),
      nextMonthDue: v.billing.nextMonthDue,
      advanceInvoice: v.billing.advanceInvoice,
      blockNegativation: v.billing.blockNegativation,
    },
  }
  if (v.document !== '') input.document = v.document
  else if (v.documentExemptionReason.trim() !== '') input.documentExemptionReason = v.documentExemptionReason.trim()
  return input
}

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export interface CustomerSchemaOptions {
  /** Em edição de cliente que já tem documento, deixar vazio mantém o atual. */
  keepsExistingDocument: boolean
}

/** Esquema do formulário de cliente. Regras de unicidade (documento) continuam no servidor. */
export function buildCustomerSchema({ keepsExistingDocument }: CustomerSchemaOptions) {
  return z
    .object({
      personType: z.enum(['PF', 'PJ']),
      name: textField({ required: true, min: 3, max: 200 }),
      tradeName: textField({ max: 200 }),
      customerTypeId: lookupField(true),
      document: z.string(),
      documentExemptionReason: textField({ max: 300 }),
      birthDate: dateField({ notFuture: true }),
      activityBranchId: lookupValueSchema.nullable(),
      parentCustomerId: lookupValueSchema.nullable(),
      emails: z.array(
        z.object({
          address: z.string().refine((v) => EMAIL_RE.test(v.trim()), 'E-mail inválido.'),
          consentId: z.string().nullable(),
        }),
      ),
      phones: z.array(
        z.object({
          type: z.enum(['RESIDENTIAL', 'COMMERCIAL', 'MOBILE', 'FAX']),
          number: z.string().refine((v) => isValidPhone(v), 'Telefone inválido: informe DDD e número.'),
          extension: textField({ max: 10 }),
          contactName: textField({ max: 100 }),
          primary: z.boolean(),
        }),
      ),
      addresses: z.array(z.object({ address: addressSchema, correspondence: z.boolean() })).min(1, 'Informe ao menos um endereço.'),
      billing: z.object({
        dueDay: integerField({ min: 1, max: 31 }),
        nextMonthDue: z.boolean(),
        advanceInvoice: z.boolean(),
        blockNegativation: z.boolean(),
      }),
    })
    .superRefine((v, ctx) => {
      const kind = v.personType === 'PF' ? 'CPF' : 'CNPJ'
      if (v.document !== '') {
        if (!isValidDocument(v.document, kind)) {
          ctx.addIssue({
            code: 'custom',
            path: ['document'],
            message: kind === 'CPF' ? 'CPF inválido: confira os dígitos.' : 'CNPJ inválido: confira os caracteres e os dígitos verificadores.',
          })
        }
      } else if (!keepsExistingDocument && v.documentExemptionReason.trim().length < 5) {
        ctx.addIssue({
          code: 'custom',
          path: ['document'],
          message: `Informe o ${kind} ou, abaixo, o motivo para cadastrar sem documento.`,
        })
      }
      const primaries = v.phones.filter((p) => p.primary).length
      if (v.phones.length > 0 && primaries !== 1) {
        ctx.addIssue({ code: 'custom', path: ['phones', 0, 'primary'], message: 'Marque exatamente um telefone como principal.' })
      }
      const corr = v.addresses.filter((a) => a.correspondence).length
      if (corr !== 1) {
        ctx.addIssue({ code: 'custom', path: ['addresses', 0, 'correspondence'], message: 'Marque exatamente um endereço como o de correspondência.' })
      }
    })
}

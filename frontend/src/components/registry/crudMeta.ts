import { z } from 'zod'
import type { ResourceClient } from '@services/registry/client'
import type { CrudItem, Ref } from '@services/registry/types'
import { defaultLookupLabel, type LookupItem, type LookupSource, type LookupValue } from './LookupField'
import { parseDecimalPtBr } from '@utils/validators/geo'
import { decimalField, integerField, lookupField, REQUIRED_MESSAGE, textField } from '@utils/zodFields'

export interface FieldBase {
  /** Chave do campo no payload (inglês camelCase). */
  name: string
  label: string
  required?: boolean
  hint?: string
  /** Mostra como coluna na lista. */
  column?: boolean
}

export type FieldMeta =
  | (FieldBase & { kind: 'text'; maxLength?: number; multiline?: boolean })
  | (FieldBase & { kind: 'integer'; min?: number; max?: number })
  | (FieldBase & { kind: 'decimal'; min?: number; max?: number })
  | (FieldBase & { kind: 'boolean'; /** Valor inicial ao criar (ex.: "Ativo" começa marcado). */ defaultValue?: boolean })
  | (FieldBase & { kind: 'select'; options: Array<{ value: string; label: string }> })
  | (FieldBase & {
      kind: 'lookup'
      /** Chave do objeto de referência na resposta (ex.: "sector" para sectorId). */
      refName: string
      source: LookupSource<LookupItem>
      /** Filtra a busca pelo valor de outro campo do formulário (cascata). */
      dependsOn?: { field: string; param: string }
    })

export interface CrudMeta {
  /** Identificador estável (rota e cache). */
  key: string
  title: string
  /** Nome no singular, minúsculo, para frases ("Novo setor"). */
  singular: string
  description?: string
  /** Ordenação padrão da lista (ex.: 'name,asc'). */
  defaultSort: string
  client: ResourceClient<CrudItem, CrudItem, Record<string, unknown>>
  fields: FieldMeta[]
}

export type FieldValue = string | boolean | LookupValue | null
export type FormValues = Record<string, FieldValue>

function isRef(v: unknown): v is Ref {
  return typeof v === 'object' && v !== null && 'id' in v && typeof (v as { id: unknown }).id === 'string'
}

export function decimalToText(n: number): string {
  return String(n).replace('.', ',')
}

/** Valores iniciais do formulário a partir do item (ou vazios para um novo). */
export function toFormValues(fields: FieldMeta[], item: CrudItem | null): FormValues {
  const values: FormValues = {}
  for (const f of fields) {
    const raw = item ? item[f.name] : undefined
    switch (f.kind) {
      case 'text':
        values[f.name] = typeof raw === 'string' ? raw : ''
        break
      case 'integer':
        values[f.name] = typeof raw === 'number' ? String(raw) : ''
        break
      case 'decimal':
        values[f.name] = typeof raw === 'number' ? decimalToText(raw) : ''
        break
      case 'boolean':
        values[f.name] = item ? raw === true : f.defaultValue === true
        break
      case 'select':
        values[f.name] = typeof raw === 'string' ? raw : ''
        break
      case 'lookup': {
        const ref = item ? item[f.refName] : undefined
        if (isRef(ref)) values[f.name] = { id: ref.id, label: defaultLookupLabel(ref) }
        else if (typeof raw === 'string') values[f.name] = { id: raw, label: raw }
        else values[f.name] = null
        break
      }
    }
  }
  return values
}

/** Payload da API a partir dos valores do formulário. */
export function toPayload(fields: FieldMeta[], values: FormValues): Record<string, unknown> {
  const out: Record<string, unknown> = {}
  for (const f of fields) {
    const v = values[f.name]
    switch (f.kind) {
      case 'text': {
        const t = typeof v === 'string' ? v.trim() : ''
        out[f.name] = t === '' ? null : t
        break
      }
      case 'integer': {
        const t = typeof v === 'string' ? v.trim() : ''
        out[f.name] = t === '' ? null : Number(t)
        break
      }
      case 'decimal': {
        const t = typeof v === 'string' ? v : ''
        out[f.name] = t.trim() === '' ? null : parseDecimalPtBr(t)
        break
      }
      case 'boolean':
        out[f.name] = v === true
        break
      case 'select':
        out[f.name] = typeof v === 'string' && v !== '' ? v : null
        break
      case 'lookup':
        out[f.name] = typeof v === 'object' && v !== null ? v.id : null
        break
    }
  }
  return out
}

/** Constrói o esquema Zod a partir dos metadados: uma única fonte para validar cada campo. */
export function buildSchema(fields: FieldMeta[]) {
  const shape: Record<string, z.ZodTypeAny> = {}
  for (const f of fields) {
    switch (f.kind) {
      case 'text':
        shape[f.name] = textField({ required: f.required, max: f.maxLength })
        break
      case 'integer':
        shape[f.name] = integerField({ required: f.required, min: f.min, max: f.max })
        break
      case 'decimal':
        shape[f.name] = decimalField({ required: f.required, min: f.min, max: f.max })
        break
      case 'boolean':
        shape[f.name] = z.boolean()
        break
      case 'select':
        shape[f.name] = f.required ? z.string().min(1, REQUIRED_MESSAGE) : z.string()
        break
      case 'lookup':
        shape[f.name] = lookupField(f.required === true)
        break
    }
  }
  return z.object(shape)
}

/** Primeiro erro de cada campo, no formato { campo: mensagem }. */
export function collectErrors(result: z.SafeParseReturnType<unknown, unknown>): Record<string, string> {
  const errors: Record<string, string> = {}
  if (result.success) return errors
  for (const issue of result.error.issues) {
    const key = String(issue.path[0] ?? '')
    if (key && !(key in errors)) errors[key] = issue.message
  }
  return errors
}

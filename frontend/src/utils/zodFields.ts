import { z } from 'zod'
import { parseDecimalPtBr } from '@utils/validators/geo'

/** Blocos de esquema reutilizados pelos formulários. Campos numéricos ficam como TEXTO no formulário. */

export const REQUIRED_MESSAGE = 'Campo obrigatório.'

export const lookupValueSchema = z.object({
  id: z.string(),
  label: z.string(),
  code: z.union([z.string(), z.number()]).nullish(),
})

export function lookupField(required: boolean) {
  const base = lookupValueSchema.nullable()
  return required ? base.refine((v) => v !== null, REQUIRED_MESSAGE) : base
}

export function textField(opts: { required?: boolean; min?: number; max?: number } = {}) {
  return z.string().superRefine((value, ctx) => {
    const t = value.trim()
    if (t === '') {
      if (opts.required) ctx.addIssue({ code: 'custom', message: REQUIRED_MESSAGE })
      return
    }
    if (opts.min !== undefined && t.length < opts.min) {
      ctx.addIssue({ code: 'custom', message: `Informe ao menos ${opts.min} caracteres.` })
    }
    if (opts.max !== undefined && t.length > opts.max) {
      ctx.addIssue({ code: 'custom', message: `No máximo ${opts.max} caracteres.` })
    }
  })
}

export function integerField(opts: { required?: boolean; min?: number; max?: number } = {}) {
  return z.string().superRefine((value, ctx) => {
    const t = value.trim()
    if (t === '') {
      if (opts.required) ctx.addIssue({ code: 'custom', message: REQUIRED_MESSAGE })
      return
    }
    if (!/^-?\d+$/.test(t)) {
      ctx.addIssue({ code: 'custom', message: 'Informe um número inteiro.' })
      return
    }
    const n = Number(t)
    if (opts.min !== undefined && n < opts.min) ctx.addIssue({ code: 'custom', message: `O mínimo é ${opts.min}.` })
    if (opts.max !== undefined && n > opts.max) ctx.addIssue({ code: 'custom', message: `O máximo é ${opts.max}.` })
  })
}

export function decimalField(opts: { required?: boolean; min?: number; max?: number } = {}) {
  return z.string().superRefine((value, ctx) => {
    if (value.trim() === '') {
      if (opts.required) ctx.addIssue({ code: 'custom', message: REQUIRED_MESSAGE })
      return
    }
    const n = parseDecimalPtBr(value)
    if (Number.isNaN(n)) {
      ctx.addIssue({ code: 'custom', message: 'Informe um número, por exemplo 12,5.' })
      return
    }
    if (opts.min !== undefined && n < opts.min) ctx.addIssue({ code: 'custom', message: `O mínimo é ${opts.min}.` })
    if (opts.max !== undefined && n > opts.max) ctx.addIssue({ code: 'custom', message: `O máximo é ${opts.max}.` })
  })
}

/** Data no formato yyyy-MM-dd (input type="date"). */
export function dateField(opts: { required?: boolean; notFuture?: boolean } = {}) {
  return z.string().superRefine((value, ctx) => {
    if (value === '') {
      if (opts.required) ctx.addIssue({ code: 'custom', message: REQUIRED_MESSAGE })
      return
    }
    if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || Number.isNaN(Date.parse(`${value}T00:00:00`))) {
      ctx.addIssue({ code: 'custom', message: 'Data inválida.' })
      return
    }
    if (opts.notFuture && value > todayIso()) {
      ctx.addIssue({ code: 'custom', message: 'A data não pode ser futura.' })
    }
  })
}

export function todayIso(now: Date = new Date()): string {
  const y = now.getFullYear()
  const m = String(now.getMonth() + 1).padStart(2, '0')
  const d = String(now.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

export function toIntOrNull(value: string): number | null {
  const t = value.trim()
  return t === '' ? null : Number(t)
}

export function toDecimalOrNull(value: string): number | null {
  return value.trim() === '' ? null : parseDecimalPtBr(value)
}

export function toTextOrNull(value: string): string | null {
  const t = value.trim()
  return t === '' ? null : t
}

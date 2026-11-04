/**
 * P1.18 (PLT-25): data, número e moeda formatados por `Intl`, no idioma
 * de preferência do usuário (hoje só pt-BR é publicado; o parâmetro
 * existe para quando outro idioma entrar, sem trocar o call site).
 *
 * Não substitui `utils/labels.ts` de uma vez: o código pré-existente
 * continua em date-fns/toLocaleString até ser migrado tela a tela. Código
 * NOVO usa estes helpers — é o que o lint do catálogo e esta frente
 * garantem; reescrever todas as telas aqui quebraria o escopo da P1.18.
 */
export type FormatLocale = string
export const DEFAULT_FORMAT_LOCALE = 'pt-BR'

/** yyyy-MM-dd (ou ISO completo) -> dd/MM/yyyy; vazio/inválido -> '—'. */
export function formatDateIntl(value: string | Date | null | undefined, locale: FormatLocale = DEFAULT_FORMAT_LOCALE): string {
  if (!value) return '—'
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return new Intl.DateTimeFormat(locale, { day: '2-digit', month: '2-digit', year: 'numeric' }).format(date)
}

/** Número com separadores do idioma; vazio -> '—'. */
export function formatNumberIntl(value: number | null | undefined, locale: FormatLocale = DEFAULT_FORMAT_LOCALE, fractionDigits = 0): string {
  if (value === null || value === undefined) return '—'
  return new Intl.NumberFormat(locale, {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  }).format(value)
}

/** Moeda (BRL por padrão — a moeda do produto; o código é parâmetro). */
export function formatCurrencyIntl(
  value: number | null | undefined,
  locale: FormatLocale = DEFAULT_FORMAT_LOCALE,
  currency = 'BRL',
): string {
  if (value === null || value === undefined) return '—'
  return new Intl.NumberFormat(locale, { style: 'currency', currency }).format(value)
}

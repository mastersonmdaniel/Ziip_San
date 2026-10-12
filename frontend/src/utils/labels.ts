import { format, isValid, parseISO } from 'date-fns'
import { ptBR } from 'date-fns/locale'
import type { NetworkCoverage, PersonType, PhoneType, UnitRelation } from '@services/registry/types'

export const RELATION_LABEL: Record<UnitRelation, string> = {
  OWNER: 'Proprietário',
  USER: 'Usuário',
  RESPONSIBLE: 'Responsável',
}

export const PERSON_TYPE_LABEL: Record<PersonType, string> = {
  PF: 'Pessoa física',
  PJ: 'Pessoa jurídica',
}

export const PHONE_TYPE_LABEL: Record<PhoneType, string> = {
  RESIDENTIAL: 'Residencial',
  COMMERCIAL: 'Comercial',
  MOBILE: 'Celular',
  FAX: 'Fax',
}

export const NETWORK_LABEL: Record<NetworkCoverage, string> = {
  NONE: 'Sem rede',
  PARTIAL: 'Rede parcial',
  FULL: 'Com rede',
}

export const UF_LIST = [
  'AC', 'AL', 'AP', 'AM', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MT', 'MS', 'MG', 'PA', 'PB', 'PR', 'PE', 'PI', 'RJ', 'RN', 'RS', 'RO', 'RR', 'SC', 'SP', 'SE', 'TO',
] as const

/** yyyy-MM-dd (ou ISO completo) -> dd/MM/yyyy. */
export function formatDate(value: string | null | undefined): string {
  if (!value) return '—'
  const date = parseISO(value)
  return isValid(date) ? format(date, 'dd/MM/yyyy', { locale: ptBR }) : value
}

export function formatNumber(value: number | null | undefined, fractionDigits = 0): string {
  if (value === null || value === undefined) return '—'
  return value.toLocaleString('pt-BR', { minimumFractionDigits: fractionDigits, maximumFractionDigits: fractionDigits })
}

export function orDash(value: string | number | null | undefined): string {
  return value === null || value === undefined || value === '' ? '—' : String(value)
}

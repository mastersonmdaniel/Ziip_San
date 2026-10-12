import { formatCnpj, isValidCnpj, stripCnpj } from './cnpj'
import { formatCpf, isValidCpf, stripCpf } from './cpf'

export type DocumentKind = 'CPF' | 'CNPJ'

/**
 * Descobre o tipo pelo conteúdo: só dígitos e até 11 posições = CPF; letras ou mais de 11 = CNPJ.
 */
export function detectDocumentKind(value: string): DocumentKind {
  const raw = stripCnpj(value)
  if (/[A-Z]/.test(raw) || raw.length > 11) return 'CNPJ'
  return 'CPF'
}

export function stripDocument(value: string, kind: DocumentKind): string {
  return kind === 'CPF' ? stripCpf(value) : stripCnpj(value)
}

export function formatDocument(value: string, kind: DocumentKind): string {
  return kind === 'CPF' ? formatCpf(value) : formatCnpj(value)
}

export function isValidDocument(value: string, kind: DocumentKind): boolean {
  return kind === 'CPF' ? isValidCpf(value) : isValidCnpj(value)
}

/**
 * Máscara de exibição LGPD para um documento COMPLETO já em mãos. As listas usam o valor
 * mascarado que a API devolve; esta função serve para reexibir mascarado após o "revelar".
 * CPF: ***.123.456-**   CNPJ: **.***.345/01DE-**
 */
export function maskDocument(value: string): string {
  const raw = stripCnpj(value)
  if (raw.length === 11 && /^\d+$/.test(raw)) {
    return `***.${raw.slice(3, 6)}.${raw.slice(6, 9)}-**`
  }
  if (raw.length === 14) {
    return `**.***.${raw.slice(5, 8)}/${raw.slice(8, 12)}-**`
  }
  return '*'.repeat(Math.max(raw.length, 3))
}

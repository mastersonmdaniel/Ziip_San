/**
 * Telefone brasileiro: DDD (2 dígitos, sem zero) + número de 8 (fixo, começa em 2-5) ou
 * 9 dígitos (celular, começa em 9). Valor canônico: E.164 (+55DDNNNNNNNNN).
 * Não validamos a tabela de DDDs ativos (muda com o tempo); a API é a autoridade.
 */

export function stripPhone(value: string): string {
  let d = value.replace(/\D/g, '')
  if (d.startsWith('55') && d.length > 11) d = d.slice(2)
  return d.slice(0, 11)
}

export function formatPhone(value: string): string {
  const d = stripPhone(value)
  if (d.length === 0) return ''
  if (d.length <= 2) return `(${d}`
  if (d.length <= 6) return `(${d.slice(0, 2)}) ${d.slice(2)}`
  if (d.length <= 10) return `(${d.slice(0, 2)}) ${d.slice(2, 6)}-${d.slice(6)}`
  return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`
}

export function isValidPhone(value: string): boolean {
  const d = stripPhone(value)
  if (!/^[1-9][1-9]/.test(d)) return false
  if (d.length === 10) return /^[2-5]/.test(d.slice(2))
  if (d.length === 11) return d[2] === '9'
  return false
}

export function toE164(value: string): string {
  return `+55${stripPhone(value)}`
}

/** E.164 brasileiro -> dígitos nacionais (para exibir em campo mascarado). */
export function fromE164(value: string): string {
  return stripPhone(value.replace(/^\+/, ''))
}

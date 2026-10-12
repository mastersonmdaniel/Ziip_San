/** CEP: 8 dígitos, máscara 00000-000. Não há dígito verificador. */

export function stripCep(value: string): string {
  return value.replace(/\D/g, '').slice(0, 8)
}

export function formatCep(value: string): string {
  const d = stripCep(value)
  return d.length <= 5 ? d : `${d.slice(0, 5)}-${d.slice(5)}`
}

export function isValidCep(value: string): boolean {
  const d = value.replace(/\D/g, '')
  return d.length === 8 && d !== '00000000'
}

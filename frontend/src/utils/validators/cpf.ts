/**
 * CPF: 11 dígitos, dois dígitos verificadores (módulo 11).
 *
 * DV1: soma dos 9 primeiros dígitos com pesos 10..2; DV = 0 se resto < 2, senão 11 - resto.
 * DV2: soma dos 10 primeiros dígitos (inclui DV1) com pesos 11..2; mesma regra.
 * Sequências de dígitos iguais (111.111.111-11 etc.) passam na conta mas são rejeitadas.
 */

export function stripCpf(value: string): string {
  return value.replace(/\D/g, '').slice(0, 11)
}

export function formatCpf(value: string): string {
  const d = stripCpf(value)
  if (d.length <= 3) return d
  if (d.length <= 6) return `${d.slice(0, 3)}.${d.slice(3)}`
  if (d.length <= 9) return `${d.slice(0, 3)}.${d.slice(3, 6)}.${d.slice(6)}`
  return `${d.slice(0, 3)}.${d.slice(3, 6)}.${d.slice(6, 9)}-${d.slice(9)}`
}

function cpfDigit(digits: number[], startWeight: number): number {
  let sum = 0
  for (let i = 0; i < digits.length; i += 1) {
    sum += (digits[i] ?? 0) * (startWeight - i)
  }
  const rest = sum % 11
  return rest < 2 ? 0 : 11 - rest
}

/** Calcula os dois DVs a partir dos 9 primeiros dígitos. */
export function computeCpfCheckDigits(base9: string): string {
  const digits = base9.split('').map(Number)
  const dv1 = cpfDigit(digits, 10)
  const dv2 = cpfDigit([...digits, dv1], 11)
  return `${dv1}${dv2}`
}

export function isValidCpf(value: string): boolean {
  const d = value.replace(/\D/g, '')
  if (d.length !== 11) return false
  if (/^(\d)\1{10}$/.test(d)) return false
  return computeCpfCheckDigits(d.slice(0, 9)) === d.slice(9)
}

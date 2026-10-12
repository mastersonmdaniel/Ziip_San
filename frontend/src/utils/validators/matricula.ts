/**
 * Matrícula do imóvel com dígito verificador (módulo 11).
 *
 * ATENÇÃO, A CONFIRMAR COM O BACKEND: o algoritmo abaixo vem do relatório 01 do levantamento
 * (comportamento do GSAN: módulo 11, pesos 2..9 da direita para a esquerda, repetindo; resto 0
 * ou 1 vira DV 0). O serviço `MatriculaDv` do SIGSAN é a autoridade. Por isso a interface NÃO
 * bloqueia nem rejeita matrícula com base nesta função: ela existe para ser conferida contra o
 * serviço Java quando este existir.
 *
 * DV = 11 - (soma mod 11); se o resultado for 10 ou 11, DV = 0.
 */

export function stripMatricula(value: string): string {
  return value.replace(/\D/g, '')
}

export function computeMatriculaDv(base: string): string {
  const digits = stripMatricula(base)
  if (digits.length === 0) throw new Error('Matrícula vazia')
  let sum = 0
  let weight = 2
  for (let i = digits.length - 1; i >= 0; i -= 1) {
    sum += Number(digits[i]) * weight
    weight = weight === 9 ? 2 : weight + 1
  }
  const dv = 11 - (sum % 11)
  return String(dv >= 10 ? 0 : dv)
}

/** Aceita a matrícula completa (base + DV), com ou sem hífen. */
export function isValidMatricula(value: string): boolean {
  const d = stripMatricula(value)
  if (d.length < 2) return false
  return computeMatriculaDv(d.slice(0, -1)) === d.slice(-1)
}

export function formatMatricula(registration: string, dv: string): string {
  return `${stripMatricula(registration)}-${dv}`
}

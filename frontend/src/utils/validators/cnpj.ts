/**
 * CNPJ numérico e alfanumérico (14 posições).
 *
 * Estrutura: 8 posições de raiz + 4 de ordem (todas alfanuméricas A-Z, 0-9) + 2 DVs (sempre dígitos).
 *
 * Cálculo do DV (módulo 11), igual ao do CNPJ numérico, mudando só o valor de cada caractere:
 *   valor = código ASCII do caractere - 48   ('0'..'9' -> 0..9; 'A' -> 17 ... 'Z' -> 42)
 *   DV1: pesos 5,4,3,2,9,8,7,6,5,4,3,2 sobre as 12 primeiras posições
 *   DV2: pesos 6,5,4,3,2,9,8,7,6,5,4,3,2 sobre as 12 primeiras + DV1
 *   DV = 0 se o resto da soma por 11 for 0 ou 1; senão 11 - resto.
 *
 * Fonte: Receita Federal do Brasil, "CNPJ Alfanumérico" (perguntas e respostas, gov.br):
 * https://www.gov.br/receitafederal/pt-br/centrais-de-conteudo/publicacoes/perguntas-e-respostas/cnpj/cnpj-alfanumerico.pdf
 * Vetor oficial: 12.ABC.345/01DE-35. O texto do PDF não pôde ser lido por máquina nesta sessão;
 * o algoritmo foi conferido por cálculo manual contra o vetor (ver cnpj.test.ts). A CONFIRMAR
 * contra o PDF oficial antes de produção.
 */

const WEIGHTS_DV1 = [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]
const WEIGHTS_DV2 = [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]

/** Mantém só A-Z e 0-9, em maiúsculas, no máximo 14 posições. */
export function stripCnpj(value: string): string {
  return value
    .toUpperCase()
    .replace(/[^0-9A-Z]/g, '')
    .slice(0, 14)
}

/** Máscara AA.AAA.AAA/AAAA-DD. */
export function formatCnpj(value: string): string {
  const d = stripCnpj(value)
  if (d.length <= 2) return d
  if (d.length <= 5) return `${d.slice(0, 2)}.${d.slice(2)}`
  if (d.length <= 8) return `${d.slice(0, 2)}.${d.slice(2, 5)}.${d.slice(5)}`
  if (d.length <= 12) return `${d.slice(0, 2)}.${d.slice(2, 5)}.${d.slice(5, 8)}/${d.slice(8)}`
  return `${d.slice(0, 2)}.${d.slice(2, 5)}.${d.slice(5, 8)}/${d.slice(8, 12)}-${d.slice(12)}`
}

/** Valor do caractere no cálculo: ASCII - 48. */
export function cnpjCharValue(ch: string): number {
  return ch.charCodeAt(0) - 48
}

function cnpjDigit(chars: string[], weights: number[]): number {
  let sum = 0
  for (let i = 0; i < weights.length; i += 1) {
    sum += cnpjCharValue(chars[i] ?? '0') * (weights[i] ?? 0)
  }
  const rest = sum % 11
  return rest < 2 ? 0 : 11 - rest
}

/** Calcula os dois DVs a partir das 12 primeiras posições (alfanuméricas, maiúsculas). */
export function computeCnpjCheckDigits(base12: string): string {
  const chars = base12.split('')
  const dv1 = cnpjDigit(chars, WEIGHTS_DV1)
  const dv2 = cnpjDigit([...chars, String(dv1)], WEIGHTS_DV2)
  return `${dv1}${dv2}`
}

export function isValidCnpj(value: string): boolean {
  const d = stripCnpj(value)
  if (d.length !== 14) return false
  if (!/^[0-9A-Z]{12}[0-9]{2}$/.test(d)) return false
  if (/^(.)\1{13}$/.test(d)) return false
  return computeCnpjCheckDigits(d.slice(0, 12)) === d.slice(12)
}

/** True quando há alguma letra: só pode ser CNPJ alfanumérico. */
export function isAlphanumericCnpj(value: string): boolean {
  return /[A-Z]/.test(stripCnpj(value))
}

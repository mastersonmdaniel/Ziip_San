/**
 * Aritmética decimal EXACTA para os painéis de perdas (P-II6-TELA).
 *
 * O núcleo Java (II-6) calcula em `BigDecimal` sem nenhum
 * arredondamento (ver `ExactWaterBalance`, `MinimumNightFlow`,
 * `DmcReturnRanking` em intelligence/ziipsan-losses/.../dma/). Um
 * `number` do JS (float64) NÃO reproduz essa exactidão — por isso os
 * volumes viajam neste pacote como STRINGS decimais e as operações
 * são feitas sobre inteiros escalados (BigInt): soma/subtracção usam
 * a maior escala dos operandos; a multiplicação soma as escalas —
 * as mesmas regras de escala do `BigDecimal`.
 */
export interface Decimal {
  /** Valor × 10^scale. */
  scaled: bigint
  scale: number
}

const DECIMAL_RE = /^-?\d+(\.\d+)?$/

export function parseDecimal(raw: string): Decimal {
  if (!DECIMAL_RE.test(raw)) {
    throw new Error(`valor decimal inválido: ${raw}`)
  }
  const negative = raw.startsWith('-')
  const body = negative ? raw.slice(1) : raw
  const [intPart, fracPart = ''] = body.split('.')
  const digits = BigInt(intPart + fracPart)
  return { scaled: negative ? -digits : digits, scale: fracPart.length }
}

function align(a: Decimal, b: Decimal): { a: bigint; b: bigint; scale: number } {
  const scale = Math.max(a.scale, b.scale)
  const pow = (n: number) => 10n ** BigInt(n)
  return {
    a: a.scaled * pow(scale - a.scale),
    b: b.scaled * pow(scale - b.scale),
    scale,
  }
}

export function decAdd(a: Decimal, b: Decimal): Decimal {
  const aligned = align(a, b)
  return { scaled: aligned.a + aligned.b, scale: aligned.scale }
}

export function decSub(a: Decimal, b: Decimal): Decimal {
  const aligned = align(a, b)
  return { scaled: aligned.a - aligned.b, scale: aligned.scale }
}

export function decMul(a: Decimal, b: Decimal): Decimal {
  return { scaled: a.scaled * b.scaled, scale: a.scale + b.scale }
}

/** -1 / 0 / 1, como o `compareTo` do BigDecimal (ignora escala). */
export function decCmp(a: Decimal, b: Decimal): number {
  const aligned = align(a, b)
  if (aligned.a < aligned.b) return -1
  if (aligned.a > aligned.b) return 1
  return 0
}

export function decSign(a: Decimal): number {
  if (a.scaled < 0n) return -1
  if (a.scaled > 0n) return 1
  return 0
}

/** Renderização com a escala do valor (como o `toPlainString` do BigDecimal). */
export function decToString(d: Decimal): string {
  const negative = d.scaled < 0n
  const digits = (negative ? -d.scaled : d.scaled).toString().padStart(d.scale + 1, '0')
  if (d.scale === 0) return `${negative ? '-' : ''}${digits}`
  const intPart = digits.slice(0, digits.length - d.scale)
  const fracPart = digits.slice(digits.length - d.scale)
  return `${negative ? '-' : ''}${intPart}.${fracPart}`
}

export const dec = (raw: string): Decimal => parseDecimal(raw)
export const DEC_ZERO: Decimal = { scaled: 0n, scale: 0 }

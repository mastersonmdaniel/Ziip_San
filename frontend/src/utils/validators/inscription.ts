/**
 * Inscrição do imóvel: LLL.SSS.QQQ.LLLL.SSS
 * (localidade, setor, quadra, lote, sublote). Os números são guardados como inteiros;
 * os zeros à esquerda existem SÓ na exibição.
 */

export interface Inscription {
  locality: number
  sector: number
  block: number
  lot: number
  sublot: number
}

/** Largura de cada parte na exibição. */
const WIDTHS = [3, 3, 3, 4, 3] as const
const TOTAL_DIGITS = 16

export function stripInscription(value: string): string {
  return value.replace(/\D/g, '').slice(0, TOTAL_DIGITS)
}

/** Aplica a máscara enquanto o usuário digita (aceita entrada parcial). */
export function maskInscription(value: string): string {
  const d = stripInscription(value)
  const parts: string[] = []
  let pos = 0
  for (const w of WIDTHS) {
    if (pos >= d.length) break
    parts.push(d.slice(pos, pos + w))
    pos += w
  }
  return parts.join('.')
}

/** Faixas do relatório 07 (S2): localidade/setor/quadra 1..999, lote 1..9999, sublote 0..999. */
export function isValidInscription(i: Inscription): boolean {
  const inRange = (v: number, min: number, max: number) => Number.isInteger(v) && v >= min && v <= max
  return (
    inRange(i.locality, 1, 999) &&
    inRange(i.sector, 1, 999) &&
    inRange(i.block, 1, 999) &&
    inRange(i.lot, 1, 9999) &&
    inRange(i.sublot, 0, 999)
  )
}

/** Converte "001.002.003.0004.005" (ou 16 dígitos) em partes; null se incompleta ou fora da faixa. */
export function parseInscription(value: string): Inscription | null {
  const d = stripInscription(value)
  if (d.length !== TOTAL_DIGITS) return null
  const n: number[] = []
  let pos = 0
  for (const w of WIDTHS) {
    n.push(Number(d.slice(pos, pos + w)))
    pos += w
  }
  const result: Inscription = {
    locality: n[0] ?? 0,
    sector: n[1] ?? 0,
    block: n[2] ?? 0,
    lot: n[3] ?? 0,
    sublot: n[4] ?? 0,
  }
  return isValidInscription(result) ? result : null
}

export function formatInscription(i: Inscription): string {
  const pad = (v: number, w: number) => String(v).padStart(w, '0')
  return [pad(i.locality, 3), pad(i.sector, 3), pad(i.block, 3), pad(i.lot, 4), pad(i.sublot, 3)].join('.')
}

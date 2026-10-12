/**
 * Coordenadas em graus decimais (SIRGAS 2000, EPSG:4674; na prática igual a WGS84 para a
 * precisão do cadastro). Exige o PAR completo e dentro do Brasil.
 *
 * Caixa envolvente aproximada do território brasileiro (inclui ilhas oceânicas):
 *   latitude  -33,75 .. 5,28   (Chuí ao Monte Caburaí)
 *   longitude -73,99 .. -28,83 (Serra do Divisor à Ilha de Martin Vaz)
 * É uma caixa, não o polígono do país: aceita pontos no mar ou em países vizinhos dentro do
 * retângulo. O servidor é a autoridade. Limites A CONFIRMAR com o IBGE.
 */

export interface LatLng {
  lat: number
  lng: number
}

export const BRAZIL_BOUNDS = {
  latMin: -33.75,
  latMax: 5.28,
  lngMin: -73.99,
  lngMax: -28.83,
} as const

export type CoordinateIssue = 'incomplete' | 'invalid' | 'outside-brazil'

/** Interpreta texto com vírgula ou ponto decimal; NaN se inválido. */
export function parseDecimalPtBr(text: string): number {
  const t = text.trim().replace(',', '.')
  if (t === '' || !/^-?\d+(\.\d+)?$/.test(t)) return Number.NaN
  return Number(t)
}

export function isInsideBrazil(p: LatLng): boolean {
  return (
    p.lat >= BRAZIL_BOUNDS.latMin &&
    p.lat <= BRAZIL_BOUNDS.latMax &&
    p.lng >= BRAZIL_BOUNDS.lngMin &&
    p.lng <= BRAZIL_BOUNDS.lngMax
  )
}

/** Valida o par digitado. Ambos vazios = sem coordenada (ok). */
export function checkCoordinatePair(latText: string, lngText: string): CoordinateIssue | null {
  const latEmpty = latText.trim() === ''
  const lngEmpty = lngText.trim() === ''
  if (latEmpty && lngEmpty) return null
  if (latEmpty !== lngEmpty) return 'incomplete'
  const lat = parseDecimalPtBr(latText)
  const lng = parseDecimalPtBr(lngText)
  if (Number.isNaN(lat) || Number.isNaN(lng)) return 'invalid'
  if (!isInsideBrazil({ lat, lng })) return 'outside-brazil'
  return null
}

export function formatCoordinate(value: number): string {
  return value.toFixed(6).replace('.', ',')
}

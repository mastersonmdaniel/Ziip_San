/**
 * GIS-06: medição no mapa web (§10.3) — distância geodésica por
 * haversina e área esférica aproximada sobre lon/lat. É medição de
 * consulta na tela (a extensão oficial de rede é a do banco, por
 * geography — GIS-08); o painel rotula o resultado como medição em
 * tela para não fingir precisão de cadastro.
 */
const EARTH_RADIUS_M = 6_371_008.8

export interface LngLatPoint {
  lng: number
  lat: number
}

function toRad(deg: number): number {
  return (deg * Math.PI) / 180
}

export function segmentDistanceM(a: LngLatPoint, b: LngLatPoint): number {
  const dLat = toRad(b.lat - a.lat)
  const dLng = toRad(b.lng - a.lng)
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(a.lat)) * Math.cos(toRad(b.lat)) * Math.sin(dLng / 2) ** 2
  return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(h))
}

/** Distância total de uma linha (soma dos segmentos), em metros. */
export function pathDistanceM(points: LngLatPoint[]): number {
  let total = 0
  for (let i = 1; i < points.length; i++) {
    total += segmentDistanceM(points[i - 1], points[i])
  }
  return total
}

/** Área de polígono fechado (anel lon/lat), em m² (esférica). */
export function polygonAreaM2(ring: LngLatPoint[]): number {
  if (ring.length < 3) return 0
  let total = 0
  for (let i = 0; i < ring.length; i++) {
    const a = ring[i]
    const b = ring[(i + 1) % ring.length]
    total += toRad(b.lng - a.lng) * (2 + Math.sin(toRad(a.lat)) + Math.sin(toRad(b.lat)))
  }
  return Math.abs((total * EARTH_RADIUS_M * EARTH_RADIUS_M) / 2)
}

export function formatDistance(m: number): string {
  if (m < 1000) return `${m.toFixed(1).replace('.', ',')} m`
  return `${(m / 1000).toFixed(3).replace('.', ',')} km`
}

export function formatArea(m2: number): string {
  if (m2 < 10_000) return `${m2.toFixed(1).replace('.', ',')} m²`
  return `${(m2 / 10_000).toFixed(4).replace('.', ',')} ha`
}

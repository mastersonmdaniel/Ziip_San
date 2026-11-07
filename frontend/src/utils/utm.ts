/**
 * GIS-06: conversão UTM ↔ geográfica (GRS80/SIRGAS 2000) no
 * navegador — espelho do Utm.java do backend, com os mesmos pontos
 * de conferência nos testes dos dois lados. Usada para validar e
 * pré-visualizar a pesquisa por coordenada UTM; a busca em si roda
 * no servidor (PostGIS).
 */
const A = 6_378_137.0
const F = 1.0 / 298.257222101
const K0 = 0.9996
const FALSE_EASTING = 500_000.0
const FALSE_NORTHING_SOUTH = 10_000_000.0

export function centralMeridianDeg(zone: number): number {
  if (zone < 1 || zone > 60) throw new Error(`fuso UTM fora de 1..60: ${zone}`)
  return (zone - 1) * 6 - 180 + 3
}

export interface LonLat {
  lon: number
  lat: number
}

export function utmToLonLat(
  easting: number,
  northing: number,
  zone: number,
  southernHemisphere: boolean,
): LonLat {
  const lon0 = (centralMeridianDeg(zone) * Math.PI) / 180
  const e2 = F * (2 - F)
  const x = easting - FALSE_EASTING
  const y = northing - (southernHemisphere ? FALSE_NORTHING_SOUTH : 0)
  const m = y / K0
  const mu = m / (A * (1 - e2 / 4 - (3 * e2 * e2) / 64 - (5 * e2 * e2 * e2) / 256))
  const e1 = (1 - Math.sqrt(1 - e2)) / (1 + Math.sqrt(1 - e2))
  const j1 = (3 * e1) / 2 - (27 * e1 ** 3) / 32
  const j2 = (21 * e1 * e1) / 16 - (55 * e1 ** 4) / 32
  const j3 = (151 * e1 ** 3) / 96
  const j4 = (1097 * e1 ** 4) / 512
  const fp =
    mu + j1 * Math.sin(2 * mu) + j2 * Math.sin(4 * mu) + j3 * Math.sin(6 * mu) + j4 * Math.sin(8 * mu)
  const sinFp = Math.sin(fp)
  const cosFp = Math.cos(fp)
  const tanFp = Math.tan(fp)
  const n = A / Math.sqrt(1 - e2 * sinFp * sinFp)
  const r = (A * (1 - e2)) / Math.pow(1 - e2 * sinFp * sinFp, 1.5)
  const t = tanFp * tanFp
  const eta2 = (e2 * cosFp * cosFp) / (1 - e2)
  const d = x / (n * K0)
  const lat =
    fp -
    ((n * tanFp) / r) *
      (d * d / 2 -
        ((5 + 3 * t + 10 * eta2 - 4 * eta2 * eta2 - 9 * e2) * d ** 4) / 24 +
        ((61 + 90 * t + 298 * eta2 + 45 * t * t - 252 * e2 - 3 * eta2 * eta2) * d ** 6) / 720)
  const lon =
    lon0 +
    (d - ((1 + 2 * t + eta2) * d ** 3) / 6 +
      ((5 - 2 * eta2 + 28 * t - 8 * eta2 * eta2 + 8 * e2 + 24 * t * t) * d ** 5) / 120) /
      cosFp
  return { lon: (lon * 180) / Math.PI, lat: (lat * 180) / Math.PI }
}

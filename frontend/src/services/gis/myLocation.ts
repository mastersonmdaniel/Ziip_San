/**
 * GIS-06 — "Minha localização" no mapa web (lacuna E2 do anexo à
 * Rodada 04 do PDCA, fechada no Ciclo 1 do programa 5× PDCA GIS).
 *
 * Só lógica pura sobre a Geolocation API do browser vive aqui: a
 * página (GisMap) pede a posição por esta porta injectável e centra
 * o mapa; nenhum acesso a hardware é simulado — sem API no browser
 * ou sem permissão, o erro é mostrado ao utilizador, nunca escondido
 * com uma posição inventada.
 *
 * Paridade mobile (decisão 73): a função vive na página web única do
 * GIS-06, que é a superfície usada no navegador do celular; o app de
 * campo NÃO usa esta via — tem GNSS próprio, de maior precisão, em
 * campo/src/gis/gnss.ts (ED-66/GIS-15).
 */

/**
 * Zoom de centragem: o MESMO que a centragem por resultado de
 * pesquisa já usa em GisMap (goToHit, zoom 17) — coerência de UX,
 * não um limiar novo.
 */
export const MY_LOCATION_ZOOM = 17

export interface MyLocationFix {
  lng: number
  lat: number
  /** Precisão declarada pelo browser/receptor, em metros; null quando a API não a declara válida. */
  accuracyM: number | null
}

/** Subconjunto estrutural de Geolocation — injectável para testes. */
export interface GeolocationLike {
  getCurrentPosition: Geolocation['getCurrentPosition']
}

/** Domínio WGS84: longitude ∈ [-180, 180], latitude ∈ [-90, 90], ambos finitos. */
export function isValidLngLat(lng: number, lat: number): boolean {
  return (
    Number.isFinite(lng) &&
    Number.isFinite(lat) &&
    lng >= -180 &&
    lng <= 180 &&
    lat >= -90 &&
    lat <= 90
  )
}

/** Extrai o fix de uma posição da API; null se a posição vier fora do domínio WGS84. */
export function fixFromPosition(position: GeolocationPosition): MyLocationFix | null {
  const { longitude, latitude, accuracy } = position.coords
  if (!isValidLngLat(longitude, latitude)) return null
  return {
    lng: longitude,
    lat: latitude,
    accuracyM: Number.isFinite(accuracy) && accuracy >= 0 ? accuracy : null,
  }
}

/**
 * Mensagens pt-BR por código fechado de GeolocationPositionError
 * (1 = PERMISSION_DENIED, 2 = POSITION_UNAVAILABLE, 3 = TIMEOUT).
 */
export function geolocationErrorMessage(code: number): string {
  switch (code) {
    case 1:
      return 'Permissão de localização negada: autorize o acesso à localização para centrar o mapa na sua posição.'
    case 2:
      return 'Posição indisponível: o aparelho não conseguiu determinar a localização agora.'
    case 3:
      return 'Tempo esgotado ao obter a localização: tente novamente.'
    default:
      return 'Não foi possível obter a sua localização.'
  }
}

/**
 * Pede a posição actual ao browser. Rejeita com Error de mensagem
 * pt-BR quando: a API não existe, o browser devolve erro, ou a
 * posição vem fora do domínio. Posição fresca (maximumAge 0) e alta
 * precisão: é uma consulta de campo, não um valor em cache.
 */
export function requestMyLocation(
  geolocation: GeolocationLike | undefined,
): Promise<MyLocationFix> {
  if (!geolocation) {
    return Promise.reject(
      new Error(
        'A geolocalização não é suportada neste navegador ou contexto (é preciso HTTPS).',
      ),
    )
  }
  return new Promise((resolve, reject) => {
    geolocation.getCurrentPosition(
      (position) => {
        const fix = fixFromPosition(position)
        if (fix) resolve(fix)
        else
          reject(
            new Error(
              'O browser devolveu uma posição fora do domínio válido de coordenadas.',
            ),
          )
      },
      (error) => reject(new Error(geolocationErrorMessage(error.code))),
      { enableHighAccuracy: true, maximumAge: 0, timeout: 10_000 },
    )
  })
}

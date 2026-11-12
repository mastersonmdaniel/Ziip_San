import { describe, expect, it, vi } from 'vitest'
import {
  MY_LOCATION_ZOOM,
  fixFromPosition,
  geolocationErrorMessage,
  isValidLngLat,
  requestMyLocation,
  type GeolocationLike,
} from './myLocation'

function fakeGeoSuccess(lon: number, lat: number, accuracy: number): GeolocationLike {
  return {
    getCurrentPosition: (success) =>
      success({
        coords: { longitude: lon, latitude: lat, accuracy },
      } as GeolocationPosition),
  }
}

function fakeGeoError(code: number): GeolocationLike {
  return {
    getCurrentPosition: (_success, error) =>
      error?.({ code, message: 'erro do browser', PERMISSION_DENIED: 1, POSITION_UNAVAILABLE: 2, TIMEOUT: 3 } as GeolocationPositionError),
  }
}

describe('myLocation — validação de coordenadas (domínio WGS84)', () => {
  it('aceita coordenadas dentro do domínio', () => {
    expect(isValidLngLat(-48.6, -27.1)).toBe(true)
    expect(isValidLngLat(180, 90)).toBe(true)
    expect(isValidLngLat(-180, -90)).toBe(true)
  })

  it('rejeita fora do domínio e não-finitos', () => {
    expect(isValidLngLat(180.0001, 0)).toBe(false)
    expect(isValidLngLat(0, -90.5)).toBe(false)
    expect(isValidLngLat(Number.NaN, 0)).toBe(false)
    expect(isValidLngLat(0, Number.POSITIVE_INFINITY)).toBe(false)
  })
})

describe('myLocation — fixFromPosition', () => {
  it('extrai lng/lat/precisão de uma posição válida', () => {
    const pos = {
      coords: { longitude: -48.67, latitude: -27.12, accuracy: 18 },
    } as GeolocationPosition
    expect(fixFromPosition(pos)).toEqual({ lng: -48.67, lat: -27.12, accuracyM: 18 })
  })

  it('precisão ausente ou inválida vira null (nunca inventa precisão)', () => {
    const pos = {
      coords: { longitude: -48.67, latitude: -27.12, accuracy: Number.NaN },
    } as GeolocationPosition
    expect(fixFromPosition(pos)?.accuracyM).toBeNull()
  })

  it('posição fora do domínio devolve null', () => {
    const pos = {
      coords: { longitude: 999, latitude: 0, accuracy: 5 },
    } as GeolocationPosition
    expect(fixFromPosition(pos)).toBeNull()
  })
})

describe('myLocation — mensagens de erro (pt-BR, por código da API)', () => {
  it('mapeia os três códigos fechados da Geolocation API', () => {
    expect(geolocationErrorMessage(1)).toContain('Permissão de localização negada')
    expect(geolocationErrorMessage(2)).toContain('indisponível')
    expect(geolocationErrorMessage(3)).toContain('Tempo esgotado ao obter a localização')
  })

  it('código desconhecido tem mensagem genérica honesta', () => {
    expect(geolocationErrorMessage(99)).toContain('Não foi possível obter a sua localização')
  })
})

describe('myLocation — requestMyLocation', () => {
  it('resolve com o fix quando o browser devolve posição', async () => {
    await expect(requestMyLocation(fakeGeoSuccess(-48.6, -27.1, 12))).resolves.toEqual({
      lng: -48.6,
      lat: -27.1,
      accuracyM: 12,
    })
  })

  it('rejeita com a mensagem do código quando o browser devolve erro', async () => {
    await expect(requestMyLocation(fakeGeoError(1))).rejects.toThrow(
      'Permissão de localização negada',
    )
    await expect(requestMyLocation(fakeGeoError(3))).rejects.toThrow('Tempo esgotado')
  })

  it('rejeita quando a API não existe (browser/contexto sem geolocation)', async () => {
    await expect(requestMyLocation(undefined)).rejects.toThrow('não é suportada')
  })

  it('rejeita quando a posição devolvida é inválida', async () => {
    await expect(requestMyLocation(fakeGeoSuccess(999, 0, 5))).rejects.toThrow(
      'fora do domínio',
    )
  })

  it('pede alta precisão sem maximumAge (posição fresca, não cache)', async () => {
    const geo: GeolocationLike = {
      getCurrentPosition: vi.fn((_success, _error, options) => {
        expect(options?.enableHighAccuracy).toBe(true)
        expect(options?.maximumAge).toBe(0)
        _success({
          coords: { longitude: 0, latitude: 0, accuracy: 1 },
        } as GeolocationPosition)
      }),
    }
    await requestMyLocation(geo)
    expect(geo.getCurrentPosition).toHaveBeenCalledOnce()
  })

  it('o zoom de centragem é o mesmo da centragem por resultado de pesquisa (17)', () => {
    expect(MY_LOCATION_ZOOM).toBe(17)
  })
})

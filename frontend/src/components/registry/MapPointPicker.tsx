import { lazy, Suspense, useId, useState } from 'react'
import { Field, Input } from '@components/ui/Field'
import {
  checkCoordinatePair,
  formatCoordinate,
  parseDecimalPtBr,
  type CoordinateIssue,
  type LatLng,
} from '@utils/validators/geo'

/** Texto digitado; vazio nos dois = sem coordenada. */
export interface CoordinateText {
  lat: string
  lng: string
}

export const EMPTY_COORDINATES: CoordinateText = { lat: '', lng: '' }

export function coordinatesToText(value: LatLng | null | undefined): CoordinateText {
  return value ? { lat: formatCoordinate(value.lat), lng: formatCoordinate(value.lng) } : EMPTY_COORDINATES
}

/** Converte o texto em ponto; null se vazio. Lança se o par for inválido (valide antes). */
export function textToCoordinates(value: CoordinateText): LatLng | null {
  if (value.lat.trim() === '' && value.lng.trim() === '') return null
  return { lat: parseDecimalPtBr(value.lat), lng: parseDecimalPtBr(value.lng) }
}

export const COORDINATE_MESSAGES: Record<CoordinateIssue, string> = {
  incomplete: 'Informe latitude e longitude juntas, ou deixe as duas em branco.',
  invalid: 'Use números com até 6 casas decimais, por exemplo -9,974500.',
  'outside-brazil': 'A coordenada está fora do Brasil. Confira o sinal (negativo no Brasil) e a ordem latitude/longitude.',
}

const MapCanvas = lazy(() => import('./MapCanvas'))

interface MapPointPickerProps {
  value: CoordinateText
  onChange: (value: CoordinateText) => void
  error?: string | null
  disabled?: boolean
}

/**
 * Seletor de ponto (lat/long em graus decimais, SIRGAS 2000). Os campos de texto são SEMPRE
 * exibidos e são a forma acessível por teclado; o mapa é um complemento visual e só aparece
 * se VITE_MAP_STYLE_URL estiver configurada (sem ela não há mapa base: nada é inventado).
 * Exige o par completo e dentro do Brasil.
 */
export function MapPointPicker({ value, onChange, error, disabled }: MapPointPickerProps) {
  const groupId = useId()
  const [touched, setTouched] = useState(false)
  const mapStyle = import.meta.env.VITE_MAP_STYLE_URL
  const issue = touched ? checkCoordinatePair(value.lat, value.lng) : null
  const ownError = issue ? COORDINATE_MESSAGES[issue] : null
  const point = checkCoordinatePair(value.lat, value.lng) === null ? textToCoordinates(value) : null

  return (
    <fieldset className="space-y-3 rounded-lg border p-4" aria-describedby={`${groupId}-help`}>
      <legend className="px-1 text-sm font-medium">Localização no mapa (opcional)</legend>
      <p id={`${groupId}-help`} className="text-xs text-muted-foreground">
        Latitude e longitude em graus decimais (SIRGAS 2000). Informe as duas ou nenhuma. Coordenada de domicílio é dado
        de localização: preencha só se houver finalidade operacional.
      </p>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Latitude" error={error ?? ownError}>
          {(aria) => (
            <Input
              {...aria}
              value={value.lat}
              onChange={(e) => onChange({ ...value, lat: e.target.value })}
              onBlur={() => setTouched(true)}
              disabled={disabled}
              inputMode="decimal"
              placeholder="-9,974500"
              autoComplete="off"
            />
          )}
        </Field>
        <Field label="Longitude">
          {(aria) => (
            <Input
              {...aria}
              value={value.lng}
              onChange={(e) => onChange({ ...value, lng: e.target.value })}
              onBlur={() => setTouched(true)}
              disabled={disabled}
              inputMode="decimal"
              placeholder="-67,810000"
              autoComplete="off"
            />
          )}
        </Field>
      </div>
      {mapStyle ? (
        <Suspense fallback={<p className="text-sm text-muted-foreground">Carregando mapa...</p>}>
          <MapCanvas
            styleUrl={mapStyle}
            point={point}
            disabled={disabled}
            onPick={(p) => onChange({ lat: formatCoordinate(p.lat), lng: formatCoordinate(p.lng) })}
          />
        </Suspense>
      ) : (
        <p className="text-xs text-muted-foreground">
          Mapa base não configurado neste ambiente (VITE_MAP_STYLE_URL). Use os campos acima.
        </p>
      )}
    </fieldset>
  )
}

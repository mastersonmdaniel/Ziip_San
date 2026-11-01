import { useEffect, useRef } from 'react'
import * as maplibregl from 'maplibre-gl'
import 'maplibre-gl/dist/maplibre-gl.css'
// O worker padrão da v6 é resolvido como ./maplibre-gl-worker.mjs relativo ao
// import.meta.url do chunk — arquivo que o build do Vite NÃO emite em dist/assets,
// então o mapa falharia ao criar o worker em produção (e no dev). Importar o
// worker como asset (?url) faz o Vite emiti-lo com hash e apontar o worker para
// a URL real, antes de qualquer Map ser construído.
import maplibreWorkerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?url'

maplibregl.setWorkerUrl(maplibreWorkerUrl)
import { BRAZIL_BOUNDS, isInsideBrazil, type LatLng } from '@utils/validators/geo'

interface MapCanvasProps {
  styleUrl: string
  point: LatLng | null
  disabled?: boolean
  onPick: (point: LatLng) => void
}

/**
 * Mapa MapLibre carregado sob demanda. Clique ou arraste o marcador para escolher o ponto.
 * Complemento visual: a entrada por teclado é feita nos campos de latitude/longitude.
 */
export default function MapCanvas({ styleUrl, point, disabled, onPick }: MapCanvasProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const mapRef = useRef<maplibregl.Map | null>(null)
  const markerRef = useRef<maplibregl.Marker | null>(null)
  const onPickRef = useRef(onPick)
  onPickRef.current = onPick

  useEffect(() => {
    if (!containerRef.current) return undefined
    const center: [number, number] = point ? [point.lng, point.lat] : [-52, -14]
    const map = new maplibregl.Map({
      container: containerRef.current,
      style: styleUrl,
      center,
      zoom: point ? 15 : 3.5,
      maxBounds: [
        [BRAZIL_BOUNDS.lngMin - 2, BRAZIL_BOUNDS.latMin - 2],
        [BRAZIL_BOUNDS.lngMax + 2, BRAZIL_BOUNDS.latMax + 2],
      ],
    })
    map.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'top-right')
    mapRef.current = map
    return () => {
      map.remove()
      mapRef.current = null
      markerRef.current = null
    }
    // O mapa é criado uma vez; o marcador é sincronizado no efeito abaixo.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [styleUrl])

  useEffect(() => {
    const map = mapRef.current
    if (!map) return undefined
    const handleClick = (event: maplibregl.MapMouseEvent) => {
      if (disabled) return
      const picked = { lat: event.lngLat.lat, lng: event.lngLat.lng }
      if (isInsideBrazil(picked)) onPickRef.current(picked)
    }
    map.on('click', handleClick)
    return () => {
      map.off('click', handleClick)
    }
  }, [disabled])

  useEffect(() => {
    const map = mapRef.current
    if (!map) return
    if (!point) {
      markerRef.current?.remove()
      markerRef.current = null
      return
    }
    if (!markerRef.current) {
      const marker = new maplibregl.Marker({ draggable: !disabled }).setLngLat([point.lng, point.lat]).addTo(map)
      marker.on('dragend', () => {
        const p = marker.getLngLat()
        if (isInsideBrazil({ lat: p.lat, lng: p.lng })) onPickRef.current({ lat: p.lat, lng: p.lng })
      })
      markerRef.current = marker
    } else {
      markerRef.current.setLngLat([point.lng, point.lat])
    }
    map.easeTo({ center: [point.lng, point.lat], duration: 300 })
  }, [point, disabled])

  return (
    <div
      ref={containerRef}
      role="region"
      aria-label="Mapa para marcar o ponto. Para informar por teclado, use os campos de latitude e longitude."
      className="h-64 w-full overflow-hidden rounded-lg border"
    />
  )
}

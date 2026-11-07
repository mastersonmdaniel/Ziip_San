/**
 * GIS-06: tratamento do documento de estilo MapLibre no navegador.
 *
 * O backend gera o estilo da fonte única (gis_style) com as cores
 * como marcadores "token:map-*" — os valores concretos são os
 * tokens map-* do ADR-0017 na paleta do app (variáveis CSS
 * --map-*), diferentes nos temas claro e escuro. Aqui os marcadores
 * viram cores do tema corrente SEM tocar nas demais propriedades,
 * e as trocas de tema reaplicam a resolução sobre o documento
 * original (o marcador nunca é perdido: trabalhamos sobre uma
 * cópia profunda a cada aplicação).
 */

export const MAP_TOKENS = [
  'map-water',
  'map-sewer',
  'map-storm',
  'map-waste',
  'map-context',
  'map-abandoned',
  'map-label-halo',
] as const

export type MapToken = (typeof MAP_TOKENS)[number]

/** Lê os tokens map-* das variáveis CSS do tema corrente. */
export function readMapTokens(
  getPropertyValue: (name: string) => string = (name) =>
    getComputedStyle(document.documentElement).getPropertyValue(name),
): Record<string, string> {
  const tokens: Record<string, string> = {}
  for (const token of MAP_TOKENS) {
    const value = getPropertyValue(`--${token}`).trim()
    if (value) tokens[token] = value
  }
  return tokens
}

const TOKEN_PREFIX = 'token:'

/**
 * Substitui "token:map-x" pela cor do tema em qualquer profundidade
 * do documento. Token sem cor definida cai para o fallback (cinza
 * neutro) — nunca para uma cor inventada por domínio.
 */
export function resolveTokenColors<T>(node: T, tokens: Record<string, string>, fallback = '#808080'): T {
  if (typeof node === 'string') {
    if (node.startsWith(TOKEN_PREFIX)) {
      const name = node.slice(TOKEN_PREFIX.length)
      return (tokens[name] ?? fallback) as T
    }
    return node
  }
  if (Array.isArray(node)) {
    return node.map((item) => resolveTokenColors(item, tokens, fallback)) as T
  }
  if (node !== null && typeof node === 'object') {
    const out: Record<string, unknown> = {}
    for (const [key, value] of Object.entries(node)) {
      out[key] = resolveTokenColors(value, tokens, fallback)
    }
    return out as T
  }
  return node
}

export interface StyleLayerLike {
  id: string
  source?: string
  'source-layer'?: string
  layout?: Record<string, unknown>
}

/**
 * Visibilidade por grupo de camada: devolve o mapa camada ->
 * 'visible' | 'none' para aplicar com setLayoutProperty, sem
 * reconstruir o estilo. Camadas de rótulo (-labels) seguem a chave
 * labelsVisible; fontes WMS e mapa base não são tocadas aqui.
 */
export function computeLayerVisibility(
  layers: StyleLayerLike[],
  visibleLayerKeys: ReadonlySet<string>,
  labelsVisible: boolean,
): Record<string, 'visible' | 'none'> {
  const visibility: Record<string, 'visible' | 'none'> = {}
  for (const layer of layers) {
    const sourceLayer = layer['source-layer']
    if (!sourceLayer) continue
    if (layer.id.endsWith('-labels')) {
      visibility[layer.id] =
        labelsVisible && visibleLayerKeys.has(sourceLayer) ? 'visible' : 'none'
    } else {
      visibility[layer.id] = visibleLayerKeys.has(sourceLayer) ? 'visible' : 'none'
    }
  }
  return visibility
}

/** URL de tile WMS (espelho do GisMapLibreStyleFactory do backend). */
export function buildWmsTileUrl(source: {
  url: string
  layers: string
  version: string
  format: string
  transparent: boolean
}): string {
  const separator = source.url.includes('?') ? '&' : '?'
  return (
    `${source.url}${separator}SERVICE=WMS&VERSION=${source.version}` +
    `&REQUEST=GetMap&LAYERS=${source.layers}&FORMAT=${source.format}` +
    `&TRANSPARENT=${source.transparent ? 'true' : 'false'}` +
    '&WIDTH=256&HEIGHT=256&CRS=EPSG:3857&STYLES=&BBOX={bbox-epsg-3857}'
  )
}

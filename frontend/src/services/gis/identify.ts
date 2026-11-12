import { LAYER_TABLE } from '@services/gis/map'

/**
 * Identificação multi-camadas por clique (PDCA GIS Ciclo 2, lacuna
 * E2 do anexo à Rodada 04): dado o conjunto de feições renderizadas
 * sob o ponto do clique, listar TODAS as que têm ficha GIS — antes o
 * GisMap ficava com a primeira (`features.find(...)`) e as restantes
 * camadas activas sob o ponto ficavam invisíveis para o utilizador.
 *
 * Lógica pura e testável, sem MapLibre: a entrada é a forma mínima
 * de uma feição renderizada (sourceLayer + properties.id).
 *
 * Regras:
 * - só entram camadas com ficha (LAYER_TABLE é a lista fechada);
 * - feição sem properties.id não é identificável — fica de fora;
 * - duplicados da mesma feição (a mesma tabela+id pode vir em mais
 *   de uma camada de estilo) saem uma vez;
 * - a ordem é a ordem de renderização que o MapLibre devolve (de
 *   cima para baixo no mapa) — é a ordem em que o utilizador vê as
 *   camadas empilhadas, e é determinística para o mesmo clique.
 *
 * Paridade mobile (decisão 73): esta lógica serve a página web
 * responsiva no celular; o app de campo não identifica feições por
 * clique web (executa OS de pacotes — ver clickOs.ts).
 */
export interface RenderedFeatureLike {
  sourceLayer?: string
  properties?: Record<string, unknown> | null
}

export interface IdentifiedCandidate {
  /** Camada de origem no estilo (chave de LAYER_TABLE). */
  sourceLayer: string
  /** Tabela física da feição (valor de LAYER_TABLE). */
  table: string
  id: string
  /** Código da feição, quando a camada o expõe nas properties. */
  code: string | null
}

export function collectIdentifiedFeatures(
  features: readonly RenderedFeatureLike[],
): IdentifiedCandidate[] {
  const seen = new Set<string>()
  const candidates: IdentifiedCandidate[] = []
  for (const feature of features) {
    const sourceLayer = feature.sourceLayer
    if (!sourceLayer) continue
    const table = LAYER_TABLE[sourceLayer]
    if (!table) continue
    const rawId = feature.properties?.id
    if (typeof rawId !== 'string' || rawId.length === 0) continue
    const key = `${table}::${rawId}`
    if (seen.has(key)) continue
    seen.add(key)
    const rawCode = feature.properties?.code
    candidates.push({
      sourceLayer,
      table,
      id: rawId,
      code: typeof rawCode === 'string' && rawCode.length > 0 ? rawCode : null,
    })
  }
  return candidates
}

/**
 * Há alguma feição sob o ponto em camada SEM ficha GIS? Mantém o
 * aviso que já existia (correção B3): camada de contexto/imóveis não
 * é silêncio — avisa que a consulta é noutro sítio.
 */
export function hasUnidentifiedLayer(features: readonly RenderedFeatureLike[]): boolean {
  return features.some((f) => Boolean(f.sourceLayer) && !collectHasTable(f))
}

function collectHasTable(feature: RenderedFeatureLike): boolean {
  return Boolean(feature.sourceLayer && LAYER_TABLE[feature.sourceLayer])
}

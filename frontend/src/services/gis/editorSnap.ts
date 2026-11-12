// Snap do editor web GIS (E2-SNAP, Ciclo 3 do PDCA GIS): logica
// pura que encaixa um ponto de clique/arrasto no vertice mais
// proximo de um conjunto de alvos ou, nao havendo vertice dentro
// da tolerancia, na projecao sobre o segmento (borda) mais
// proximo. Sem dependencia de biblioteca de mapa: trabalha em
// qualquer espaco cartesiano — unidades da geometria na
// superficie local do editor e pixeis na superficie do MapLibre
// (quem chama converte; a tolerancia e sempre do espaco dado).
//
// Antes deste modulo, o snap no editor so existia como encaixe
// manual das extremidades no modo "unir arcos" (mergeLines de
// utils/geometry.ts); na revisao de pacotes e na importacao o
// snap e de servidor. Aqui ele passa a valer ao desenhar e ao
// arrastar vertices no editor.
import { dist, type Pt } from "../../utils/geometry";

/** Uma polilinha alvo de snap (vertices na ordem). */
export interface SnapTarget {
  points: Pt[];
}

export interface SnapResult {
  /** Ponto final: o alvo do snap, ou o ponto original sem snap. */
  point: Pt;
  /** "vertex" (vertice), "edge" (borda) ou null (sem snap). */
  kind: "vertex" | "edge" | null;
  /** Distancia ao alvo no espaco dado; null quando nao ha snap. */
  distance: number | null;
}

function projectOnSegment(a: Pt, b: Pt, p: Pt): Pt {
  const dx = b.x - a.x;
  const dy = b.y - a.y;
  const len2 = dx * dx + dy * dy;
  if (len2 === 0) return a;
  const t = Math.max(0, Math.min(1, ((p.x - a.x) * dx + (p.y - a.y) * dy) / len2));
  return { x: a.x + t * dx, y: a.y + t * dy };
}

/**
 * Encaixa `at` nos alvos: vertice mais proximo dentro da
 * tolerancia tem prioridade (mesmo que uma borda esteja mais
 * perto — ligar a um no existente e a intencao topologica
 * forte); sem vertice, vale a projecao mais proxima sobre um
 * segmento. Tolerancia nao positiva nunca encaixa.
 */
export function snapPoint(at: Pt, targets: SnapTarget[], tolerance: number): SnapResult {
  const none: SnapResult = { point: at, kind: null, distance: null };
  if (!(tolerance > 0) || targets.length === 0) return none;

  let bestVertex: Pt | null = null;
  let bestVertexDist = Number.POSITIVE_INFINITY;
  let bestEdge: Pt | null = null;
  let bestEdgeDist = Number.POSITIVE_INFINITY;

  for (const target of targets) {
    const pts = target.points;
    for (let i = 0; i < pts.length; i++) {
      const d = dist(at, pts[i]);
      if (d < bestVertexDist) {
        bestVertexDist = d;
        bestVertex = pts[i];
      }
      if (i + 1 < pts.length) {
        const proj = projectOnSegment(pts[i], pts[i + 1], at);
        const de = dist(at, proj);
        if (de < bestEdgeDist) {
          bestEdgeDist = de;
          bestEdge = proj;
        }
      }
    }
  }

  if (bestVertex !== null && bestVertexDist <= tolerance) {
    return { point: bestVertex, kind: "vertex", distance: bestVertexDist };
  }
  if (bestEdge !== null && bestEdgeDist <= tolerance) {
    return { point: bestEdge, kind: "edge", distance: bestEdgeDist };
  }
  return none;
}

/** Geometria GeoJSON estrutural (sem depender dos tipos do MapLibre). */
export interface GeoJsonGeometryLike {
  type: string;
  coordinates?: unknown;
  geometries?: GeoJsonGeometryLike[];
}

function toPt(coord: unknown): Pt | null {
  if (!Array.isArray(coord) || coord.length < 2) return null;
  const [x, y] = coord;
  if (typeof x !== "number" || typeof y !== "number") return null;
  if (!Number.isFinite(x) || !Number.isFinite(y)) return null;
  return { x, y };
}

function toPolyline(coords: unknown): Pt[] {
  if (!Array.isArray(coords)) return [];
  const out: Pt[] = [];
  for (const c of coords) {
    const p = toPt(c);
    if (p) out.push(p);
  }
  return out;
}

/**
 * Extrai as polilinhas alvo de snap de uma geometria GeoJSON
 * (a das feicoes renderizadas pelo MapLibre): Point/MultiPoint
 * viram vertices isolados; LineString/MultiLineString, as
 * linhas; Polygon/MultiPolygon, os aneis; GeometryCollection,
 * a uniao das partes. Geometria desconhecida ou malformada nao
 * inventa alvos: devolve vazio.
 */
export function polylinesFromGeoJsonGeometry(
  geometry: GeoJsonGeometryLike | null | undefined,
): Pt[][] {
  if (!geometry || typeof geometry.type !== "string") return [];
  const coords = geometry.coordinates;
  switch (geometry.type) {
    case "Point": {
      const p = toPt(coords);
      return p ? [[p]] : [];
    }
    case "MultiPoint":
      return Array.isArray(coords)
        ? coords.map((c) => toPolyline([c])).filter((l) => l.length > 0)
        : [];
    case "LineString": {
      const line = toPolyline(coords);
      return line.length > 0 ? [line] : [];
    }
    case "MultiLineString":
      return Array.isArray(coords)
        ? coords.map((c) => toPolyline(c)).filter((l) => l.length > 0)
        : [];
    case "Polygon":
      return Array.isArray(coords)
        ? coords.map((ring) => toPolyline(ring)).filter((l) => l.length > 0)
        : [];
    case "MultiPolygon":
      return Array.isArray(coords)
        ? coords.flatMap((poly) =>
            Array.isArray(poly)
              ? poly.map((ring) => toPolyline(ring)).filter((l) => l.length > 0)
              : [],
          )
        : [];
    case "GeometryCollection":
      return Array.isArray(geometry.geometries)
        ? geometry.geometries.flatMap((g) => polylinesFromGeoJsonGeometry(g))
        : [];
    default:
      return [];
  }
}

// Geometria pura do editor web (GIS-09, ED-53) — espelho TS de
// GisGeom (Java): a tela pre-visualiza mover/rotacionar/escalar,
// dividir, unir e inserir no com a mesma matematica que a api
// confere. Sem dependencia de biblioteca de mapa.
export interface Pt { x: number; y: number }

export function translate(shape: Pt[], dx: number, dy: number): Pt[] {
  return shape.map((p) => ({ x: p.x + dx, y: p.y + dy }));
}

export function rotate(shape: Pt[], pivot: Pt, degrees: number): Pt[] {
  const rad = (degrees * Math.PI) / 180;
  const cos = Math.cos(rad);
  const sin = Math.sin(rad);
  return shape.map((p) => {
    const dx = p.x - pivot.x;
    const dy = p.y - pivot.y;
    return { x: pivot.x + dx * cos - dy * sin, y: pivot.y + dx * sin + dy * cos };
  });
}

export function scale(shape: Pt[], pivot: Pt, factor: number): Pt[] {
  if (!(factor > 0)) throw new Error("fator de escala deve ser positivo");
  return shape.map((p) => ({
    x: pivot.x + (p.x - pivot.x) * factor,
    y: pivot.y + (p.y - pivot.y) * factor,
  }));
}

export function centroid(shape: Pt[]): Pt {
  const n = shape.length;
  const count =
    n > 1 && shape[0].x === shape[n - 1].x && shape[0].y === shape[n - 1].y
      ? n - 1
      : n;
  let sx = 0;
  let sy = 0;
  for (let i = 0; i < count; i++) { sx += shape[i].x; sy += shape[i].y; }
  return { x: sx / count, y: sy / count };
}

export function dist(a: Pt, b: Pt): number {
  return Math.hypot(a.x - b.x, a.y - b.y);
}

function projectOnSegment(a: Pt, b: Pt, p: Pt): Pt {
  const dx = b.x - a.x;
  const dy = b.y - a.y;
  const len2 = dx * dx + dy * dy;
  if (len2 === 0) return a;
  const t = Math.max(0, Math.min(1, ((p.x - a.x) * dx + (p.y - a.y) * dy) / len2));
  return { x: a.x + t * dx, y: a.y + t * dy };
}

export interface SplitResult { first: Pt[]; second: Pt[]; node: Pt }

export function splitLineAt(line: Pt[], at: Pt, tolerance: number): SplitResult {
  let bestSeg = -1;
  let bestProj: Pt | null = null;
  let bestDist = Number.MAX_VALUE;
  for (let i = 0; i + 1 < line.length; i++) {
    const proj = projectOnSegment(line[i], line[i + 1], at);
    const d = dist(at, proj);
    if (d < bestDist) { bestDist = d; bestSeg = i; bestProj = proj; }
  }
  if (bestSeg < 0 || bestDist > tolerance || bestProj === null) {
    throw new Error("ponto de divisao fora do arco");
  }
  // Correcao D6 (revisao ED-53), espelho fiel do GisGeom Java:
  // projecao colada numa extremidade (distancia <= tolerancia)
  // e recusada — geraria arco de comprimento ~0 (degenerado).
  if (
    dist(line[0], bestProj) <= tolerance ||
    dist(line[line.length - 1], bestProj) <= tolerance
  ) {
    throw new Error("ponto de divisao na extremidade do arco");
  }
  // Divisao exatamente num vertice existente (espelho do Java):
  // o no e o vertice e a projecao NAO e acrescentada de novo —
  // sem ponto duplicado nos dois trechos.
  for (let i = 1; i + 1 < line.length; i++) {
    if (dist(line[i], bestProj) <= tolerance) {
      return {
        first: line.slice(0, i + 1),
        second: line.slice(i),
        node: line[i],
      };
    }
  }
  const first = [...line.slice(0, bestSeg + 1), bestProj];
  const second = [bestProj, ...line.slice(bestSeg + 1)];
  return { first, second, node: bestProj };
}

// Inserir no que segmenta: mesma operacao do split (espelho de
// GisGeom.insertNodeAt).
export function insertNodeAt(line: Pt[], at: Pt, tolerance: number): SplitResult {
  return splitLineAt(line, at, tolerance);
}

// Mover um vertice pelo indice (espelho de GisGeom.moveVertex).
export function moveVertex(shape: Pt[], index: number, to: Pt): Pt[] {
  if (index < 0 || index >= shape.length) {
    throw new Error("vertice fora da geometria");
  }
  const out = [...shape];
  out[index] = to;
  return out;
}

// WKT minimo para pre-visualizacao local (espelho dos formatos
// que GisGeom emite: 7 casas decimais).
function fmt(v: number): string {
  return v.toFixed(7);
}

export function toPointWkt(p: Pt): string {
  return `POINT(${fmt(p.x)} ${fmt(p.y)})`;
}

export function toLineWkt(line: Pt[]): string {
  if (line.length < 2) throw new Error("linha exige ao menos 2 vertices");
  return `LINESTRING(${line.map((p) => `${fmt(p.x)} ${fmt(p.y)}`).join(", ")})`;
}

export function toPolygonWkt(ring: Pt[]): string {
  const first = ring[0];
  const last = ring[ring.length - 1];
  if (ring.length < 4 || !first || !last || first.x !== last.x || first.y !== last.y) {
    throw new Error("anel de poligono exige ao menos 4 pontos e fechamento");
  }
  return `POLYGON((${ring.map((p) => `${fmt(p.x)} ${fmt(p.y)}`).join(", ")}))`;
}

export function mergeLines(a: Pt[], b: Pt[], tolerance: number): Pt[] {
  const a0 = a[0]; const aN = a[a.length - 1];
  const b0 = b[0]; const bN = b[b.length - 1];
  if (dist(aN, b0) <= tolerance) return [...a, ...b.slice(1)];
  if (dist(aN, bN) <= tolerance) return [...a, ...[...b].reverse().slice(1)];
  if (dist(a0, bN) <= tolerance) return [...b, ...a.slice(1)];
  if (dist(a0, b0) <= tolerance) return [...[...a].reverse(), ...b.slice(1)];
  throw new Error("arcos sem extremidade comum");
}

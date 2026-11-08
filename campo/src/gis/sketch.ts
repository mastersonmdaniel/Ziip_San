/**
 * Croqui de campo (ED-66 / GIS-15, §8): croqui VETORIAL com amarracoes ao
 * alinhamento predial e ao meio-fio (padrao Corsan, 19b §3.3) — o mesmo
 * par de referencias que o gis_arc guarda como distancias (§3.6.2).
 * O croqui e um gis_attachment vetorial: tracos (polilinhas em
 * coordenadas LOCAIS do croqui, metros a partir de uma origem declarada)
 * e amarracoes medidas. Nenhuma coordenada geografica e inventada aqui:
 * o croqui vive no seu plano local e quem o ancora no mundo e o fix da
 * feicao, registrado no delta.
 */

export type TieKind = 'BUILDING_ALIGNMENT' | 'CURB';

export interface SketchTie {
  kind: TieKind;
  /** Rotulo da referencia (ex.: esquina do lote, meio-fio da rua) — texto do executor. */
  referenceLabel: string;
  /** Distancia medida da feicao a referencia, em metros. */
  distanceM: number;
}

export interface SketchStroke {
  /** Pontos do traco no plano local do croqui (metros, origem declarada). */
  points: { x: number; y: number }[];
  label: string | null;
}

export interface FieldSketch {
  featureId: string;
  strokes: SketchStroke[];
  ties: SketchTie[];
  capturedAt: string;
}

export type SketchProblem =
  | 'NO_STROKES' | 'STROKE_TOO_SHORT' | 'NO_TIES' | 'TIE_DISTANCE_NEGATIVE' | 'TIE_LABEL_MISSING';

/**
 * Croqui SUBMETIVEL: ao menos um traco com 2+ pontos e ao menos uma
 * amarracao com distancia e referencia — croqui sem amarracao nao cumpre
 * o §8 (e rascunho local, nao anexo de pacote).
 */
export function sketchProblems(sketch: FieldSketch): SketchProblem[] {
  const problems: SketchProblem[] = [];
  if (sketch.strokes.length === 0) problems.push('NO_STROKES');
  if (sketch.strokes.some((s) => s.points.length < 2)) problems.push('STROKE_TOO_SHORT');
  if (sketch.ties.length === 0) problems.push('NO_TIES');
  if (sketch.ties.some((t) => !(t.distanceM >= 0))) problems.push('TIE_DISTANCE_NEGATIVE');
  if (sketch.ties.some((t) => !t.referenceLabel || t.referenceLabel.trim() === '')) {
    problems.push('TIE_LABEL_MISSING');
  }
  return problems;
}

export function isSketchSubmittable(sketch: FieldSketch): boolean {
  return sketchProblems(sketch).length === 0;
}

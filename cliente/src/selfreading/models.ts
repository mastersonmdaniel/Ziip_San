/**
 * Logica pura da tela de autoleitura (sem DOM, sem rede). Cada validacao
 * espelha o construtor compacto do record Java dono do contrato — a
 * tela recusa localmente o que o nucleo recusaria, sem regra nova.
 * Fontes: ver transport.ts (ficheiro:record de cada tipo).
 */
import type {
  GeoPoint,
  GeoProof,
  OcrExtraction,
  PhotoArtifact,
  SelfReadingOutcome,
  SelfReadingReason,
  SelfReadingScoreConfig,
  SelfReadingStatus,
  SelfReadingSubmission,
} from './transport.ts';

/** PhotoArtifact.java (compacto): ref nao vazia, hash 64 hex, dimensoes > 0. */
export function validatePhoto(photo: PhotoArtifact): string | null {
  if (!photo.artifactRef.trim()) return 'Informe a referência da foto.';
  if (!/^[0-9a-fA-F]{64}$/.test(photo.sha256Hex)) {
    return 'O hash da foto precisa de 64 caracteres hexadecimais.';
  }
  if (!(photo.widthPx > 0) || !(photo.heightPx > 0)) {
    return 'As dimensões da foto precisam ser maiores que zero.';
  }
  return null;
}

/** GeoPoint.java (compacto): finita, lat -90..90, lon -180..180. */
export function validateGeoPoint(p: GeoPoint): string | null {
  if (!Number.isFinite(p.latitude) || !Number.isFinite(p.longitude)) {
    return 'Coordenada não finita não é posição.';
  }
  if (p.latitude < -90 || p.latitude > 90) return 'Latitude fora de -90..90.';
  if (p.longitude < -180 || p.longitude > 180) return 'Longitude fora de -180..180.';
  return null;
}

/** GeoProof.java (compacto): precisao finita e >= 0. */
export function validateGeoProof(proof: GeoProof): string | null {
  const pointError = validateGeoPoint(proof.position);
  if (pointError) return pointError;
  if (!Number.isFinite(proof.accuracyMetres) || proof.accuracyMetres < 0) {
    return 'A precisão do GPS precisa ser finita e maior ou igual a zero.';
  }
  if (!proof.capturedAt.trim()) return 'Informe o instante da captura da prova geográfica.';
  return null;
}

/**
 * SelfReadingSubmission.java (compacto): id/unitId/reference obrigatorios,
 * historico so com consumos >= 0, declarada/anterior >= 0 quando presentes.
 * Foto ausente NAO e erro local: o nucleo roteia sem foto para
 * PENDING_HUMAN_REVIEW/PHOTO_MISSING (SelfReadingIntakeService).
 */
export function validateSubmission(s: SelfReadingSubmission): string | null {
  if (!s.id.trim()) return 'Informe o identificador da submissão.';
  if (!s.unitId.trim()) return 'Informe a unidade consumidora.';
  if (!/^\d{4}-\d{2}$/.test(s.reference)) return 'Informe a competência (AAAA-MM).';
  if (s.declaredReading !== null && (!Number.isInteger(s.declaredReading) || s.declaredReading < 0)) {
    return 'A leitura declarada não pode ser negativa.';
  }
  if (s.previousReading !== null && (!Number.isInteger(s.previousReading) || s.previousReading < 0)) {
    return 'A leitura anterior não pode ser negativa.';
  }
  if (s.history.some((v) => !Number.isInteger(v) || v < 0)) {
    return 'O histórico só aceita consumos maiores ou iguais a zero.';
  }
  if (s.photo !== null) {
    const e = validatePhoto(s.photo);
    if (e) return e;
  }
  if (s.geoProof !== null) {
    const e = validateGeoProof(s.geoProof);
    if (e) return e;
  }
  if (s.connectionPosition !== null) {
    const e = validateGeoPoint(s.connectionPosition);
    if (e) return e;
  }
  return null;
}

/** SelfReadingScoreConfig.java (compacto): pesos em [0,1] somando exactamente 1, limiar [0,100], distancias > 0 finitas. */
export function validateScoreConfig(c: SelfReadingScoreConfig): string | null {
  const weights = [c.weightOcr, c.weightCoherence, c.weightPhoto, c.weightGeo];
  if (weights.some((w) => !Number.isFinite(w) || w < 0 || w > 1)) {
    return 'Cada peso do score precisa estar entre 0 e 1.';
  }
  // Soma em centesimos: os pesos do nucleo sao decimais exactos (BigDecimal).
  const sumCents = weights.reduce((acc, w) => acc + Math.round(w * 100), 0);
  if (sumCents !== 100) return 'Os 4 pesos do score têm de somar exactamente 1.';
  if (!Number.isFinite(c.acceptThreshold) || c.acceptThreshold < 0 || c.acceptThreshold > 100) {
    return 'O limiar de aceite precisa estar entre 0 e 100.';
  }
  if (!(c.maxDistanceMetres > 0) || !Number.isFinite(c.maxDistanceMetres)) {
    return 'A distância máxima precisa ser finita e maior que zero.';
  }
  if (!(c.maxAccuracyMetres > 0) || !Number.isFinite(c.maxAccuracyMetres)) {
    return 'A precisão máxima precisa ser finita e maior que zero.';
  }
  return null;
}

/** OcrExtraction.java (compacto): EXTRACTED exige leitura/confianca; NO_READING nao carrega nenhuma. */
export function validateOcrExtraction(e: OcrExtraction): string | null {
  if (!e.engineId.trim()) return 'O identificador do motor OCR não pode ser vazio.';
  if (e.status === 'EXTRACTED') {
    if (e.reading === null || e.reading < 0) return 'A leitura OCR é obrigatória e não pode ser negativa.';
    if (e.confidence === null || e.confidence < 0 || e.confidence > 1) {
      return 'A confiança OCR precisa estar entre 0 e 1.';
    }
    return null;
  }
  if (e.reading !== null || e.confidence !== null) {
    return 'Sem leitura, nenhuma leitura nem confiança é apresentada.';
  }
  return null;
}

/** Rotulos derivados dos Javadoc dos enums Java (sem texto de negocio novo). */
export const STATUS_LABELS: Record<SelfReadingStatus, string> = {
  ACCEPTED_FOR_CRITIQUE: 'Aceita para a crítica',
  PENDING_HUMAN_REVIEW: 'Aguarda revisão humana',
  PENDING_OCR: 'Aguarda leitura da foto (OCR)',
};

export const REASON_LABELS: Record<SelfReadingReason, string> = {
  NO_OCR_ADAPTER: 'Sem motor de leitura da foto nesta instalação',
  OCR_UNAVAILABLE: 'Motor de leitura da foto indisponível',
  PHOTO_MISSING: 'Submissão sem foto',
  OCR_NO_READING: 'A foto não permitiu ler o mostrador',
  SCORE_BELOW_THRESHOLD: 'Score abaixo do limiar de aceite',
  GEO_BEYOND_MAX_DISTANCE: 'Distância à ligação além do máximo configurado',
  GEO_ACCURACY_INSUFFICIENT: 'Precisão do GPS acima da máxima configurada',
  GEO_PROOF_MISSING: 'Sem prova geográfica ou sem coordenadas da ligação',
  DECLARED_READING_DIVERGES: 'Leitura declarada diverge da leitura da foto',
  ACCEPTED: 'Aceite: score no limiar ou acima e prova dentro do filtro',
};

/**
 * HumanReviewQueue.pending(): ordem deterministica (competencia,
 * unidade, submissao) — a mesma comparacao da fila Java, sobre strings.
 */
export function sortReviewQueue(items: SelfReadingOutcome[]): SelfReadingOutcome[] {
  return [...items].sort((a, b) =>
    a.reference.localeCompare(b.reference)
    || a.unitId.localeCompare(b.unitId)
    || a.submissionId.localeCompare(b.submissionId));
}

/** HumanReviewQueue.enqueue(): so PENDING_HUMAN_REVIEW entra na fila. */
export function isReviewable(o: SelfReadingOutcome): boolean {
  return o.status === 'PENDING_HUMAN_REVIEW';
}

export function formatScore(v: number): string {
  return v.toFixed(2).replace('.', ',');
}

export function formatComponent(v: number): string {
  return v.toFixed(2).replace('.', ',');
}

export function formatDistance(v: number | null): string {
  return v === null ? 'não medida' : `${v.toFixed(1).replace('.', ',')} m`;
}

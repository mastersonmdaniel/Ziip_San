/**
 * Porta tipada da autoleitura do consumidor (P-II7-TELA, II-7).
 * Espelha campo a campo os records do nucleo Java
 * operations/.../metering/selfreading/ (main 4b58e550):
 *  - SelfReadingSubmission.java:9-19, PhotoArtifact.java:16,
 *    GeoProof.java:13, GeoPoint.java:11, GeoProofResult.java:13,
 *    ReadingScore.java:8, OcrExtraction.java:16, OcrStatus.java,
 *    SelfReadingStatus.java, SelfReadingReason.java,
 *    SelfReadingOutcome.java:11, CritiqueHandoff.java:19,
 *    SelfReadingScoreConfig.java:20, SelfReadingParameters.java.
 * NENHUMA rota HTTP e afirmada: nao ha controlador destes nucleos na
 * main. A fiacao HTTP e a pendencia P-II7-FIACAO. O motor OCR real e
 * P-II7-OCR [SEM FONTE] — entra pela mesma OcrPort.
 * BigDecimal/Java double chegam como number; YearMonth como "AAAA-MM";
 * Instant como string ISO; UUID como string.
 */

export type SelfReadingStatus =
  | 'ACCEPTED_FOR_CRITIQUE'
  | 'PENDING_HUMAN_REVIEW'
  | 'PENDING_OCR';

export type SelfReadingReason =
  | 'NO_OCR_ADAPTER'
  | 'OCR_UNAVAILABLE'
  | 'PHOTO_MISSING'
  | 'OCR_NO_READING'
  | 'SCORE_BELOW_THRESHOLD'
  | 'GEO_BEYOND_MAX_DISTANCE'
  | 'GEO_ACCURACY_INSUFFICIENT'
  | 'GEO_PROOF_MISSING'
  | 'DECLARED_READING_DIVERGES'
  | 'ACCEPTED';

export type OcrStatus = 'EXTRACTED' | 'NO_READING';

/** GeoPoint.java: record(double latitude, double longitude). */
export interface GeoPoint {
  latitude: number;
  longitude: number;
}

/** PhotoArtifact.java: record(String, String, int, int). */
export interface PhotoArtifact {
  artifactRef: string;
  sha256Hex: string;
  widthPx: number;
  heightPx: number;
}

/** GeoProof.java: record(GeoPoint, double, Instant). */
export interface GeoProof {
  position: GeoPoint;
  accuracyMetres: number;
  capturedAt: string;
}

/** GeoProofResult.java: record(Double, boolean, boolean, boolean). */
export interface GeoProofResult {
  distanceMetres: number | null;
  withinMaxDistance: boolean;
  accuracySufficient: boolean;
  present: boolean;
}

/** ReadingScore.java: record(BigDecimal x5). */
export interface ReadingScore {
  total: number;
  ocrComponent: number;
  coherenceComponent: number;
  photoComponent: number;
  geoComponent: number;
}

/** OcrExtraction.java: record(OcrStatus, Integer, BigDecimal, String). */
export interface OcrExtraction {
  status: OcrStatus;
  reading: number | null;
  confidence: number | null;
  engineId: string;
}

/** CritiqueHandoff.java: record(UUID, UUID, YearMonth, Integer x3, List, BigDecimal, String, Double). */
export interface CritiqueHandoff {
  submissionId: string;
  unitId: string;
  reference: string;
  currentReading: number | null;
  previousReading: number | null;
  measuredConsumption: number | null;
  history: number[];
  scoreTotal: number;
  photoArtifactRef: string;
  geoDistanceMetres: number | null;
}

/** SelfReadingSubmission.java: record(UUID, UUID, YearMonth, Integer, PhotoArtifact, GeoProof, GeoPoint, Integer, List). */
export interface SelfReadingSubmission {
  id: string;
  unitId: string;
  reference: string;
  declaredReading: number | null;
  photo: PhotoArtifact | null;
  geoProof: GeoProof | null;
  connectionPosition: GeoPoint | null;
  previousReading: number | null;
  history: number[];
}

/** SelfReadingOutcome.java: record(UUID, UUID, YearMonth, Status, List<Reason>, ReadingScore, Integer, GeoProofResult, CritiqueHandoff). */
export interface SelfReadingOutcome {
  submissionId: string;
  unitId: string;
  reference: string;
  status: SelfReadingStatus;
  reasons: SelfReadingReason[];
  score: ReadingScore | null;
  ocrReading: number | null;
  geo: GeoProofResult;
  handoff: CritiqueHandoff | null;
}

/** SelfReadingScoreConfig.java: record(BigDecimal x5, double, double). Valores reais [A DEFINIR] (P-II7-LIMIARES). */
export interface SelfReadingScoreConfig {
  weightOcr: number;
  weightCoherence: number;
  weightPhoto: number;
  weightGeo: number;
  acceptThreshold: number;
  maxDistanceMetres: number;
  maxAccuracyMetres: number;
}

/** SelfReadingParameters.java — chaves propostas (registo/fiacao: P-II7-FIACAO). */
export const SELF_READING_PARAMETER_KEYS = {
  scoreAcceptThreshold: 'meter.selfreading_score_accept_threshold',
  geoMaxDistanceMetres: 'meter.selfreading_geo_max_distance_m',
  geoMaxAccuracyMetres: 'meter.selfreading_geo_max_accuracy_m',
  weightOcr: 'meter.selfreading_weight_ocr',
  weightCoherence: 'meter.selfreading_weight_coherence',
  weightPhoto: 'meter.selfreading_weight_photo',
  weightGeo: 'meter.selfreading_weight_geo',
} as const;

/** OcrPort.java — unico caminho por onde uma leitura nasce de uma foto. */
export interface OcrPort {
  extract(photo: PhotoArtifact): Promise<OcrExtraction>;
}

/**
 * Porta da tela: a integracao (P-II7-FIACAO) liga-a ao
 * SelfReadingIntakeService.evaluate(submission, config). Sem ligacao,
 * a tela apresenta a pendencia — nunca fabrica um desfecho.
 */
export interface SelfReadingPort {
  evaluate(
    submission: SelfReadingSubmission,
    config: SelfReadingScoreConfig,
  ): Promise<SelfReadingOutcome>;
}

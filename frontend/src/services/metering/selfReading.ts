/**
 * Porta tipada da fila de revisao humana da autoleitura (P-II7-TELA, II-7).
 * Espelha campo a campo os records do nucleo Java
 * operations/.../metering/selfreading/ (main 4b58e550):
 * SelfReadingOutcome.java:11, SelfReadingStatus.java,
 * SelfReadingReason.java, ReadingScore.java:8, GeoProofResult.java:13,
 * CritiqueHandoff.java:19, HumanReviewQueue.java (pending/size).
 * NENHUMA rota HTTP e afirmada: nao ha controlador deste nucleo na
 * main — a fiacao HTTP e a pendencia P-II7-FIACAO. A DECISAO humana
 * (aceitar/corrigir/pedir nova foto) e fluxo da P-II7-FIACAO: o nucleo
 * nao a tipifica, por isso esta porta so expoe a fila e o detalhe.
 */

export type SelfReadingStatus =
  | 'ACCEPTED_FOR_CRITIQUE'
  | 'PENDING_HUMAN_REVIEW'
  | 'PENDING_OCR'

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
  | 'ACCEPTED'

export interface GeoProofResult {
  distanceMetres: number | null
  withinMaxDistance: boolean
  accuracySufficient: boolean
  present: boolean
}

export interface ReadingScore {
  total: number
  ocrComponent: number
  coherenceComponent: number
  photoComponent: number
  geoComponent: number
}

export interface CritiqueHandoff {
  submissionId: string
  unitId: string
  reference: string
  currentReading: number | null
  previousReading: number | null
  measuredConsumption: number | null
  history: number[]
  scoreTotal: number
  photoArtifactRef: string
  geoDistanceMetres: number | null
}

/** SelfReadingOutcome.java — o desfecho completo de uma submissao. */
export interface SelfReadingOutcome {
  submissionId: string
  unitId: string
  reference: string
  status: SelfReadingStatus
  reasons: SelfReadingReason[]
  score: ReadingScore | null
  ocrReading: number | null
  geo: GeoProofResult
  handoff: CritiqueHandoff | null
}

/** HumanReviewQueue.java: pending() ordenado + size(). */
export interface SelfReadingReviewPort {
  pending(): Promise<SelfReadingOutcome[]>
}

export const STATUS_LABELS: Record<SelfReadingStatus, string> = {
  ACCEPTED_FOR_CRITIQUE: 'Aceita para a crítica',
  PENDING_HUMAN_REVIEW: 'Aguarda revisão humana',
  PENDING_OCR: 'Aguarda leitura da foto (OCR)',
}

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
}

export const ALL_REASONS = Object.keys(REASON_LABELS) as SelfReadingReason[]

/** HumanReviewQueue.pending(): ordem deterministica (competencia, unidade, submissao). */
export function sortReviewQueue(items: SelfReadingOutcome[]): SelfReadingOutcome[] {
  return [...items].sort(
    (a, b) =>
      a.reference.localeCompare(b.reference) ||
      a.unitId.localeCompare(b.unitId) ||
      a.submissionId.localeCompare(b.submissionId),
  )
}

/** Filtro da tela sobre a fila ja ordenada: por motivo declarado (sem regra nova). */
export function filterByReason(
  items: SelfReadingOutcome[],
  reason: SelfReadingReason | '',
): SelfReadingOutcome[] {
  if (reason === '') return items
  return items.filter((o) => o.reasons.includes(reason))
}

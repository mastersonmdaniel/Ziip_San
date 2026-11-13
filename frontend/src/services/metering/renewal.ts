/**
 * P-II5-TELA — porta tipada do plano anual de troca de hidrômetros.
 * Espelha os records de operations/ziipsan-metering/.../hydrometry/renewal/:
 *   AnnualRenewalPlan.java (AnnualPlan, PlanLine, RenewalRestriction,
 *     RenewalCandidate), MeterRenewalSnapshot.java (+ MeterParkStatus),
 *   RenewalScoreEngine.java (ScoredMeter, ScoreBreakdown, Criterion),
 *   RenewalScoreConfig.java (RenewalWeights), MetrologicalClock.java
 *     (ClockAssessment, ClockStatus), RenewalOrderPayload.java,
 *   SelectiveRenewalOrderService.java (RenewalOrderPort/OpenRenewalOrderLookup).
 * NENHUMA rota HTTP é afirmada: sem controlador na main; fiação = pendência
 * existente P-II5-INT-* (não fechada aqui). Restrição anual, pesos e limiares
 * são parâmetros do tenant [A DEFINIR] (P-II5-PARAM) — a tela mostra os que
 * o plano trouxer; nunca presume valores.
 */
export type MeterParkStatus = 'AVAILABLE' | 'INSTALLED' | 'IN_MAINTENANCE' | 'STOLEN' | 'WRITTEN_OFF'
export type ClockStatus = 'OVERDUE' | 'DUE_WITHIN_HORIZON' | 'CURRENT' | 'UNKNOWN_NO_VERIFICATION'
export type Criterion = 'AGE' | 'VOLUME' | 'CLASS_TECHNOLOGY' | 'WATER_QUALITY'

export type RenewalRestriction =
  | { type: 'ByCount'; maxReplacements: number }
  | { type: 'ByBudget'; maxBudget: string }

export interface MeterRenewalSnapshot {
  meterId: string; meterNumber: string; unitId: string; status: MeterParkStatus
  installedOn: string | null; manufactureYear: number | null
  totalizedVolumeM3: number; accuracyClass: number; technologyKey: string
  lastVerificationOn: string | null
  /** null enquanto a base não expuser qualidade da água por hidrômetro. */
  waterQualityAggressiveness: string | null
  replacementCost: string | null
}
export interface ScoreBreakdown {
  ageComponent: string; volumeComponent: string; technologyComponent: string
  waterQualityComponent: string; waterQualityApplied: boolean
  ageContribution: string; volumeContribution: string
  technologyContribution: string; waterQualityContribution: string
}
export interface ScoredMeter {
  snapshot: MeterRenewalSnapshot; breakdown: ScoreBreakdown
  totalScore: string; dominantCriterion: Criterion
}
export interface ClockAssessment {
  meterId: string; lastVerificationOn: string | null
  nextVerificationOn: string | null; status: ClockStatus; daysUntilDue: number | null
}
export interface PlanLine {
  position: number; meterId: string; meterNumber: string; unitId: string
  totalScore: string; dominantCriterion: Criterion; clockStatus: ClockStatus
  nextVerificationOn: string | null; replacementCost: string | null
}
export interface AnnualPlan {
  planYear: number; restriction: RenewalRestriction; minScore: string | null
  lines: PlanLine[]; eligibleCount: number; excludedByStatusCount: number
  excludedBelowMinScoreCount: number; excludedByRestrictionCount: number
  /** null = total desconhecido (linha sem custo) — nunca estimado. */
  totalEstimatedCost: string | null
}
export interface RenewalOrderPayload {
  tenantId: string; planYear: number; position: number; meterId: string
  unitId: string; meterNumber: string; serviceTypeCode: string
  targetClass: string; targetId: string; totalScore: string
  dominantCriterion: Criterion; clockStatus: ClockStatus
  nextVerificationOn: string | null; effectPayloadJson: string
}

/** Porta consumida pela tela (espelha AnnualRenewalPlan + SelectiveRenewalOrderService). */
export interface RenewalPlanPort {
  getPlan(planYear: number): Promise<AnnualPlan | null>
  buildPayloads(tenantId: string, plan: AnnualPlan): Promise<RenewalOrderPayload[]>
}

/**
 * Formata um custo (string decimal do núcleo) em reais (R$, BRL), em pt-BR —
 * moeda decidida pelo dono (decisão 76, 13/11/2026; P-R41-4-09-MOEDA, Opção A:
 * R$ em tudo, incluindo o ramo ByBudget de restrictionLabel). null = sem
 * custo registado ("—"). Valor não numérico é devolvido verbatim, sem
 * prefixo de moeda — nunca descartado nem apresentado como valor em reais.
 * Precisão preservada (R41B-R-02): só a parte inteira é agrupada por milhar
 * e a parte fraccionária é mantida verbatim da string do núcleo — sem passar
 * por Number, logo sem arredondar nem perder precisão acima de 2^53.
 * String vazia (ou só-espaços) é valor não numérico, nunca zero.
 */
export function formatCost(value: string | null): string {
  if (value === null) return '—'
  const match = /^([+-]?)(?:(\d+)(?:\.(\d*))?|\.(\d+))$/.exec(value)
  if (!match) return value
  const sign = match[1]
  const intPart = match[2] ?? '0'
  const fracPart = match[3] ?? match[4]
  const grouped = intPart.replace(/\B(?=(\d{3})+(?!\d))/g, '.')
  const formatted = fracPart ? `${sign}${grouped},${fracPart}` : `${sign}${grouped}`
  return `R$ ${formatted}`
}

export function restrictionLabel(r: RenewalRestriction): string {
  return r.type === 'ByCount'
    ? `Máximo de ${r.maxReplacements} trocas no ano`
    : `Orçamento máximo de ${formatCost(r.maxBudget)}`
}

/**
 * P-II2-TELA — porta tipada da fila de exceções e crítica de leituras.
 * Espelha campo a campo os records do núcleo Java
 * operations/ziipsan-metering/.../metering/critique/ (lidos na main):
 *   CritiqueException.java (unitId, reference, findings, status, correctedReading)
 *   CritiqueResult.java:14, RuleFinding.java:1, ExceptionStatus.java,
 *   CritiqueRuleKind.java, CritiqueRule.java, CritiqueConfig.java,
 *   ReadingSubmission.java, EstimationMethod.java, VeeEstimator.java,
 *   LeakSignal.java / LeakDetectionConfig.java / HourlyConsumption.java.
 * NENHUMA rota HTTP é afirmada: não há controlador destes núcleos na main;
 * a fiação é a pendência existente P-II2-FIACAO (não fechada aqui).
 * Limiares reais: [A DEFINIR] pelo tenant/regulador (P-II2-LIMIARES,
 * CritiqueParameters.java) — proofDefaults() do Java é construção de prova,
 * nunca omissão operacional, e não é reproduzido aqui como valor real.
 */
export type ExceptionStatus = 'PENDING' | 'ACCEPTED' | 'CORRECTED' | 'REREADING_REQUESTED'
export type CritiqueRuleKind =
  | 'NEGATIVE_OR_REGRESSING_READING' | 'ZERO_CONSUMPTION'
  | 'DEVIATION_ABOVE_HISTORY' | 'DEVIATION_BELOW_HISTORY'
  | 'OUTSIDE_ABSOLUTE_BAND' | 'MISSING_READING'
export type EstimationMethod = 'HISTORICAL_MEAN' | 'HISTORICAL_MEDIAN' | 'LAST_VALID'

/** BigDecimal do Java chega como string para não perder precisão. */
export interface RuleFinding {
  ruleCode: string
  kind: CritiqueRuleKind
  observed: string | null
  threshold: string | null
  detail: string
}
/** Espelho de CritiqueException (classe com acessores homónimos). */
export interface CritiqueExceptionView {
  unitId: string
  /** YearMonth ISO "AAAA-MM". */
  reference: string
  findings: RuleFinding[]
  status: ExceptionStatus
  correctedReading: number | null
}
export interface CritiqueResult { unitId: string; reference: string; findings: RuleFinding[] }
export interface CritiqueRule {
  code: string; kind: CritiqueRuleKind; enabled: boolean
  deviationPercent: string | null; bandMin: number | null; bandMax: number | null
}
export interface ReadingSubmission {
  unitId: string; reference: string
  currentReading: number | null; previousReading: number | null
  measuredConsumption: number | null; history: number[]
}
export interface LeakSignal {
  unitId: string; firstNight: string; lastNight: string
  minimumNightMean: string; configuredMinNightFlow: string; consecutiveNights: number
}

/** Porta consumida pela tela (padrão ED-40 DeskPort/KioskIssuePort). */
export interface CritiquePort {
  listExceptions(): Promise<CritiqueExceptionView[]>
  accept(exception: CritiqueExceptionView): Promise<CritiqueExceptionView>
  correct(exception: CritiqueExceptionView, correctedReading: number): Promise<CritiqueExceptionView>
  requestRereading(exception: CritiqueExceptionView): Promise<CritiqueExceptionView>
  estimate(history: number[], method: EstimationMethod): Promise<number>
}

/** Abertas = PENDING ou REREADING_REQUESTED (ExceptionQueue.open()). */
export function isOpenStatus(s: ExceptionStatus): boolean {
  return s === 'PENDING' || s === 'REREADING_REQUESTED'
}
/** Ordem determinística da fila: referência, unitId, código da 1.ª regra. */
export function sortExceptions(list: CritiqueExceptionView[]): CritiqueExceptionView[] {
  return [...list].sort((a, b) =>
    a.reference.localeCompare(b.reference)
    || a.unitId.localeCompare(b.unitId)
    || (a.findings[0]?.ruleCode ?? '').localeCompare(b.findings[0]?.ruleCode ?? ''))
}
/** Transições válidas de CritiqueException: ACCEPTED/CORRECTED são terminais. */
export function allowedTransitions(s: ExceptionStatus): ExceptionStatus[] {
  if (s === 'PENDING') return ['ACCEPTED', 'CORRECTED', 'REREADING_REQUESTED']
  if (s === 'REREADING_REQUESTED') return ['CORRECTED', 'ACCEPTED']
  return []
}

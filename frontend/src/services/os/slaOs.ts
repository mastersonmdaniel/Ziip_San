/**
 * P-II4-TELA — porta tipada de SLA e exigências de encerramento das OS.
 *
 * Espelho campo a campo dos records do núcleo Java na main
 * (`shared/ziipsan-core/.../core/slaos/` e
 * `core/serviceorder/ServiceOrderStatus.java`). NENHUMA rota HTTP é
 * afirmada: não existe controlador REST para este núcleo na main — a
 * fiação é a pendência P-II4-FIACAO, que esta tela NÃO fecha.
 *
 * O catálogo problema–causa–remédio é conteúdo [A DEFINIR] do tenant
 * (pendência P-II4-CATALOGO): o núcleo recebe os dados do catálogo de
 * fora (`InMemoryPcrCatalog(Map)` — "quem os fornece é a configuração
 * do tenant"). A tela mostra o MECANISMO (tripla obrigatória validada
 * contra o catálogo) e o estado "catálogo por configurar"; nunca
 * códigos inventados. Nos testes usam-se apenas os códigos `TST-*` das
 * suites Java do núcleo, nomeados como fixture de teste.
 */

/** TypeSlaConfig (TypeSlaConfig.java:21). Prazos null = não configurados. */
export interface TypeSlaConfig {
  typeCode: string
  internalDeadlineHours: number | null
  regulatorDeadlineHours: number | null
  riskThresholdPercent: number
  escalationThresholdsPercent: number[]
}

/** DualDeadlineClock.DeadlineKind (DualDeadlineClock.java:30). */
export type DeadlineKind = 'INTERNAL' | 'REGULATOR'

/** DualDeadlineClock.DeadlineState (DualDeadlineClock.java:32). */
export type DeadlineState = 'WITHIN' | 'AT_RISK' | 'BREACHED' | 'NOT_CONFIGURED'

/** DualDeadlineClock.DeadlineAssessment (DualDeadlineClock.java:34). */
export interface DeadlineAssessment {
  kind: DeadlineKind
  state: DeadlineState
  dueAtOrNull: string | null
  elapsedSeconds: number
}

/** DualDeadlineClock.DualAssessment (DualDeadlineClock.java:40). */
export interface DualAssessment {
  internal: DeadlineAssessment
  regulator: DeadlineAssessment
}

/** SlaEscalation.EventKind (SlaEscalation.java:24). */
export type SlaEventKind = 'RISK_ALERT' | 'BREACH' | 'ESCALATED'

/** SlaEscalation.SlaEvent (SlaEscalation.java:26). */
export interface SlaEvent {
  kind: SlaEventKind
  deadlineOrNull: DeadlineKind | null
  escalationLevel: number
  at: string
}

/** ProblemCauseRemedy (ProblemCauseRemedy.java:23) — a tripla obrigatória. */
export interface ProblemCauseRemedy {
  problemCode: string
  causeCode: string
  remedyCode: string
}

/** ProblemCauseRemedy.InvalidReason (ProblemCauseRemedy.java:37). */
export type PcrInvalidReason =
  | 'UNKNOWN_PROBLEM'
  | 'CAUSE_NOT_FOR_PROBLEM'
  | 'REMEDY_NOT_FOR_COMBINATION'

/** ServiceOrderStatus (ServiceOrderStatus.java:17). */
export type ServiceOrderStatus =
  | 'DRAFT'
  | 'OPEN'
  | 'SCHEDULED'
  | 'IN_EXECUTION'
  | 'EXECUTED'
  | 'WAITING_DEPENDENCY'
  | 'PENDING_APPROVAL'
  | 'CLOSED_EXECUTED'
  | 'CLOSED_NOT_EXECUTED'

/** CloseGate.Requirements (CloseGate.java:31). */
export interface CloseRequirements {
  minClearedPhotos: number
  requiredChecklistItems: string[]
  requiredDocumentKinds: string[]
}

/** CloseGate.Evidence (CloseGate.java:42). */
export interface CloseEvidence {
  clearedPhotoCount: number
  completedChecklistItems: string[]
  presentDocumentKinds: string[]
}

/** CloseGate.RefusalKind (CloseGate.java:51). */
export type CloseRefusalKind =
  | 'ORDER_NOT_EXECUTED'
  | 'MISSING_PHOTOS'
  | 'MISSING_CHECKLIST_ITEM'
  | 'MISSING_DOCUMENT'
  | 'MISSING_PROBLEM_CAUSE_REMEDY'
  | 'INVALID_PROBLEM_CAUSE_REMEDY'

/** CloseGate.Refusal (CloseGate.java:60). */
export interface CloseRefusal {
  kind: CloseRefusalKind
  detail: string
}

/**
 * Estado do catálogo problema–causa–remédio do tenant. O conteúdo é
 * [A DEFINIR] (P-II4-CATALOGO): sem configuração entregue, a tela
 * declara `configured: false` e nenhuma validação de tripla é fingida.
 */
export interface PcrCatalogState {
  configured: boolean
}

/** Uma OS vista pela tela de SLA: identidade + tipo + abertura + estado. */
export interface SlaOrderView {
  orderId: string
  typeCode: string
  status: ServiceOrderStatus
  openedAt: string
  assessment: DualAssessment
  escalationLevel: number
  events: SlaEvent[]
}

/** Avaliação de encerramento pedida à porta (CloseGate.evaluate). */
export interface CloseGateInput {
  status: ServiceOrderStatus
  requirements: CloseRequirements
  evidence: CloseEvidence
  codingOrNull: ProblemCauseRemedy | null
}

/**
 * Porta consumida pela tela de SLA/exigências. Espelha TypeSlaConfig,
 * DualDeadlineClock.evaluate, SlaEscalation.eventsAt e
 * CloseGate.evaluate do núcleo; a implementação HTTP é a P-II4-FIACAO.
 */
export interface SlaOsPort {
  typeConfigs(): Promise<TypeSlaConfig[]>
  orders(): Promise<SlaOrderView[]>
  pcrCatalogState(): Promise<PcrCatalogState>
  evaluateCloseGate(input: CloseGateInput): Promise<CloseRefusal[]>
}

/** A tripla está completa (os três códigos presentes e não vazios). */
export function isPcrComplete(coding: ProblemCauseRemedy | null): boolean {
  return (
    coding != null &&
    coding.problemCode.trim() !== '' &&
    coding.causeCode.trim() !== '' &&
    coding.remedyCode.trim() !== ''
  )
}

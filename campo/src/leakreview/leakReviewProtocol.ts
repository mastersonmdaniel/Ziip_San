/**
 * Protocolo da revisao por vazamento no aparelho (ED-23), sobre o app
 * de campo da ED-20: espelho TS das fases puras Java de
 * br.com.ziipsan.commercial.billing.leakprotocol — a vistoria e o
 * laudo nascem no celular, sem papel. O servidor revalida TUDO: o que
 * o aparelho monta offline nunca e decisao final.
 */

export type ProtocolPhase =
  | 'OPENED' | 'INSPECTION' | 'REPORT' | 'DECISION'
  | 'RECTIFICATION' | 'CLOSED_GRANTED' | 'CLOSED_DENIED' | 'CANCELLED';

const NEXT: Partial<Record<ProtocolPhase, ProtocolPhase[]>> = {
  OPENED: ['INSPECTION', 'CANCELLED'],
  INSPECTION: ['REPORT', 'CANCELLED'],
  // Laudo inconclusivo devolve a vistoria:
  REPORT: ['DECISION', 'INSPECTION', 'CANCELLED'],
  DECISION: ['RECTIFICATION', 'CLOSED_DENIED', 'CANCELLED'],
  RECTIFICATION: ['CLOSED_GRANTED', 'CANCELLED'],
};

/** Transicao de fase permitida? (a ordem e a do LeakReviewProtocol Java). */
export function canTransition(from: ProtocolPhase, to: ProtocolPhase): boolean {
  return (NEXT[from] ?? []).includes(to);
}

export interface FieldInspectionDraft {
  serviceOrderId: string;
  beforePhotoId: string | null;
  afterPhotoId: string | null;
  meterIdentityCode: string | null;
  meterReading: number | null;
  leakFound: boolean;
  leakLocation: string | null;
}

/**
 * Vistoria pronta para virar laudo? Espelha as guardas do
 * FieldInspection Java: fotos de antes/depois, identidade do
 * hidrometro confirmada, leitura presente e local do vazamento
 * quando encontrado. Devolve os motivos que faltam (vazio = pronta).
 */
export function inspectionGaps(draft: FieldInspectionDraft): string[] {
  const gaps: string[] = [];
  if (!draft.beforePhotoId) gaps.push('BEFORE_PHOTO_MISSING');
  if (!draft.afterPhotoId) gaps.push('AFTER_PHOTO_MISSING');
  if (!draft.meterIdentityCode) gaps.push('METER_IDENTITY_MISSING');
  if (draft.meterReading === null || draft.meterReading < 0) gaps.push('METER_READING_MISSING');
  if (draft.leakFound && !draft.leakLocation) gaps.push('LEAK_LOCATION_MISSING');
  return gaps;
}

export type ReportConclusion = 'LEAK_CONFIRMED' | 'NO_LEAK_FOUND' | 'INCONCLUSIVE';

/** Conclusao compativel com o fato de campo? (espelha o LeakReport Java). */
export function conclusionMatchesInspection(
  conclusion: ReportConclusion, leakFound: boolean, inconclusiveReason: string | null,
): boolean {
  if (conclusion === 'LEAK_CONFIRMED') return leakFound;
  if (conclusion === 'INCONCLUSIVE') return !!inconclusiveReason;
  return true;
}

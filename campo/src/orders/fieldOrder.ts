/**
 * OS em campo no aparelho (ED-20), sobre a base offline da ED-18:
 * espelho TS das regras puras Java de br.com.ziipsan.core.fieldos —
 * revalidacao do corte offline, confirmacao de identidade por codigo e
 * estados visiveis da execucao. O servidor revalida TUDO na sincronizacao
 * (FieldOsSyncService): o que o aparelho decide offline nunca e final.
 */

export type DebitState = 'OPEN' | 'PAID' | 'NONE';
export type Prohibition = 'CONTESTATION_OPEN' | 'NOTICE_NOT_GIVEN' | 'LEGAL_PROHIBITION';

export interface CutFacts {
  debitState: DebitState;
  contestationOpen: boolean;
  noticeGiven: boolean;
  prohibitions: Prohibition[];
}

export interface CutSnapshot {
  orderId: string;
  unitId: string;
  taskId: string;
  issuedAt: string;
  factsAtIssue: CutFacts;
}

export type CutReason =
  | 'DEBIT_PAID_AFTER_ISSUE' | 'DEBIT_PAID_AT_ISSUE' | 'NO_OPEN_DEBIT'
  | 'CONTESTATION_OPEN' | 'NOTICE_NOT_GIVEN' | 'LEGAL_PROHIBITION';

/** Mesma regra do CutRevalidation Java: devolve os motivos de cancelamento (vazio = pode executar). */
export function revalidateCut(atIssue: CutFacts, current: CutFacts): CutReason[] {
  const reasons: CutReason[] = [];
  const prohibitions = new Set<Prohibition>(current.prohibitions);
  if (current.contestationOpen) prohibitions.add('CONTESTATION_OPEN');
  if (!current.noticeGiven) prohibitions.add('NOTICE_NOT_GIVEN');
  if (current.debitState === 'PAID') {
    reasons.push(atIssue.debitState === 'OPEN' ? 'DEBIT_PAID_AFTER_ISSUE' : 'DEBIT_PAID_AT_ISSUE');
  }
  if (current.debitState === 'NONE') reasons.push('NO_OPEN_DEBIT');
  if (prohibitions.has('CONTESTATION_OPEN')) reasons.push('CONTESTATION_OPEN');
  if (prohibitions.has('NOTICE_NOT_GIVEN')) reasons.push('NOTICE_NOT_GIVEN');
  if (prohibitions.has('LEGAL_PROHIBITION')) reasons.push('LEGAL_PROHIBITION');
  return reasons;
}

export type ScanChannel = 'QR' | 'BARCODE' | 'NFC';
export type ScanTarget = 'METER' | 'SEAL' | 'ASSET_PLATE';

export interface ExpectedIdentity { unitId: string; target: ScanTarget; code: string }
export interface ScannedCode { unitId: string | null; target: ScanTarget; code: string; channel: ScanChannel }

/** Confirma identidade/posicao antes de leitura ou intervencao; codigo de outra unidade NAO confirma. */
export function identityMatches(expected: ExpectedIdentity, scanned: ScannedCode): boolean {
  return scanned.target === expected.target
    && scanned.code === expected.code
    && (scanned.unitId === null || scanned.unitId === expected.unitId);
}

export type VisibleState = 'EN_ROUTE' | 'ON_SITE' | 'IN_EXECUTION' | 'COMPLETED';

const NEXT: Record<VisibleState, VisibleState | null> = {
  EN_ROUTE: 'ON_SITE',
  ON_SITE: 'IN_EXECUTION',
  IN_EXECUTION: 'COMPLETED',
  COMPLETED: null,
};

/** Transicao visivel seguinte; null quando a transicao nao existe (a ordem e a do FieldExecution Java). */
export function nextVisibleState(current: VisibleState, target: VisibleState): VisibleState | null {
  return NEXT[current] === target ? target : null;
}

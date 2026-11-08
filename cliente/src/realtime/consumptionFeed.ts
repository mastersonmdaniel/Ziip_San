/**
 * Consumo quase em tempo real no app (ED-39, sobre a ED-17 — GES-27):
 * espelho TypeScript de ConsumerRealtimePolicy (Java), mesmos vetores.
 * A curva horaria so aparece com o parametro do tenant ligado (regra
 * da ED-17); o polling tem piso de 60 s; leitura com mais de 2 h e
 * rotulada desatualizada, nunca apresentada como "agora".
 */
export const MIN_POLL_MS = 60_000;
export const STALE_AFTER_MS = 2 * 60 * 60 * 1000;

export function effectivePollMs(requestedMs: number): number {
  if (!Number.isFinite(requestedMs) || requestedMs <= 0) {
    throw new Error('intervalo de atualizacao deve ser positivo');
  }
  return Math.max(requestedMs, MIN_POLL_MS);
}

export function isStale(readingAtIso: string, nowIso: string): boolean {
  const reading = Date.parse(readingAtIso);
  const now = Date.parse(nowIso);
  if (Number.isNaN(reading) || Number.isNaN(now)) {
    throw new Error('instantes da leitura e de agora sao obrigatorios');
  }
  return now - reading > STALE_AFTER_MS;
}

export interface HourlyPoint {
  hourIso: string;
  litres: number;
  /** Estado do dado da ED-17: MEASURED, ESTIMATED, SUBSTITUTED, LATE. */
  dataState: string;
}

/** A curva que o app pode mostrar: vazia quando o parametro do tenant esta desligado. */
export function visibleCurve(hourlyVisible: boolean, points: HourlyPoint[]): HourlyPoint[] {
  return hourlyVisible ? points : [];
}

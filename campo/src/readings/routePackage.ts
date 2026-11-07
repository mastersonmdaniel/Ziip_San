/**
 * Pacote de rota baixado do servidor (ED-18): uma rota de leitura para trabalhar
 * offline. Cada tarefa carrega o task_id gerado no servidor — e ele que o aparelho
 * devolve na sincronizacao (idempotencia) —, o historico minimo para a critica local
 * e o retrato do catalogo de ocorrencias da P2.5 vigente no tenant.
 */
export interface OccurrenceSnapshot {
  code: string;
  requiresPhoto: boolean;
  requiresReading: boolean;
}

export interface RouteTask {
  taskId: string;
  unitId: string;
  installationId: string;
  kind: 'WATER' | 'WELL';
  reference: string; // AAAA-MM
  previousReading: number | null;
  meterDigits: number;
  sequence: number;
}

export interface RoutePackage {
  routeId: string;
  reference: string;
  downloadedAt: string;
  occurrences: OccurrenceSnapshot[];
  tasks: RouteTask[];
}

/** Leitura executada em campo, na fila local ate sincronizar. */
export interface PendingReading {
  taskId: string;
  reading: number | null;
  occurrenceCode: string | null;
  photoRefs: string[];
  ocrValue: number | null;
  capturedAt: string;
}

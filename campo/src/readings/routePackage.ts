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
  /**
   * Endereco legivel da unidade (R2-M4 / P-01). OPCIONAL: pacotes de rota
   * emitidos antes desta correcao nao o carregam, e a tela segue exibindo
   * os IDs. O endereco ainda NAO tem fonte no servidor — nenhum cadastro
   * de unidade com endereco foi localizado (TS, Java, cadastro) —, entao
   * o preenchimento deste campo pelo montador do pacote de rota segue
   * pendencia nomeada (RG2-C-P2); aqui ele so e exibido quando presente,
   * nunca inventado.
   */
  unitAddress?: string | null;
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

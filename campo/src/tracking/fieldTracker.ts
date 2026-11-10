import type { TrackPositionFix, TrackPositionSource } from './trackPosition.ts';
import { queuedPointFromFix, type TrackPointQueue } from './trackQueue.ts';

/**
 * Motor de coleta da trilha no aparelho (P-MOB-GPS-01, decisao 59).
 *
 * Espelho, no aparelho, das travas que o servidor aplica em
 * Tracking.ingest (nucleo, ED-20) — a defesa e em profundidade, mas a
 * palavra final e sempre a do servidor:
 *
 *  1. SEM AVISO, NAO ARRANCA: sem o aviso de rastreamento ao empregado
 *     registado (noticeAcknowledged), start() recusa com o erro nomeado
 *     TRACKING_NOTICE_NOT_ACKNOWLEDGED e a fonte NUNCA e subscrita —
 *     espelho de Tracking.requireEmployeeNotice ("a trilha nao comeca
 *     sem o aviso (LGPD)").
 *  2. FORA DA JORNADA, NAO COLETA: cada fix e julgado pelo seu proprio
 *     instante contra a janela da jornada (inclusiva nas duas pontas,
 *     como Tracking.Shift.contains): fora, o fix e descartado — nem
 *     ponto isolado entra na fila.
 *  3. DENTRO, COLETA E ENFILEIRA: na cadencia do intervalo dado, o fix
 *     vira ponto na fila (tracking/trackQueue.ts), de onde so sai com o
 *     recibo da sincronizacao (tracking/trackSender.ts).
 *
 * TODAS as entradas sao explicitas e obrigatorias: trabalhador, equipe
 * (ou null declarado), janela da jornada, aviso, intervalo de coleta,
 * fonte de posicao, fila e relogio. NENHUM intervalo nem limiar tem
 * valor por omissao neste modulo.
 *
 * O relogio injetado decide o ENCERRAMENTO: a cada fix recebido, se o
 * relogio ja passou do fim da jornada, o motor cancela a subscricao e
 * para (stoppedReason SHIFT_ENDED) — a coleta nao fica aberta para
 * alem da janela que a autoriza. A aceitacao de cada ponto, essa, julga
 * o instante do proprio ponto, como o servidor.
 */

/** Janela da jornada (espelho de Tracking.Shift): instantes ISO 8601 UTC, inclusiva nas pontas. */
export interface FieldShiftWindow {
  start: string;
  end: string;
}

export interface FieldTrackerOptions {
  workerId: string;
  /** Equipe do trabalhador, ou null EXPLICITAMENTE declarado (espelho de teamIdOrNull). */
  teamId: string | null;
  shift: FieldShiftWindow;
  /** Aviso de rastreamento ao empregado registado (portao LGPD da ED-20). */
  noticeAcknowledged: boolean;
  /** Intervalo minimo entre pontos coletados, em ms — do chamador, sem omissao. */
  intervalMs: number;
  source: TrackPositionSource;
  queue: TrackPointQueue;
  /** Relogio injetado (nos testes, um relogio falso controlado). */
  clock: () => Date;
}

export type FieldTrackerProblem =
  | 'WORKER_ID_MISSING'
  | 'INVALID_SHIFT_WINDOW'
  | 'INVALID_INTERVAL'
  | 'TRACKING_NOTICE_NOT_ACKNOWLEDGED';

export class FieldTrackerError extends Error {
  readonly problem: FieldTrackerProblem;

  constructor(problem: FieldTrackerProblem, message: string) {
    super(message);
    this.name = 'FieldTrackerError';
    this.problem = problem;
  }
}

export type FieldTrackerStoppedReason = 'STOPPED_BY_CALLER' | 'SHIFT_ENDED';

export interface FieldTrackerStatus {
  running: boolean;
  /** Pontos coletados e enfileirados. */
  collected: number;
  /** Fixes descartados por cairem fora da janela da jornada. */
  discardedOutsideShift: number;
  /** Fixes descartados pela amostragem do intervalo de coleta. */
  discardedBySampling: number;
  /** Fixes descartados porque a gravacao na fila falhou (erro em lastEnqueueError). */
  enqueueFailures: number;
  lastEnqueueError: string | null;
  stoppedReason: FieldTrackerStoppedReason | null;
}

export interface FieldTracker {
  /**
   * Arranca a coleta. Devolve true se arrancou agora; false se ja estava
   * a correr. Sem aviso registado, REJEITA com FieldTrackerError
   * TRACKING_NOTICE_NOT_ACKNOWLEDGED e nao subscreve a fonte.
   */
  start(): Promise<boolean>;
  /** Para a coleta e cancela a subscricao. Parar sem ter arrancado e no-op. */
  stop(): Promise<void>;
  /** Aguarda as gravacoes na fila ja disparadas pelos fixes recebidos. */
  flush(): Promise<void>;
  status(): FieldTrackerStatus;
}

class FieldTrackerImpl implements FieldTracker {
  private readonly options: FieldTrackerOptions;
  private readonly shiftStartMs: number;
  private readonly shiftEndMs: number;
  private unsubscribe: (() => void) | null = null;
  private lastCollectedAtMs: number | null = null;
  private pendingEnqueues: Promise<void>[] = [];
  private collectedCount = 0;
  private outsideShiftCount = 0;
  private samplingCount = 0;
  private enqueueFailureCount = 0;
  private lastEnqueueError: string | null = null;
  private stoppedReason: FieldTrackerStoppedReason | null = null;

  constructor(options: FieldTrackerOptions) {
    if (!options.workerId) {
      throw new FieldTrackerError('WORKER_ID_MISSING', 'trabalhador e obrigatorio para a trilha');
    }
    const startMs = Date.parse(options.shift.start);
    const endMs = Date.parse(options.shift.end);
    if (!Number.isFinite(startMs) || !Number.isFinite(endMs) || endMs <= startMs) {
      throw new FieldTrackerError(
        'INVALID_SHIFT_WINDOW',
        'janela da jornada invalida: o fim deve ser depois do inicio (Tracking.Shift, ED-20)',
      );
    }
    if (!Number.isFinite(options.intervalMs) || options.intervalMs <= 0) {
      throw new FieldTrackerError('INVALID_INTERVAL', 'intervalo de coleta deve ser positivo');
    }
    this.options = options;
    this.shiftStartMs = startMs;
    this.shiftEndMs = endMs;
  }

  async start(): Promise<boolean> {
    if (!this.options.noticeAcknowledged) {
      // Espelho de Tracking.requireEmployeeNotice: sem o aviso
      // registado, a trilha NAO comeca — e a fonte nem e subscrita.
      throw new FieldTrackerError(
        'TRACKING_NOTICE_NOT_ACKNOWLEDGED',
        'aviso de rastreamento ao empregado nao registrado: a trilha nao comeca sem o aviso (LGPD)',
      );
    }
    if (this.unsubscribe !== null) return false;
    this.stoppedReason = null;
    this.unsubscribe = this.options.source.subscribe((fix) => this.onFix(fix));
    return true;
  }

  async stop(): Promise<void> {
    if (this.unsubscribe === null) return;
    this.stopInternal('STOPPED_BY_CALLER');
  }

  async flush(): Promise<void> {
    await Promise.all(this.pendingEnqueues);
    this.pendingEnqueues = [];
  }

  status(): FieldTrackerStatus {
    return {
      running: this.unsubscribe !== null,
      collected: this.collectedCount,
      discardedOutsideShift: this.outsideShiftCount,
      discardedBySampling: this.samplingCount,
      enqueueFailures: this.enqueueFailureCount,
      lastEnqueueError: this.lastEnqueueError,
      stoppedReason: this.stoppedReason,
    };
  }

  private stopInternal(reason: FieldTrackerStoppedReason): void {
    const unsubscribe = this.unsubscribe;
    this.unsubscribe = null;
    this.stoppedReason = reason;
    if (unsubscribe !== null) unsubscribe();
  }

  private onFix(fix: TrackPositionFix): void {
    // Encerramento pela janela: passado o fim da jornada (pelo relogio),
    // a coleta fecha — nao fica subscricao aberta fora da autorizacao.
    if (this.options.clock().getTime() > this.shiftEndMs) {
      this.stopInternal('SHIFT_ENDED');
      return;
    }
    const atMs = Date.parse(fix.at);
    if (!Number.isFinite(atMs) || atMs < this.shiftStartMs || atMs > this.shiftEndMs) {
      this.outsideShiftCount += 1;
      return;
    }
    if (this.lastCollectedAtMs !== null && atMs - this.lastCollectedAtMs < this.options.intervalMs) {
      this.samplingCount += 1;
      return;
    }
    this.lastCollectedAtMs = atMs;
    const point = queuedPointFromFix(this.options.workerId, this.options.teamId, fix);
    const enqueued = this.options.queue.enqueue(point).then(
      () => { this.collectedCount += 1; },
      (error: unknown) => {
        this.enqueueFailureCount += 1;
        this.lastEnqueueError = error instanceof Error ? error.message : String(error);
      },
    );
    this.pendingEnqueues.push(enqueued);
  }
}

/** Fabrica do motor de coleta — entradas todas explicitas, sem omissoes. */
export function createFieldTracker(options: FieldTrackerOptions): FieldTracker {
  return new FieldTrackerImpl(options);
}

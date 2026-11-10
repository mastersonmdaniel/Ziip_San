import type { QueuedTrackPoint, TrackPointQueue } from './trackQueue.ts';

/**
 * Remetente da trilha: a SINCRONIZACAO dos pontos (aparelho -> servidor,
 * na terminologia do dono) espelho do POST /track da Frente GPS-A
 * (P-MOB-GPS-01). Um lote de pontos sobe; o servidor devolve UM RECIBO
 * POR PONTO, porque a ingestao no servidor tambem e por ponto
 * (Tracking.ingest -> TrackStore.append, sem tudo-ou-nada — ED-20):
 *
 *  - ponto ACEITE sai da fila;
 *  - ponto RECUSADO sai da fila COM O MOTIVO REGISTADO no resultado —
 *    recusado nao fica a reenviar para sempre (a recusa do servidor e
 *    nomeada e definitiva para aquele ponto: sem aviso, fora da
 *    jornada, trava); o motivo volta ao chamador em rejected[], que e
 *    onde a integracao o regista;
 *  - FALHA DE REDE mantem tudo na fila e para a volta (networkFailed):
 *    nada se apaga sem recibo.
 *
 * O contrato de recibos e verificado: recibo em falta, duplicado ou
 * fora do lote e violacao do contrato (TRACK_RECEIPT_MISMATCH) e NADA
 * sai da fila nessa volta — sem recibo fiavel por ponto, nao se apaga
 * prova de campo por palpite.
 *
 * O transporte e uma PORTA ({@link TrackTransport}), como o
 * SyncTransport das leituras (sync/syncEngine.ts), que tambem e porta
 * sem implementacao HTTP no repo: a implementacao HTTP concreta segue o
 * padrao que a integracao usar para o /sync — nenhuma pilha nova e
 * inventada aqui.
 */

/** Recibo do servidor para UM ponto do lote (indice 0-based dentro do lote). */
export interface TrackPointReceipt {
  index: number;
  accepted: boolean;
  /**
   * Motivo nomeado da recusa quando accepted=false — transportado
   * tal-qual do servidor (o nome e da recusa do proprio Tracking,
   * nucleo ED-20); null quando o servidor nao o devolve.
   */
  reason: string | null;
}

export interface TrackUploadResult {
  receipts: TrackPointReceipt[];
}

/** Porta de transporte da trilha (espelho do POST /track do servidor). */
export interface TrackTransport {
  uploadTrack(points: QueuedTrackPoint[]): Promise<TrackUploadResult>;
}

export interface TrackSenderOptions {
  queue: TrackPointQueue;
  transport: TrackTransport;
  /** Tamanho do lote — do chamador, sem omissao. */
  batchSize: number;
}

export interface RejectedTrackPoint {
  point: QueuedTrackPoint;
  reason: string | null;
}

export interface TrackSendSummary {
  accepted: number;
  /** Recusados pelo servidor, com o motivo — o registo da recusa. */
  rejected: RejectedTrackPoint[];
  /** Pontos que ficaram na fila ao fim da volta. */
  remaining: number;
  /** True quando a volta parou por falha de rede (fila intacta a partir dai). */
  networkFailed: boolean;
}

export type TrackSenderProblem = 'INVALID_BATCH_SIZE' | 'TRACK_RECEIPT_MISMATCH';

export class TrackSenderError extends Error {
  readonly problem: TrackSenderProblem;

  constructor(problem: TrackSenderProblem, message: string) {
    super(message);
    this.name = 'TrackSenderError';
    this.problem = problem;
  }
}

export interface TrackSender {
  /** Drena a fila em lotes ate esvaziar ou a rede falhar. */
  sendPending(): Promise<TrackSendSummary>;
}

class TrackSenderImpl implements TrackSender {
  private readonly queue: TrackPointQueue;
  private readonly transport: TrackTransport;
  private readonly batchSize: number;

  constructor(options: TrackSenderOptions) {
    if (!Number.isFinite(options.batchSize) || options.batchSize <= 0) {
      throw new TrackSenderError('INVALID_BATCH_SIZE', 'tamanho do lote deve ser positivo');
    }
    this.queue = options.queue;
    this.transport = options.transport;
    this.batchSize = Math.floor(options.batchSize);
  }

  async sendPending(): Promise<TrackSendSummary> {
    let accepted = 0;
    const rejected: RejectedTrackPoint[] = [];
    let networkFailed = false;
    for (;;) {
      const pending = await this.queue.pending();
      if (pending.length === 0) break;
      const batch = pending.slice(0, this.batchSize);
      let result: TrackUploadResult;
      try {
        result = await this.transport.uploadTrack(batch);
      } catch {
        // Falha de rede: os pontos ficam na fila para a proxima volta.
        networkFailed = true;
        break;
      }
      const byIndex = this.verifyReceipts(batch, result.receipts);
      const treated: QueuedTrackPoint[] = [];
      for (let index = 0; index < batch.length; index += 1) {
        const receipt = byIndex.get(index);
        if (receipt === undefined) {
          // Inalcancavel apos verifyReceipts; guarda para o compilador.
          throw new TrackSenderError('TRACK_RECEIPT_MISMATCH', 'recibo em falta para o indice ' + index);
        }
        treated.push(batch[index]);
        if (receipt.accepted) accepted += 1;
        else rejected.push({ point: batch[index], reason: receipt.reason });
      }
      // Aceites e recusados saem juntos, so depois dos recibos conferidos.
      await this.queue.acknowledge(treated);
    }
    return { accepted, rejected, remaining: await this.queue.size(), networkFailed };
  }

  /**
   * Um recibo por ponto do lote, indices 0..n-1, sem duplicados nem
   * recibos a mais: qualquer desvio e violacao do contrato e a volta
   * aborta SEM mexer na fila.
   */
  private verifyReceipts(
    batch: QueuedTrackPoint[], receipts: TrackPointReceipt[],
  ): Map<number, TrackPointReceipt> {
    const byIndex = new Map<number, TrackPointReceipt>();
    for (const receipt of receipts) {
      if (!Number.isInteger(receipt.index) || receipt.index < 0 || receipt.index >= batch.length) {
        throw new TrackSenderError(
          'TRACK_RECEIPT_MISMATCH',
          'recibo fora do lote: indice ' + receipt.index + ' para um lote de ' + batch.length,
        );
      }
      if (byIndex.has(receipt.index)) {
        throw new TrackSenderError(
          'TRACK_RECEIPT_MISMATCH', 'recibo duplicado para o indice ' + receipt.index,
        );
      }
      byIndex.set(receipt.index, receipt);
    }
    if (byIndex.size !== batch.length) {
      throw new TrackSenderError(
        'TRACK_RECEIPT_MISMATCH',
        'recibos ' + byIndex.size + ' para um lote de ' + batch.length + ' pontos',
      );
    }
    return byIndex;
  }
}

/** Fabrica do remetente da trilha. */
export function createTrackSender(options: TrackSenderOptions): TrackSender {
  return new TrackSenderImpl(options);
}

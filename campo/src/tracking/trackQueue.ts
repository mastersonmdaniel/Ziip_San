import type { TrackPositionFix } from './trackPosition.ts';

/**
 * Fila de pontos da trilha no aparelho (P-MOB-GPS-01, decisao 59).
 *
 * O ponto em fila e o espelho TS de Tracking.TrackPoint (nucleo, ED-20:
 * workerId, teamIdOrNull, at, latitude, longitude), acrescido da
 * precisao do fix quando o aparelho a declara. Os pontos nascem no motor
 * de coleta (tracking/fieldTracker.ts) ja julgados pela janela da
 * jornada e pelo aviso, e saem da fila SOMENTE com o recibo do servidor
 * (tracking/trackSender.ts) — aceite ou recusado, nunca por descarte
 * silencioso.
 *
 * A persistencia da fila e uma PORTA tipada
 * ({@link TrackQueuePersistence}): esta entrega traz a implementacao em
 * MEMORIA, para os testes e para a integracao ensaiar sem aparelho. A
 * persistencia cifrada no aparelho (tabela propria no suporte SQLCipher
 * da P-04) e a pendencia P-MOB-GPS-04 — a fila em memoria NAO a finge:
 * reiniciar o app perde os pontos ainda nao sincronizados, e e por isso
 * que a P-MOB-GPS-04 e porta obrigatoria antes do uso real da trilha.
 */

/** Ponto da trilha aguardando sincronizacao (espelho de Tracking.TrackPoint). */
export interface QueuedTrackPoint {
  workerId: string;
  teamId: string | null;
  /** Instante do ponto, ISO 8601 UTC (o instante do fix). */
  at: string;
  latitude: number;
  longitude: number;
  /** Precisao horizontal do fix, em metros; null quando nao declarada. */
  accuracyM: number | null;
}

/** Constroi o ponto em fila a partir do fix, com o trabalhador do motor. */
export function queuedPointFromFix(
  workerId: string, teamId: string | null, fix: TrackPositionFix,
): QueuedTrackPoint {
  return {
    workerId,
    teamId,
    at: fix.at,
    latitude: fix.latitude,
    longitude: fix.longitude,
    accuracyM: fix.accuracyM,
  };
}

/**
 * Porta de persistencia da fila. Contrato de remocao: remove EXATAMENTE
 * as instancias devolvidas por pending() que o remetente ja tratou com
 * recibo — a chave de remocao na persistencia cifrada (P-MOB-GPS-04)
 * sera desenhada la; dentro do processo, a identidade da instancia e a
 * chave exata.
 */
export interface TrackQueuePersistence {
  append(point: QueuedTrackPoint): Promise<void>;
  /** Pontos pendentes na ordem em que entraram (FIFO). */
  pending(): Promise<QueuedTrackPoint[]>;
  /** Remove exatamente os pontos dados (ja tratados com recibo). */
  remove(points: QueuedTrackPoint[]): Promise<void>;
  size(): Promise<number>;
}

/** Persistencia em memoria — testes e ensaio da integracao, NAO producao. */
export class InMemoryTrackQueuePersistence implements TrackQueuePersistence {
  private readonly points: QueuedTrackPoint[] = [];

  async append(point: QueuedTrackPoint): Promise<void> {
    this.points.push(point);
  }

  async pending(): Promise<QueuedTrackPoint[]> {
    return [...this.points];
  }

  async remove(points: QueuedTrackPoint[]): Promise<void> {
    for (const point of points) {
      const index = this.points.indexOf(point);
      if (index >= 0) this.points.splice(index, 1);
    }
  }

  async size(): Promise<number> {
    return this.points.length;
  }
}

/**
 * Fila de pontos da trilha sobre uma persistencia injetada. O motor
 * enfileira (enqueue); o remetente le (pending) e confirma a saida
 * (acknowledge) so depois do recibo do servidor para cada ponto.
 */
export class TrackPointQueue {
  private readonly persistence: TrackQueuePersistence;

  constructor(persistence: TrackQueuePersistence) {
    this.persistence = persistence;
  }

  async enqueue(point: QueuedTrackPoint): Promise<void> {
    await this.persistence.append(point);
  }

  async pending(): Promise<QueuedTrackPoint[]> {
    return this.persistence.pending();
  }

  /** Saida confirmada por recibo: aceites e recusados, nunca por palpite. */
  async acknowledge(points: QueuedTrackPoint[]): Promise<void> {
    await this.persistence.remove(points);
  }

  async size(): Promise<number> {
    return this.persistence.size();
  }
}

/** Fabrica no estilo das demais do campo (createSaveReading, createSqlCipherLocalStore). */
export function createTrackPointQueue(persistence: TrackQueuePersistence): TrackPointQueue {
  return new TrackPointQueue(persistence);
}

/** Fila em memoria pronta para testes/ensaio — declarada como tal. */
export function createInMemoryTrackPointQueue(): TrackPointQueue {
  return new TrackPointQueue(new InMemoryTrackQueuePersistence());
}

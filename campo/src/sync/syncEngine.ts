import type { FieldSecurityBridge } from '../plugins/fieldSecurity.ts';
import type { PendingReading, RoutePackage } from '../readings/routePackage.ts';

/** Diretiva devolvida pelo servidor no contato (espelho de DeviceDirective, Java). */
export type DeviceDirective = 'NONE' | 'RETIRE' | 'BLOCK';

export interface SyncTransport {
  /** Diretiva atual do aparelho (contato leve, antes de qualquer envio). */
  fetchDirective(): Promise<DeviceDirective>;
  /** Envia as pendencias da rota; devolve os recibos por task_id. */
  uploadPending(routeId: string, pending: PendingReading[]): Promise<{ taskId: string; duplicate: boolean }[]>;
  /** Baixa o pacote de uma rota. */
  downloadRoute(routeId: string): Promise<RoutePackage>;
}

export interface LocalStore {
  pendingReadings(routeId: string): Promise<PendingReading[]>;
  saveRoute(pkg: RoutePackage): Promise<void>;
  clearRoute(routeId: string): Promise<void>;
}

/**
 * Motor de sincronizacao do app (ED-18):
 *  - BLOCK: nao envia NADA e apaga tudo (wipe) — o servidor recusou o aparelho;
 *  - RETIRE: envia as pendencias uma ultima vez e apaga tudo (wipe);
 *  - NONE: envia as pendencias; o reenvio usa os MESMOS task_id (a fila local nao
 *    gera id novo por tentativa), entao uma queda no meio do envio nao duplica
 *    leitura no servidor — ele responde duplicate=true para a tarefa repetida.
 */
export class SyncEngine {
  private readonly transport: SyncTransport;
  private readonly store: LocalStore;
  private readonly security: FieldSecurityBridge;

  constructor(transport: SyncTransport, store: LocalStore, security: FieldSecurityBridge) {
    this.transport = transport;
    this.store = store;
    this.security = security;
  }

  async syncRoute(routeId: string): Promise<{ uploaded: number; wiped: boolean }> {
    const directive = await this.transport.fetchDirective();
    if (directive === 'BLOCK') {
      await this.security.wipeAll();
      return { uploaded: 0, wiped: true };
    }
    const pending = await this.store.pendingReadings(routeId);
    let uploaded = 0;
    if (pending.length > 0) {
      const receipts = await this.transport.uploadPending(routeId, pending);
      uploaded = receipts.length;
    }
    if (directive === 'RETIRE') {
      await this.security.wipeAll();
      return { uploaded, wiped: true };
    }
    await this.store.clearRoute(routeId);
    return { uploaded, wiped: false };
  }
}

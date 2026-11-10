/**
 * Implementacao SQLCipher do LocalStore (RG2-C-P4 / P-04).
 *
 * Fecha a pendencia deixada por sync/fieldReadingStore.ts: la esta a PORTA
 * (createSaveReading delega para LocalStore.saveReading); aqui esta o
 * store real que a integracao injeta no SyncEngine e no controller. A
 * persistencia em si vive no nativo (FieldDatabasePlugin, SQLCipher); esta
 * classe e a fiacao TS sobre a ponte (plugins/fieldDatabase.ts):
 * serializa os DTOs reais em JSON, sem acrescentar nem renomear campo.
 *
 * Semantica (a do contrato em syncEngine.ts, executada pelo nativo):
 *  - saveReading: upsert por taskId — confirmar a mesma tarefa duas vezes
 *    substitui, nao duplica (o reenvio usa o MESMO task_id e o servidor
 *    responde duplicate=true);
 *  - a rota de uma leitura deriva do mapa task->route gravado no
 *    saveRoute; tarefa desconhecida = erro nomeado UNKNOWN_TASK
 *    propagado da ponte, nunca orfa silenciosa;
 *  - clearRoute apaga so a rota indicada (pacote, mapa e leituras dela).
 *
 * Nenhuma leitura vive em memoria nesta classe: cada operacao atravessa a
 * ponte, entao o que foi gravado sobrevive ao reinicio do app — que e
 * exatamente a falha do R2-M2 que este store fecha.
 */
import type { FieldDatabaseBridge } from '../plugins/fieldDatabase.ts';
import type { PendingReading, RoutePackage } from '../readings/routePackage.ts';
import type { LocalStore } from './syncEngine.ts';

export class SqlCipherLocalStore implements LocalStore {
  private readonly bridge: FieldDatabaseBridge;

  constructor(bridge: FieldDatabaseBridge) {
    this.bridge = bridge;
  }

  async pendingReadings(routeId: string): Promise<PendingReading[]> {
    const { readingsJson } = await this.bridge.pendingReadings(routeId);
    return JSON.parse(readingsJson) as PendingReading[];
  }

  async saveReading(reading: PendingReading): Promise<void> {
    // O erro UNKNOWN_TASK da ponte propaga sem reescrita: e o nome que a
    // integracao e os testes reconhecem.
    await this.bridge.saveReading(JSON.stringify(reading));
  }

  async saveRoute(pkg: RoutePackage): Promise<void> {
    await this.bridge.saveRoute(JSON.stringify(pkg));
  }

  async clearRoute(routeId: string): Promise<void> {
    await this.bridge.clearRoute(routeId);
  }
}

/** Fabrica no mesmo estilo de createSaveReading (fieldReadingStore.ts). */
export function createSqlCipherLocalStore(bridge: FieldDatabaseBridge): SqlCipherLocalStore {
  return new SqlCipherLocalStore(bridge);
}

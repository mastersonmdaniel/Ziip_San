/**
 * Contrato da ponte com o FieldDatabasePlugin (Kotlin) — RG2-C-P4 / P-04.
 *
 * O plugin nativo abre o banco local cifrado com SQLCipher e executa as
 * operacoes do LocalStore (sync/syncEngine.ts). So JSON dos DTOs reais
 * (RoutePackage / PendingReading de readings/routePackage.ts) atravessa a
 * ponte, nos dois sentidos — nenhum campo novo e inventado aqui.
 *
 * REGRA CENTRAL DO DESENHO DA CHAVE (achado SS4-B da revisao cruzada da
 * ED-18): a passphrase do SQLCipher NUNCA atravessa esta ponte. Ela e
 * gerada no nativo (32 bytes, SecureRandom), guardada apenas embrulhada
 * (AES-GCM) pela chave nao-extraivel do Android Keystore, e desembrulhada
 * somente em memoria, dentro do nativo. Por isso esta interface nao tem
 * nenhum metodo de chave/passphrase — como secureToken.ts nao tem metodo
 * que devolva o refresh token.
 */
export interface FieldDatabaseBridge {
  /**
   * Grava o pacote de rota (JSON do RoutePackage real). O nativo deriva o
   * mapa task->route das tasks do proprio pacote gravado.
   */
  saveRoute(routePackageJson: string): Promise<void>;
  /**
   * Devolve as leituras pendentes da rota como JSON (array de
   * PendingReading serializados), na ordem em que foram gravadas.
   */
  pendingReadings(routeId: string): Promise<{ readingsJson: string }>;
  /**
   * Grava/substitui UMA leitura (JSON do PendingReading real), upsert por
   * taskId. A rota deriva do mapa task->route gravado no saveRoute; uma
   * leitura cuja taskId nao consta de nenhuma rota gravada e RECUSADA com
   * o erro nomeado UNKNOWN_TASK (nunca vira orfa silenciosa).
   */
  saveReading(readingJson: string): Promise<void>;
  /** Apaga pacote, mapa e leituras SOMENTE da rota indicada. */
  clearRoute(routeId: string): Promise<void>;
}

export const FIELD_DATABASE_PLUGIN_NAME = 'FieldDatabase';

/**
 * Prefixo do erro nomeado de tarefa desconhecida no saveReading: o nativo
 * rejeita a chamada com uma mensagem que comeca por este prefixo, e o
 * SqlCipherLocalStore a propaga sem a reescrever.
 */
export const UNKNOWN_TASK_ERROR = 'UNKNOWN_TASK';

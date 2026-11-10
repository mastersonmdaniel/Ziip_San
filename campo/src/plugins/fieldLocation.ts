/**
 * Contrato da ponte com o FieldLocationPlugin (Kotlin) — P-MOB-GPS-01.
 *
 * O plugin nativo le a posicao comum do aparelho pelo LocationManager do
 * Android, SOMENTE em primeiro plano (app aberto): a coleta continua
 * com o app fechado (foreground service) e a pendencia P-MOB-GPS-03 —
 * nao e simulada aqui nem no nativo.
 *
 * Pela ponte so atravessa o fix (latitude, longitude, instante ISO 8601
 * UTC convertido no nativo do relogio do fix, precisao quando o aparelho
 * a declara). NENHUMA decisao de trilha atravessa a ponte: jornada,
 * aviso, amostragem e fila vivem no TS (tracking/fieldTracker.ts), e a
 * palavra final de ingestao e do servidor (Tracking.ingest, ED-20).
 *
 * A fiacao concreta desta interface ao objeto do plugin registado no
 * Capacitor (registerPlugin) e da casca/integracao, como nos demais
 * plugins do campo: aqui vive o contrato que o adaptador consome
 * (tracking/bridgeTrackPositionSource.ts) e que os testes dublam.
 */

/** Fix como atravessa a ponte (o nativo emite exatamente estes campos). */
export interface FieldLocationFix {
  latitude: number;
  longitude: number;
  /** Instante do fix, ISO 8601 UTC. */
  at: string;
  /** Precisao horizontal estimada, em metros; null quando nao declarada. */
  accuracyM: number | null;
}

export interface FieldLocationBridge {
  /**
   * Um fix NOVO do aparelho, ou null quando nenhum fornecedor de
   * localizacao esta ativo. Fix antigo de cache NAO e devolvido como se
   * fosse atual: sem fix novo, a chamada fica pendente ate ele chegar
   * ou a permissao/fornecedor falhar com erro nomeado — quem decide o
   * prazo da espera e o chamador.
   */
  getCurrentPosition(): Promise<FieldLocationFix | null>;
  /**
   * Comeca a vigia em primeiro plano: o nativo pede atualizacoes ao
   * LocationManager com este intervalo minimo entre entregas (ms). Uma
   * so vigia de cada vez: comecar outra com uma ativa REJEITA com
   * LOCATION_WATCH_ALREADY_ACTIVE.
   */
  startWatch(minIntervalMs: number): Promise<void>;
  /** Para a vigia ativa; sem vigia ativa, e no-op. */
  stopWatch(): Promise<void>;
  /**
   * Subscreve os fixes emitidos pela vigia; devolve a funcao de
   * cancelamento da subscricao (a vigia em si para com stopWatch).
   */
  onPosition(listener: (fix: FieldLocationFix) => void): () => void;
}

export const FIELD_LOCATION_PLUGIN_NAME = 'FieldLocation';

/**
 * Nome do evento do plugin por onde os fixes da vigia chegam ao TS
 * (notifyListeners no nativo): e a este evento que a fiacao da ponte
 * liga o onPosition acima.
 */
export const FIELD_LOCATION_POSITION_EVENT = 'position';

/**
 * Prefixo do erro nomeado de permissao de localizacao negada: o nativo
 * rejeita a chamada com uma mensagem que comeca por este prefixo, e o
 * adaptador propaga-o sem o reescrever (padrao do UNKNOWN_TASK de
 * fieldDatabase.ts).
 */
export const LOCATION_PERMISSION_DENIED_ERROR = 'LOCATION_PERMISSION_DENIED';

/** Prefixo do erro nomeado de vigia ja ativa no startWatch. */
export const LOCATION_WATCH_ALREADY_ACTIVE_ERROR = 'LOCATION_WATCH_ALREADY_ACTIVE';

/** Prefixo do erro nomeado de nenhum fornecedor de localizacao ativo. */
export const LOCATION_PROVIDER_UNAVAILABLE_ERROR = 'LOCATION_PROVIDER_UNAVAILABLE';

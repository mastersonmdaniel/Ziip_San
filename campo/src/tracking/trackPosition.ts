/**
 * Porta de posicao da TRILHA do trabalhador (P-MOB-GPS-01, decisao 59;
 * governanca da ED-20 em shared/ziipsan-core, Tracking.java).
 *
 * Esta porta NAO e a GnssSource de gis/gnss.ts: aquela e do cadastro
 * tecnico RTK (ED-66) — receptor externo, tipo de solucao, PDOP — e nao
 * se reutiliza para a trilha. A trilha consome a posicao comum do
 * aparelho (LocationManager do Android, via plugin FieldLocation —
 * plugins/fieldLocation.ts): latitude, longitude, instante e, quando o
 * aparelho a declara, a precisao horizontal estimada.
 *
 * NENHUM acesso a hardware vive aqui: a porta so recebe o fix ja lido
 * pelo nativo. Nenhum intervalo, limiar ou filtro de precisao e presumido
 * neste modulo — a cadencia da coleta e entrada explicita do motor
 * (tracking/fieldTracker.ts) e a aceitacao do ponto e a do servidor
 * (Tracking.ingest, ED-20).
 */

/** Fix bruto da trilha, como o aparelho o entrega. */
export interface TrackPositionFix {
  latitude: number;
  longitude: number;
  /**
   * Instante do fix, ISO 8601 UTC — e o instante DO PONTO: e contra ele
   * que a janela da jornada se julga, no aparelho e no servidor
   * (Tracking.Shift.contains julga point.at — ED-20).
   */
  at: string;
  /**
   * Precisao horizontal estimada declarada pelo aparelho, em metros;
   * null quando o aparelho nao a declara. Transportada, nunca usada como
   * filtro aqui: nenhum teto de precisao foi fixado pelo desenho.
   */
  accuracyM: number | null;
}

/**
 * Fonte de posicoes da trilha: implementada pelo adaptador sobre o
 * plugin nativo de localizacao (tracking/bridgeTrackPositionSource.ts).
 * Padrao da GnssSource (gis/gnss.ts): posicao atual sob pedido +
 * subscricao de posicoes para a coleta continua em primeiro plano.
 */
export interface TrackPositionSource {
  /** Posicao atual, ou null quando o aparelho ainda nao tem fix. */
  currentPosition(): Promise<TrackPositionFix | null>;
  /**
   * Subscreve as posicoes seguintes; devolve a funcao de cancelamento.
   * A cadencia de ENTREGA e a do aparelho/plugin; a cadencia de COLETA
   * (amostragem da trilha) e do motor, por entrada explicita dele.
   */
  subscribe(listener: (fix: TrackPositionFix) => void): () => void;
}

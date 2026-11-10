import type { FieldLocationBridge, FieldLocationFix } from '../plugins/fieldLocation.ts';
import type { TrackPositionFix, TrackPositionSource } from './trackPosition.ts';

/**
 * Adaptador da ponte de localizacao para a porta da trilha
 * (tracking/trackPosition.ts) — a fiacao da trilha sobre a ponte, no
 * padrao do SqlCipherLocalStore (sync/sqlCipherLocalStore.ts): SO
 * converte o fix campo a campo, sem renomear nem acrescentar nada.
 *
 * A vigia nativa e partilhada: a primeira subscricao comeca a vigia com
 * o intervalo minimo dado na construcao (entrada explicita do chamador
 * — e o pedido ao LocationManager, poupanca de bateria no nativo); o
 * cancelamento da ultima subscricao para a vigia. A cadencia de COLETA
 * da trilha continua a ser a amostragem do motor
 * (tracking/fieldTracker.ts), nao esta.
 */
export class BridgeTrackPositionSource implements TrackPositionSource {
  private readonly bridge: FieldLocationBridge;
  private readonly watchIntervalMs: number;
  private subscribers = 0;
  private watchStart: Promise<void> | null = null;

  /**
   * Erro nomeado do arranque da vigia, quando ele falhou (permissao
   * negada, sem fornecedor); null caso contrario. O arranque da vigia e
   * assincrono face a subscricao: a falha nao e engolida em silencio
   * nem disfarcada de "sem posicoes" — fica aqui para a integracao ver.
   */
  watchError: string | null = null;

  constructor(bridge: FieldLocationBridge, watchIntervalMs: number) {
    this.bridge = bridge;
    this.watchIntervalMs = watchIntervalMs;
  }

  async currentPosition(): Promise<TrackPositionFix | null> {
    const fix = await this.bridge.getCurrentPosition();
    // A ponte pode resolver vazia (sem objeto) quando nao ha fix: no
    // contrato isso e null, e e null que a porta devolve.
    return fix == null ? null : toTrackPositionFix(fix);
  }

  subscribe(listener: (fix: TrackPositionFix) => void): () => void {
    const removeBridgeListener = this.bridge.onPosition((fix) => listener(toTrackPositionFix(fix)));
    this.subscribers += 1;
    if (this.watchStart === null) {
      this.watchStart = this.bridge.startWatch(this.watchIntervalMs).then(
        () => undefined,
        (error: unknown) => {
          this.watchError = error instanceof Error ? error.message : String(error);
        },
      );
    }
    let active = true;
    return () => {
      if (!active) return;
      active = false;
      removeBridgeListener();
      this.subscribers -= 1;
      if (this.subscribers === 0 && this.watchStart !== null) {
        const started = this.watchStart;
        this.watchStart = null;
        // Para so depois de o arranque assentar (comecar e parar nao se cruzam).
        void started.then(() => this.bridge.stopWatch()).then(undefined, () => undefined);
      }
    };
  }
}

/** Conversao campo a campo do fix da ponte para o fix da porta. */
export function toTrackPositionFix(fix: FieldLocationFix): TrackPositionFix {
  return {
    latitude: fix.latitude,
    longitude: fix.longitude,
    at: fix.at,
    accuracyM: fix.accuracyM,
  };
}

/** Fabrica no estilo das demais do campo. */
export function createBridgeTrackPositionSource(
  bridge: FieldLocationBridge, watchIntervalMs: number,
): BridgeTrackPositionSource {
  return new BridgeTrackPositionSource(bridge, watchIntervalMs);
}

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createBridgeTrackPositionSource, toTrackPositionFix } from './bridgeTrackPositionSource.ts';
import type { FieldLocationBridge, FieldLocationFix } from '../plugins/fieldLocation.ts';

// Massa SINTETICA de ensaio (declarada): coordenadas ficticias de bancada.
const FIX: FieldLocationFix = {
  latitude: -26.9, longitude: -48.7, at: '2026-11-10T09:00:00Z', accuracyM: 8,
};

/** Ponte dublê: regista a vigia e emite os fixes que o teste mandar. */
class FakeBridge implements FieldLocationBridge {
  startWatchCalls: number[] = [];
  stopWatchCalls = 0;
  private readonly listeners = new Set<(fix: FieldLocationFix) => void>();

  async getCurrentPosition(): Promise<FieldLocationFix | null> {
    return FIX;
  }

  async startWatch(minIntervalMs: number): Promise<void> {
    this.startWatchCalls.push(minIntervalMs);
  }

  async stopWatch(): Promise<void> {
    this.stopWatchCalls += 1;
  }

  onPosition(listener: (fix: FieldLocationFix) => void): () => void {
    this.listeners.add(listener);
    return () => { this.listeners.delete(listener); };
  }

  emit(fix: FieldLocationFix): void {
    for (const listener of [...this.listeners]) listener(fix);
  }
}

test('currentPosition converte o fix da ponte campo a campo', async () => {
  const source = createBridgeTrackPositionSource(new FakeBridge(), 30_000);
  assert.deepEqual(await source.currentPosition(), {
    latitude: -26.9, longitude: -48.7, at: '2026-11-10T09:00:00Z', accuracyM: 8,
  });
  assert.deepEqual(toTrackPositionFix({ ...FIX, accuracyM: null }), {
    latitude: -26.9, longitude: -48.7, at: '2026-11-10T09:00:00Z', accuracyM: null,
  });
});

test('primeira subscricao comeca a vigia com o intervalo da construcao; fixes chegam convertidos', async () => {
  const bridge = new FakeBridge();
  const source = createBridgeTrackPositionSource(bridge, 30_000);
  const received: unknown[] = [];
  const unsubscribe = source.subscribe((fix) => received.push(fix));
  bridge.emit(FIX);
  assert.deepEqual(bridge.startWatchCalls, [30_000]);
  assert.deepEqual(received, [{
    latitude: -26.9, longitude: -48.7, at: '2026-11-10T09:00:00Z', accuracyM: 8,
  }]);
  unsubscribe();
});

test('vigia partilhada: para so quando a ultima subscricao cancela', async () => {
  const bridge = new FakeBridge();
  const source = createBridgeTrackPositionSource(bridge, 30_000);
  const offFirst = source.subscribe(() => undefined);
  const offSecond = source.subscribe(() => undefined);
  assert.deepEqual(bridge.startWatchCalls, [30_000]); // uma vigia so
  offFirst();
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(bridge.stopWatchCalls, 0);
  offSecond();
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(bridge.stopWatchCalls, 1);
});

test('arranque da vigia recusado fica registado em watchError, sem ser engolido', async () => {
  const bridge = new FakeBridge();
  bridge.startWatch = async () => {
    throw new Error('LOCATION_PERMISSION_DENIED: sem permissao de localizacao (ensaio)');
  };
  const source = createBridgeTrackPositionSource(bridge, 30_000);
  const unsubscribe = source.subscribe(() => undefined);
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(
    source.watchError,
    'LOCATION_PERMISSION_DENIED: sem permissao de localizacao (ensaio)',
  );
  unsubscribe();
});

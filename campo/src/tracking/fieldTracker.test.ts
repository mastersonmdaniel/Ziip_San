import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  createFieldTracker, FieldTrackerError, type FieldTrackerOptions,
} from './fieldTracker.ts';
import type { TrackPositionFix, TrackPositionSource } from './trackPosition.ts';
import { createInMemoryTrackPointQueue, type TrackPointQueue } from './trackQueue.ts';

// Massa SINTETICA de ensaio (declarada): trabalhador e coordenadas
// ficticios de bancada, jornada ficticia de 08:00 a 12:00 UTC.
const WORKER = '11111111-1111-1111-1111-111111111111';
const SHIFT = { start: '2026-11-10T08:00:00Z', end: '2026-11-10T12:00:00Z' };

/** Fonte dublê: emite os fixes que o teste mandar, regista subscricoes. */
class FakeSource implements TrackPositionSource {
  subscriptions = 0;
  unsubscriptions = 0;
  private readonly listeners = new Set<(fix: TrackPositionFix) => void>();

  async currentPosition(): Promise<TrackPositionFix | null> {
    return null;
  }

  subscribe(listener: (fix: TrackPositionFix) => void): () => void {
    this.subscriptions += 1;
    this.listeners.add(listener);
    return () => {
      this.unsubscriptions += 1;
      this.listeners.delete(listener);
    };
  }

  emit(at: string, latitude = -26.9): void {
    const fix: TrackPositionFix = { latitude, longitude: -48.7, at, accuracyM: 10 };
    for (const listener of [...this.listeners]) listener(fix);
  }
}

/** Relogio falso controlavel pelo teste. */
function fakeClock(initialIso: string) {
  let nowMs = Date.parse(initialIso);
  return {
    clock: () => new Date(nowMs),
    set(iso: string) { nowMs = Date.parse(iso); },
  };
}

function options(overrides: Partial<FieldTrackerOptions> = {}): {
  opts: FieldTrackerOptions; source: FakeSource; queue: TrackPointQueue;
  clockCtl: ReturnType<typeof fakeClock>;
} {
  const source = new FakeSource();
  const queue = createInMemoryTrackPointQueue();
  const clockCtl = fakeClock('2026-11-10T09:00:00Z');
  const opts: FieldTrackerOptions = {
    workerId: WORKER,
    teamId: null,
    shift: SHIFT,
    noticeAcknowledged: true,
    intervalMs: 60_000,
    source,
    queue,
    clock: clockCtl.clock,
    ...overrides,
  };
  return { opts, source, queue, clockCtl };
}

test('sem aviso registado o motor NAO arranca e a fonte nunca e subscrita', async () => {
  const { opts, source, queue } = options({ noticeAcknowledged: false });
  const tracker = createFieldTracker(opts);
  await assert.rejects(tracker.start(), (error: unknown) => {
    assert.ok(error instanceof FieldTrackerError);
    assert.equal(error.problem, 'TRACKING_NOTICE_NOT_ACKNOWLEDGED');
    return true;
  });
  assert.equal(source.subscriptions, 0);
  assert.equal(tracker.status().running, false);
  assert.equal(await queue.size(), 0);
});

test('fora da jornada nao coleta: fix antes do inicio e depois do fim sao descartados', async () => {
  const { opts, source, queue } = options();
  const tracker = createFieldTracker(opts);
  assert.equal(await tracker.start(), true);
  // Instante do fix ANTES do inicio da jornada (relogio dentro, nao basta).
  source.emit('2026-11-10T07:59:00Z');
  // Instante do fix DEPOIS do fim da jornada.
  source.emit('2026-11-10T12:00:01Z');
  await tracker.flush();
  assert.equal(await queue.size(), 0);
  assert.equal(tracker.status().discardedOutsideShift, 2);
  assert.equal(tracker.status().collected, 0);
  await tracker.stop();
});

test('dentro da jornada coleta e enfileira o ponto do trabalhador', async () => {
  const { opts, source, queue } = options();
  const tracker = createFieldTracker(opts);
  await tracker.start();
  source.emit('2026-11-10T09:00:00Z', -26.9123);
  await tracker.flush();
  const pending = await queue.pending();
  assert.equal(pending.length, 1);
  assert.deepEqual(pending[0], {
    workerId: WORKER,
    teamId: null,
    at: '2026-11-10T09:00:00Z',
    latitude: -26.9123,
    longitude: -48.7,
    accuracyM: 10,
  });
  assert.equal(tracker.status().collected, 1);
  await tracker.stop();
});

test('pontas da jornada sao inclusivas, como Tracking.Shift.contains', async () => {
  const { opts, source, queue } = options({ intervalMs: 1 });
  const tracker = createFieldTracker(opts);
  await tracker.start();
  source.emit('2026-11-10T08:00:00Z');
  source.emit('2026-11-10T12:00:00Z');
  await tracker.flush();
  assert.equal(await queue.size(), 2);
  await tracker.stop();
});

test('amostragem: fixes dentro do intervalo sao descartados; passado o intervalo, coleta', async () => {
  const { opts, source, queue } = options();
  const tracker = createFieldTracker(opts);
  await tracker.start();
  source.emit('2026-11-10T09:00:00Z');
  source.emit('2026-11-10T09:00:30Z'); // 30s < 60s do intervalo
  source.emit('2026-11-10T09:01:00Z'); // exatamente o intervalo: coleta
  await tracker.flush();
  assert.equal(await queue.size(), 2);
  assert.equal(tracker.status().discardedBySampling, 1);
  await tracker.stop();
});

test('fim da jornada pelo relogio encerra a coleta e cancela a subscricao', async () => {
  const { opts, source, queue, clockCtl } = options();
  const tracker = createFieldTracker(opts);
  await tracker.start();
  source.emit('2026-11-10T09:00:00Z');
  clockCtl.set('2026-11-10T12:00:01Z');
  source.emit('2026-11-10T12:00:01Z');
  await tracker.flush();
  assert.equal(tracker.status().running, false);
  assert.equal(tracker.status().stoppedReason, 'SHIFT_ENDED');
  assert.equal(source.unsubscriptions, 1);
  assert.equal(await queue.size(), 1); // so o ponto das 09:00
});

test('janela invalida e intervalo invalido sao recusados na construcao, pelo nome', () => {
  const { opts } = options();
  assert.throws(
    () => createFieldTracker({ ...opts, shift: { start: SHIFT.end, end: SHIFT.start } }),
    (error: unknown) => error instanceof FieldTrackerError && error.problem === 'INVALID_SHIFT_WINDOW',
  );
  assert.throws(
    () => createFieldTracker({ ...opts, intervalMs: 0 }),
    (error: unknown) => error instanceof FieldTrackerError && error.problem === 'INVALID_INTERVAL',
  );
});

test('start repetido nao subscreve duas vezes; stop sem arranque e no-op', async () => {
  const { opts, source } = options();
  const tracker = createFieldTracker(opts);
  await tracker.stop(); // no-op, nao lanca
  assert.equal(await tracker.start(), true);
  assert.equal(await tracker.start(), false);
  assert.equal(source.subscriptions, 1);
  await tracker.stop();
  assert.equal(tracker.status().stoppedReason, 'STOPPED_BY_CALLER');
});

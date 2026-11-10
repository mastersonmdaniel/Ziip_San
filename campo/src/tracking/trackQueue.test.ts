import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  createInMemoryTrackPointQueue, queuedPointFromFix, type QueuedTrackPoint,
} from './trackQueue.ts';

// Massa SINTETICA de ensaio (declarada): trabalhador e coordenadas
// ficticios de bancada, nenhum ponto de rede real.
const WORKER = '11111111-1111-1111-1111-111111111111';

function point(at: string, latitude = -26.9): QueuedTrackPoint {
  return { workerId: WORKER, teamId: null, at, latitude, longitude: -48.7, accuracyM: 12 };
}

test('fila em memoria: enfileira e devolve pendentes na ordem de entrada (FIFO)', async () => {
  const queue = createInMemoryTrackPointQueue();
  const first = point('2026-11-10T09:00:00Z');
  const second = point('2026-11-10T09:01:00Z');
  await queue.enqueue(first);
  await queue.enqueue(second);
  assert.deepEqual(await queue.pending(), [first, second]);
  assert.equal(await queue.size(), 2);
});

test('acknowledge remove exatamente os pontos tratados com recibo', async () => {
  const queue = createInMemoryTrackPointQueue();
  const first = point('2026-11-10T09:00:00Z');
  const second = point('2026-11-10T09:01:00Z');
  const third = point('2026-11-10T09:02:00Z');
  await queue.enqueue(first);
  await queue.enqueue(second);
  await queue.enqueue(third);
  await queue.acknowledge([first, third]);
  assert.deepEqual(await queue.pending(), [second]);
  assert.equal(await queue.size(), 1);
});

test('ponto em fila e o espelho de Tracking.TrackPoint montado do fix', () => {
  const built = queuedPointFromFix(WORKER, '22222222-2222-2222-2222-222222222222', {
    latitude: -26.91, longitude: -48.71, at: '2026-11-10T09:00:00Z', accuracyM: null,
  });
  assert.deepEqual(built, {
    workerId: WORKER,
    teamId: '22222222-2222-2222-2222-222222222222',
    at: '2026-11-10T09:00:00Z',
    latitude: -26.91,
    longitude: -48.71,
    accuracyM: null,
  });
});

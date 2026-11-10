import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  createTrackSender, TrackSenderError,
  type TrackPointReceipt, type TrackTransport, type TrackUploadResult,
} from './trackSender.ts';
import {
  createInMemoryTrackPointQueue, type QueuedTrackPoint, type TrackPointQueue,
} from './trackQueue.ts';

// Massa SINTETICA de ensaio (declarada): trabalhador e coordenadas
// ficticios de bancada. O motivo de recusa e um nome de ensaio — em
// producao o motivo e o da recusa do servidor (Tracking, ED-20),
// transportado tal-qual.
const WORKER = '11111111-1111-1111-1111-111111111111';
const REASON = 'PONTO_FORA_DA_JORNADA_ENSAIO';

function point(at: string): QueuedTrackPoint {
  return { workerId: WORKER, teamId: null, at, latitude: -26.9, longitude: -48.7, accuracyM: 10 };
}

/** Transporte dublê programavel: responde recibos por indice, ou falha. */
class FakeTransport implements TrackTransport {
  calls: QueuedTrackPoint[][] = [];
  plan: ((points: QueuedTrackPoint[]) => TrackUploadResult) | null = null;

  async uploadTrack(points: QueuedTrackPoint[]): Promise<TrackUploadResult> {
    this.calls.push([...points]);
    if (this.plan === null) {
      return { receipts: points.map((_, index) => ({ index, accepted: true, reason: null })) };
    }
    return this.plan(points);
  }
}

async function queueWith(points: QueuedTrackPoint[]): Promise<TrackPointQueue> {
  const queue = createInMemoryTrackPointQueue();
  for (const p of points) await queue.enqueue(p);
  return queue;
}

test('remetente drena com recibos mistos: aceite sai, recusado sai com o motivo registado', async () => {
  const first = point('2026-11-10T09:00:00Z');
  const second = point('2026-11-10T09:01:00Z');
  const third = point('2026-11-10T09:02:00Z');
  const queue = await queueWith([first, second, third]);
  const transport = new FakeTransport();
  transport.plan = (points) => ({
    receipts: points.map((_, index): TrackPointReceipt => (index === 1
      ? { index, accepted: false, reason: REASON }
      : { index, accepted: true, reason: null })),
  });
  const sender = createTrackSender({ queue, transport, batchSize: 10 });
  const summary = await sender.sendPending();
  assert.equal(summary.accepted, 2);
  assert.deepEqual(summary.rejected, [{ point: second, reason: REASON }]);
  assert.equal(summary.remaining, 0);
  assert.equal(summary.networkFailed, false);
  assert.equal(await queue.size(), 0);
});

test('recusa do servidor nao prende a fila: o recusado nao volta a ser enviado', async () => {
  const refused = point('2026-11-10T09:00:00Z');
  const queue = await queueWith([refused]);
  const transport = new FakeTransport();
  transport.plan = (points) => ({
    receipts: points.map((_, index) => ({ index, accepted: false, reason: REASON })),
  });
  const sender = createTrackSender({ queue, transport, batchSize: 10 });
  await sender.sendPending();
  assert.equal(await queue.size(), 0);
  // Um ponto novo entra depois; a volta seguinte envia SO o novo.
  const fresh = point('2026-11-10T09:05:00Z');
  await queue.enqueue(fresh);
  transport.plan = null; // aceita tudo
  const summary = await sender.sendPending();
  assert.equal(summary.accepted, 1);
  assert.equal(transport.calls.length, 2);
  assert.deepEqual(transport.calls[1], [fresh]);
});

test('falha de rede mantem os pontos na fila; a volta seguinte drena', async () => {
  const only = point('2026-11-10T09:00:00Z');
  const queue = await queueWith([only]);
  const transport = new FakeTransport();
  transport.plan = () => { throw new Error('rede indisponivel (ensaio)'); };
  const sender = createTrackSender({ queue, transport, batchSize: 10 });
  const failed = await sender.sendPending();
  assert.equal(failed.networkFailed, true);
  assert.equal(failed.accepted, 0);
  assert.equal(failed.remaining, 1);
  assert.deepEqual(await queue.pending(), [only]);
  transport.plan = null;
  const recovered = await sender.sendPending();
  assert.equal(recovered.accepted, 1);
  assert.equal(recovered.remaining, 0);
});

test('drena em lotes do tamanho dado: 5 pontos com lote 2 fazem 3 envios (2, 2, 1)', async () => {
  const points = ['09:00', '09:01', '09:02', '09:03', '09:04']
    .map((hhmm) => point('2026-11-10T' + hhmm + ':00Z'));
  const queue = await queueWith(points);
  const transport = new FakeTransport();
  const sender = createTrackSender({ queue, transport, batchSize: 2 });
  const summary = await sender.sendPending();
  assert.equal(summary.accepted, 5);
  assert.deepEqual(transport.calls.map((call) => call.length), [2, 2, 1]);
});

test('recibo em falta viola o contrato: nada sai da fila e o erro e nomeado', async () => {
  const first = point('2026-11-10T09:00:00Z');
  const second = point('2026-11-10T09:01:00Z');
  const queue = await queueWith([first, second]);
  const transport = new FakeTransport();
  transport.plan = () => ({ receipts: [{ index: 0, accepted: true, reason: null }] });
  const sender = createTrackSender({ queue, transport, batchSize: 10 });
  await assert.rejects(sender.sendPending(), (error: unknown) => {
    assert.ok(error instanceof TrackSenderError);
    assert.equal(error.problem, 'TRACK_RECEIPT_MISMATCH');
    return true;
  });
  assert.deepEqual(await queue.pending(), [first, second]);
});

test('fila vazia: nenhuma chamada ao transporte e resumo a zeros', async () => {
  const queue = await queueWith([]);
  const transport = new FakeTransport();
  const sender = createTrackSender({ queue, transport, batchSize: 10 });
  const summary = await sender.sendPending();
  assert.deepEqual(summary, { accepted: 0, rejected: [], remaining: 0, networkFailed: false });
  assert.equal(transport.calls.length, 0);
});

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { SyncEngine, type DeviceDirective, type SyncTransport } from './syncEngine.ts';
import type { LocalStore } from './syncEngine.ts';
import type { FieldSecurityBridge } from '../plugins/fieldSecurity.ts';
import type { PendingReading } from '../readings/routePackage.ts';

const PENDING: PendingReading[] = [
  { taskId: 'task-1', reading: 12345, occurrenceCode: 'NORMAL', photoRefs: [], ocrValue: null, capturedAt: '2026-11-07T12:00:00Z' },
];

function harness(directive: DeviceDirective) {
  const calls: string[] = [];
  const sentTaskIds: string[] = [];
  const transport: SyncTransport = {
    async fetchDirective() { calls.push('directive'); return directive; },
    async uploadPending(_routeId, pending) {
      calls.push('upload');
      for (const p of pending) sentTaskIds.push(p.taskId);
      return pending.map((p) => ({ taskId: p.taskId, duplicate: false }));
    },
    async downloadRoute() { throw new Error('nao usado'); },
  };
  const saved: PendingReading[] = [];
  let queue = [...PENDING];
  const store: LocalStore = {
    async pendingReadings() { return [...queue]; },
    // P-04 (fusao do lote R3-D): a gravacao upserta por task_id — a leitura
    // gravada passa a ser devolvida por pendingReadings e enviada na
    // proxima sync. (Na main viva o metodo e OBRIGATORIO no contrato
    // LocalStore; o duplo aqui o exercita de verdade, nao como no-op.)
    async saveReading(reading: PendingReading) {
      saved.push(reading);
      queue = [...queue.filter((q) => q.taskId !== reading.taskId), reading];
    },
    async saveRoute() {},
    // clearRoute do duplo NAO esvazia a fila: o teste de reenvio abaixo
    // exige que a segunda sync releia a mesma pendencia.
    async clearRoute() { calls.push('clearRoute'); },
  };
  const security: FieldSecurityBridge = {
    async openEncryptedDatabase() { return { opened: true }; },
    async wipeAll() { calls.push('wipe'); return { wiped: true }; },
  };
  return { engine: new SyncEngine(transport, store, security), calls, sentTaskIds, saved, store };
}

test('BLOCK nao envia nada e apaga tudo', async () => {
  const { engine, calls, sentTaskIds } = harness('BLOCK');
  const result = await engine.syncRoute('rota-1');
  assert.deepEqual(sentTaskIds, []);
  assert.deepEqual(calls, ['directive', 'wipe']);
  assert.deepEqual(result, { uploaded: 0, wiped: true });
});

test('RETIRE envia as pendencias uma vez e depois apaga tudo', async () => {
  const { engine, calls, sentTaskIds } = harness('RETIRE');
  const result = await engine.syncRoute('rota-1');
  assert.deepEqual(sentTaskIds, ['task-1']);
  assert.deepEqual(calls, ['directive', 'upload', 'wipe']);
  assert.deepEqual(result, { uploaded: 1, wiped: true });
});

test('NONE envia com o task_id original da tarefa (reenvio nao gera id novo)', async () => {
  const { engine, sentTaskIds } = harness('NONE');
  await engine.syncRoute('rota-1');
  await engine.syncRoute('rota-1');
  // A fila local devolve sempre a mesma tarefa: os dois envios levam 'task-1',
  // e a deduplicacao final e do servidor (recibo duplicate).
  assert.deepEqual(sentTaskIds, ['task-1', 'task-1']);
});

test('P-04 (lote R3-D): LocalStore.saveReading grava na fila e a proxima sync envia', async () => {
  const { engine, sentTaskIds, saved, store } = harness('NONE');
  const reading: PendingReading = {
    taskId: 'task-2', reading: 505, occurrenceCode: null, photoRefs: [], ocrValue: null,
    capturedAt: '2026-11-08T10:00:00Z',
  };
  await store.saveReading(reading);
  assert.equal(saved.length, 1);
  assert.equal(saved[0].taskId, 'task-2');
  const result = await engine.syncRoute('rota-1');
  assert.deepEqual(sentTaskIds, ['task-1', 'task-2']);
  assert.equal(result.uploaded, 2);
  // Regravar a mesma tarefa substitui, nao duplica.
  await store.saveReading({ ...reading, reading: 510 });
  const result2 = await engine.syncRoute('rota-1');
  assert.deepEqual(sentTaskIds, ['task-1', 'task-2', 'task-1', 'task-2']);
  assert.equal(result2.uploaded, 2);
});

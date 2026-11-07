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
  const store: LocalStore = {
    async pendingReadings() { return PENDING; },
    async saveRoute() {},
    async clearRoute() { calls.push('clearRoute'); },
  };
  const security: FieldSecurityBridge = {
    async openEncryptedDatabase() { return { opened: true }; },
    async wipeAll() { calls.push('wipe'); return { wiped: true }; },
  };
  return { engine: new SyncEngine(transport, store, security), calls, sentTaskIds };
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

import { test } from 'node:test';
import assert from 'node:assert/strict';
import type { PendingReading } from '../readings/routePackage.ts';
import type { LocalStore } from './syncEngine.ts';
import { createSaveReading } from './fieldReadingStore.ts';

function memoryStore(): { store: LocalStore; saved: () => PendingReading[] } {
  const rows = new Map<string, PendingReading>();
  const store: LocalStore = {
    async pendingReadings() { return [...rows.values()]; },
    async saveReading(reading) {
      rows.set(reading.taskId, reading);
    },
    async saveRoute() {},
    async clearRoute() {},
  };
  return { store, saved: () => [...rows.values()] };
}

const READING: PendingReading = {
  taskId: 'task-1', reading: 12012, occurrenceCode: null, photoRefs: [], ocrValue: null,
  capturedAt: '2026-11-08T10:00:00Z',
};

test('P-04: a porta delega a gravacao ao LocalStore real', async () => {
  const { store, saved } = memoryStore();
  const saveReading = createSaveReading(store);
  await saveReading(READING);
  assert.equal(saved().length, 1);
  assert.equal(saved()[0].reading, 12012);
  assert.equal(saved()[0].capturedAt, '2026-11-08T10:00:00Z');
});

test('P-04: confirmar a mesma tarefa duas vezes substitui (upsert por taskId), nao duplica', async () => {
  const { store, saved } = memoryStore();
  const saveReading = createSaveReading(store);
  await saveReading(READING);
  await saveReading({ ...READING, reading: 12013 });
  assert.equal(saved().length, 1);
  assert.equal(saved()[0].reading, 12013);
});

test('P-04: a leitura gravada volta pela fila local (sobrevive ao reload do controller)', async () => {
  const { store } = memoryStore();
  const saveReading = createSaveReading(store);
  await saveReading(READING);
  assert.deepEqual(await store.pendingReadings('rota-1'), [READING]);
});

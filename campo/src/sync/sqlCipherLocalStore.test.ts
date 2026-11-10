import { test } from 'node:test';
import assert from 'node:assert/strict';
import type { FieldDatabaseBridge } from '../plugins/fieldDatabase.ts';
import { UNKNOWN_TASK_ERROR } from '../plugins/fieldDatabase.ts';
import type { PendingReading, RoutePackage } from '../readings/routePackage.ts';
import { SqlCipherLocalStore, createSqlCipherLocalStore } from './sqlCipherLocalStore.ts';

/**
 * Duble da ponte FieldDatabase: reproduz EM MEMORIA a semantica declarada
 * do plugin nativo (FieldDatabasePlugin.kt / FieldDatabaseSupport.kt) —
 * mapa task->route derivado do pacote gravado no saveRoute, upsert por
 * taskId, clearRoute so da rota, erro nomeado UNKNOWN_TASK. O duble NAO e
 * a persistencia: ele guarda o que o teste precisa para provar a fiacao
 * TS; a cifra e o disco sao do nativo (prova instrumentada, nao rodada
 * nesta VM — ver RELATORIO-FRENTE-A).
 */
function fakeBridge(): FieldDatabaseBridge {
  const routes = new Map<string, { taskIds: string[] }>();
  const readings = new Map<string, { routeId: string; reading: PendingReading }>();
  return {
    async saveRoute(routePackageJson: string) {
      const pkg = JSON.parse(routePackageJson) as RoutePackage;
      routes.set(pkg.routeId, { taskIds: pkg.tasks.map((t) => t.taskId) });
    },
    async pendingReadings(routeId: string) {
      const list = [...readings.values()]
        .filter((r) => r.routeId === routeId)
        .map((r) => r.reading);
      return { readingsJson: JSON.stringify(list) };
    },
    async saveReading(readingJson: string) {
      const reading = JSON.parse(readingJson) as PendingReading;
      let routeId: string | null = null;
      for (const [id, route] of routes) {
        if (route.taskIds.includes(reading.taskId)) { routeId = id; break; }
      }
      if (routeId === null) {
        throw new Error(`${UNKNOWN_TASK_ERROR}: tarefa desconhecida para saveReading: ${reading.taskId}`);
      }
      readings.set(reading.taskId, { routeId, reading });
    },
    async clearRoute(routeId: string) {
      routes.delete(routeId);
      for (const [taskId, row] of readings) {
        if (row.routeId === routeId) readings.delete(taskId);
      }
    },
  };
}

function routePackage(routeId: string, taskIds: string[]): RoutePackage {
  return {
    routeId,
    reference: '2026-11',
    downloadedAt: '2026-11-10T06:00:00Z',
    occurrences: [{ code: 'NORMAL', requiresPhoto: false, requiresReading: true }],
    tasks: taskIds.map((taskId, i) => ({
      taskId,
      unitId: `unit-${taskId}`,
      installationId: `inst-${taskId}`,
      kind: 'WATER' as const,
      reference: '2026-11',
      previousReading: 12000,
      meterDigits: 5,
      sequence: i + 1,
    })),
  };
}

const READING: PendingReading = {
  taskId: 'task-1', reading: 12012, occurrenceCode: null,
  photoRefs: ['file:///ocorrencia.jpg'], ocrValue: 12012,
  capturedAt: '2026-11-10T09:00:00Z',
};

test('RG2-C-P4: gravar e reler com NOVA instancia sobre a mesma ponte (reload simulado)', async () => {
  const bridge = fakeBridge();
  const store1 = createSqlCipherLocalStore(bridge);
  await store1.saveRoute(routePackage('rota-1', ['task-1', 'task-2']));
  await store1.saveReading(READING);
  // Reload = nova instancia do store sobre a mesma ponte/banco: nada pode
  // viver na memoria da instancia antiga.
  const store2 = new SqlCipherLocalStore(bridge);
  assert.deepEqual(await store2.pendingReadings('rota-1'), [READING]);
});

test('RG2-C-P4: upsert pela mesma taskId substitui sem duplicar', async () => {
  const store = createSqlCipherLocalStore(fakeBridge());
  await store.saveRoute(routePackage('rota-1', ['task-1']));
  await store.saveReading(READING);
  await store.saveReading({ ...READING, reading: 12020 });
  const pending = await store.pendingReadings('rota-1');
  assert.equal(pending.length, 1);
  assert.equal(pending[0].reading, 12020);
  assert.equal(pending[0].taskId, 'task-1');
});

test('RG2-C-P4: pendingReadings devolve so as leituras da rota pedida', async () => {
  const store = createSqlCipherLocalStore(fakeBridge());
  await store.saveRoute(routePackage('rota-1', ['task-1']));
  await store.saveRoute(routePackage('rota-2', ['task-9']));
  await store.saveReading(READING);
  await store.saveReading({ ...READING, taskId: 'task-9', reading: 777 });
  assert.deepEqual((await store.pendingReadings('rota-1')).map((r) => r.taskId), ['task-1']);
  assert.deepEqual((await store.pendingReadings('rota-2')).map((r) => r.taskId), ['task-9']);
  assert.deepEqual(await store.pendingReadings('rota-inexistente'), []);
});

test('RG2-C-P4: clearRoute apaga so a rota pedida (pacote, mapa e leituras)', async () => {
  const store = createSqlCipherLocalStore(fakeBridge());
  await store.saveRoute(routePackage('rota-1', ['task-1']));
  await store.saveRoute(routePackage('rota-2', ['task-9']));
  await store.saveReading(READING);
  await store.saveReading({ ...READING, taskId: 'task-9', reading: 777 });
  await store.clearRoute('rota-1');
  assert.deepEqual(await store.pendingReadings('rota-1'), []);
  assert.equal((await store.pendingReadings('rota-2')).length, 1);
  // O mapa da rota apagada some junto: a tarefa dela volta a ser desconhecida.
  await assert.rejects(store.saveReading(READING), new RegExp(`^Error: ${UNKNOWN_TASK_ERROR}`));
  // E a rota sobrevivente continua gravando normalmente.
  await store.saveReading({ ...READING, taskId: 'task-9', reading: 778 });
  assert.equal((await store.pendingReadings('rota-2'))[0].reading, 778);
});

test('RG2-C-P4: leitura de tarefa desconhecida e recusada com o erro nomeado UNKNOWN_TASK', async () => {
  const store = createSqlCipherLocalStore(fakeBridge());
  await store.saveRoute(routePackage('rota-1', ['task-1']));
  await assert.rejects(
    store.saveReading({ ...READING, taskId: 'task-fantasma' }),
    (err: unknown) => {
      assert.ok(err instanceof Error);
      assert.ok(err.message.startsWith(UNKNOWN_TASK_ERROR), err.message);
      assert.ok(err.message.includes('task-fantasma'), err.message);
      return true;
    },
  );
  // Nada foi gravado como orfa: a fila da rota segue vazia.
  assert.deepEqual(await store.pendingReadings('rota-1'), []);
});

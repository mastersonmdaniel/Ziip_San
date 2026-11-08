import { test } from 'node:test';
import assert from 'node:assert/strict';
import { applyReceipts, prepareUpload, type DeltaQueueState } from './gisDeltaSync.ts';
import type { GisDelta } from './gisDelta.ts';

function delta(id: string, at: string): GisDelta {
  return {
    clientChangeId: id, packageId: 'pkg-sintetico-1', operation: 'UPDATE',
    featureId: 'feat-valvula-1', featureTable: 'gis_node', baseVersion: 7,
    changedAttributes: { operational_status: 'CLOSED' },
    baseAttributes: { operational_status: 'OPEN' },
    geomWkt: null, fix: null, asBuilt: false, capturedAt: at,
  };
}
const D1 = delta('0192a1b4-0000-7000-8000-000000000001', '2026-11-08T12:00:00Z');
const D2 = delta('0192a1b4-0000-7000-8000-000000000002', '2026-11-08T12:05:00Z');

test('BLOCK (aparelho bloqueado, ED-18/ADR-0030): nenhum delta sai no lote', () => {
  const state: DeltaQueueState = { pending: [D1, D2], conflicted: [] };
  assert.deepEqual(prepareUpload(state, 'BLOCK'), []);
});

test('NONE/RETIRE: lote leva os pendentes na ordem de captura, com os ids originais', () => {
  const state: DeltaQueueState = { pending: [D2, D1], conflicted: [] };
  assert.deepEqual(prepareUpload(state, 'NONE').map((d) => d.clientChangeId),
    [D1.clientChangeId, D2.clientChangeId]);
  assert.deepEqual(prepareUpload(state, 'RETIRE').map((d) => d.clientChangeId),
    [D1.clientChangeId, D2.clientChangeId]);
});

test('reenvio nao duplica: recibo DUPLICATE tira o delta da fila como o APPLIED', () => {
  const state: DeltaQueueState = { pending: [D1, D2], conflicted: [] };
  const next = applyReceipts(state, [
    { clientChangeId: D1.clientChangeId, outcome: 'DUPLICATE', conflictFields: [], reason: null },
    { clientChangeId: D2.clientChangeId, outcome: 'APPLIED', conflictFields: [], reason: null },
  ]);
  assert.deepEqual(next.pending, []);
  assert.deepEqual(next.conflicted, []);
});

test('conflito por atributo move o delta para a fila de conflito com os campos (resolucao na web)', () => {
  const state: DeltaQueueState = { pending: [D1], conflicted: [] };
  const next = applyReceipts(state, [
    {
      clientChangeId: D1.clientChangeId, outcome: 'CONFLICT',
      conflictFields: ['operational_status'], reason: null,
    },
  ]);
  assert.deepEqual(next.pending, []);
  assert.equal(next.conflicted.length, 1);
  assert.deepEqual(next.conflicted[0].conflictFields, ['operational_status']);
});

test('M2: prepareUpload com pacote VENCIDO recusa pelo nome e nenhum delta sai', () => {
  const state: DeltaQueueState = { pending: [D1, D2], conflicted: [] };
  assert.throws(
    () => prepareUpload(state, 'NONE', {
      validUntil: '2026-11-09T08:00:00Z', nowIso: '2026-11-10T00:00:00Z',
    }),
    /PACKAGE_EXPIRED/,
  );
});

test('M2: prepareUpload com pacote vigente envia normalmente (validade informada)', () => {
  const state: DeltaQueueState = { pending: [D2, D1], conflicted: [] };
  assert.deepEqual(
    prepareUpload(state, 'NONE', {
      validUntil: '2026-11-09T08:00:00Z', nowIso: '2026-11-08T12:30:00Z',
    }).map((d) => d.clientChangeId),
    [D1.clientChangeId, D2.clientChangeId],
  );
});

test('delta sem recibo continua pendente; rejeitado vai para conflito com o motivo, nunca some', () => {
  const state: DeltaQueueState = { pending: [D1, D2], conflicted: [] };
  const next = applyReceipts(state, [
    {
      clientChangeId: D2.clientChangeId, outcome: 'REJECTED',
      conflictFields: [], reason: 'pacote-vencido',
    },
  ]);
  assert.deepEqual(next.pending.map((d) => d.clientChangeId), [D1.clientChangeId]);
  assert.deepEqual(next.conflicted[0].conflictFields, ['__rejected__:pacote-vencido']);
});

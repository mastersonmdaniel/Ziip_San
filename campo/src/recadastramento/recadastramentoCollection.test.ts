import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  buildPendingValue,
  maskDocumentValue,
  type RecadastramentoTask,
} from './recadastramentoCollection.ts';

const TASK: RecadastramentoTask = {
  taskId: 'task-1',
  batchId: 'batch-1',
  unitId: 'unit-1',
  customerId: 'customer-1',
  fields: [
    { targetType: 'CUSTOMER', field: 'name', osType: 'CUSTOMER_DATA_UPDATE' },
    { targetType: 'CUSTOMER', field: 'document', osType: 'CUSTOMER_DATA_UPDATE' },
    { targetType: 'CONSUMER_UNIT', field: 'builtAreaM2', osType: 'UNIT_PROFILE_UPDATE' },
  ],
};

test('coleta de campo pre-definido monta o pendente com o task_id da tarefa', () => {
  const pending = buildPendingValue(TASK, 'CONSUMER_UNIT', 'builtAreaM2', ' 120.5 ', [], '2026-11-07T12:00:00Z');
  assert.equal(pending.taskId, 'task-1');
  assert.equal(pending.batchId, 'batch-1');
  assert.equal(pending.collectedValue, '120.5');
  assert.equal(pending.valueMasked, false);
});

test('campo fora do pre-definido da tarefa e recusado no aparelho', () => {
  assert.throws(
    () => buildPendingValue(TASK, 'CUSTOMER', 'tradeName', 'Loja X', [], '2026-11-07T12:00:00Z'),
    /fora do pre-definido/,
  );
});

test('valor vazio e recusado no aparelho', () => {
  assert.throws(
    () => buildPendingValue(TASK, 'CUSTOMER', 'name', '   ', [], '2026-11-07T12:00:00Z'),
    /vazio/,
  );
});

test('documento so viaja mascarado: a mascara sai pronta da coleta', () => {
  const pending = buildPendingValue(TASK, 'CUSTOMER', 'document', '123.456.789-09', [], '2026-11-07T12:00:00Z');
  assert.equal(pending.valueMasked, true);
  assert.equal(pending.collectedValue, '*********09');
  assert.equal(maskDocumentValue('12345678909'), '*********09');
});

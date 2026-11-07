import { test } from 'node:test';
import assert from 'node:assert/strict';
import { buildSyncPayload } from './onDeviceOcr.ts';

test('payload de sincronizacao nao carrega imagem do visor nem campo de imagem', () => {
  const payload = buildSyncPayload({
    taskId: 't-1',
    reading: 12345,
    occurrenceCode: 'NORMAL',
    occurrencePhotoRefs: ['sha256:foto-ocorrencia'],
    ocrValue: 12345,
    capturedAt: '2026-11-07T12:00:00Z',
  });
  const keys = Object.keys(payload).sort();
  assert.deepEqual(keys, ['capturedAt', 'occurrenceCode', 'ocrValue', 'photoRefs', 'reading', 'taskId']);
  assert.ok(!JSON.stringify(payload).includes('visor'));
  assert.ok(!JSON.stringify(payload).includes('base64'));
});

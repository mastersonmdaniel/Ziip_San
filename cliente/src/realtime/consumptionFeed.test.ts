import { test } from 'node:test';
import assert from 'node:assert/strict';
import { effectivePollMs, isStale, visibleCurve } from './consumptionFeed.ts';

test('polling abaixo do piso e elevado a 60 s; acima e respeitado', () => {
  assert.equal(effectivePollMs(5_000), 60_000);
  assert.equal(effectivePollMs(300_000), 300_000);
  assert.throws(() => effectivePollMs(0));
});

test('leitura com mais de 2 h e desatualizada; recente nao', () => {
  assert.equal(isStale('2026-11-09T09:00:00Z', '2026-11-09T12:00:00Z'), true);
  assert.equal(isStale('2026-11-09T11:30:00Z', '2026-11-09T12:00:00Z'), false);
});

test('curva desligada no parametro nao mostra ponto nenhum', () => {
  const points = [{ hourIso: '2026-11-09T11:00:00Z', litres: 12, dataState: 'MEASURED' }];
  assert.deepEqual(visibleCurve(false, points), []);
  assert.deepEqual(visibleCurve(true, points), points);
});

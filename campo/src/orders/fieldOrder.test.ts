import { test } from 'node:test';
import assert from 'node:assert/strict';
import { revalidateCut, identityMatches, nextVisibleState, type CutFacts } from './fieldOrder.ts';

const OPEN_OK: CutFacts = { debitState: 'OPEN', contestationOpen: false, noticeGiven: true, prohibitions: [] };

test('offline: quitacao registrada depois da emissao do pacote cancela a OS de corte', () => {
  const now: CutFacts = { ...OPEN_OK, debitState: 'PAID' };
  assert.deepEqual(revalidateCut(OPEN_OK, now), ['DEBIT_PAID_AFTER_ISSUE']);
});

test('offline: debito aberto, aviso dado e sem vedacao permite executar', () => {
  assert.deepEqual(revalidateCut(OPEN_OK, OPEN_OK), []);
});

test('offline: contestacao aberta veda o corte', () => {
  const now: CutFacts = { ...OPEN_OK, contestationOpen: true };
  assert.deepEqual(revalidateCut(OPEN_OK, now), ['CONTESTATION_OPEN']);
});

test('codigo de outra unidade nao confirma identidade do hidrometro', () => {
  assert.equal(identityMatches(
    { unitId: 'un-1', target: 'METER', code: 'HID-123' },
    { unitId: 'un-2', target: 'METER', code: 'HID-123', channel: 'QR' }), false);
  assert.equal(identityMatches(
    { unitId: 'un-1', target: 'METER', code: 'HID-123' },
    { unitId: 'un-1', target: 'METER', code: 'HID-123', channel: 'NFC' }), true);
});

test('estados visiveis seguem a ordem a caminho -> no local -> em execucao -> concluida', () => {
  assert.equal(nextVisibleState('EN_ROUTE', 'ON_SITE'), 'ON_SITE');
  assert.equal(nextVisibleState('EN_ROUTE', 'COMPLETED'), null);
  assert.equal(nextVisibleState('COMPLETED', 'ON_SITE'), null);
});

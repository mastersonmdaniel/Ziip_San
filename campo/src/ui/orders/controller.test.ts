import { test } from 'node:test';
import assert from 'node:assert/strict';
import type { CutFacts } from '../../orders/fieldOrder.ts';
import { OrderScreensController, type OrderScreensDeps } from './controller.ts';
import type { FieldOrder, FieldOrderPackage } from './models.ts';

const OPEN_OK: CutFacts = { debitState: 'OPEN', contestationOpen: false, noticeGiven: true, prohibitions: [] };

const CUT: FieldOrder = {
  orderId: 'os-1', unitId: 'u-1', taskId: 'task-1', kindLabel: 'Corte',
  state: 'EN_ROUTE', issuedAt: '2026-11-08T06:00:00Z',
  cutSnapshot: {
    orderId: 'os-1', unitId: 'u-1', taskId: 'task-1', issuedAt: '2026-11-08T06:00:00Z',
    factsAtIssue: OPEN_OK,
  },
  expectedIdentity: { unitId: 'u-1', target: 'METER', code: 'HID-123' },
};

const OTHER: FieldOrder = {
  orderId: 'os-2', unitId: 'u-2', taskId: 'task-2', kindLabel: 'Ligação',
  state: 'ON_SITE', issuedAt: '2026-11-08T06:00:00Z',
};

const PKG: FieldOrderPackage = { orders: [CUT, OTHER] };

function deps(over: Partial<OrderScreensDeps> = {}): OrderScreensDeps {
  return { pkg: PKG, getCurrentCutFacts: null, ...over };
}

test('estados iniciais vem do pacote e o progresso conta concluidas', () => {
  const ctl = new OrderScreensController(deps());
  assert.equal(ctl.stateOf('os-1'), 'EN_ROUTE');
  assert.equal(ctl.stateOf('os-2'), 'ON_SITE');
  assert.equal(ctl.progress().done, 0);
  assert.equal(ctl.progress().total, 2);
});

test('avanco segue a ordem a caminho -> no local -> em execucao -> concluida', () => {
  const ctl = new OrderScreensController(deps());
  assert.deepEqual(ctl.advance('os-1'), { ok: true });
  assert.equal(ctl.stateOf('os-1'), 'ON_SITE');
  assert.deepEqual(ctl.advance('os-1'), { ok: true });
  assert.equal(ctl.stateOf('os-1'), 'IN_EXECUTION');
  // Sem fotos, a conclusao e recusada pelo portao (nao afrouxado).
  assert.equal(ctl.advance('os-1').ok, false);
  assert.equal(ctl.stateOf('os-1'), 'IN_EXECUTION');
});

test('conclusao exige foto de antes e de depois; com as duas, conclui', () => {
  const ctl = new OrderScreensController(deps());
  ctl.advance('os-1');
  ctl.advance('os-1');
  assert.equal(ctl.addBeforePhoto('os-1', 'file:///antes.jpg'), true);
  assert.equal(ctl.advance('os-1').ok, false);
  assert.equal(ctl.addAfterPhoto('os-1', '   '), false);
  assert.equal(ctl.addAfterPhoto('os-1', 'file:///depois.jpg'), true);
  assert.deepEqual(ctl.advance('os-1'), { ok: true });
  assert.equal(ctl.stateOf('os-1'), 'COMPLETED');
  assert.equal(ctl.progress().done, 1);
  assert.equal(ctl.advance('os-1').ok, false);
});

test('revalidacao sem pacote de corte nao se aplica; sem porta, declara a pendencia', async () => {
  const ctl = new OrderScreensController(deps());
  assert.deepEqual(await ctl.revalidateCut('os-2'),
    { ok: false, error: 'Ordem sem pacote de corte — revalidação não se aplica.' });
  assert.deepEqual(await ctl.revalidateCut('os-1'),
    { ok: false, error: 'Fatos atuais de corte indisponíveis nesta integração (P-ED20-02) — revalidação pendente.' });
});

test('revalidacao com a porta: quitacao apos a emissao cancela; sem mudanca, pode executar', async () => {
  let current: CutFacts = OPEN_OK;
  const ctl = new OrderScreensController(deps({
    getCurrentCutFacts: async () => current,
  }));
  assert.deepEqual(await ctl.revalidateCut('os-1'), { ok: true, reasons: [] });
  assert.deepEqual(ctl.cutReasons('os-1'), []);
  current = { ...OPEN_OK, debitState: 'PAID' };
  assert.deepEqual(await ctl.revalidateCut('os-1'), { ok: true, reasons: ['DEBIT_PAID_AFTER_ISSUE'] });
  assert.deepEqual(ctl.cutReasons('os-1'), ['DEBIT_PAID_AFTER_ISSUE']);
});

test('identidade: codigo de outra unidade nao confirma; da mesma unidade confirma', () => {
  const ctl = new OrderScreensController(deps());
  assert.deepEqual(
    ctl.confirmIdentity('os-1', { unitId: 'u-2', target: 'METER', code: 'HID-123', channel: 'QR' }),
    { ok: false, error: 'Código de outra unidade — identidade não confirmada.' });
  assert.equal(ctl.isIdentityConfirmed('os-1'), false);
  assert.deepEqual(
    ctl.confirmIdentity('os-1', { unitId: 'u-1', target: 'METER', code: 'HID-123', channel: 'NFC' }),
    { ok: true });
  assert.equal(ctl.isIdentityConfirmed('os-1'), true);
});

test('identidade sem esperado no pacote recusa sem confirmar no escuro', () => {
  const ctl = new OrderScreensController(deps());
  assert.deepEqual(
    ctl.confirmIdentity('os-2', { unitId: null, target: 'METER', code: 'HID-1', channel: 'QR' }),
    { ok: false, error: 'Ordem sem identidade esperada no pacote — confirmação indisponível.' });
});

test('fotos de antes/depois anexam por URI, listam e removem pelo controller', () => {
  const ctl = new OrderScreensController(deps());
  ctl.addBeforePhoto('os-2', 'file:///a.jpg');
  ctl.addBeforePhoto('os-2', 'file:///a.jpg');
  assert.deepEqual(ctl.beforePhotoRefs('os-2'), ['file:///a.jpg']);
  ctl.removeBeforePhoto('os-2', 'file:///a.jpg');
  assert.deepEqual(ctl.beforePhotoRefs('os-2'), []);
  ctl.addAfterPhoto('os-2', 'file:///d.jpg');
  assert.deepEqual(ctl.afterPhotoRefs('os-2'), ['file:///d.jpg']);
});

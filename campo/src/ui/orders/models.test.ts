import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  OrderNavigator,
  addPhotoRef,
  completionGate,
  cutReasonLabel,
  debitStateLabel,
  nextState,
  nextStateLabel,
  orderLabel,
  orderProgress,
  revalidateOrderCut,
  orderIdentityMatches,
  visibleStateLabel,
  type FieldOrder,
  type FieldOrderPackage,
} from './models.ts';

const CUT: FieldOrder = {
  orderId: 'os-1', unitId: 'u-1', taskId: 'task-1', kindLabel: 'Corte',
  unitAddress: 'Rua das Flores, 123', state: 'EN_ROUTE', issuedAt: '2026-11-08T06:00:00Z',
  cutSnapshot: {
    orderId: 'os-1', unitId: 'u-1', taskId: 'task-1', issuedAt: '2026-11-08T06:00:00Z',
    factsAtIssue: { debitState: 'OPEN', contestationOpen: false, noticeGiven: true, prohibitions: [] },
  },
  expectedIdentity: { unitId: 'u-1', target: 'METER', code: 'HID-123' },
};

const OTHER: FieldOrder = {
  orderId: 'os-2', unitId: 'u-2', taskId: 'task-2', kindLabel: 'Ligação',
  state: 'ON_SITE', issuedAt: '2026-11-08T06:00:00Z',
};

const PKG: FieldOrderPackage = { orders: [CUT, OTHER] };

test('rotulo da OS usa tipo + IDs e o endereco quando o pacote o carrega', () => {
  assert.equal(orderLabel(CUT), 'Corte · OS os-1 · unidade u-1 · Rua das Flores, 123');
  assert.equal(orderLabel(OTHER), 'Ligação · OS os-2 · unidade u-2');
  assert.equal(orderLabel({ ...CUT, unitAddress: '  ' }), 'Corte · OS os-1 · unidade u-1');
});

test('rotulos dos estados visiveis e do botao de avanco (FieldExecution)', () => {
  assert.equal(visibleStateLabel('EN_ROUTE'), 'A caminho');
  assert.equal(visibleStateLabel('ON_SITE'), 'No local');
  assert.equal(visibleStateLabel('IN_EXECUTION'), 'Em execução');
  assert.equal(visibleStateLabel('COMPLETED'), 'Concluída');
  assert.equal(nextStateLabel('EN_ROUTE'), 'Registrar chegada');
  assert.equal(nextStateLabel('ON_SITE'), 'Iniciar execução');
  assert.equal(nextStateLabel('IN_EXECUTION'), 'Concluir OS');
  assert.equal(nextStateLabel('COMPLETED'), null);
  assert.equal(nextState('EN_ROUTE'), 'ON_SITE');
  assert.equal(nextState('COMPLETED'), null);
});

test('rotulos dos motivos de corte e do debito (CutReason real)', () => {
  assert.match(cutReasonLabel('DEBIT_PAID_AFTER_ISSUE'), /quitado após/);
  assert.match(cutReasonLabel('CONTESTATION_OPEN'), /Contestação/);
  assert.match(cutReasonLabel('LEGAL_PROHIBITION'), /Vedação/);
  assert.equal(debitStateLabel('OPEN'), 'aberto');
  assert.equal(debitStateLabel('PAID'), 'quitado');
  assert.equal(debitStateLabel('NONE'), 'sem débito');
});

test('progresso do pacote conta concluidas pelo estado COMPLETED', () => {
  const p = orderProgress(PKG, new Map([['os-1', 'COMPLETED'], ['os-2', 'ON_SITE']]));
  assert.equal(p.total, 2);
  assert.equal(p.done, 1);
  assert.equal(p.pending, 1);
  assert.equal(p.rows[0].state, 'COMPLETED');
});

test('foto antes/depois: anexar URI apara, ignora vazia e nao duplica', () => {
  assert.deepEqual(addPhotoRef([], '  file:///antes.jpg '), ['file:///antes.jpg']);
  assert.deepEqual(addPhotoRef(['file:///a.jpg'], 'file:///a.jpg'), ['file:///a.jpg']);
  assert.deepEqual(addPhotoRef(['file:///a.jpg'], '   '), ['file:///a.jpg']);
});

test('portao de conclusao exige IN_EXECUTION + foto de antes e de depois', () => {
  assert.deepEqual(completionGate('EN_ROUTE', ['a'], ['b']), { ok: false, error: 'OS em "A caminho" não pode ser concluída.' });
  assert.equal(completionGate('IN_EXECUTION', [], ['b']).ok, false);
  assert.equal(completionGate('IN_EXECUTION', ['a'], []).ok, false);
  assert.deepEqual(completionGate('IN_EXECUTION', ['a'], ['b']), { ok: true });
});

test('revalidacao de corte usa a regra real da ED-20 (quitacao apos a emissao cancela)', () => {
  const atIssue = CUT.cutSnapshot!.factsAtIssue;
  assert.deepEqual(revalidateOrderCut(atIssue, atIssue), []);
  assert.deepEqual(
    revalidateOrderCut(atIssue, { ...atIssue, debitState: 'PAID' }),
    ['DEBIT_PAID_AFTER_ISSUE'],
  );
});

test('identidade: codigo de outra unidade nao confirma; da mesma unidade confirma', () => {
  const expected = CUT.expectedIdentity!;
  assert.equal(orderIdentityMatches(expected,
    { unitId: 'u-2', target: 'METER', code: 'HID-123', channel: 'QR' }), false);
  assert.equal(orderIdentityMatches(expected,
    { unitId: 'u-1', target: 'METER', code: 'HID-123', channel: 'NFC' }), true);
});

test('navegacao de OS: abrir ordem vai ao detalhe; voltar desfaz', () => {
  const nav = new OrderNavigator();
  assert.equal(nav.current(), 'lista');
  nav.openOrder('os-1');
  assert.equal(nav.current(), 'detalhe');
  assert.equal(nav.orderId(), 'os-1');
  assert.equal(nav.back(), true);
  assert.equal(nav.current(), 'lista');
  assert.equal(nav.back(), false);
});

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { LOCAL_PUSH_TEXTS, sanitizePushPayload } from './pushPayload.ts';

test('payload so com codigos passa', () => {
  const p = sanitizePushPayload({
    alertCode: 'telemetry.peak-consumption',
    titleKey: 'push.peak.title',
    bodyKey: 'push.peak.body',
    deepLink: '/consumo',
  });
  assert.equal(p.alertCode, 'telemetry.peak-consumption');
});

test('CPF, valor e telefone em qualquer chave sao recusados', () => {
  assert.throws(() => sanitizePushPayload({ alertCode: 'bill.available', bodyKey: '123.456.789-09' }));
  assert.throws(() => sanitizePushPayload({ alertCode: 'bill.available', bodyKey: 'Valor R$ 152,30' }));
  assert.throws(() => sanitizePushPayload({ alertCode: 'bill.available', titleKey: '+55 47 99999-0000' }));
});

test('chave livre, alerta desconhecido e deep link com id sao recusados', () => {
  assert.throws(() => sanitizePushPayload({ alertCode: 'bill.available', customerName: 'Fulano' }));
  assert.throws(() => sanitizePushPayload({ alertCode: 'marketing.promo' }));
  assert.throws(() =>
    sanitizePushPayload({ alertCode: 'bill.available', deepLink: '/conta/00000000-0000-0000-0000-0000000000a3' }),
  );
});

test('D1: nome e endereco em texto livre sao recusados (sonda da revisao)', () => {
  assert.throws(() =>
    sanitizePushPayload({ alertCode: 'bill.available', titleKey: 'Ola Maria Silva, sua conta venceu' }),
  );
  assert.throws(() => sanitizePushPayload({ alertCode: 'bill.available', bodyKey: 'Rua das Flores 123' }));
  assert.throws(() =>
    sanitizePushPayload({ alertCode: 'bill.available', bodyKey: 'Ola Maria Silva, sua conta venceu' }),
  );
  assert.throws(() =>
    sanitizePushPayload({ alertCode: 'outage.scheduled', titleKey: 'Rua das Flores 123' }),
  );
});

test('D1: chave de modelo fora do conjunto fechado ou de outro alerta e recusada', () => {
  assert.throws(() => sanitizePushPayload({ alertCode: 'bill.available', titleKey: 'push.qualquer.title' }));
  assert.throws(() => sanitizePushPayload({ alertCode: 'bill.available', titleKey: 'push.peak.title' }));
  assert.throws(() => sanitizePushPayload({ alertCode: 'bill.available', bodyKey: 'push.bill.title' }));
  const p = sanitizePushPayload({
    alertCode: 'bill.available',
    titleKey: 'push.bill.title',
    bodyKey: 'push.bill.body',
  });
  assert.equal(p.titleKey, 'push.bill.title');
});

test('textos locais de push nao contem marcador de dado pessoal', () => {
  for (const text of Object.values(LOCAL_PUSH_TEXTS)) {
    assert.ok(!/\{|\}|%s|nome|cpf/i.test(`${text.title} ${text.body}`));
  }
});

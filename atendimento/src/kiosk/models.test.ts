/**
 * Testes do espelho do nucleo (models.ts) — pacote ed-40-telas.
 * As massas e os valores esperados espelham as suites Java entregues da
 * ED-40 (KioskAndPanelTest, PriorityOrderingTest, QueueTicketLifecycleTest,
 * KioskParametersTest, WaitingTimeReportSyntheticTest), incluindo a
 * correcao D3 (closedWithoutStart) e a fronteira exata no limite.
 * Fixtures FICTICIAS de demonstracao (unidades/datas sinteticas, como nas
 * suites Java) — nenhum dado real.
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  FACTORY_WAIT_LIMIT_BUSINESS_DAY_MINUTES,
  FACTORY_WAIT_LIMIT_HOLIDAY_ADJACENT_MINUTES,
  HolidayCalendar,
  IllegalArgumentError,
  IllegalStateError,
  KioskTerminal,
  MissingParameterError,
  PRIORITY_CATEGORIES,
  QueueTicket,
  commonProfile,
  generateWaitingReport,
  kioskEnabledParam,
  legalPriority,
  limitFor,
  makeWaitingLimitPolicy,
  nextToCall,
  orderWaiting,
  panelSnapshot,
  priorityProfile,
  waitLimitBusinessDayMinutes,
  waitLimitHolidayAdjacentMinutes,
  type PriorityAttendanceProfile,
} from './models.ts';

const UNIT = '00000000-0000-0000-0000-0000000000a1';
const UNIT_B = '00000000-0000-0000-0000-0000000000b2';
const DEVICE = '00000000-0000-0000-0000-0000000000d1';
const T0 = '2026-03-04T08:00:00';

function ticket(
  seq: number,
  priority: boolean,
  issued: string,
  unit: string = UNIT,
): QueueTicket {
  const profile = priority ? priorityProfile('ELDERLY_60_OR_OVER') : commonProfile();
  return new QueueTicket(`ticket-${unit}-${seq}-${issued}`, unit, seq, 'ATENDIMENTO', profile, issued);
}

test('displayCode marca prioridade legal com P e comum com N, sequencial %03d', () => {
  assert.equal(ticket(7, true, T0).displayCode(), 'P007');
  assert.equal(ticket(7, false, T0).displayCode(), 'N007');
  assert.equal(ticket(12, false, T0).displayCode(), 'N012');
  assert.equal(ticket(3, true, T0).displayCode(), 'P003');
});

test('ordenacao: prioridade legal primeiro, mesmo emitida depois (TicketQueue)', () => {
  const normal1 = ticket(1, false, '2026-03-04T08:00:00');
  const normal2 = ticket(2, false, '2026-03-04T08:01:00');
  const prio = ticket(3, true, '2026-03-04T08:05:00');
  assert.deepEqual(orderWaiting([normal1, normal2, prio]), [prio, normal1, normal2]);
  assert.equal(nextToCall([normal1, normal2, prio]), prio);
});

test('ordenacao: FIFO dentro da classe e desempate pelo sequencial no mesmo instante', () => {
  const pLater = ticket(1, true, '2026-03-04T08:10:00');
  const pEarlier = ticket(2, true, '2026-03-04T08:02:00');
  assert.deepEqual(orderWaiting([pLater, pEarlier]), [pEarlier, pLater]);
  // Mesmo instante de emissao: sequencial menor primeiro.
  const seqHigh = ticket(9, false, T0);
  const seqLow = ticket(4, false, T0);
  assert.deepEqual(orderWaiting([seqHigh, seqLow]), [seqLow, seqHigh]);
});

test('ordenacao: senha chamada sai da fila de espera', () => {
  const t = ticket(1, false, T0);
  t.call('2026-03-04T08:03:00', 'G1');
  assert.deepEqual(orderWaiting([t]), []);
  assert.equal(nextToCall([t]), null);
});

test('todas as categorias da Lei 10.048/2000 sao prioridade legal; NONE nao e', () => {
  for (const c of PRIORITY_CATEGORIES) {
    const p: PriorityAttendanceProfile = {
      category: c,
      companionPresent: false,
      accessibleFormat: 'NONE',
      assistanceChannel: 'NONE',
    };
    assert.equal(legalPriority(p), c !== 'NONE', c);
  }
});

test('ciclo de vida grava emissao, chamada e inicio; espera = emissao -> inicio', () => {
  const t = ticket(1, false, T0);
  t.call('2026-03-04T08:06:00', 'G2');
  t.startService('2026-03-04T08:09:00');
  t.complete('2026-03-04T08:20:00');
  assert.equal(t.issuedAt, T0);
  assert.equal(t.calledAt(), '2026-03-04T08:06:00');
  assert.equal(t.serviceStartedAt(), '2026-03-04T08:09:00');
  assert.equal(t.deskId(), 'G2');
  assert.equal(t.status(), 'COMPLETED');
  assert.equal(t.waitUntilCallSeconds(), 360);
  assert.equal(t.waitingTimeSeconds(), 540);
});

test('ciclo de vida recusa transicoes invalidas SEM reescrever instantes anteriores', () => {
  const t = ticket(1, false, T0);
  assert.throws(() => t.startService('2026-03-04T08:01:00'), IllegalStateError);
  assert.throws(() => t.call('2026-03-04T07:59:00', 'G1'), IllegalArgumentError);
  t.call('2026-03-04T08:10:00', 'G1');
  // Chamada dupla recusada e o instante/guiche originais permanecem.
  assert.throws(() => t.call('2026-03-04T08:12:00', 'G9'), IllegalStateError);
  assert.equal(t.calledAt(), '2026-03-04T08:10:00');
  assert.equal(t.deskId(), 'G1');
  // Inicio antes da hora da chamada recusado; inicio nao e gravado.
  assert.throws(() => t.startService('2026-03-04T08:05:00'), IllegalArgumentError);
  assert.equal(t.serviceStartedAt(), null);
  t.startService('2026-03-04T08:11:00');
  // Encerramento antes do inicio recusado; cancelamento em atendimento recusado.
  assert.throws(() => t.complete('2026-03-04T08:10:30'), IllegalArgumentError);
  assert.throws(() => t.cancel('2026-03-04T08:12:00'), IllegalStateError);
  assert.equal(t.finishedAt(), null);
  t.complete('2026-03-04T08:30:00');
  assert.equal(t.calledAt(), '2026-03-04T08:10:00');
  assert.equal(t.serviceStartedAt(), '2026-03-04T08:11:00');
});

test('ausencia so apos a chamada; cancelamento a partir de WAITING e de CALLED', () => {
  const noShow = ticket(1, false, T0);
  assert.throws(() => noShow.markNoShow('2026-03-04T08:01:00'), IllegalStateError);
  noShow.call('2026-03-04T08:02:00', 'G1');
  noShow.markNoShow('2026-03-04T08:07:00');
  assert.equal(noShow.status(), 'NO_SHOW');
  assert.equal(noShow.waitingTimeSeconds(), null);

  const cancelledWaiting = ticket(2, false, T0);
  cancelledWaiting.cancel('2026-03-04T08:05:00');
  assert.equal(cancelledWaiting.status(), 'CANCELLED');

  const cancelledCalled = ticket(3, false, T0);
  cancelledCalled.call('2026-03-04T08:02:00', 'G2');
  cancelledCalled.cancel('2026-03-04T08:06:00');
  assert.equal(cancelledCalled.status(), 'CANCELLED');
  assert.equal(cancelledCalled.calledAt(), '2026-03-04T08:02:00');
});

test('senha com tipo em branco ou sequencial nao positivo e recusada', () => {
  assert.throws(
    () => new QueueTicket('x', UNIT, 1, ' ', commonProfile(), T0),
    IllegalArgumentError,
  );
  assert.throws(
    () => new QueueTicket('x', UNIT, 0, 'ATENDIMENTO', commonProfile(), T0),
    IllegalArgumentError,
  );
});

test('quiosque desligado recusa emissao e nao permite nenhuma acao (KioskTerminal)', () => {
  const off = KioskTerminal.fromParameters(DEVICE, UNIT, false);
  assert.equal(off.kioskModeEnabled(), false);
  assert.throws(
    () => off.issueTicket('t1', 1, 'ATENDIMENTO', commonProfile(), T0),
    IllegalStateError,
  );
  assert.equal(off.actionAllowed('ISSUE_TICKET'), false);
  assert.equal(off.actionAllowed('ACCESSIBILITY_HELP'), false);
  assert.equal(off.actionAllowed('VIEW_CALL_PANEL'), false);
  assert.equal(off.actionAllowed(null), false);
});

test('quiosque ligado emite para a SUA unidade e permite so a lista fechada', () => {
  const on = KioskTerminal.fromParameters(DEVICE, UNIT, true);
  const t = on.issueTicket('t3', 3, 'ATENDIMENTO', priorityProfile('PERSON_WITH_DISABILITY'), T0);
  assert.equal(t.serviceUnitId, UNIT);
  assert.equal(t.displayCode(), 'P003');
  assert.equal(t.issuedAt, T0);
  assert.equal(on.actionAllowed('ISSUE_TICKET'), true);
  assert.equal(on.actionAllowed('ACCESSIBILITY_HELP'), true);
  assert.equal(on.actionAllowed('VIEW_CALL_PANEL'), true);
});

test('parametro do quiosque ausente e erro explicito, nunca habilitado em silencio', () => {
  assert.throws(() => KioskTerminal.fromParameters(DEVICE, UNIT, null), MissingParameterError);
  assert.throws(() => kioskEnabledParam(undefined), MissingParameterError);
  assert.equal(kioskEnabledParam(true), true);
  assert.equal(kioskEnabledParam(false), false);
});

test('painel: atual e a chamada mais recente da unidade; historico desc; outra unidade fora', () => {
  const a = ticket(1, false, '2026-03-04T08:00:00');
  const b = ticket(2, false, '2026-03-04T08:01:00');
  const other = ticket(9, false, '2026-03-04T08:00:00', UNIT_B);
  a.call('2026-03-04T08:05:00', 'G1');
  b.call('2026-03-04T08:09:00', 'G2');
  other.call('2026-03-04T08:12:00', 'G9');
  const view = panelSnapshot([a, b, other], UNIT, 5);
  assert.equal(view.current?.displayCode, 'N002');
  assert.equal(view.current?.deskId, 'G2');
  assert.equal(view.current?.calledAt, '2026-03-04T08:09:00');
  assert.equal(view.history.length, 1);
  assert.equal(view.history[0]?.displayCode, 'N001');
  const empty = panelSnapshot([], UNIT, 5);
  assert.equal(empty.current, null);
  assert.deepEqual(empty.history, []);
});

test('painel: NEGATIVA explicita — o anuncio expoe SOMENTE codigo, guiche, unidade, hora e prioridade', () => {
  const a = ticket(1, false, T0);
  a.call('2026-03-04T08:05:00', 'G1');
  const view = panelSnapshot([a], UNIT, 5);
  const keys = Object.keys(view.current as object).sort();
  assert.deepEqual(keys, ['calledAt', 'deskId', 'displayCode', 'legalPriority', 'serviceUnitId']);
  const serialized = JSON.stringify(view).toLowerCase();
  for (const forbidden of ['nome', 'cpf', 'telefone', 'email', 'endereco', 'cliente']) {
    assert.equal(serialized.includes(forbidden), false, `campo pessoal vazou: ${forbidden}`);
  }
});

test('parametros de limite: fabrica 15/30 da Lei SC 12.465/2002; valor do tenant e o que vale', () => {
  assert.equal(FACTORY_WAIT_LIMIT_BUSINESS_DAY_MINUTES, 15);
  assert.equal(FACTORY_WAIT_LIMIT_HOLIDAY_ADJACENT_MINUTES, 30);
  assert.equal(waitLimitBusinessDayMinutes(15), 15);
  assert.equal(waitLimitHolidayAdjacentMinutes(30), 30);
  assert.equal(waitLimitBusinessDayMinutes(20), 20);
  assert.equal(waitLimitHolidayAdjacentMinutes(45), 45);
  assert.throws(() => waitLimitBusinessDayMinutes(null), MissingParameterError);
  assert.throws(() => waitLimitHolidayAdjacentMinutes(undefined), MissingParameterError);
  assert.throws(() => waitLimitBusinessDayMinutes(0), IllegalArgumentError);
  assert.throws(() => waitLimitHolidayAdjacentMinutes(-5), IllegalArgumentError);
});

// Calendario FIXTURE (dado sintetico de teste, como na suite Java):
// feriado prolongado sintetico na segunda 02/03/2026 e isolado na quarta 11/03/2026.
const CALENDAR = HolidayCalendar.of(new Set(['2026-03-02', '2026-03-11']));
const POLICY = makeWaitingLimitPolicy(
  CALENDAR,
  waitLimitBusinessDayMinutes(FACTORY_WAIT_LIMIT_BUSINESS_DAY_MINUTES),
  waitLimitHolidayAdjacentMinutes(FACTORY_WAIT_LIMIT_HOLIDAY_ADJACENT_MINUTES),
);

function started(seq: number, day: string, waitMinutes: number, unit: string = UNIT): QueueTicket {
  const issued = `${day}T08:00:00`;
  const t = new QueueTicket(`started-${unit}-${seq}-${day}`, unit, seq, 'ATENDIMENTO', commonProfile(), issued);
  const callMin = Math.max(1, waitMinutes - 1);
  t.call(`${day}T08:${String(callMin).padStart(2, '0')}:00`, 'G1');
  t.startService(
    waitMinutes < 60
      ? `${day}T08:${String(waitMinutes).padStart(2, '0')}:00`
      : `${day}T09:${String(waitMinutes - 60).padStart(2, '0')}:00`,
  );
  return t;
}

test('calendario: feriado prolongado amplia vespera e dia seguinte; isolado nao amplia', () => {
  assert.equal(CALENDAR.isProlongedHoliday('2026-03-02'), true);
  assert.equal(CALENDAR.isProlongedHoliday('2026-03-11'), false);
  assert.equal(CALENDAR.isHolidayAdjacent('2026-02-27'), true);
  assert.equal(CALENDAR.isHolidayAdjacent('2026-03-03'), true);
  assert.equal(CALENDAR.isHolidayAdjacent('2026-03-10'), false);
  assert.equal(CALENDAR.isHolidayAdjacent('2026-03-12'), false);
  assert.equal(limitFor(POLICY, '2026-02-27').minutes, 30);
  assert.equal(limitFor(POLICY, '2026-02-27').kind, 'HOLIDAY_ADJACENT');
  assert.equal(limitFor(POLICY, '2026-03-04').minutes, 15);
  assert.equal(limitFor(POLICY, '2026-03-04').kind, 'BUSINESS_DAY');
});

test('relatorio RAS-26: separa dentro/fora por unidade e dia; pendente fora de dentro/fora', () => {
  const all: QueueTicket[] = [
    started(1, '2026-03-04', 10),
    started(2, '2026-03-04', 15),
    started(3, '2026-03-04', 16),
    started(4, '2026-03-04', 40),
    started(5, '2026-02-27', 20),
    started(6, '2026-02-27', 30),
    started(7, '2026-02-27', 31),
    started(8, '2026-03-03', 29),
    started(9, '2026-03-03', 45),
    started(1, '2026-03-04', 5, UNIT_B),
    new QueueTicket('pendente-1', UNIT, 12, 'ATENDIMENTO', commonProfile(), '2026-03-04T09:00:00'),
  ];
  const rows = generateWaitingReport(all, POLICY);
  assert.equal(rows.length, 4);
  const row = (unit: string, day: string) => {
    const r = rows.find((x) => x.serviceUnitId === unit && x.date === day);
    assert.ok(r, `linha ausente ${unit} ${day}`);
    return r;
  };
  const common = row(UNIT, '2026-03-04');
  assert.equal(common.limitMinutes, 15);
  assert.equal(common.limitKind, 'BUSINESS_DAY');
  assert.equal(common.startedCount, 4);
  assert.equal(common.withinLimit, 2);
  assert.equal(common.outsideLimit, 2);
  assert.equal(common.withinPct, 50);
  assert.equal(common.averageWaitMinutes, 20.25);
  assert.equal(common.maxWaitMinutes, 40);
  // A senha nao iniciada conta A PARTE: nao entra em dentro/fora nem nas iniciadas.
  assert.equal(common.pendingStart, 1);
  assert.equal(common.startedCount, common.withinLimit + common.outsideLimit);

  const eve = row(UNIT, '2026-02-27');
  assert.equal(eve.limitMinutes, 30);
  assert.equal(eve.limitKind, 'HOLIDAY_ADJACENT');
  assert.equal(eve.withinLimit, 2);
  assert.equal(eve.outsideLimit, 1);

  const after = row(UNIT, '2026-03-03');
  assert.equal(after.limitMinutes, 30);
  assert.equal(after.withinLimit, 1);
  assert.equal(after.outsideLimit, 1);

  const unitB = row(UNIT_B, '2026-03-04');
  assert.equal(unitB.startedCount, 1);
  assert.equal(unitB.withinLimit, 1);
  assert.equal(unitB.pendingStart, 0);
});

test('relatorio RAS-26: NO_SHOW/CANCELLED nao inflam pendingStart (correcao D3 da ED-40)', () => {
  const day = '2026-03-04';
  const issued = `${day}T08:00:00`;
  const waiting = new QueueTicket('d3-waiting', UNIT, 21, 'ATENDIMENTO', commonProfile(), issued);
  const called = new QueueTicket('d3-called', UNIT, 22, 'ATENDIMENTO', commonProfile(), issued);
  called.call('2026-03-04T08:03:00', 'G1');
  const noShow = new QueueTicket('d3-noshow', UNIT, 23, 'ATENDIMENTO', commonProfile(), issued);
  noShow.call('2026-03-04T08:04:00', 'G1');
  noShow.markNoShow('2026-03-04T08:10:00');
  const cancelledWaiting = new QueueTicket('d3-cw', UNIT, 24, 'ATENDIMENTO', commonProfile(), issued);
  cancelledWaiting.cancel('2026-03-04T08:05:00');
  const cancelledCalled = new QueueTicket('d3-cc', UNIT, 25, 'ATENDIMENTO', commonProfile(), issued);
  cancelledCalled.call('2026-03-04T08:02:00', 'G2');
  cancelledCalled.cancel('2026-03-04T08:06:00');
  const rows = generateWaitingReport(
    [waiting, called, noShow, cancelledWaiting, cancelledCalled, started(26, day, 10)],
    POLICY,
  );
  assert.equal(rows.length, 1);
  const r = rows[0] as (typeof rows)[number];
  assert.equal(r.startedCount, 1);
  assert.equal(r.withinLimit, 1);
  assert.equal(r.pendingStart, 2);
  assert.equal(r.closedWithoutStart, 3);
  // Particao completa do grupo: iniciadas + pendentes + encerradas sem inicio = total.
  assert.equal(r.startedCount + r.pendingStart + r.closedWithoutStart, 6);
});

test('relatorio RAS-26: espera exatamente no limite conta como DENTRO do limite', () => {
  const rows = generateWaitingReport(
    [started(1, '2026-03-04', 15), started(2, '2026-02-27', 30)],
    POLICY,
  );
  const byDay = new Map(rows.map((r) => [r.date, r]));
  assert.equal(byDay.get('2026-03-04')?.withinLimit, 1);
  assert.equal(byDay.get('2026-02-27')?.withinLimit, 1);
});

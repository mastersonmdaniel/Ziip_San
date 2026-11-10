/**
 * Testes de comportamento das telas (controller.ts + ports.ts) com
 * TRANSPORTE FALSO — pacote ed-40-telas.
 *
 * Cobertura exigida: recusa com o quiosque desligado (sem tocar a
 * porta), URLs EXATAS das duas rotas documentadas, painel sem dado
 * pessoal (negativa explicita, inclusive campo extra injetado pelo
 * "servidor"), ciclo de vida do guiche sem reescrita de instantes e
 * relatorio com nao-iniciadas fora de dentro/fora.
 * Fixtures FICTICIAS de demonstracao — nenhum dado real.
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  HolidayCalendar,
  IllegalStateError,
  KioskTerminal,
  QueueTicket,
  commonProfile,
  makeWaitingLimitPolicy,
  priorityProfile,
} from './models.ts';
import {
  HttpPanelTransport,
  HttpWaitingReportTransport,
  LocalDeskPort,
  LocalKioskIssuePort,
  LocalPanelSource,
  LocalWaitingReportSource,
  type IssueRequest,
  type IssuedTicketView,
  type KioskIssuePort,
} from './ports.ts';
import {
  DeskController,
  KioskController,
  PanelController,
  WaitingReportController,
  assertPanelClean,
  panelContainsPersonalData,
  type KioskDeps,
} from './controller.ts';

const UNIT = '00000000-0000-0000-0000-0000000000a1';
const DEVICE = '00000000-0000-0000-0000-0000000000d1';

class SpyIssuePort implements KioskIssuePort {
  calls = 0;
  async issue(_request: IssueRequest): Promise<IssuedTicketView> {
    this.calls++;
    return { displayCode: 'X000', issuedAt: '2026-03-04T08:00:00', serviceUnitId: UNIT };
  }
}

function kioskDeps(over: Partial<KioskDeps> = {}): KioskDeps {
  let seq = 0;
  let id = 0;
  return {
    deviceId: DEVICE,
    serviceUnitId: UNIT,
    kioskEnabled: true,
    issuePort: new LocalKioskIssuePort(KioskTerminal.fromParameters(DEVICE, UNIT, true)),
    serviceTypes: ['ATENDIMENTO'],
    now: () => '2026-03-04T08:00:00',
    newTicketId: () => `ticket-${++id}`,
    nextSequence: () => ++seq,
    ...over,
  };
}

test('quiosque desligado: emissao RECUSADA sem chamar a porta de emissao', async () => {
  const spy = new SpyIssuePort();
  const ctl = new KioskController(kioskDeps({ kioskEnabled: false, issuePort: spy }));
  assert.equal(ctl.isRefused(), true);
  assert.deepEqual(ctl.allowedActions(), []);
  const outcome = await ctl.issueTicket('ATENDIMENTO', commonProfile());
  assert.equal(outcome.status, 'REFUSED');
  assert.equal(spy.calls, 0);
  assert.equal(ctl.accessibilityHelp(), null);
});

test('quiosque sem parametro configurado: erro explicito e emissao recusada', async () => {
  const spy = new SpyIssuePort();
  const ctl = new KioskController(kioskDeps({ kioskEnabled: null, issuePort: spy }));
  assert.ok(ctl.configurationError);
  assert.equal(ctl.isRefused(), true);
  const outcome = await ctl.issueTicket('ATENDIMENTO', commonProfile());
  assert.equal(outcome.status, 'REFUSED');
  assert.equal(spy.calls, 0);
});

test('quiosque ligado: emissao devolve SO codigo, hora de emissao e unidade', async () => {
  const ctl = new KioskController(kioskDeps());
  const outcome = await ctl.issueTicket('ATENDIMENTO', commonProfile());
  assert.equal(outcome.status, 'ISSUED');
  if (outcome.status !== 'ISSUED') return;
  assert.deepEqual(Object.keys(outcome.view).sort(), ['displayCode', 'issuedAt', 'serviceUnitId']);
  assert.equal(outcome.view.displayCode, 'N001');
  assert.equal(outcome.view.issuedAt, '2026-03-04T08:00:00');
  assert.equal(outcome.view.serviceUnitId, UNIT);
  const prio = await ctl.issueTicket('ATENDIMENTO', priorityProfile('PREGNANT'));
  assert.equal(prio.status, 'ISSUED');
  if (prio.status === 'ISSUED') assert.equal(prio.view.displayCode, 'P002');
});

test('quiosque ligado: tipo fora do catalogo injetado e invalido; ajuda lista formatos e canais da P1.3', async () => {
  const ctl = new KioskController(kioskDeps());
  const bad = await ctl.issueTicket('SERVICO_INEXISTENTE', commonProfile());
  assert.equal(bad.status, 'INVALID');
  const help = ctl.accessibilityHelp();
  assert.ok(help);
  assert.deepEqual(help.formats.map((f) => f.value), ['BRAILLE', 'LARGE_PRINT', 'ACCESSIBLE_PDF', 'AUDIO']);
  assert.deepEqual(help.channels.map((c) => c.value), ['LIBRAS', 'TDD', 'VIDEO_SERVICE']);
});

test('painel HTTP: URL exata da rota documentada e campo pessoal do servidor e DESCARTADO', async () => {
  const urls: string[] = [];
  const transport = new HttpPanelTransport(async (url: string) => {
    urls.push(url);
    return {
      current: {
        displayCode: 'N002',
        deskId: 'G2',
        serviceUnitId: UNIT,
        calledAt: '2026-03-04T08:09:00',
        legalPriority: false,
        // Campo FORA do contrato injetado pelo dublê: nao pode chegar a tela.
        nomeCliente: 'NAO DEVE APARECER',
        cpf: '000.000.000-00',
      },
      history: [
        {
          displayCode: 'N001',
          deskId: 'G1',
          serviceUnitId: UNIT,
          calledAt: '2026-03-04T08:05:00',
          legalPriority: false,
          nomeCliente: 'NAO DEVE APARECER',
        },
      ],
    };
  });
  const ctl = new PanelController(transport);
  const view = await ctl.load(UNIT);
  assert.deepEqual(urls, [`/painel-chamada?unidade=${UNIT}`]);
  // NEGATIVA explicita: nenhum dado pessoal na vista, em nenhuma profundidade.
  assert.equal(panelContainsPersonalData(view), false);
  assert.equal(JSON.stringify(view).includes('NAO DEVE APARECER'), false);
  assert.deepEqual(Object.keys(view.current as object).sort(), [
    'calledAt',
    'deskId',
    'displayCode',
    'legalPriority',
    'serviceUnitId',
  ]);
  assert.equal(view.current?.displayCode, 'N002');
  assert.deepEqual(ctl.currentRow(), { displayCode: 'N002', deskId: 'G2', time: '08:09' });
  assert.deepEqual(ctl.historyRows(), [{ displayCode: 'N001', deskId: 'G1', time: '08:05' }]);
});

test('painel: assertPanelClean recusa vista contaminada com dado pessoal', () => {
  const dirty = {
    current: {
      displayCode: 'N001',
      deskId: 'G1',
      serviceUnitId: UNIT,
      calledAt: '2026-03-04T08:05:00',
      legalPriority: false,
      // simulacao de vazamento a montante da lista branca
      nome: 'FULANO',
    } as never,
    history: [],
  };
  assert.equal(panelContainsPersonalData(dirty), true);
  assert.throws(() => assertPanelClean(dirty));
});

test('painel local: snapshot do nucleo apos chamadas, sem dado pessoal', async () => {
  const a = new QueueTicket('p1', UNIT, 1, 'ATENDIMENTO', commonProfile(), '2026-03-04T08:00:00');
  const b = new QueueTicket('p2', UNIT, 2, 'ATENDIMENTO', commonProfile(), '2026-03-04T08:01:00');
  a.call('2026-03-04T08:05:00', 'G1');
  b.call('2026-03-04T08:09:00', 'G2');
  const ctl = new PanelController(new LocalPanelSource([a, b], 5));
  const view = await ctl.load(UNIT);
  assert.equal(view.current?.displayCode, 'N002');
  assert.equal(view.history[0]?.displayCode, 'N001');
  assert.equal(panelContainsPersonalData(view), false);
});

function deskSetup() {
  const common = new QueueTicket('d1', UNIT, 1, 'ATENDIMENTO', commonProfile(), '2026-03-04T08:00:00');
  const prio = new QueueTicket(
    'd2',
    UNIT,
    2,
    'ATENDIMENTO',
    priorityProfile('ELDERLY_60_OR_OVER'),
    '2026-03-04T08:05:00',
  );
  const port = new LocalDeskPort([common, prio]);
  let clock = '2026-03-04T08:10:00';
  const ctl = new DeskController(port, 'G1', () => clock);
  return {
    common,
    prio,
    port,
    ctl,
    setClock: (v: string) => {
      clock = v;
    },
  };
}

test('guiche: chamar proxima chama a PRIORITARIA primeiro e grava chamada e guiche', async () => {
  const { ctl } = deskSetup();
  await ctl.load();
  // Previa da fila segue a ordem do nucleo: prioridade legal antes da comum.
  assert.deepEqual(ctl.waiting().map((t) => t.displayCode), ['P002', 'N001']);
  const called = await ctl.callNext();
  assert.equal(called.displayCode, 'P002');
  assert.equal(called.deskId, 'G1');
  assert.equal(called.calledAt, '2026-03-04T08:10:00');
  assert.equal(called.status, 'CALLED');
});

test('guiche: ciclo completo preserva os instantes anteriores (sem reescrita)', async () => {
  const { ctl, setClock } = deskSetup();
  await ctl.load();
  const called = await ctl.callNext();
  setClock('2026-03-04T08:12:00');
  const started = await ctl.startService(called.id);
  assert.equal(started.serviceStartedAt, '2026-03-04T08:12:00');
  assert.equal(started.calledAt, '2026-03-04T08:10:00');
  setClock('2026-03-04T08:25:00');
  const done = await ctl.complete(called.id);
  assert.equal(done.status, 'COMPLETED');
  assert.equal(done.calledAt, '2026-03-04T08:10:00');
  assert.equal(done.serviceStartedAt, '2026-03-04T08:12:00');
  assert.equal(done.finishedAt, '2026-03-04T08:25:00');
});

test('guiche: ausencia apos chamada nao grava inicio; cancelar em atendimento e recusado', async () => {
  const { ctl, setClock } = deskSetup();
  await ctl.load();
  const first = await ctl.callNext(); // P002
  setClock('2026-03-04T08:15:00');
  const noShow = await ctl.markNoShow(first.id);
  assert.equal(noShow.status, 'NO_SHOW');
  assert.equal(noShow.serviceStartedAt, null);
  const second = await ctl.callNext(); // N001
  assert.equal(second.displayCode, 'N001');
  await ctl.startService(second.id);
  await assert.rejects(() => ctl.cancel(second.id), IllegalStateError);
  const cancelled = await ctl.complete(second.id);
  assert.equal(cancelled.status, 'COMPLETED');
});

test('guiche: chamar com a fila vazia e recusado pela porta', async () => {
  const port = new LocalDeskPort([]);
  const ctl = new DeskController(port, 'G1', () => '2026-03-04T08:10:00');
  await ctl.load();
  await assert.rejects(() => ctl.callNext(), IllegalStateError);
});

test('relatorio HTTP: URL exata da rota RAS-26 e nao-iniciadas fora de dentro/fora', async () => {
  const urls: string[] = [];
  const transport = new HttpWaitingReportTransport(async (url: string) => {
    urls.push(url);
    return [
      {
        serviceUnitId: UNIT,
        date: '2026-03-04',
        limitMinutes: 15,
        limitKind: 'BUSINESS_DAY',
        startedCount: 4,
        withinLimit: 2,
        outsideLimit: 2,
        withinPct: 50.0,
        averageWaitMinutes: 20.25,
        maxWaitMinutes: 40,
        pendingStart: 1,
        closedWithoutStart: 3,
        campoExtra: 'descartado pela lista branca',
      },
    ];
  });
  const ctl = new WaitingReportController(transport);
  const rows = await ctl.load('2026-03-01', '2026-03-31');
  assert.deepEqual(urls, ['/atendimento/relatorio-espera?de=2026-03-01&ate=2026-03-31']);
  assert.equal(rows.length, 1);
  const r = rows[0] as (typeof rows)[number];
  // Iniciadas = dentro + fora; pendentes e encerradas sem inicio ficam A PARTE.
  assert.equal(r.startedCount, r.withinLimit + r.outsideLimit);
  assert.equal(r.pendingStart, 1);
  assert.equal(r.closedWithoutStart, 3);
  assert.equal(r.startedCount + r.pendingStart + r.closedWithoutStart, 8);
  assert.equal('campoExtra' in r, false);
  await assert.rejects(() => ctl.load('2026-03-31', '2026-03-01'));
  await assert.rejects(() => ctl.load('', '2026-03-01'));
});

test('relatorio local: fonte sobre o nucleo separa pendentes no periodo pedido', async () => {
  const policy = makeWaitingLimitPolicy(HolidayCalendar.of(new Set<string>()), 15, 30);
  const startedTicket = new QueueTicket('r1', UNIT, 1, 'ATENDIMENTO', commonProfile(), '2026-03-04T08:00:00');
  startedTicket.call('2026-03-04T08:09:00', 'G1');
  startedTicket.startService('2026-03-04T08:10:00');
  const pending = new QueueTicket('r2', UNIT, 2, 'ATENDIMENTO', commonProfile(), '2026-03-04T09:00:00');
  const outsidePeriod = new QueueTicket('r3', UNIT, 3, 'ATENDIMENTO', commonProfile(), '2026-04-01T09:00:00');
  const ctl = new WaitingReportController(
    new LocalWaitingReportSource([startedTicket, pending, outsidePeriod], policy),
  );
  const rows = await ctl.load('2026-03-01', '2026-03-31');
  assert.equal(rows.length, 1);
  const r = rows[0] as (typeof rows)[number];
  assert.equal(r.startedCount, 1);
  assert.equal(r.withinLimit, 1);
  assert.equal(r.outsideLimit, 0);
  assert.equal(r.pendingStart, 1);
});

test('guiche (R2-F2): cancelar senha em espera (WAITING) directo, sem a chamar primeiro', async () => {
  const { ctl, setClock } = deskSetup();
  await ctl.load();
  // As duas senhas estao WAITING e nenhuma esta no guiche.
  assert.equal(ctl.activeForDesk(), null);
  assert.deepEqual(ctl.waiting().map((t) => t.displayCode), ['P002', 'N001']);
  setClock('2026-03-04T08:20:00');
  const cancelled = await ctl.cancel('d1'); // N001, ainda WAITING
  assert.equal(cancelled.status, 'CANCELLED');
  assert.equal(cancelled.calledAt, null);
  assert.equal(cancelled.deskId, null);
  assert.equal(cancelled.finishedAt, '2026-03-04T08:20:00');
  // Saiu da fila de espera; a prioritaria continua aguardando.
  assert.deepEqual(ctl.waiting().map((t) => t.displayCode), ['P002']);
  // E a fila continua operavel: a seguinte a chamar e a prioritaria.
  const called = await ctl.callNext();
  assert.equal(called.displayCode, 'P002');
});

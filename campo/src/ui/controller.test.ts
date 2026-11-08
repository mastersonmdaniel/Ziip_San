import { test } from 'node:test';
import assert from 'node:assert/strict';
import type { PendingReading, RoutePackage } from '../readings/routePackage.ts';
import { SyncEngine, type LocalStore, type SyncTransport } from '../sync/syncEngine.ts';
import type { FieldSecurityBridge } from '../plugins/fieldSecurity.ts';
import {
  FieldEmission,
  type BillingRulesBridge,
  type FieldBillResultDto,
  type FieldTaskEmission,
  type OcrVerdictName,
  type ThermalPrinterBridge,
  type ThermalRenderBridge,
} from '../field/fieldEmission.ts';
import { FieldScreensController, type FieldScreensDeps } from './controller.ts';
import { printerHealthDisplay } from './models.ts';

const PKG: RoutePackage = {
  routeId: 'rota-1',
  reference: '2026-11',
  downloadedAt: '2026-11-08T06:00:00Z',
  occurrences: [
    { code: 'VAZAMENTO', requiresPhoto: true, requiresReading: false },
  ],
  tasks: [
    { taskId: 'task-1', unitId: 'u-1', installationId: 'i-1', kind: 'WATER', reference: '2026-11', previousReading: 12000, meterDigits: 5, sequence: 1 },
    { taskId: 'task-2', unitId: 'u-2', installationId: 'i-2', kind: 'WATER', reference: '2026-11', previousReading: 500, meterDigits: 5, sequence: 2 },
  ],
};

const RESULT: FieldBillResultDto = {
  taskId: 'task-1', consumptionM3: 12, billedM3: 12, waterValue: '3.00', fixedValue: '12.50',
  sewerValue: '12.40', debitsValue: '0.00', creditsApplied: '0.00', creditsNotApplied: '0.00',
  total: '27.90', fingerprint: 'abc123',
};

function makeStore(initial: PendingReading[] = []) {
  let pending = [...initial];
  const saved: PendingReading[] = [];
  const store: LocalStore = {
    async pendingReadings() { return [...pending]; },
    async saveRoute() {},
    async clearRoute() { pending = []; },
  };
  return {
    store,
    saved,
    saveReading: async (r: PendingReading) => {
      saved.push(r);
      pending = [...pending.filter((p) => p.taskId !== r.taskId), r];
    },
    current: () => [...pending],
  };
}

function makeSync(store: LocalStore, directive: 'NONE' | 'RETIRE' | 'BLOCK' = 'NONE') {
  const transport: SyncTransport = {
    async fetchDirective() { return directive; },
    async uploadPending(_routeId, pending) {
      return pending.map((p) => ({ taskId: p.taskId, duplicate: false }));
    },
    async downloadRoute() { throw new Error('nao usado'); },
  };
  const security: FieldSecurityBridge = {
    async openEncryptedDatabase() { return { opened: true }; },
    async wipeAll() { return { wiped: true }; },
  };
  return new SyncEngine(transport, store, security);
}

function makeEmission() {
  const calls: string[] = [];
  const rules: BillingRulesBridge = {
    async verifyArtifactHash() { calls.push('hash'); return { match: true, actual: 'abc' }; },
    async calculateFieldBill(inputJson: unknown) {
      calls.push('calc');
      return { ...RESULT, taskId: (inputJson as { taskId: string }).taskId };
    },
  };
  const printer: ThermalPrinterBridge = {
    async print() { calls.push('print'); return { printed: true }; },
    async health() { return { headOk: true, batteryPct: 87 }; },
  };
  const renderer: ThermalRenderBridge = {
    async renderThermal() { calls.push('render'); return { command: '^XA^XZ' }; },
  };
  return { emission: new FieldEmission(rules, printer, renderer, 'hash', 2), printer, calls };
}

function deps(over: Partial<FieldScreensDeps> = {}): { deps: FieldScreensDeps; storeFake: ReturnType<typeof makeStore> } {
  const storeFake = makeStore();
  const base: FieldScreensDeps = {
    routePackage: PKG,
    store: storeFake.store,
    sync: makeSync(storeFake.store),
    emission: null,
    buildEmissionTask: null,
    saveReading: storeFake.saveReading,
    ocrSettings: null,
    ocrEngine: null,
    printer: null,
    fetchDivergences: null,
    queryDirective: null,
    now: () => '2026-11-08T10:00:00Z',
  };
  return { deps: { ...base, ...over }, storeFake };
}

function emissionTask(verdict: OcrVerdictName) {
  return (task: { taskId: string }): FieldTaskEmission => ({
    taskId: task.taskId,
    inputJson: { taskId: task.taskId },
    templateId: 'tpl-1',
    documentJson: {},
    ocrVerdict: verdict,
    requiredSignatureLevel: null,
    deviceCertificatePresent: true,
    signedLevel: null,
  });
}

test('load carrega a fila local real e o progresso reflete as leituras', async () => {
  const storeFake = makeStore([
    { taskId: 'task-2', reading: 505, occurrenceCode: null, photoRefs: [], ocrValue: null, capturedAt: 'T' },
  ]);
  const { deps: d } = deps({ store: storeFake.store, sync: makeSync(storeFake.store) });
  const ctl = new FieldScreensController(d);
  await ctl.load();
  const p = ctl.progress();
  assert.equal(p.done, 1);
  assert.equal(p.rows.find((r) => r.task.taskId === 'task-2')?.state, 'LIDA');
});

test('submitReading valido persiste pela porta e entra no progresso', async () => {
  const { deps: d, storeFake } = deps();
  const ctl = new FieldScreensController(d);
  await ctl.load();
  const res = await ctl.submitReading('task-1', {
    readingText: '12012', occurrenceCode: null, photoRefs: [], ocrValue: null,
  });
  assert.deepEqual(res, { ok: true, persisted: true });
  assert.equal(storeFake.saved.length, 1);
  assert.equal(storeFake.saved[0].reading, 12012);
  assert.equal(storeFake.saved[0].capturedAt, '2026-11-08T10:00:00Z');
  assert.equal(ctl.progress().done, 1);
});

test('submitReading sem a porta de gravacao assume memoria de sessao (nao finge persistir)', async () => {
  const { deps: d } = deps({ saveReading: null });
  const ctl = new FieldScreensController(d);
  await ctl.load();
  const res = await ctl.submitReading('task-1', {
    readingText: '12012', occurrenceCode: null, photoRefs: [], ocrValue: null,
  });
  assert.deepEqual(res, { ok: true, persisted: false });
  assert.equal(ctl.progress().done, 1);
});

test('submitReading respeita a ocorrencia que exige foto', async () => {
  const { deps: d, storeFake } = deps();
  const ctl = new FieldScreensController(d);
  await ctl.load();
  const res = await ctl.submitReading('task-1', {
    readingText: '', occurrenceCode: 'VAZAMENTO', photoRefs: [], ocrValue: null,
  });
  assert.equal(res.ok, false);
  assert.equal(storeFake.saved.length, 0);
});

test('D-01: ocorrencia que exige foto confirma pela camada de tela/controller (anexo de URI, sem injetar photoRefs)', async () => {
  const { deps: d, storeFake } = deps();
  const ctl = new FieldScreensController(d);
  await ctl.load();
  const draftCtl = ctl.draftFor('task-1');
  draftCtl.readingText = '';
  draftCtl.occurrenceCode = 'VAZAMENTO';
  // Sem foto anexada, a confirmacao segue bloqueada: a regra nao afrouxou.
  const blocked = await ctl.submitReading('task-1', draftCtl);
  assert.equal(blocked.ok, false);
  assert.equal(storeFake.saved.length, 0);
  // Caminho da Tela 2: o campo de foto de ocorrencia chama esta API do
  // controller (nada de atribuir photoRefs direto no rascunho no teste).
  assert.equal(ctl.addOccurrencePhoto('task-1', '   '), false);
  assert.equal(ctl.draftRequiresPhoto('task-1'), true);
  assert.equal(ctl.addOccurrencePhoto('task-1', ' file:///ocorrencia.jpg '), true);
  assert.deepEqual(ctl.occurrencePhotoRefs('task-1'), ['file:///ocorrencia.jpg']);
  const res = await ctl.submitReading('task-1', ctl.draftFor('task-1'));
  assert.deepEqual(res, { ok: true, persisted: true });
  assert.deepEqual(storeFake.saved[0].photoRefs, ['file:///ocorrencia.jpg']);
  // Remover a foto pela mesma camada volta a bloquear a confirmacao.
  ctl.removeOccurrencePhoto('task-1', 'file:///ocorrencia.jpg');
  assert.deepEqual(ctl.occurrencePhotoRefs('task-1'), []);
  const blockedAgain = await ctl.submitReading('task-1', ctl.draftFor('task-1'));
  assert.equal(blockedAgain.ok, false);
  assert.equal(storeFake.saved.length, 1);
});

test('D-02: saude da impressora percorre NOT_CHECKED -> READY e a exibicao mostra o DTO real', async () => {
  const { printer } = makeEmission();
  const { deps: d } = deps({ printer });
  const ctl = new FieldScreensController(d);
  await ctl.load();
  assert.equal(ctl.hasPrinter(), true);
  assert.deepEqual(ctl.printerHealthState(), { status: 'NOT_CHECKED' });
  assert.match(printerHealthDisplay(ctl.printerHealthState())[0], /ainda não verificada/);
  const state = await ctl.refreshPrinterHealth();
  assert.deepEqual(state, { status: 'READY', headOk: true, batteryPct: 87 });
  assert.deepEqual(printerHealthDisplay(state), ['Cabeçote: OK', 'Bateria: 87%']);
});

test('D-02: sem ponte o estado e UNAVAILABLE; ponte falhando vira ERROR sem valor fingido', async () => {
  const { deps: dNo } = deps({ printer: null });
  const ctlNo = new FieldScreensController(dNo);
  await ctlNo.load();
  assert.equal(ctlNo.hasPrinter(), false);
  assert.deepEqual(ctlNo.printerHealthState(), { status: 'UNAVAILABLE' });
  assert.deepEqual(await ctlNo.refreshPrinterHealth(), { status: 'UNAVAILABLE' });
  assert.match(printerHealthDisplay(ctlNo.printerHealthState())[0], /não injetada/);

  const failing: ThermalPrinterBridge = {
    async print() { return { printed: true }; },
    async health() { throw new Error('bluetooth fora'); },
  };
  const { deps: dFail } = deps({ printer: failing });
  const ctlFail = new FieldScreensController(dFail);
  await ctlFail.load();
  const state = await ctlFail.refreshPrinterHealth();
  assert.deepEqual(state, { status: 'ERROR', message: 'bluetooth fora' });
  assert.match(printerHealthDisplay(state)[0], /bluetooth fora/);
});

test('OCR on-device alimenta o rascunho e o veredito usa a regra da ED-18', async () => {
  const { deps: d } = deps({
    ocrSettings: { mode: 'REQUIRE', toleranceDigits: 0 },
    ocrEngine: { async recognizeDigits() { return { digits: '12912' }; } },
  });
  const ctl = new FieldScreensController(d);
  await ctl.load();
  ctl.draftFor('task-1').readingText = '12012';
  const value = await ctl.runOcr('task-1', 'file:///visor.jpg');
  assert.equal(value, 12912);
  assert.equal(ctl.ocrVerdict('task-1'), 'REVIEW');
  assert.equal(ctl.hasOcrEngine(), true);
});

test('emitir sem fachada/porta devolve UNAVAILABLE nomeando a pendencia (nada e fingido)', async () => {
  const { deps: d } = deps();
  const ctl = new FieldScreensController(d);
  await ctl.load();
  await ctl.submitReading('task-1', { readingText: '12012', occurrenceCode: null, photoRefs: [], ocrValue: null });
  const res = await ctl.emit('task-1');
  assert.equal(res.status, 'UNAVAILABLE');
});

test('emitir sem leitura registrada devolve NO_READING', async () => {
  const { emission } = makeEmission();
  const { deps: d } = deps({ emission, buildEmissionTask: emissionTask('MATCH') });
  const ctl = new FieldScreensController(d);
  await ctl.load();
  const res = await ctl.emit('task-1');
  assert.equal(res.status, 'NO_READING');
});

test('fluxo feliz: emissao pela fachada real imprime, retem e expoe a conta sem recalculo', async () => {
  const { emission, printer, calls } = makeEmission();
  const { deps: d } = deps({ emission, buildEmissionTask: emissionTask('MATCH'), printer });
  const ctl = new FieldScreensController(d);
  await ctl.load();
  await ctl.submitReading('task-1', { readingText: '12012', occurrenceCode: null, photoRefs: [], ocrValue: null });
  const res = await ctl.emit('task-1');
  assert.equal(res.status, 'DONE');
  if (res.status === 'DONE') assert.equal(res.outcome.kind, 'PRINTED');
  assert.deepEqual(calls, ['hash', 'calc', 'render', 'print']);
  assert.equal(ctl.billFor('task-1')?.total, '27.90');
  assert.equal(ctl.retainedBills().length, 1);
  assert.equal(ctl.held.count(), 0);
  assert.deepEqual(await ctl.printerHealth(), { headOk: true, batteryPct: 87 });
  assert.equal(await ctl.reprint('task-1'), true);
  assert.deepEqual(calls, ['hash', 'calc', 'render', 'print', 'render', 'print']);
});

test('OCR em REVIEW: a fachada retem, a tela expoe na fila de retencoes com o motivo do contrato', async () => {
  const { emission, calls } = makeEmission();
  const { deps: d } = deps({ emission, buildEmissionTask: emissionTask('REVIEW') });
  const ctl = new FieldScreensController(d);
  await ctl.load();
  await ctl.submitReading('task-1', { readingText: '12012', occurrenceCode: null, photoRefs: [], ocrValue: 12912 });
  const res = await ctl.emit('task-1');
  assert.equal(res.status, 'DONE');
  if (res.status === 'DONE') assert.equal(res.outcome.kind, 'OCR_HELD');
  assert.deepEqual(calls, []);
  assert.equal(ctl.billFor('task-1'), null);
  assert.equal(ctl.held.count(), 1);
  assert.equal(ctl.held.list()[0].kind, 'OCR_HELD');
});

test('syncNow usa a SyncEngine real: envia pendentes e registra a rodada da sessao', async () => {
  const storeFake = makeStore([
    { taskId: 'task-1', reading: 12012, occurrenceCode: null, photoRefs: [], ocrValue: null, capturedAt: 'T' },
  ]);
  const { deps: d } = deps({ store: storeFake.store, sync: makeSync(storeFake.store) });
  const ctl = new FieldScreensController(d);
  await ctl.load();
  const run = await ctl.syncNow();
  assert.equal(run.uploaded, 1);
  assert.equal(run.wiped, false);
  assert.equal(ctl.lastSyncRun()?.at, '2026-11-08T10:00:00Z');
  assert.equal(ctl.progress().done, 0);
});

test('syncNow com diretiva BLOCK apaga a fila local (comportamento real da ED-18)', async () => {
  const storeFake = makeStore([
    { taskId: 'task-1', reading: 12012, occurrenceCode: null, photoRefs: [], ocrValue: null, capturedAt: 'T' },
  ]);
  const { deps: d } = deps({ store: storeFake.store, sync: makeSync(storeFake.store, 'BLOCK') });
  const ctl = new FieldScreensController(d);
  await ctl.load();
  const run = await ctl.syncNow();
  assert.deepEqual({ uploaded: run.uploaded, wiped: run.wiped }, { uploaded: 0, wiped: true });
  assert.equal(ctl.progress().done, 0);
});

test('R2-m2: refreshDivergences com a porta alimenta a fila da sessao (fonte: servidor)', async () => {
  const { deps: d } = deps({
    fetchDivergences: async () => [
      { taskId: 'task-1', basisDivergent: false, differences: [{ field: 'total', fieldValue: '27.90', serverValue: '31.40' }] },
    ],
  });
  const ctl = new FieldScreensController(d);
  await ctl.load();
  assert.equal(ctl.hasDivergenceFeed(), true);
  assert.equal(ctl.divergenceFeedState(), 'NOT_QUERIED');
  assert.equal(ctl.divergences.count(), 0);
  assert.equal(await ctl.refreshDivergences(), 'READY');
  assert.equal(ctl.divergenceFeedState(), 'READY');
  assert.equal(ctl.divergences.count(), 1);
  assert.equal(ctl.divergences.forTask('task-1')?.differences[0].serverValue, '31.40');
});

test('R2-m2: sem a porta, a fila e UNAVAILABLE e nenhuma divergencia e fingida', async () => {
  const { deps: d } = deps({ fetchDivergences: null });
  const ctl = new FieldScreensController(d);
  await ctl.load();
  assert.equal(ctl.hasDivergenceFeed(), false);
  assert.equal(await ctl.refreshDivergences(), 'UNAVAILABLE');
  assert.equal(ctl.divergenceFeedState(), 'UNAVAILABLE');
  assert.equal(ctl.divergences.count(), 0);
});

test('R2-m2: porta falhando vira ERROR e mantem a ultima fila consultada', async () => {
  let fail = false;
  const { deps: d } = deps({
    fetchDivergences: async () => {
      if (fail) throw new Error('servidor fora');
      return [{ taskId: 'task-1', basisDivergent: true, differences: [] }];
    },
  });
  const ctl = new FieldScreensController(d);
  await ctl.load();
  assert.equal(await ctl.refreshDivergences(), 'READY');
  assert.equal(ctl.divergences.count(), 1);
  fail = true;
  assert.equal(await ctl.refreshDivergences(), 'ERROR');
  assert.equal(ctl.divergenceFeedError(), 'servidor fora');
  assert.equal(ctl.divergences.count(), 1);
});

test('R2-m3: refreshDirective consulta a diretiva fora da sync e guarda so por consulta expressa', async () => {
  const { deps: d } = deps({ queryDirective: async () => 'BLOCK' as const });
  const ctl = new FieldScreensController(d);
  await ctl.load();
  assert.equal(ctl.hasDirectiveQuery(), true);
  assert.equal(ctl.directiveQueryState(), 'NOT_QUERIED');
  assert.equal(ctl.directive(), null);
  assert.equal(await ctl.refreshDirective(), 'READY');
  assert.equal(ctl.directive(), 'BLOCK');
  // A sync com BLOCK nao muda a diretiva guardada: so a consulta expressa muda.
  const storeFake = makeStore([
    { taskId: 'task-1', reading: 12012, occurrenceCode: null, photoRefs: [], ocrValue: null, capturedAt: 'T' },
  ]);
  const { deps: dSync } = deps({ store: storeFake.store, sync: makeSync(storeFake.store, 'BLOCK'), queryDirective: async () => 'BLOCK' as const });
  const ctlSync = new FieldScreensController(dSync);
  await ctlSync.load();
  assert.equal(ctlSync.directive(), null);
  await ctlSync.syncNow();
  assert.equal(ctlSync.directive(), null);
});

test('R2-m3: sem a porta, a diretiva e UNAVAILABLE e nada e inferido da sync', async () => {
  const { deps: d } = deps({ queryDirective: null });
  const ctl = new FieldScreensController(d);
  await ctl.load();
  assert.equal(ctl.hasDirectiveQuery(), false);
  assert.equal(await ctl.refreshDirective(), 'UNAVAILABLE');
  assert.equal(ctl.directiveQueryState(), 'UNAVAILABLE');
  assert.equal(ctl.directive(), null);
});

test('R2-m3: porta que devolve null = servidor sem diretiva a informar (desconhecida, nao "nenhuma")', async () => {
  const { deps: d } = deps({ queryDirective: async () => null });
  const ctl = new FieldScreensController(d);
  await ctl.load();
  assert.equal(await ctl.refreshDirective(), 'READY');
  assert.equal(ctl.directive(), null);
});

test('R2-m3: porta de diretiva falhando vira ERROR sem valor fingido', async () => {
  const { deps: d } = deps({
    queryDirective: async () => { throw new Error('rede fora'); },
  });
  const ctl = new FieldScreensController(d);
  await ctl.load();
  assert.equal(await ctl.refreshDirective(), 'ERROR');
  assert.equal(ctl.directiveQueryError(), 'rede fora');
  assert.equal(ctl.directive(), null);
});

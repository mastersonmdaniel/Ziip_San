import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  FieldEmission,
  type BillingRulesBridge,
  type FieldBillResultDto,
  type FieldTaskEmission,
  type ThermalPrinterBridge,
  type ThermalRenderBridge,
} from './fieldEmission.ts';

const RESULT: FieldBillResultDto = {
  taskId: 'task-1',
  consumptionM3: 12,
  billedM3: 12,
  waterValue: '3.00',
  fixedValue: '12.50',
  sewerValue: '12.40',
  debitsValue: '0.00',
  creditsApplied: '0.00',
  creditsNotApplied: '0.00',
  total: '27.90',
  fingerprint: 'abc123',
};

function harness(opts: { hashMatch?: boolean; printOk?: boolean } = {}) {
  const calls: string[] = [];
  const rules: BillingRulesBridge = {
    async verifyArtifactHash() {
      calls.push('hash');
      return { match: opts.hashMatch !== false, actual: 'abc' };
    },
    async calculateFieldBill(inputJson: unknown) {
      calls.push('calc');
      const taskId = (inputJson as { taskId?: string }).taskId ?? RESULT.taskId;
      return { ...RESULT, taskId };
    },
  };
  const printer: ThermalPrinterBridge = {
    async print() {
      calls.push('print');
      return { printed: opts.printOk !== false };
    },
    async health() {
      return { headOk: true, batteryPct: 90 };
    },
  };
  const renderer: ThermalRenderBridge = {
    async renderThermal() {
      calls.push('render');
      return { command: '^XA^XZ' };
    },
  };
  const emission = new FieldEmission(rules, printer, renderer, 'expected-hash', 2);
  return { emission, calls };
}

function task(overrides: Partial<FieldTaskEmission> = {}): FieldTaskEmission {
  const taskId = overrides.taskId ?? 'task-1';
  return {
    taskId,
    inputJson: { taskId },
    templateId: 'tpl-1',
    documentJson: {},
    ocrVerdict: 'MATCH',
    requiredSignatureLevel: null,
    deviceCertificatePresent: true,
    signedLevel: null,
    ...overrides,
  };
}

test('OCR em REVIEW segura a emissao: nada calcula nem imprime', async () => {
  const { emission, calls } = harness();
  const outcome = await emission.emit(task({ ocrVerdict: 'REVIEW' }));
  assert.equal(outcome.kind, 'OCR_HELD');
  assert.deepEqual(calls, []);
  assert.equal(emission.retainedBills().length, 0);
});

test('hash do artefato divergente barra a rota antes do calculo', async () => {
  const { emission, calls } = harness({ hashMatch: false });
  const outcome = await emission.emit(task());
  assert.equal(outcome.kind, 'ARTIFACT_HASH_MISMATCH');
  assert.deepEqual(calls, ['hash']);
});

test('certificado ausente em tipo que exige assinatura nao emite', async () => {
  const { emission, calls } = harness();
  const outcome = await emission.emit(
    task({ requiredSignatureLevel: 'QUALIFIED', deviceCertificatePresent: false, signedLevel: null }),
  );
  assert.equal(outcome.kind, 'CERTIFICATE_MISSING');
  assert.deepEqual(calls, ['hash']);
  assert.equal(emission.retainedBills().length, 0);
});

test('fluxo feliz: hash, calculo, render e impressao; conta fica retida', async () => {
  const { emission, calls } = harness();
  const outcome = await emission.emit(task());
  assert.equal(outcome.kind, 'PRINTED');
  assert.deepEqual(calls, ['hash', 'calc', 'render', 'print']);
  assert.equal(emission.retainedBills().length, 1);
});

test('envio a cada N: dispara no multiplo e sempre no fim da rota', async () => {
  const { emission } = harness();
  await emission.emit(task());
  assert.equal(emission.shouldFlush(), false);
  assert.equal(emission.shouldFlushAtRouteEnd(), true);
  await emission.emit(task({ taskId: 'task-2' }));
  assert.equal(emission.retainedBills().length, 2);
  assert.equal(emission.shouldFlush(), true);
  // reter a mesma task de novo nao duplica a fila
  await emission.emit(task());
  assert.equal(emission.retainedBills().length, 2);
  emission.ackSent(['task-1', 'task-2']);
  assert.equal(emission.retainedBills().length, 0);
  assert.equal(emission.shouldFlushAtRouteEnd(), false);
});

test('impressao nao confirmada nao retem a conta como impressa', async () => {
  const { emission } = harness({ printOk: false });
  await assert.rejects(() => emission.emit(task()), /nao confirmada/);
  assert.equal(emission.retainedBills().length, 0);
});

test('reimpressao de tarefa nao retida e erro', async () => {
  const { emission } = harness();
  await assert.rejects(() => emission.reprint('task-x', 'tpl-1', {}), /nao retida/);
});

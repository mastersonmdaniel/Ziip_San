import { test } from 'node:test';
import assert from 'node:assert/strict';
import type { PendingReading, RouteTask } from '../readings/routePackage.ts';
import {
  assertValidSnapshot,
  buildFieldTaskEmission,
  createBuildEmissionTask,
  type FieldEmissionInputs,
  type FieldTariffSnapshotJson,
} from './fieldTariffSnapshot.ts';

// Retrato do caso `simples-uma-economia` do billing-contract-fixtures.json
// da ED-19 (valores literais do fixture, nao inventados).
const SNAPSHOT: FieldTariffSnapshotJson = {
  tariffVersionId: '00000000-0000-0000-0000-0000000000aa',
  categoryId: '00000000-0000-0000-0000-0000000000cc',
  minimumM3PerEconomy: 10,
  fixedChargePerEconomy: 12.5,
  brackets: [
    { pricePerM3: 1.5, widthM3: 10 },
    { pricePerM3: 2.5, widthM3: 10 },
    { pricePerM3: 4.0, widthM3: null },
  ],
  sewerEnabled: true,
  sewerPct: 80,
  parameterVersions: { tariff: 'v2026-01' },
};

const TASK: RouteTask = {
  taskId: '00000000-0000-0000-0000-000000000001',
  unitId: '00000000-0000-0000-0000-000000000101',
  installationId: '00000000-0000-0000-0000-000000000201',
  kind: 'WATER', reference: '2026-11', previousReading: 1000, meterDigits: 5, sequence: 1,
};

const READING: PendingReading = {
  taskId: TASK.taskId, reading: 1012, occurrenceCode: null, photoRefs: [], ocrValue: null,
  capturedAt: '2026-11-08T10:00:00Z',
};

const INPUTS: FieldEmissionInputs = {
  economies: 1, debits: [], credits: [], dueDate: '2026-12-10',
  templateId: 'tpl-1', documentJson: {}, ocrVerdict: 'MATCH',
  requiredSignatureLevel: null, deviceCertificatePresent: true, signedLevel: null,
};

test('P-03: o inputJson montado e identico ao do fixture simples-uma-economia', () => {
  const built = buildFieldTaskEmission(TASK, READING, SNAPSHOT, INPUTS);
  assert.equal(built.taskId, TASK.taskId);
  assert.equal(built.templateId, 'tpl-1');
  assert.deepEqual(built.inputJson, {
    taskId: '00000000-0000-0000-0000-000000000001',
    unitId: '00000000-0000-0000-0000-000000000101',
    installationId: '00000000-0000-0000-0000-000000000201',
    currentReading: 1012,
    previousReading: 1000,
    meterDigits: 5,
    economies: 1,
    debits: [],
    credits: [],
    dueDate: '2026-12-10',
    snapshot: SNAPSHOT,
  });
});

test('P-03: validacao do retrato espelha o construtor Java (faixas e limites)', () => {
  assertValidSnapshot(SNAPSHOT);
  assert.throws(() => assertValidSnapshot({ ...SNAPSHOT, brackets: [] }), /sem nenhuma faixa/);
  assert.throws(
    () => assertValidSnapshot({
      ...SNAPSHOT,
      brackets: [{ pricePerM3: 1, widthM3: null }, { pricePerM3: 2, widthM3: 10 }],
    }),
    /so pode ser a ultima/,
  );
  assert.throws(
    () => assertValidSnapshot({
      ...SNAPSHOT,
      brackets: [{ pricePerM3: 1, widthM3: 10 }],
    }),
    /ultima faixa.*aberta/,
  );
  assert.throws(() => assertValidSnapshot({ ...SNAPSHOT, minimumM3PerEconomy: -1 }), /minimo.*negativo/);
  assert.throws(() => assertValidSnapshot({ ...SNAPSHOT, sewerPct: -5 }), /esgoto negativo/);
});

test('P-03: emissao sem leitura atual, sem economias ou sem insumos recusa com motivo nomeado', () => {
  assert.throws(
    () => buildFieldTaskEmission(TASK, { ...READING, reading: null }, SNAPSHOT, INPUTS),
    /exige a leitura atual/,
  );
  assert.throws(() => buildFieldTaskEmission(TASK, READING, SNAPSHOT, { ...INPUTS, economies: 0 }), /economias/);
  assert.throws(() => buildFieldTaskEmission(TASK, READING, SNAPSHOT, { ...INPUTS, dueDate: ' ' }), /vencimento/);
  assert.throws(() => buildFieldTaskEmission(TASK, READING, SNAPSHOT, { ...INPUTS, templateId: '' }), /templateId/);
});

test('P-03: a fabrica fecha o resolvedor na assinatura da porta; sem dado, erro nomeado', () => {
  const build = createBuildEmissionTask(() => ({ snapshot: SNAPSHOT, inputs: INPUTS }));
  const built = build(TASK, READING);
  assert.equal((built.inputJson as { currentReading: number }).currentReading, 1012);
  assert.equal(built.ocrVerdict, 'MATCH');
  const missing = createBuildEmissionTask(() => null);
  assert.throws(() => missing(TASK, READING), /P-03: sem retrato de tarifa/);
});

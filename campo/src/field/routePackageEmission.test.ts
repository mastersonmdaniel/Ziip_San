import { test } from 'node:test';
import assert from 'node:assert/strict';
import type {
  PendingReading,
  RoutePackage,
  RouteTask,
} from '../readings/routePackage.ts';
import { createBuildEmissionTask } from './fieldTariffSnapshot.ts';
import {
  createPackageEmissionResolver,
  type FieldDeviceEmissionInputs,
} from './routePackageEmission.ts';

// Dados literais do caso `simples-uma-economia` do
// billing-contract-fixtures.json da ED-19 (valores do fixture, nao
// inventados) — o inputJson montado tem de ser identico ao do fixture.
const DEVICE: FieldDeviceEmissionInputs = {
  templateId: 'tpl-1',
  documentJson: {},
  requiredSignatureLevel: null,
  deviceCertificatePresent: true,
  signedLevel: null,
  ocrVerdictFor: () => 'MATCH',
};

function fixtureTask(): RouteTask {
  return {
    taskId: '00000000-0000-0000-0000-000000000001',
    unitId: '00000000-0000-0000-0000-000000000101',
    installationId: '00000000-0000-0000-0000-000000000201',
    kind: 'WATER', reference: '2026-11', previousReading: 1000, meterDigits: 5, sequence: 1,
    emission: {
      snapshot: {
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
      },
      economies: 1,
      debits: [],
      credits: [],
      dueDate: '2026-12-10',
      barcodeValue: null,
    },
  };
}

function pkg(tasks: RouteTask[]): RoutePackage {
  return {
    routeId: 'rota-1', reference: '2026-11', downloadedAt: '2026-11-08T09:00:00Z',
    occurrences: [{ code: 'NORMAL', requiresPhoto: false, requiresReading: true }],
    tasks,
  };
}

const READING: PendingReading = {
  taskId: '00000000-0000-0000-0000-000000000001', reading: 1012, occurrenceCode: null,
  photoRefs: [], ocrValue: null, capturedAt: '2026-11-08T10:00:00Z',
};

test('P-03/RG2-C-P2: pacote com dados -> inputJson identico ao fixture simples-uma-economia', () => {
  const task = fixtureTask();
  const build = createBuildEmissionTask(createPackageEmissionResolver(pkg([task]), DEVICE));
  const built = build(task, READING);
  assert.equal(built.taskId, task.taskId);
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
    snapshot: task.emission!.snapshot,
  });
});

test('P-03/RG2-C-P2: debitos e creditos do bloco entram no inputJson (caso multiplas-economias do fixture)', () => {
  const task = fixtureTask();
  task.emission = {
    ...task.emission!,
    economies: 3,
    debits: [{ amount: 15.0, code: 'TAXA_SERVICO' }],
    credits: [{ amount: 20.0, code: 'CREDITO_ANTERIOR' }],
    barcodeValue: '84690000000000000000000000000000000000000000000',
  };
  const build = createBuildEmissionTask(createPackageEmissionResolver(pkg([task]), DEVICE));
  const built = build(task, READING);
  const json = built.inputJson as { economies: number; debits: unknown; credits: unknown };
  assert.equal(json.economies, 3);
  assert.deepEqual(json.debits, [{ amount: 15.0, code: 'TAXA_SERVICO' }]);
  assert.deepEqual(json.credits, [{ amount: 20.0, code: 'CREDITO_ANTERIOR' }]);
  // O barcodeValue viaja no bloco do pacote, mas NAO entra no inputJson:
  // o formato do fixture nao tem esse campo e nenhum e inventado aqui.
  assert.equal(task.emission.barcodeValue, '84690000000000000000000000000000000000000000000');
  assert.equal('barcodeValue' in (built.inputJson as object), false);
});

test('P-03/RG2-C-P2: pacote sem bloco (antigo) continua valido e a emissao responde UNAVAILABLE', () => {
  const task = fixtureTask();
  delete task.emission;
  const resolver = createPackageEmissionResolver(pkg([task]), DEVICE);
  assert.equal(resolver(task), null);
  const build = createBuildEmissionTask(resolver);
  assert.throws(() => build(task, READING), /P-03: sem retrato de tarifa/);
});

test('P-03/RG2-C-P2: tarefa fora do pacote resolve null (nada presumido)', () => {
  const inPkg = fixtureTask();
  const outside: RouteTask = { ...fixtureTask(), taskId: '00000000-0000-0000-0000-000000000099' };
  const resolver = createPackageEmissionResolver(pkg([inPkg]), DEVICE);
  assert.equal(resolver(outside), null);
});

test('P-03/RG2-C-P2: bloco parcial recusa nomeada listando o que falta — nunca emissao parcial', () => {
  const task = fixtureTask();
  const partial = { snapshot: task.emission!.snapshot, economies: 1 } as RouteTask['emission'];
  task.emission = partial;
  const resolver = createPackageEmissionResolver(pkg([task]), DEVICE);
  assert.throws(
    () => resolver(task),
    /P-03: bloco de emissao parcial da tarefa 00000000-0000-0000-0000-000000000001 \(faltam: debits, credits, dueDate, barcodeValue\)/,
  );
});

test('P-03/RG2-C-P2: bloco com retrato de forma invalida recusa no build (espelho Java), nao emite', () => {
  const task = fixtureTask();
  task.emission = {
    ...task.emission!,
    snapshot: { ...task.emission!.snapshot, brackets: [] },
  };
  const build = createBuildEmissionTask(createPackageEmissionResolver(pkg([task]), DEVICE));
  assert.throws(() => build(task, READING), /P-03: retrato sem nenhuma faixa tarifaria/);
});

test('P-03/RG2-C-P2: ocrVerdict vem do chamador (controller), nunca do pacote', () => {
  const task = fixtureTask();
  const device: FieldDeviceEmissionInputs = { ...DEVICE, ocrVerdictFor: () => 'REVIEW' };
  const build = createBuildEmissionTask(createPackageEmissionResolver(pkg([task]), device));
  assert.equal(build(task, READING).ocrVerdict, 'REVIEW');
});

test('P-03/RG2-C-P2: insumos de aparelho sao os do chamador, sem default escondido', () => {
  const task = fixtureTask();
  const device: FieldDeviceEmissionInputs = {
    templateId: 'tpl-termico-v3',
    documentJson: { origem: 'boot' },
    requiredSignatureLevel: 'ADVANCED',
    deviceCertificatePresent: false,
    signedLevel: null,
    ocrVerdictFor: () => 'DISABLED',
  };
  const build = createBuildEmissionTask(createPackageEmissionResolver(pkg([task]), device));
  const built = build(task, READING);
  assert.equal(built.templateId, 'tpl-termico-v3');
  assert.deepEqual(built.documentJson, { origem: 'boot' });
  assert.equal(built.requiredSignatureLevel, 'ADVANCED');
  assert.equal(built.deviceCertificatePresent, false);
  assert.equal(built.signedLevel, null);
  assert.equal(built.ocrVerdict, 'DISABLED');
});

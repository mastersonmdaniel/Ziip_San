import { test } from 'node:test';
import assert from 'node:assert/strict';
import type { PendingReading, RoutePackage } from '../readings/routePackage.ts';
import type { FieldBillResultDto } from '../field/fieldEmission.ts';
import {
  HeldQueue,
  Navigator,
  addOccurrencePhotoRef,
  billDisplayRows,
  buildPendingReading,
  directiveLabel,
  occurrenceRequiresPhoto,
  ocrVerdictFor,
  outcomeDisplay,
  parseReading,
  printerHealthDisplay,
  routeProgress,
  syncView,
  taskLabel,
  validateReading,
  type ReadingDraft,
} from './models.ts';

const PKG: RoutePackage = {
  routeId: 'rota-1',
  reference: '2026-11',
  downloadedAt: '2026-11-08T06:00:00Z',
  occurrences: [
    { code: 'NORMAL', requiresPhoto: false, requiresReading: true },
    { code: 'VAZAMENTO', requiresPhoto: true, requiresReading: false },
    { code: 'IMPEDIDO', requiresPhoto: false, requiresReading: false },
  ],
  tasks: [
    { taskId: 'task-2', unitId: 'u-2', installationId: 'i-2', kind: 'WELL', reference: '2026-11', previousReading: null, meterDigits: 5, sequence: 2 },
    { taskId: 'task-1', unitId: 'u-1', installationId: 'i-1', kind: 'WATER', reference: '2026-11', previousReading: 12000, meterDigits: 5, sequence: 1 },
  ],
};

const PENDING_1: PendingReading = {
  taskId: 'task-1', reading: 12012, occurrenceCode: null, photoRefs: [], ocrValue: null,
  capturedAt: '2026-11-08T09:00:00Z',
};

const RESULT: FieldBillResultDto = {
  taskId: 'task-1', consumptionM3: 12, billedM3: 12, waterValue: '3.00', fixedValue: '12.50',
  sewerValue: '12.40', debitsValue: '0.00', creditsApplied: '0.00', creditsNotApplied: '0.00',
  total: '27.90', fingerprint: 'abc123',
};

function draft(over: Partial<ReadingDraft> = {}): ReadingDraft {
  return { readingText: '12012', occurrenceCode: null, photoRefs: [], ocrValue: null, ...over };
}

test('progresso da rota: ordena por sequence e conta feitas x pendentes pela fila real', () => {
  const p = routeProgress(PKG, [PENDING_1]);
  assert.equal(p.total, 2);
  assert.equal(p.done, 1);
  assert.equal(p.pending, 1);
  assert.deepEqual(p.rows.map((r) => r.task.taskId), ['task-1', 'task-2']);
  assert.equal(p.rows[0].state, 'LIDA');
  assert.equal(p.rows[1].state, 'PENDENTE');
  assert.equal(p.rows[0].pending?.reading, 12012);
});

test('rotulo da tarefa usa somente os identificadores do DTO (sem endereco inventado)', () => {
  assert.equal(taskLabel(PKG.tasks[1]), 'Água · unidade u-1 · instalação i-1');
  assert.equal(taskLabel(PKG.tasks[0]), 'Poço · unidade u-2 · instalação i-2');
});

test('parse da leitura: digitos ou null', () => {
  assert.equal(parseReading(' 00123 '), 123);
  assert.equal(parseReading(''), null);
  assert.equal(parseReading('12a3'), null);
  assert.equal(parseReading('-5'), null);
});

test('validacao: leitura normal exige o valor digitado', () => {
  const t = PKG.tasks[1];
  assert.deepEqual(validateReading(t, PKG.occurrences, draft()), { ok: true, reading: 12012 });
  const empty = validateReading(t, PKG.occurrences, draft({ readingText: '' }));
  assert.equal(empty.ok, false);
});

test('validacao: ocorrencia manda em leitura/foto obrigatorias (OccurrenceSnapshot real)', () => {
  const t = PKG.tasks[1];
  const noPhoto = validateReading(t, PKG.occurrences, draft({ readingText: '', occurrenceCode: 'VAZAMENTO' }));
  assert.equal(noPhoto.ok, false);
  const withPhoto = validateReading(t, PKG.occurrences,
    draft({ readingText: '', occurrenceCode: 'VAZAMENTO', photoRefs: ['file:///foto.jpg'] }));
  assert.deepEqual(withPhoto, { ok: true, reading: null });
  const noReading = validateReading(t, PKG.occurrences, draft({ readingText: '', occurrenceCode: 'NORMAL' }));
  assert.equal(noReading.ok, false);
  const unknown = validateReading(t, PKG.occurrences, draft({ occurrenceCode: 'INEXISTENTE' }));
  assert.equal(unknown.ok, false);
});

test('foto de ocorrencia (D-01): anexar URI apara, ignora vazia e nao duplica', () => {
  assert.deepEqual(addOccurrencePhotoRef([], '  file:///foto.jpg '), ['file:///foto.jpg']);
  assert.deepEqual(addOccurrencePhotoRef(['file:///foto.jpg'], 'file:///foto.jpg'), ['file:///foto.jpg']);
  assert.deepEqual(addOccurrencePhotoRef(['file:///a.jpg'], '   '), ['file:///a.jpg']);
  assert.deepEqual(addOccurrencePhotoRef(['file:///a.jpg'], 'file:///b.jpg'), ['file:///a.jpg', 'file:///b.jpg']);
  const original = ['file:///a.jpg'];
  addOccurrencePhotoRef(original, 'file:///b.jpg');
  assert.deepEqual(original, ['file:///a.jpg']);
  assert.equal(occurrenceRequiresPhoto(PKG.occurrences, 'VAZAMENTO'), true);
  assert.equal(occurrenceRequiresPhoto(PKG.occurrences, 'NORMAL'), false);
  assert.equal(occurrenceRequiresPhoto(PKG.occurrences, null), false);
  assert.equal(occurrenceRequiresPhoto(PKG.occurrences, 'INEXISTENTE'), false);
});

test('buildPendingReading monta o DTO real da ED-18 e copia as fotos', () => {
  const d = draft({ occurrenceCode: 'VAZAMENTO', photoRefs: ['file:///a.jpg'], ocrValue: 12010 });
  const p = buildPendingReading(PKG.tasks[1], d, null, '2026-11-08T09:30:00Z');
  assert.deepEqual(p, {
    taskId: 'task-1', reading: null, occurrenceCode: 'VAZAMENTO',
    photoRefs: ['file:///a.jpg'], ocrValue: 12010, capturedAt: '2026-11-08T09:30:00Z',
  });
  d.photoRefs.push('file:///b.jpg');
  assert.equal(p.photoRefs.length, 1);
});

test('veredito OCR: sem definicoes do tenant, DISABLED; com a regra da ED-18, MATCH/REVIEW', () => {
  const t = PKG.tasks[1];
  assert.equal(ocrVerdictFor(t, 12012, 99999, null), 'DISABLED');
  assert.equal(ocrVerdictFor(t, 12012, 12012, { mode: 'REQUIRE', toleranceDigits: 0 }), 'MATCH');
  assert.equal(ocrVerdictFor(t, 12012, 12912, { mode: 'REQUIRE', toleranceDigits: 0 }), 'REVIEW');
  assert.equal(ocrVerdictFor(t, 12012, 12912, { mode: 'SUGGEST', toleranceDigits: 0 }), 'SUGGESTION');
});

test('exibicao da conta: somente os campos reais do FieldBillResultDto, sem recalculo', () => {
  const rows = billDisplayRows(RESULT);
  assert.deepEqual(rows.map((r) => r.label), [
    'Consumo (m³)', 'Faturado (m³)', 'Água', 'Taxa fixa', 'Esgoto', 'Débitos',
    'Créditos aplicados', 'Créditos não aplicados', 'Total', 'Impressão digital da conta',
  ]);
  assert.equal(rows.find((r) => r.label === 'Total')?.value, '27.90');
  assert.equal(rows.find((r) => r.label === 'Água')?.value, '3.00');
});

test('outcomeDisplay cobre os 4 kinds reais do EmissionOutcome', () => {
  assert.equal(outcomeDisplay({ kind: 'PRINTED', taskId: 'task-1', result: RESULT }).printed, true);
  assert.equal(outcomeDisplay({ kind: 'OCR_HELD', taskId: 'task-1' }).title, 'Emissão retida — revisão de OCR');
  assert.equal(outcomeDisplay({ kind: 'CERTIFICATE_MISSING', taskId: 'task-1' }).printed, false);
  assert.equal(outcomeDisplay({ kind: 'ARTIFACT_HASH_MISMATCH', taskId: 'task-1' }).printed, false);
});

test('fila de retencoes: retem nao-impressos, substitui por tarefa e resolve no PRINTED', () => {
  const q = new HeldQueue();
  q.record({ kind: 'OCR_HELD', taskId: 'task-1' });
  q.record({ kind: 'CERTIFICATE_MISSING', taskId: 'task-2' });
  assert.equal(q.count(), 2);
  q.record({ kind: 'CERTIFICATE_MISSING', taskId: 'task-1' });
  assert.equal(q.count(), 2);
  assert.equal(q.list().find((e) => e.taskId === 'task-1')?.kind, 'CERTIFICATE_MISSING');
  q.record({ kind: 'PRINTED', taskId: 'task-1', result: RESULT });
  assert.equal(q.has('task-1'), false);
  assert.equal(q.count(), 1);
});

test('saude da impressora (D-02): exibe so cabecote/bateria do DTO real e distingue os estados', () => {
  assert.deepEqual(printerHealthDisplay({ status: 'READY', headOk: true, batteryPct: 87 }),
    ['Cabeçote: OK', 'Bateria: 87%']);
  assert.deepEqual(printerHealthDisplay({ status: 'READY', headOk: false, batteryPct: 12 }),
    ['Cabeçote: com problema', 'Bateria: 12%']);
  assert.deepEqual(printerHealthDisplay({ status: 'READY', headOk: null, batteryPct: null }),
    ['Cabeçote: sem leitura', 'Bateria: sem leitura']);
  assert.match(printerHealthDisplay({ status: 'UNAVAILABLE' })[0], /não injetada/);
  assert.match(printerHealthDisplay({ status: 'NOT_CHECKED' })[0], /ainda não verificada/);
  assert.match(printerHealthDisplay({ status: 'ERROR', message: 'bluetooth fora' })[0], /bluetooth fora/);
});

test('diretivas e visao de sincronizacao usam os rotulos do contrato real', () => {
  assert.match(directiveLabel('BLOCK'), /bloqueado/);
  assert.match(directiveLabel('RETIRE'), /retirar/);
  assert.equal(directiveLabel('NONE'), 'Nenhuma diretiva do servidor');
  assert.equal(syncView(3, null).pendingCount, 3);
  assert.equal(syncView(3, null).lastRunLabel, null);
  assert.match(syncView(0, { uploaded: 2, wiped: true, at: 'T' }).lastRunLabel ?? '', /apagados/);
  // D-04: SyncView nao carrega mais o campo morto directiveLabel (sempre
  // null): syncRoute nao devolve a diretiva, entao ela nao e afirmada.
  assert.equal('directiveLabel' in syncView(3, null), false);
});

test('navegacao: abrir tarefa vai a leitura; voltar desfaz na ordem', () => {
  const nav = new Navigator();
  assert.equal(nav.current(), 'rota');
  nav.openTask('task-1');
  assert.equal(nav.current(), 'leitura');
  assert.equal(nav.taskId(), 'task-1');
  nav.go('emissao', 'task-1');
  assert.equal(nav.current(), 'emissao');
  assert.equal(nav.back(), true);
  assert.equal(nav.current(), 'leitura');
  assert.equal(nav.back(), true);
  assert.equal(nav.current(), 'rota');
  assert.equal(nav.back(), false);
});

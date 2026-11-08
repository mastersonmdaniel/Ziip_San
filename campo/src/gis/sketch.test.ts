import { test } from 'node:test';
import assert from 'node:assert/strict';
import { isSketchSubmittable, sketchProblems, type FieldSketch } from './sketch.ts';

const SKETCH: FieldSketch = {
  featureId: 'feat-valvula-1',
  strokes: [{ points: [{ x: 0, y: 0 }, { x: 3, y: 0 }, { x: 3, y: 2 }], label: 'rede' }],
  ties: [
    { kind: 'BUILDING_ALIGNMENT', referenceLabel: 'esquina do lote 12', distanceM: 4.5 },
    { kind: 'CURB', referenceLabel: 'meio-fio da rua de ensaio', distanceM: 1.2 },
  ],
  capturedAt: '2026-11-08T12:00:00Z',
};

test('croqui com tracos e amarracoes (alinhamento predial e meio-fio) e submetivel', () => {
  assert.deepEqual(sketchProblems(SKETCH), []);
  assert.equal(isSketchSubmittable(SKETCH), true);
});

test('croqui sem amarracao e rascunho, nao anexo de pacote (GIS-15 exige amarracoes)', () => {
  assert.deepEqual(sketchProblems({ ...SKETCH, ties: [] }), ['NO_TIES']);
});

test('traco de um ponto so e amarracao sem rotulo sao recusados pelo nome', () => {
  const bad: FieldSketch = {
    ...SKETCH,
    strokes: [{ points: [{ x: 0, y: 0 }], label: null }],
    ties: [{ kind: 'CURB', referenceLabel: ' ', distanceM: 1 }],
  };
  assert.deepEqual(sketchProblems(bad), ['STROKE_TOO_SHORT', 'TIE_LABEL_MISSING']);
});

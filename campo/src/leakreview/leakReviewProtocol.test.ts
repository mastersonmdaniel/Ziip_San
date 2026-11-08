import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  canTransition, inspectionGaps, conclusionMatchesInspection,
  type FieldInspectionDraft,
} from './leakReviewProtocol.ts';

const complete: FieldInspectionDraft = {
  serviceOrderId: 'os-1', beforePhotoId: 'p1', afterPhotoId: 'p2',
  meterIdentityCode: 'M-123', meterReading: 42, leakFound: true, leakLocation: 'cavalete',
};

test('fases seguem a ordem do protocolo, sem pular a decisao nem a retificacao', () => {
  assert.equal(canTransition('OPENED', 'INSPECTION'), true);
  assert.equal(canTransition('INSPECTION', 'REPORT'), true);
  assert.equal(canTransition('REPORT', 'DECISION'), true);
  assert.equal(canTransition('DECISION', 'RECTIFICATION'), true);
  assert.equal(canTransition('RECTIFICATION', 'CLOSED_GRANTED'), true);
  assert.equal(canTransition('DECISION', 'CLOSED_DENIED'), true);
  assert.equal(canTransition('OPENED', 'DECISION'), false);
  assert.equal(canTransition('DECISION', 'CLOSED_GRANTED'), false);
  assert.equal(canTransition('CLOSED_GRANTED', 'CANCELLED'), false);
});

test('laudo inconclusivo devolve o protocolo a vistoria', () => {
  assert.equal(canTransition('REPORT', 'INSPECTION'), true);
});

test('vistoria completa nao tem lacunas; sem foto ou sem local do vazamento, tem', () => {
  assert.deepEqual(inspectionGaps(complete), []);
  assert.deepEqual(
    inspectionGaps({ ...complete, afterPhotoId: null, leakLocation: null }),
    ['AFTER_PHOTO_MISSING', 'LEAK_LOCATION_MISSING']);
  assert.deepEqual(
    inspectionGaps({ ...complete, meterIdentityCode: null, meterReading: null }),
    ['METER_IDENTITY_MISSING', 'METER_READING_MISSING']);
});

test('conclusao do laudo precisa casar com o fato de campo', () => {
  assert.equal(conclusionMatchesInspection('LEAK_CONFIRMED', true, null), true);
  assert.equal(conclusionMatchesInspection('LEAK_CONFIRMED', false, null), false);
  assert.equal(conclusionMatchesInspection('INCONCLUSIVE', false, null), false);
  assert.equal(conclusionMatchesInspection('INCONCLUSIVE', false, 'acesso negado'), true);
  assert.equal(conclusionMatchesInspection('NO_LEAK_FOUND', false, null), true);
});

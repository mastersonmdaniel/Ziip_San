import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  asBuiltRefusals, captureMethodFor, isAsBuiltGrade, validateFix, type GnssFix,
} from './gnss.ts';

// Massa SINTETICA de ensaio (declarada): coordenadas ficticias de bancada,
// nenhum ponto de rede real.
const FIXED: GnssFix = {
  transport: 'BLUETOOTH_NMEA', solution: 'RTK_FIXED',
  latitude: -26.900001, longitude: -48.700001,
  ellipsoidalHeightM: 812.40, hAccuracyM: 0.02, vAccuracyM: 0.03,
  pdop: 1.4, satellites: 18, antennaHeightM: 2.0,
  capturedAt: '2026-11-08T12:00:00Z',
};
const TARGET = { hTargetM: 0.10, vTargetM: 0.05 };

test('fix RTK fixo completo e valido e deriva capture_method RTK_FIXED', () => {
  assert.deepEqual(validateFix(FIXED), []);
  assert.equal(captureMethodFor(FIXED), 'RTK_FIXED');
});

test('solucao flutuante e autonoma derivam RTK_FLOAT e GNSS_NAVIGATION (V13_2)', () => {
  assert.equal(captureMethodFor({ ...FIXED, solution: 'RTK_FLOAT' }), 'RTK_FLOAT');
  assert.equal(captureMethodFor({ ...FIXED, solution: 'AUTONOMOUS' }), 'GNSS_NAVIGATION');
});

test('fix sem PDOP nao e fix de campo completo (GIS-15 exige tipo de solucao e PDOP)', () => {
  assert.deepEqual(validateFix({ ...FIXED, pdop: null }), ['PDOP_MISSING']);
});

test('fix sem satelites e sem exatidoes acusa cada ausencia pelo nome', () => {
  const problems = validateFix({ ...FIXED, satellites: null, hAccuracyM: null, vAccuracyM: null });
  assert.deepEqual(problems, ['SATELLITES_MISSING', 'H_ACCURACY_MISSING', 'V_ACCURACY_MISSING']);
});

test('as-built: RTK fixo dentro da meta do tenant e aceito', () => {
  assert.deepEqual(asBuiltRefusals(FIXED, TARGET), []);
  assert.equal(isAsBuiltGrade(FIXED, TARGET), true);
});

test('as-built recusado sem RTK fixo (flutuante dentro da meta nao basta)', () => {
  const refusals = asBuiltRefusals({ ...FIXED, solution: 'RTK_FLOAT' }, TARGET);
  assert.deepEqual(refusals, ['NOT_RTK_FIXED']);
});

test('as-built recusado com RTK fixo FORA da meta horizontal do tenant', () => {
  const refusals = asBuiltRefusals({ ...FIXED, hAccuracyM: 0.25 }, TARGET);
  assert.deepEqual(refusals, ['H_OUT_OF_TARGET']);
});

test('as-built recusado fora da meta vertical; classe sem meta vertical nao cobra vertical', () => {
  assert.deepEqual(asBuiltRefusals({ ...FIXED, vAccuracyM: 0.09 }, TARGET), ['V_OUT_OF_TARGET']);
  assert.deepEqual(
    asBuiltRefusals({ ...FIXED, vAccuracyM: 0.09 }, { hTargetM: 0.10, vTargetM: null }), []);
});

test('fix invalido acumula FIX_INVALID junto da recusa de solucao', () => {
  const refusals = asBuiltRefusals({ ...FIXED, solution: 'AUTONOMOUS', pdop: null }, TARGET);
  assert.deepEqual(refusals, ['FIX_INVALID', 'NOT_RTK_FIXED']);
});

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { divergentDigits, evaluateOcr } from './ocrCompare.ts';

// Mesmos vetores de OcrComparisonTest (Java): os dois lados da regra nao podem divergir.
test('modo desligado nao compara', () => {
  assert.equal(evaluateOcr('OFF', 0, 5, 12345, 99999, 10000), 'DISABLED');
});
test('igualdade e MATCH; tolerancia respeitada', () => {
  assert.equal(evaluateOcr('REQUIRE', 0, 5, 12345, 12345, 12000), 'MATCH');
  assert.equal(evaluateOcr('REQUIRE', 1, 5, 12345, 12346, 12000), 'MATCH');
  assert.equal(evaluateOcr('REQUIRE', 0, 5, 12345, 12346, 12000), 'REVIEW');
});
test('sugerir nao manda a revisao; historico divergente manda', () => {
  assert.equal(evaluateOcr('SUGGEST', 0, 5, 12345, 12945, 12000), 'SUGGESTION');
  assert.equal(evaluateOcr('SUGGEST', 0, 5, 11000, 11000, 12000), 'REVIEW');
});
test('virada do mostrador nao e divergencia', () => {
  assert.equal(evaluateOcr('REQUIRE', 0, 5, 12, 12, 99990), 'MATCH');
});
test('digitos divergentes alinhados pelo mostrador', () => {
  assert.equal(divergentDigits(123, 23, 5), 1);
});

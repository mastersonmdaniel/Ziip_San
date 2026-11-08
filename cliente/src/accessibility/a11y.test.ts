import { test } from 'node:test';
import assert from 'node:assert/strict';
import { animationsEnabled, effectiveFontScale, elementPassesA11y } from './a11y.ts';

test('fonte dinamica do aparelho e respeitada sem teto do app', () => {
  assert.equal(effectiveFontScale(1.3), 1.3);
  assert.equal(effectiveFontScale(2.0), 2.0);
  assert.throws(() => effectiveFontScale(0));
});

test('reducao de movimento do aparelho desliga animacao', () => {
  assert.equal(animationsEnabled(true), false);
  assert.equal(animationsEnabled(false), true);
});

test('alvo minimo por plataforma e rotulo obrigatorio (SG-782)', () => {
  assert.equal(elementPassesA11y({ label: 'Pagar', width: 48, height: 48, platform: 'android' }), true);
  assert.equal(elementPassesA11y({ label: 'Pagar', width: 44, height: 44, platform: 'android' }), false);
  assert.equal(elementPassesA11y({ label: 'Pagar', width: 44, height: 44, platform: 'ios' }), true);
  assert.equal(elementPassesA11y({ label: ' ', width: 64, height: 64, platform: 'ios' }), false);
});

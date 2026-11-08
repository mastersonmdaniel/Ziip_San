import { test } from 'node:test';
import assert from 'node:assert/strict';
import { cssVariables, type TenantTheme } from './tenantTheme.ts';

const THEME: TenantTheme = {
  displayName: 'Saneamento Exemplo',
  primaryColor: '#00548F',
  onPrimaryColor: '#FFFFFF',
  brandTextColor: '#00548F',
};

test('tema valido gera as mesmas variaveis CSS do portal (P3.21)', () => {
  assert.deepEqual(cssVariables(THEME), {
    '--brand-primary': '#00548F',
    '--brand-on-primary': '#FFFFFF',
    '--brand-text': '#00548F',
  });
});

test('cor fora do formato e nome vazio sao recusados, sem cor presumida', () => {
  assert.throws(() => cssVariables({ ...THEME, primaryColor: 'azul' }));
  assert.throws(() => cssVariables({ ...THEME, displayName: ' ' }));
});

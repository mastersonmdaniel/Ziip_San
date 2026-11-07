import { test } from 'node:test';
import assert from 'node:assert/strict';
import { assertRemoteUpdateAllowed, type RemoteBundleManifest } from './remoteUpdate.ts';

const INSTALLED = { appId: 'br.com.ziipsan.campo', purposeHash: 'hash-proposito-1' };
const BASE: RemoteBundleManifest = {
  appId: 'br.com.ziipsan.campo', version: '1.1.0', purposeHash: 'hash-proposito-1',
  contents: ['web-bundle'], signature: 'assinatura',
};

test('bundle web assinado, mesmo app e mesmo proposito: permitido', () => {
  assert.deepEqual(assertRemoteUpdateAllowed(BASE, INSTALLED), { allowed: true });
});
test('mudar o proposito por atualizacao remota: recusado', () => {
  assert.deepEqual(assertRemoteUpdateAllowed({ ...BASE, purposeHash: 'outro' }, INSTALLED),
    { allowed: false, reason: 'purpose-change' });
});
test('conteudo nativo por atualizacao remota: recusado (exige APK novo)', () => {
  assert.deepEqual(assertRemoteUpdateAllowed({ ...BASE, contents: ['web-bundle', 'native-lib'] }, INSTALLED),
    { allowed: false, reason: 'native-content' });
});
test('manifesto sem assinatura: recusado', () => {
  assert.deepEqual(assertRemoteUpdateAllowed({ ...BASE, signature: '' }, INSTALLED),
    { allowed: false, reason: 'unsigned' });
});

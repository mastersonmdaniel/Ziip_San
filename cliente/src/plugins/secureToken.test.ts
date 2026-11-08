import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  SECURE_TOKEN_PLUGIN_NAME,
  bridgeMethodNames,
  bridgeRespectsRnf39,
  type SecureTokenBridge,
} from './secureToken.ts';

/** Ponte conforme o contrato: so os quatro metodos, nenhum le o refresh. */
class ConformeBridge implements SecureTokenBridge {
  stored: string | null = null;
  async storeRefreshToken(refreshToken: string): Promise<void> {
    this.stored = refreshToken;
  }
  async getAccessToken(): Promise<{ accessToken: string; expiresAt: number }> {
    return { accessToken: 'access-sintetico', expiresAt: 1 };
  }
  async hasRefreshToken(): Promise<{ present: boolean }> {
    return { present: this.stored !== null };
  }
  async clear(): Promise<void> {
    this.stored = null;
  }
}

/** Ponte NAO conforme: expoe leitura do refresh ao JS (viola a RNF-39). */
class LeRefreshBridge extends ConformeBridge {
  async getRefreshToken(): Promise<string> {
    return this.stored ?? '';
  }
}

test('D3/RNF-39: ponte conforme expoe so os quatro metodos do contrato', () => {
  const bridge = new ConformeBridge();
  assert.deepEqual(bridgeMethodNames(bridge).sort(), [
    'clear',
    'getAccessToken',
    'hasRefreshToken',
    'storeRefreshToken',
  ]);
  assert.equal(bridgeRespectsRnf39(bridge), true);
  assert.equal(SECURE_TOKEN_PLUGIN_NAME, 'SecureToken');
});

test('D3/RNF-39: ponte com metodo de leitura do refresh e recusada pela guarda', () => {
  const bridge = new LeRefreshBridge();
  assert.ok(bridgeMethodNames(bridge).includes('getRefreshToken'));
  assert.equal(bridgeRespectsRnf39(bridge), false);
});

test('D3/RNF-39: guardar, presenca e apagar funcionam sem devolver o refresh', async () => {
  const bridge = new ConformeBridge();
  assert.deepEqual(await bridge.hasRefreshToken(), { present: false });
  await bridge.storeRefreshToken('refresh-sintetico');
  assert.deepEqual(await bridge.hasRefreshToken(), { present: true });
  const access = await bridge.getAccessToken();
  assert.equal(access.accessToken, 'access-sintetico');
  assert.ok(!('refreshToken' in access));
  await bridge.clear();
  assert.deepEqual(await bridge.hasRefreshToken(), { present: false });
});

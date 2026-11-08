import { test } from 'node:test';
import assert from 'node:assert/strict';
import { PortalSession, type BrowserOpener, type TokenExchange } from './session.ts';
import type { SecureTokenBridge } from '../plugins/secureToken.ts';

const ISSUER = 'https://sso.exemplo.invalid/realms/ziipsan-portal';
const CLIENT_ID = 'ziipsan-cliente-samae-exemplo';
const REDIRECT = 'https://app.exemplo.invalid/auth/callback';

class FakeBridge implements SecureTokenBridge {
  stored: string[] = [];
  cleared = 0;
  async storeRefreshToken(refreshToken: string): Promise<void> {
    this.stored.push(refreshToken);
  }
  async getAccessToken(): Promise<{ accessToken: string; expiresAt: number }> {
    return { accessToken: 'access-sintetico', expiresAt: 1 };
  }
  async hasRefreshToken(): Promise<{ present: boolean }> {
    return { present: this.stored.length > 0 };
  }
  async clear(): Promise<void> {
    this.cleared++;
    this.stored = [];
  }
}

class FakeExchange implements TokenExchange {
  calls: Array<{ code: string; verifier: string; redirectUri: string }> = [];
  async exchangeCode(
    code: string,
    codeVerifier: string,
    redirectUri: string,
  ): Promise<{ refreshToken: string }> {
    this.calls.push({ code, verifier: codeVerifier, redirectUri });
    return { refreshToken: 'refresh-sintetico' };
  }
}

class FakeBrowser implements BrowserOpener {
  opened: string[] = [];
  async open(url: string): Promise<void> {
    this.opened.push(url);
  }
}

function fixture() {
  const bridge = new FakeBridge();
  const exchange = new FakeExchange();
  const browser = new FakeBrowser();
  const session = new PortalSession(bridge, exchange, browser, ISSUER, CLIENT_ID, REDIRECT);
  return { bridge, exchange, browser, session };
}

async function beginAndState(session: PortalSession, browser: FakeBrowser): Promise<string> {
  await session.beginLogin();
  const url = new URL(browser.opened[0]);
  assert.equal(url.searchParams.get('response_type'), 'code');
  assert.equal(url.searchParams.get('code_challenge_method'), 'S256');
  assert.equal(url.searchParams.get('client_id'), CLIENT_ID);
  assert.equal(url.searchParams.get('redirect_uri'), REDIRECT);
  assert.ok(url.searchParams.get('code_challenge'));
  return url.searchParams.get('state')!;
}

test('D3: login completo entrega o refresh UMA vez a ponte e limpa o pendente', async () => {
  const { bridge, exchange, browser, session } = fixture();
  const state = await beginAndState(session, browser);
  await session.completeLogin(`${REDIRECT}?code=code-sintetico&state=${state}`);
  assert.deepEqual(bridge.stored, ['refresh-sintetico']);
  assert.equal(exchange.calls.length, 1);
  assert.equal(exchange.calls[0].code, 'code-sintetico');
  assert.equal(exchange.calls[0].redirectUri, REDIRECT);
  assert.ok(exchange.calls[0].verifier.length >= 43);
  // Pendente limpo: repetir o mesmo retorno NAO troca de novo.
  await assert.rejects(() => session.completeLogin(`${REDIRECT}?code=code-sintetico&state=${state}`));
  assert.equal(exchange.calls.length, 1);
  assert.deepEqual(bridge.stored, ['refresh-sintetico']);
});

test('D3: state divergente e recusado sem trocar nem guardar', async () => {
  const { bridge, exchange, browser, session } = fixture();
  await beginAndState(session, browser);
  await assert.rejects(() => session.completeLogin(`${REDIRECT}?code=x&state=estado-errado`));
  assert.equal(exchange.calls.length, 0);
  assert.deepEqual(bridge.stored, []);
});

test('D3: retorno fora da origem/caminho registrados e recusado', async () => {
  const { bridge, exchange, browser, session } = fixture();
  const state = await beginAndState(session, browser);
  await assert.rejects(() =>
    session.completeLogin(`https://outro.exemplo.invalid/auth/callback?code=x&state=${state}`),
  );
  await assert.rejects(() => session.completeLogin(`${REDIRECT}/extra?code=x&state=${state}`));
  assert.equal(exchange.calls.length, 0);
  assert.deepEqual(bridge.stored, []);
});

test('D3: logout apaga o cofre pela ponte', async () => {
  const { bridge, session } = fixture();
  await session.logout();
  assert.equal(bridge.cleared, 1);
});

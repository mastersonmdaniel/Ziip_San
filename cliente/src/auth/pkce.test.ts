import { test } from 'node:test';
import assert from 'node:assert/strict';
import { buildAuthorizeUrl, generateCodeChallenge, generateCodeVerifier } from './pkce.ts';

test('verifier tem 64 caracteres do alfabeto PKCE', () => {
  const v = generateCodeVerifier(() => 0);
  assert.equal(v.length, 64);
  assert.match(v, /^[A-Za-z0-9\-._~]+$/);
});

test('challenge e o S256 do RFC 7636, apendice B', async () => {
  const challenge = await generateCodeChallenge('dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk');
  assert.equal(challenge, 'E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM');
});

test('URL de autorizacao usa o realm ziipsan-portal, S256 e o client do app', () => {
  const url = buildAuthorizeUrl({
    issuerBaseUrl: 'https://auth.example/realms/ziipsan-portal',
    clientId: 'ziipsan-cliente-samae-exemplo',
    redirectUri: 'https://app.exemplo.invalid/auth/callback',
    codeChallenge: 'abc',
    state: 'xyz',
  });
  assert.ok(url.includes('/realms/ziipsan-portal/protocol/openid-connect/auth'));
  assert.ok(url.includes('code_challenge_method=S256'));
  assert.ok(url.includes('client_id=ziipsan-cliente-samae-exemplo'));
  assert.ok(url.includes('response_type=code'));
});

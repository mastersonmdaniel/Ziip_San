import { test } from 'node:test';
import assert from 'node:assert/strict';
import { buildAuthorizeUrl, generateCodeChallenge, generateCodeVerifier } from './pkce.ts';

test('verifier tem 64 caracteres do alfabeto PKCE', () => {
  const v = generateCodeVerifier(() => 0);
  assert.equal(v.length, 64);
  assert.match(v, /^[A-Za-z0-9\-._~]+$/);
});

test('challenge e o S256 (base64url do SHA-256) do verifier', async () => {
  // Vetor do RFC 7636, apendice B.
  const challenge = await generateCodeChallenge('dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk');
  assert.equal(challenge, 'E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM');
});

test('URL de autorizacao usa o realm ziipsan, S256 e retorno por App Link', () => {
  const url = buildAuthorizeUrl({
    issuerBaseUrl: 'https://auth.example/realms/ziipsan',
    clientId: 'ziipsan-campo',
    redirectUri: 'https://campo.ziipsan.invalid/auth/callback',
    codeChallenge: 'abc',
    state: 'xyz',
  });
  assert.ok(url.includes('/realms/ziipsan/protocol/openid-connect/auth'));
  assert.ok(url.includes('code_challenge_method=S256'));
  assert.ok(url.includes('response_type=code'));
});

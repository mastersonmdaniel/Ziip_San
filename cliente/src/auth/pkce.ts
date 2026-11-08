/**
 * PKCE (RFC 7636) do login do app do cliente (ED-39; ADR-0026/ADR-0030):
 * login no navegador do sistema, realm `ziipsan-portal` (o do portal,
 * NAO o `ziipsan` do backoffice/campo), client publico POR APP criado
 * pelo pipeline, retorno por Universal Link (iOS) ou App Link (Android).
 * Espelho da regra da ED-18 com o realm e o client desta tarefa.
 */

const VERIFIER_ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~';

function secureRandom(): number {
  const buf = new Uint32Array(1);
  crypto.getRandomValues(buf);
  return buf[0] / 0x100000000;
}

export function generateCodeVerifier(random: () => number = secureRandom): string {
  let out = '';
  for (let i = 0; i < 64; i++) {
    out += VERIFIER_ALPHABET[Math.floor(random() * VERIFIER_ALPHABET.length)];
  }
  return out;
}

export async function generateCodeChallenge(verifier: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier));
  return base64UrlEncode(new Uint8Array(digest));
}

export function base64UrlEncode(bytes: Uint8Array): string {
  let binary = '';
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}

export interface AuthorizeUrlParams {
  issuerBaseUrl: string;
  clientId: string;
  redirectUri: string;
  codeChallenge: string;
  state: string;
}

/** URL de autorizacao do realm ziipsan-portal (PKCE S256 obrigatorio). */
export function buildAuthorizeUrl(p: AuthorizeUrlParams): string {
  const url = new URL(`${p.issuerBaseUrl}/protocol/openid-connect/auth`);
  url.searchParams.set('client_id', p.clientId);
  url.searchParams.set('redirect_uri', p.redirectUri);
  url.searchParams.set('response_type', 'code');
  url.searchParams.set('scope', 'openid');
  url.searchParams.set('code_challenge', p.codeChallenge);
  url.searchParams.set('code_challenge_method', 'S256');
  url.searchParams.set('state', p.state);
  return url.toString();
}

export const PORTAL_REALM = 'ziipsan-portal';

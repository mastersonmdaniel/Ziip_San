import { buildAuthorizeUrl, generateCodeChallenge, generateCodeVerifier } from './pkce.ts';
import type { SecureTokenBridge } from '../plugins/secureToken.ts';

/**
 * Sessao do app do cliente (ED-39): identica em espirito a do campo
 * (ED-18), com o realm `ziipsan-portal` e o client publico DESTE app.
 *  1. login no navegador do sistema com PKCE;
 *  2. retorno por Universal Link/App Link com o code;
 *  3. troca do code NESTA camada JS (correcao D3 da revisao cruzada:
 *     a versao original deste comentario dizia que a troca ocorria
 *     "fora do alcance de leitura do JS" — FALSO neste codigo:
 *     `exchangeCode` roda no JS e o refresh token RETORNA ao JS no
 *     resultado da troca). O que ESTE codigo garante e o que a
 *     RNF-39 exige do lado do app a partir dai: o JS entrega o refresh
 *     token UMA unica vez ao plugin nativo (storeRefreshToken,
 *     Keychain/Keystore), NAO o persiste em nenhum armazenamento web,
 *     NAO o le de volta (a ponte nao tem metodo de leitura do refresh)
 *     e o descarta da sessao (verifier/state limpos).
 *  4. dai em diante, refresh -> access token roda NO plugin nativo,
 *     com rotacao, sem o refresh voltar ao JS.
 * A garantia mais forte — o refresh NUNCA transitar pelo JS, com a
 * troca code->tokens executada dentro do plugin nativo — e passo de
 * integracao/POC-55 declarado no relatorio; NAO e o que esta camada
 * faz, e este comentario nao a alega mais.
 * Nenhum token vai para localStorage/sessionStorage/IndexedDB.
 */
export interface TokenExchange {
  exchangeCode(code: string, codeVerifier: string, redirectUri: string): Promise<{ refreshToken: string }>;
}

export interface BrowserOpener {
  open(url: string): Promise<void>;
}

export class PortalSession {
  private pendingVerifier: string | null = null;
  private pendingState: string | null = null;

  private readonly tokens: SecureTokenBridge;
  private readonly exchange: TokenExchange;
  private readonly browser: BrowserOpener;
  private readonly issuerBaseUrl: string;
  private readonly clientId: string;
  private readonly redirectUri: string;

  // Campos declarados explicitamente (sem parameter properties):
  // mesma semantica, e o arquivo fica executavel no node --test
  // (strip-only), o que viabiliza session.test.ts (correcao D3).
  constructor(
    tokens: SecureTokenBridge,
    exchange: TokenExchange,
    browser: BrowserOpener,
    issuerBaseUrl: string,
    clientId: string,
    redirectUri: string,
  ) {
    this.tokens = tokens;
    this.exchange = exchange;
    this.browser = browser;
    this.issuerBaseUrl = issuerBaseUrl;
    this.clientId = clientId;
    this.redirectUri = redirectUri;
  }

  async beginLogin(): Promise<void> {
    this.pendingVerifier = generateCodeVerifier();
    this.pendingState = generateCodeVerifier().slice(0, 32);
    const challenge = await generateCodeChallenge(this.pendingVerifier);
    const url = buildAuthorizeUrl({
      issuerBaseUrl: this.issuerBaseUrl,
      clientId: this.clientId,
      redirectUri: this.redirectUri,
      codeChallenge: challenge,
      state: this.pendingState,
    });
    await this.browser.open(url);
  }

  /** Chamado quando o Universal Link/App Link de retorno chega (appUrlOpen). */
  async completeLogin(callbackUrl: string): Promise<void> {
    const url = new URL(callbackUrl);
    const code = url.searchParams.get('code');
    const state = url.searchParams.get('state');
    if (!code || !this.pendingVerifier || state !== this.pendingState) {
      throw new Error('Retorno de login invalido.');
    }
    if (url.origin + url.pathname !== this.redirectUri) {
      throw new Error('Retorno de login fora do link registrado do app.');
    }
    const { refreshToken } = await this.exchange.exchangeCode(code, this.pendingVerifier, this.redirectUri);
    this.pendingVerifier = null;
    this.pendingState = null;
    await this.tokens.storeRefreshToken(refreshToken);
  }

  async logout(): Promise<void> {
    await this.tokens.clear();
  }
}

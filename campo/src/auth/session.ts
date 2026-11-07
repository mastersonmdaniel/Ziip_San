import { APP_LINK_REDIRECT_URI, buildAuthorizeUrl, generateCodeChallenge, generateCodeVerifier } from './pkce.ts';
import type { SecureTokenBridge } from '../plugins/secureToken.ts';

/**
 * Sessao do app de campo (ED-18; ADR-0030):
 *  1. login no navegador do sistema (Custom Tab) com PKCE no realm ziipsan;
 *  2. retorno por App Link com o code;
 *  3. troca do code por tokens FORA do alcance do JS: quem recebe o refresh
 *     token e o plugin nativo (storeRefreshToken) — o JS o repassa uma unica
 *     vez, vindo da resposta da troca, e nunca o le de volta nem o guarda.
 * Nenhum token vai para localStorage/sessionStorage/IndexedDB.
 */
export interface TokenExchange {
  exchangeCode(code: string, codeVerifier: string, redirectUri: string): Promise<{ refreshToken: string }>;
}

export interface BrowserOpener {
  open(url: string): Promise<void>;
}

export class FieldSession {
  private pendingVerifier: string | null = null;
  private pendingState: string | null = null;

  private readonly tokens: SecureTokenBridge;
  private readonly exchange: TokenExchange;
  private readonly browser: BrowserOpener;
  private readonly issuerBaseUrl: string;
  private readonly clientId: string;

  constructor(tokens: SecureTokenBridge, exchange: TokenExchange, browser: BrowserOpener,
              issuerBaseUrl: string, clientId: string = 'ziipsan-campo') {
    this.tokens = tokens;
    this.exchange = exchange;
    this.browser = browser;
    this.issuerBaseUrl = issuerBaseUrl;
    this.clientId = clientId;
  }

  async beginLogin(): Promise<void> {
    this.pendingVerifier = generateCodeVerifier();
    this.pendingState = generateCodeVerifier().slice(0, 32);
    const challenge = await generateCodeChallenge(this.pendingVerifier);
    const url = buildAuthorizeUrl({
      issuerBaseUrl: this.issuerBaseUrl,
      clientId: this.clientId,
      redirectUri: APP_LINK_REDIRECT_URI,
      codeChallenge: challenge,
      state: this.pendingState,
    });
    await this.browser.open(url);
  }

  /** Chamado quando o App Link de retorno chega (appUrlOpen do Capacitor). */
  async completeLogin(callbackUrl: string): Promise<void> {
    const url = new URL(callbackUrl);
    const code = url.searchParams.get('code');
    const state = url.searchParams.get('state');
    if (!code || !this.pendingVerifier || state !== this.pendingState) {
      throw new Error('Retorno de login invalido.');
    }
    const { refreshToken } = await this.exchange.exchangeCode(code, this.pendingVerifier, APP_LINK_REDIRECT_URI);
    this.pendingVerifier = null;
    this.pendingState = null;
    await this.tokens.storeRefreshToken(refreshToken);
  }

  async logout(): Promise<void> {
    await this.tokens.clear();
  }
}

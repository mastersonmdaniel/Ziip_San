/**
 * Contrato da ponte com o plugin nativo de tokens do app do cliente
 * (ED-39; RNF-39): UMA interface, DOIS cofres —
 *  - Android: EncryptedSharedPreferences com chave do Android Keystore
 *    (o mesmo plugin da ED-18, reutilizado);
 *  - iOS: Keychain (kSecClassGenericPassword, acessibilidade
 *    AfterFirstUnlockThisDeviceOnly, sem sincronizacao com iCloud).
 *
 * REGRA CENTRAL: o refresh token (rotativo) vive SO no cofre nativo e
 * e INACESSIVEL ao JavaScript: esta interface NAO tem metodo que o
 * devolva. O JS guarda (uma vez, no fim do login), pede access token
 * (a troca refresh->access roda no nativo, com rotacao) e apaga.
 * A implementacao iOS (Swift) e a Android (Kotlin, da ED-18) sao
 * passos de integracao nomeados no relatorio: o CONTRATO e os testes
 * de forma vivem aqui (secureToken.test.ts, correcao D3) e os testes
 * de fluxo da sessao em src/auth/session.test.ts. Transparencia de
 * fluxo (idem D3): na troca INICIAL, o refresh chega a esta ponte
 * vindo da camada JS (ver session.ts); o que esta interface veda e a
 * LEITURA de volta e qualquer persistencia fora do cofre.
 */
export interface SecureTokenBridge {
  storeRefreshToken(refreshToken: string): Promise<void>;
  getAccessToken(): Promise<{ accessToken: string; expiresAt: number }>;
  hasRefreshToken(): Promise<{ present: boolean }>;
  clear(): Promise<void>;
}

export const SECURE_TOKEN_PLUGIN_NAME = 'SecureToken';

/** Guarda de contrato: nenhum nome de metodo expoe o refresh token ao JS. */
export function bridgeMethodNames(bridge: SecureTokenBridge): string[] {
  return Object.getOwnPropertyNames(Object.getPrototypeOf(bridge)).filter(
    (n) => n !== 'constructor',
  );
}

const ALLOWED_BRIDGE_METHODS = new Set([
  'storeRefreshToken',
  'getAccessToken',
  'hasRefreshToken',
  'clear',
]);

/**
 * Verdadeiro se a ponte respeita a RNF-39: somente os quatro metodos do
 * contrato — nenhum deles devolve o refresh token (hasRefreshToken
 * devolve so a presenca, como na ED-18).
 */
export function bridgeRespectsRnf39(bridge: SecureTokenBridge): boolean {
  return bridgeMethodNames(bridge).every((n) => ALLOWED_BRIDGE_METHODS.has(n));
}

/**
 * Contrato da ponte com o plugin nativo SecureTokenPlugin (Kotlin).
 *
 * REGRA CENTRAL DA ED-18 / RNF-39: o refresh token (rotativo) vive SO no Android
 * Keystore (EncryptedSharedPreferences com chave do Keystore) e e INACESSIVEL ao
 * JavaScript: esta interface NAO tem nenhum metodo que devolva o refresh token.
 * O JavaScript pede ao nativo um access token de vida curta — a troca
 * refresh -> access acontece dentro do plugin nativo — e so isso atravessa a ponte.
 */
export interface SecureTokenBridge {
  /** Guarda o refresh token recebido no fim do login. O JS nunca o le de volta. */
  storeRefreshToken(refreshToken: string): Promise<void>;
  /** Troca (no nativo) o refresh token vigente por um access token; rotacao incluida. */
  getAccessToken(): Promise<{ accessToken: string; expiresAt: number }>;
  hasRefreshToken(): Promise<{ present: boolean }>;
  /** Apaga o refresh token (logout, BLOCK, RETIRE). */
  clear(): Promise<void>;
}

export const SECURE_TOKEN_PLUGIN_NAME = 'SecureToken';

/** Guarda de contrato usada pelos testes: nenhum nome de metodo expoe o refresh token. */
export function bridgeMethodNames(bridge: SecureTokenBridge): string[] {
  return Object.getOwnPropertyNames(Object.getPrototypeOf(bridge)).filter(
    (n) => n !== 'constructor',
  );
}

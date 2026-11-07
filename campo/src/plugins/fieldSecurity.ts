/**
 * Contrato da ponte com o FieldSecurityPlugin (Kotlin): a chave do banco local e
 * gerada e guardada no Android Keystore (nao extraivel) e nunca atravessa a ponte;
 * o JavaScript so pede abrir o banco e apagar tudo (wipe) nas diretivas BLOCK/RETIRE.
 */
export interface FieldSecurityBridge {
  /** Abre (criando na primeira vez) o banco local cifrado com a chave do Keystore. */
  openEncryptedDatabase(): Promise<{ opened: boolean }>;
  /** Apaga banco local, fotos pendentes e tokens. Irreversivel — diretivas BLOCK/RETIRE e logout. */
  wipeAll(): Promise<{ wiped: boolean }>;
}

export const FIELD_SECURITY_PLUGIN_NAME = 'FieldSecurity';

package br.com.ziipsan.platform.infra.crypto;

import java.util.Optional;
import java.util.UUID;

/**
 * P0.28: persistencia das DEKs embrulhadas (tabela {@code public.tenant_key}, core V6).
 * Interface interna do provedor de chaves — nao e uma das interfaces de infra do
 * P0.45. A implementacao de producao local e {@link JdbcTenantKeyStore} (JDBC puro);
 * testes usam um duplo em memoria. O material aqui ja vem cifrado pela KEK: esta
 * camada nunca ve uma DEK em claro.
 */
public interface TenantKeyStore {

    /** Linha de chave de um tenant. Campos wrapped sao nulos quando DESTROYED. */
    record KeyMaterial(int version, byte[] wrappedEncryptionKey, byte[] wrappedBlindIndexKey, String status) {
    }

    Optional<KeyMaterial> loadActive(UUID tenantId);

    /** Cria uma nova versao ATIVA e marca a anterior como ROTATED. Devolve a versao. */
    int createVersion(UUID tenantId, byte[] wrappedEncryptionKey, byte[] wrappedBlindIndexKey);

    /** Apaga o material cifrado de todas as versoes do tenant (crypto-shredding). */
    void destroyAll(UUID tenantId);

    /** true quando o tenant ja teve as chaves destruidas: nunca recriar em silencio. */
    boolean destroyed(UUID tenantId);
}

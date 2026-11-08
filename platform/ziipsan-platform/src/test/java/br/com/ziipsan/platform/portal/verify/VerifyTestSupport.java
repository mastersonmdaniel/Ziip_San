package br.com.ziipsan.platform.portal.verify;

import br.com.ziipsan.security.crypto.BlindIndex;
import br.com.ziipsan.security.crypto.FieldCipher;
import br.com.ziipsan.security.crypto.FieldProtector;
import br.com.ziipsan.security.crypto.TenantKeyProvider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Chaves de teste DERIVADAS POR TENANT (correcao D2 da revisao cruzada).
 *
 * <p>No {@code BlindIndex} real, a separacao entre tenants vem SO da chave
 * ({@code HMAC(chaveDoTenant, contexto|valor)}; o {@code tenantId} nao entra
 * na mensagem do HMAC). A fixture original devolvia a MESMA chave para
 * qualquer tenant, o que tornava falsa a prova cross-tenant de
 * {@code VerificationCodeTest} contra as classes reais. Esta fixture
 * reproduz o contrato do {@code TenantKeyProvider} de producao (chave por
 * tenant, cifra != indice cego): cada chave e o SHA-256 de um rotulo
 * distinto + o {@code tenantId} — deterministico (a mesma chamada com o
 * mesmo tenant gera o mesmo protector) e distinto entre tenants.
 */
final class VerifyTestSupport {

    private VerifyTestSupport() { }

    private static byte[] derive(String label, UUID tenantId) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest((label + "|" + tenantId).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel na JVM", e);
        }
    }

    static TenantKeyProvider keys() {
        return new TenantKeyProvider() {
            @Override public byte[] encryptionKey(UUID tenantId) {
                return derive("ziipsan/test/encryption", tenantId);
            }
            @Override public byte[] blindIndexKey(UUID tenantId) {
                return derive("ziipsan/test/blind-index", tenantId);
            }
        };
    }

    static FieldProtector protector() {
        TenantKeyProvider keys = keys();
        return new FieldProtector(new FieldCipher(keys), new BlindIndex(keys));
    }
}

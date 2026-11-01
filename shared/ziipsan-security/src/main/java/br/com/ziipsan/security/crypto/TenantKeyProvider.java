package br.com.ziipsan.security.crypto;

import java.util.UUID;

/**
 * Origem das chaves de um tenant.
 *
 * <p>Duas chaves distintas, de 32 bytes cada: a de <b>cifra</b> (AES-256-GCM) e a do <b>índice
 * cego</b> (HMAC-SHA-256). Elas nunca podem ser a mesma: quem consegue calcular o índice não deve
 * conseguir decifrar, e a recíproca.
 *
 * <p>A implementação de produção deve buscar as chaves no Vault, protegidas por uma KEK, e
 * guardá-las em cache só em memória. <b>Essa implementação ainda não existe</b>: o diário registra
 * a decisão de arquitetura, mas o código de integração com o Vault não foi escrito. Sem um bean
 * desta interface, a aplicação não deve subir (falha fechada), em vez de gravar documento em claro.
 */
public interface TenantKeyProvider {

    /** Chave AES-256 (32 bytes) usada para cifrar campos do tenant. */
    byte[] encryptionKey(UUID tenantId);

    /** Chave HMAC (32 bytes) usada no índice cego do tenant. Diferente da de cifra. */
    byte[] blindIndexKey(UUID tenantId);
}

package br.com.ziipsan.platform.infra.crypto;

import br.com.ziipsan.platform.infra.secrets.SecretStore;
import br.com.ziipsan.security.crypto.AesGcmEnvelope;
import br.com.ziipsan.security.crypto.CryptoException;
import br.com.ziipsan.security.crypto.TenantKeyProvider;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * P0.28: {@link TenantKeyProvider} sobre o cofre local (R1 sobre o cofre local).
 *
 * <p>Envelope: a KEK (32 bytes, gerada uma vez e guardada no cofre em
 * {@code kek/current}) cifra cada DEK; as DEKs cifradas ficam em
 * {@code public.tenant_key} (core V6) via {@link TenantKeyStore}. A KEK nunca vai
 * para o banco e as DEKs nunca ficam em claro fora da memoria deste provedor
 * (cache em memoria, nunca em disco). Nao e o provider de producao da LC.4 (KMS):
 * e o provider local, para dados sinteticos, e o contrato dele
 * ({@link TenantKeyProvider} + esta classe) e o que a LC.4 deve honrar.
 *
 * <p>Rotacao: cria uma versao nova ATIVA; a anterior fica ROTATED no banco. Limitacao
 * honesta, registrada no roteiro: a rotacao NAO re-cifra os campos antigos — valores
 * cifrados com a versao anterior so decifram ate um job de re-cifra existir (o
 * FieldCipher usa so a chave corrente). Rotacao sem re-cifra e, portanto, um evento
 * a planejar, nao uma operacao invisivel.
 *
 * <p>Destruicao (crypto-shredding): apaga o material cifrado de todas as versoes e
 * limpa o cache. Depois dela, {@link #encryptionKey} e {@link #blindIndexKey} falham
 * em vez de recriar chaves em silencio — recriar devolveria um tenant "novo" sobre
 * dados antigos ilegíveis sem aviso.
 */
public final class LocalVaultTenantKeyProvider implements TenantKeyProvider {

    public static final String KEK_KEY = "kek/current";
    private static final int KEY_BYTES = 32;

    private final SecretStore vault;
    private final TenantKeyStore store;
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<UUID, byte[][]> cache = new ConcurrentHashMap<>();

    public LocalVaultTenantKeyProvider(SecretStore vault, TenantKeyStore store) {
        if (vault == null || store == null) {
            throw new IllegalArgumentException("cofre e store de chaves sao obrigatorios");
        }
        this.vault = vault;
        this.store = store;
    }

    @Override
    public byte[] encryptionKey(UUID tenantId) {
        return keyPair(tenantId)[0].clone();
    }

    @Override
    public byte[] blindIndexKey(UUID tenantId) {
        return keyPair(tenantId)[1].clone();
    }

    /** Cria a proxima versao de DEK do tenant. Devolve a versao criada. */
    public int rotate(UUID tenantId) {
        requireTenant(tenantId);
        if (store.destroyed(tenantId)) {
            throw new CryptoException("tenant com chaves destruidas: rotacao recusada");
        }
        byte[] kek = kek();
        byte[] enc = freshKey();
        byte[] bidx = freshKey();
        int version = store.createVersion(tenantId,
                AesGcmEnvelope.wrap(kek, enc), AesGcmEnvelope.wrap(kek, bidx));
        cache.put(tenantId, new byte[][]{enc, bidx});
        return version;
    }

    /** Destroi as DEKs do tenant: os dados cifrados dele tornam-se ilegíveis. */
    public void destroy(UUID tenantId) {
        requireTenant(tenantId);
        store.destroyAll(tenantId);
        cache.remove(tenantId);
    }

    private byte[][] keyPair(UUID tenantId) {
        requireTenant(tenantId);
        byte[][] cached = cache.get(tenantId);
        if (cached != null) {
            return cached;
        }
        if (store.destroyed(tenantId)) {
            throw new CryptoException("chaves do tenant destruidas (crypto-shredding): dado ilegivel");
        }
        var active = store.loadActive(tenantId);
        byte[] kek = kek();
        byte[][] pair;
        if (active.isPresent()) {
            var material = active.get();
            if (material.wrappedEncryptionKey() == null || material.wrappedBlindIndexKey() == null) {
                throw new CryptoException("tenant_key sem material cifrado para o tenant");
            }
            pair = new byte[][]{
                    AesGcmEnvelope.unwrap(kek, material.wrappedEncryptionKey()),
                    AesGcmEnvelope.unwrap(kek, material.wrappedBlindIndexKey())};
        } else {
            byte[] enc = freshKey();
            byte[] bidx = freshKey();
            store.createVersion(tenantId,
                    AesGcmEnvelope.wrap(kek, enc), AesGcmEnvelope.wrap(kek, bidx));
            pair = new byte[][]{enc, bidx};
        }
        cache.put(tenantId, pair);
        return pair;
    }

    private byte[] kek() {
        return vault.get(KEK_KEY).orElseGet(() -> {
            byte[] fresh = freshKey();
            vault.put(KEK_KEY, fresh);
            return fresh;
        });
    }

    private byte[] freshKey() {
        byte[] key = new byte[KEY_BYTES];
        random.nextBytes(key);
        return key;
    }

    private static void requireTenant(UUID tenantId) {
        if (tenantId == null) {
            throw new CryptoException("sem tenant: nao ha chave a prover");
        }
    }
}

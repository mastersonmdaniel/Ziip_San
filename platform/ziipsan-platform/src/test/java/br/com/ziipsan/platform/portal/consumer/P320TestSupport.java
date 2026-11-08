package br.com.ziipsan.platform.portal.consumer;

import br.com.ziipsan.security.crypto.BlindIndex;
import br.com.ziipsan.security.crypto.FieldCipher;
import br.com.ziipsan.security.crypto.FieldProtector;
import br.com.ziipsan.security.crypto.TenantKeyProvider;

import java.util.Arrays;
import java.util.UUID;

/** Chaves fixas de teste (mesmo padrao do PortalTestSupport da P3.3). */
final class P320TestSupport {
    private P320TestSupport() { }
    static FieldProtector protector() {
        byte[] enc = new byte[32]; byte[] idx = new byte[32];
        Arrays.fill(enc, (byte) 7); Arrays.fill(idx, (byte) 9);
        TenantKeyProvider keys = new TenantKeyProvider() {
            @Override public byte[] encryptionKey(UUID tenantId) { return enc.clone(); }
            @Override public byte[] blindIndexKey(UUID tenantId) { return idx.clone(); }
        };
        return new FieldProtector(new FieldCipher(keys), new BlindIndex(keys));
    }
}

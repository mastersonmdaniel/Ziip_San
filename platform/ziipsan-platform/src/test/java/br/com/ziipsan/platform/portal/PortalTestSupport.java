package br.com.ziipsan.platform.portal;

import br.com.ziipsan.security.crypto.BlindIndex;
import br.com.ziipsan.security.crypto.FieldCipher;
import br.com.ziipsan.security.crypto.FieldProtector;
import br.com.ziipsan.security.crypto.TenantKeyProvider;

import java.util.Arrays;
import java.util.UUID;

/** Chaves fixas de teste (32 bytes distintos para cifra e indice cego). */
final class PortalTestSupport {

    private PortalTestSupport() { }

    static TenantKeyProvider keys() {
        byte[] enc = new byte[32];
        byte[] idx = new byte[32];
        Arrays.fill(enc, (byte) 7);
        Arrays.fill(idx, (byte) 9);
        return new TenantKeyProvider() {
            @Override public byte[] encryptionKey(UUID tenantId) { return enc.clone(); }
            @Override public byte[] blindIndexKey(UUID tenantId) { return idx.clone(); }
        };
    }

    static FieldProtector protector() {
        TenantKeyProvider keys = keys();
        return new FieldProtector(new FieldCipher(keys), new BlindIndex(keys));
    }

    static FieldCipher cipher() {
        return new FieldCipher(keys());
    }
}

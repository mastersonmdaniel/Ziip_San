package br.com.ziipsan.platform.infra;

import br.com.ziipsan.platform.infra.crypto.LocalVaultTenantKeyProvider;
import br.com.ziipsan.platform.infra.secrets.LocalFileSecretStore;
import br.com.ziipsan.security.crypto.CryptoException;
import br.com.ziipsan.security.crypto.FieldCipher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P0.28: contrato do provedor de chaves sobre o cofre local (KEK no cofre, DEK por
 * tenant guardada cifrada). A LC.4 (KMS) deve honrar o mesmo contrato com outra
 * implementacao de {@code TenantKeyProvider}.
 */
class LocalVaultTenantKeyProviderTest {

    private static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    @DisplayName("chaves de cifra e de indice sao distintas, de 32 bytes, e estaveis por tenant")
    void keysAreDistinctStableAndSized(@TempDir Path vaultDir) {
        var provider = new LocalVaultTenantKeyProvider(
                new LocalFileSecretStore(vaultDir), new InMemoryTenantKeyStore());
        assertThat(provider.encryptionKey(TENANT_A)).hasSize(32);
        assertThat(provider.blindIndexKey(TENANT_A)).hasSize(32);
        assertThat(provider.encryptionKey(TENANT_A)).isNotEqualTo(provider.blindIndexKey(TENANT_A));
        assertThat(provider.encryptionKey(TENANT_A)).isEqualTo(provider.encryptionKey(TENANT_A));
        assertThat(provider.encryptionKey(TENANT_A)).isNotEqualTo(provider.encryptionKey(TENANT_B));
    }

    @Test
    @DisplayName("a DEK guardada esta cifrada: o material no cofre/store nao e a chave em claro")
    void storedMaterialIsNotPlaintext(@TempDir Path vaultDir) {
        var vault = new LocalFileSecretStore(vaultDir);
        var store = new InMemoryTenantKeyStore();
        var provider = new LocalVaultTenantKeyProvider(vault, store);
        byte[] plain = provider.encryptionKey(TENANT_A);
        var material = store.loadActive(TENANT_A).orElseThrow();
        assertThat(material.wrappedEncryptionKey()).isNotEqualTo(plain);
        // A KEK existe no cofre e tambem nao e a DEK.
        assertThat(vault.get(LocalVaultTenantKeyProvider.KEK_KEY)).isPresent();
        assertThat(vault.get(LocalVaultTenantKeyProvider.KEK_KEY).orElseThrow()).isNotEqualTo(plain);
    }

    @Test
    @DisplayName("rotacao: chave nova para dados novos, versao anterior preservada como ROTATED")
    void rotationCreatesNewVersion(@TempDir Path vaultDir) {
        var store = new InMemoryTenantKeyStore();
        var provider = new LocalVaultTenantKeyProvider(new LocalFileSecretStore(vaultDir), store);
        byte[] before = provider.encryptionKey(TENANT_A);
        int version = provider.rotate(TENANT_A);
        assertThat(version).isEqualTo(2);
        assertThat(provider.encryptionKey(TENANT_A)).isNotEqualTo(before);
        assertThat(store.loadActive(TENANT_A).orElseThrow().version()).isEqualTo(2);
    }

    @Test
    @DisplayName("crypto-shredding: destruir a DEK torna ilegivel o dado cifrado, sem recriar")
    void destroyingMakesDataUnreadable(@TempDir Path vaultDir) {
        var provider = new LocalVaultTenantKeyProvider(new LocalFileSecretStore(vaultDir), new InMemoryTenantKeyStore());
        var cipher = new FieldCipher(provider);
        String sealed = cipher.encrypt(TENANT_A, "customers.document", "documento-sintetico");
        assertThat(cipher.decrypt(TENANT_A, "customers.document", sealed)).isEqualTo("documento-sintetico");
        provider.destroy(TENANT_A);
        assertThatThrownBy(() -> cipher.decrypt(TENANT_A, "customers.document", sealed))
                .isInstanceOf(CryptoException.class);
        assertThatThrownBy(() -> provider.encryptionKey(TENANT_A)).isInstanceOf(CryptoException.class);
    }
}

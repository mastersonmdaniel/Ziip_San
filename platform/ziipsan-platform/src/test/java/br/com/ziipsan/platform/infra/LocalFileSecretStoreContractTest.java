package br.com.ziipsan.platform.infra;

import br.com.ziipsan.platform.infra.secrets.LocalFileSecretStore;
import br.com.ziipsan.platform.infra.secrets.SecretStore;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

/** P0.45: contrato de SecretStore contra o cofre local (arquivos). */
class LocalFileSecretStoreContractTest extends SecretStoreContractTest {

    @TempDir
    Path root;

    @Override
    protected SecretStore store() {
        return new LocalFileSecretStore(root);
    }
}

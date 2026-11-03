package br.com.ziipsan.platform.infra;

import br.com.ziipsan.platform.infra.storage.LocalFileObjectStorage;
import br.com.ziipsan.platform.infra.storage.ObjectStorage;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

/** P0.45: contrato de ObjectStorage contra a implementacao local (arquivos). */
class LocalFileObjectStorageContractTest extends ObjectStorageContractTest {

    @TempDir
    Path root;

    @Override
    protected ObjectStorage storage() {
        return new LocalFileObjectStorage(root);
    }
}

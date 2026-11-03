package br.com.ziipsan.platform.infra;

import br.com.ziipsan.platform.infra.storage.ObjectStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P0.45: suite de contrato de {@link ObjectStorage}. Qualquer implementacao — local,
 * emulador de S3 ou, na LC.1, S3 gerenciado — deve passar nesta suite. As subclasses
 * concretas (ex.: {@code LocalFileObjectStorageContractTest}) so fornecem a instancia.
 */
public abstract class ObjectStorageContractTest {

    protected abstract ObjectStorage storage();

    @Test
    @DisplayName("put + get devolve os mesmos bytes; exists true; delete apaga")
    void putGetExistsDelete() throws Exception {
        var storage = storage();
        byte[] content = "conteudo-sintetico".getBytes(StandardCharsets.UTF_8);
        storage.put("docs", "a/b.txt", new ByteArrayInputStream(content), "text/plain");
        assertThat(storage.exists("docs", "a/b.txt")).isTrue();
        try (var in = storage.get("docs", "a/b.txt").orElseThrow()) {
            assertThat(in.readAllBytes()).isEqualTo(content);
        }
        storage.delete("docs", "a/b.txt");
        assertThat(storage.exists("docs", "a/b.txt")).isFalse();
        assertThat(storage.get("docs", "a/b.txt")).isEmpty();
    }

    @Test
    @DisplayName("get/exists de inexistente nao falha; delete de inexistente e idempotente")
    void missingIsNotAnError() {
        var storage = storage();
        assertThat(storage.get("docs", "nao-existe.txt")).isEmpty();
        assertThat(storage.exists("docs", "nao-existe.txt")).isFalse();
        storage.delete("docs", "nao-existe.txt");
    }

    @Test
    @DisplayName("put sobrescreve; chave com .. e recusada (sem fuga do bucket)")
    void overwriteAndTraversalRefused() {
        var storage = storage();
        storage.put("docs", "x.txt", new ByteArrayInputStream("v1".getBytes(StandardCharsets.UTF_8)), null);
        storage.put("docs", "x.txt", new ByteArrayInputStream("v2".getBytes(StandardCharsets.UTF_8)), null);
        assertThatThrownBy(() -> storage.put("docs", "../fora.txt",
                new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

package br.com.ziipsan.platform.contract;

import br.com.ziipsan.platform.infra.storage.ObjectStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Resolvedor de producao das referencias SaaS (P2.14): forma
 * {@code object:<chave>[#sha256=<hex64>]}, tipo pelo sufixo da chave,
 * forma desconhecida ou objeto ausente resolve para vazio (nunca
 * excecao, nunca byte errado).
 */
class ObjectStorageSaaSContractDocumentResolverTest {

    private static final String BUCKET = "ziipsan-saas-docs";

    private static ObjectStorage memoryStorage(Map<String, byte[]> objects) {
        return new ObjectStorage() {
            @Override
            public void put(String bucket, String key, InputStream content, String contentType) {
                try {
                    objects.put(bucket + "/" + key, content.readAllBytes());
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }

            @Override
            public Optional<InputStream> get(String bucket, String key) {
                byte[] bytes = objects.get(bucket + "/" + key);
                return bytes == null ? Optional.empty() : Optional.of(new ByteArrayInputStream(bytes));
            }

            @Override
            public boolean exists(String bucket, String key) {
                return objects.containsKey(bucket + "/" + key);
            }

            @Override
            public void delete(String bucket, String key) {
                objects.remove(bucket + "/" + key);
            }
        };
    }

    @Test
    @DisplayName("ref com hash: resolve bytes, tipo pdf e hash registrado")
    void resolvesPdfRefWithHash() {
        Map<String, byte[]> objects = new HashMap<>();
        objects.put(BUCKET + "/faturas/fat-1.pdf", "%PDF-fatura".getBytes());
        var resolver = new ObjectStorageSaaSContractDocumentResolver(memoryStorage(objects), BUCKET);
        String hash = "a".repeat(64);
        Optional<SaaSContractDocumentResolver.ResolvedDocument> resolved =
                resolver.resolve("object:faturas/fat-1.pdf#sha256=" + hash);
        assertThat(resolved).isPresent();
        assertThat(resolved.get().content()).isEqualTo("%PDF-fatura".getBytes());
        assertThat(resolved.get().contentType()).isEqualTo("application/pdf");
        assertThat(resolved.get().filename()).isEqualTo("fat-1.pdf");
        assertThat(resolved.get().sha256Hex()).isEqualTo(hash);
    }

    @Test
    @DisplayName("ref xml sem hash: resolve com sha256 nulo (sem conferencia possivel)")
    void resolvesXmlRefWithoutHash() {
        Map<String, byte[]> objects = new HashMap<>();
        objects.put(BUCKET + "/nfse/nfse-1.xml", "<nfse/>".getBytes());
        var resolver = new ObjectStorageSaaSContractDocumentResolver(memoryStorage(objects), BUCKET);
        Optional<SaaSContractDocumentResolver.ResolvedDocument> resolved =
                resolver.resolve("object:nfse/nfse-1.xml");
        assertThat(resolved).isPresent();
        assertThat(resolved.get().contentType()).isEqualTo("application/xml");
        assertThat(resolved.get().sha256Hex()).isNull();
    }

    @Test
    @DisplayName("forma desconhecida, hash malformado, chave vazia e objeto ausente: vazio")
    void unknownFormsResolveToEmpty() {
        Map<String, byte[]> objects = new HashMap<>();
        objects.put(BUCKET + "/faturas/fat-1.pdf", "%PDF".getBytes());
        var resolver = new ObjectStorageSaaSContractDocumentResolver(memoryStorage(objects), BUCKET);
        assertThat(resolver.resolve("s3://bucket/fat-1.pdf")).isEmpty();
        assertThat(resolver.resolve("object:faturas/fat-1.pdf#sha256=xyz")).isEmpty();
        assertThat(resolver.resolve("object:faturas/fat-1.pdf#sha256=" + "a".repeat(63))).isEmpty();
        assertThat(resolver.resolve("object:")).isEmpty();
        assertThat(resolver.resolve(null)).isEmpty();
        assertThat(resolver.resolve("object:faturas/inexistente.pdf")).isEmpty();
    }
}

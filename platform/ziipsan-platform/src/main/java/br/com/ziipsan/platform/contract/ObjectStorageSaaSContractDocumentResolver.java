package br.com.ziipsan.platform.contract;

import br.com.ziipsan.platform.infra.storage.ObjectStorage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Optional;

/**
 * Resolvedor de producao das referencias de documento SaaS (P2.14).
 *
 * <p>Convencao proposta por esta frente (a P2.13 confirma ou ajusta na
 * integracao — ver o relatorio da P2.14; ate la, referencia da NFS-e
 * em outra forma resolve para vazio e o download responde 404):
 * {@code object:<chave>[#sha256=<hex64>]}. A chave e a chave do
 * objeto no bucket de documentos SaaS (sem {@code ..}, sem barra
 * inicial — a validacao e do {@link ObjectStorage}); o sufixo
 * {@code #sha256=} e o hash registrado no momento da autorizacao.
 * O tipo vem do sufixo da chave ({@code .pdf} ou {@code .xml});
 * outro sufixo resolve para {@code application/octet-stream} e o
 * servico o recusa quando o formato pedido nao bate.
 *
 * <p>Sem anotacao Spring de proposito (mesmo padrao do
 * {@code DocumentService} da P1.20): a fiação vive em
 * {@code TenantContractConfig}, na app.
 */
public final class ObjectStorageSaaSContractDocumentResolver implements SaaSContractDocumentResolver {

    private static final String PREFIX = "object:";
    private static final String HASH_MARKER = "#sha256=";

    private final ObjectStorage storage;
    private final String bucket;

    public ObjectStorageSaaSContractDocumentResolver(ObjectStorage storage, String bucket) {
        if (storage == null || bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("armazenamento e bucket sao obrigatorios");
        }
        this.storage = storage;
        this.bucket = bucket;
    }

    @Override
    public Optional<ResolvedDocument> resolve(String documentRef) {
        if (documentRef == null || !documentRef.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String rest = documentRef.substring(PREFIX.length());
        String key = rest;
        String sha256 = null;
        int hashAt = rest.indexOf(HASH_MARKER);
        if (hashAt >= 0) {
            key = rest.substring(0, hashAt);
            sha256 = rest.substring(hashAt + HASH_MARKER.length()).toLowerCase(Locale.ROOT);
            if (!sha256.matches("[0-9a-f]{64}")) {
                return Optional.empty();
            }
        }
        if (key.isBlank()) {
            return Optional.empty();
        }
        Optional<InputStream> found;
        try {
            found = storage.get(bucket, key);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (found.isEmpty()) {
            return Optional.empty();
        }
        byte[] content;
        try (InputStream in = found.get()) {
            content = in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("falha ao ler o documento SaaS", e);
        }
        String filename = key.contains("/") ? key.substring(key.lastIndexOf('/') + 1) : key;
        String contentType = key.toLowerCase(Locale.ROOT).endsWith(".pdf")
                ? "application/pdf"
                : key.toLowerCase(Locale.ROOT).endsWith(".xml")
                        ? "application/xml"
                        : "application/octet-stream";
        return Optional.of(new ResolvedDocument(content, contentType, filename, sha256));
    }
}

package br.com.ziipsan.platform.contract;

import br.com.ziipsan.platform.infra.storage.ObjectStorage;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Resolvedor de producao das referencias de documento SaaS (P2.14).
 *
 * <p>Convencao proposta por esta frente (confirmada na integracao P2-A):
 * {@code object:<chave>[#sha256=<hex64>]}. A chave e a chave do
 * objeto no bucket de documentos SaaS (sem {@code ..}, sem barra
 * inicial — a validacao e do {@link ObjectStorage}); o sufixo
 * {@code #sha256=} e o hash registrado no momento da autorizacao.
 * O tipo vem do sufixo da chave ({@code .pdf} ou {@code .xml});
 * outro sufixo resolve para {@code application/octet-stream} e o
 * servico o recusa quando o formato pedido nao bate.
 *
 * <p>Ponte P2-A (P2.13 ↔ P2.14): a referencia da NFS-e que a P2.13
 * grava e o Id da DPS (42 posicoes, {@code [0-9A-Z]{42}}), nao a
 * forma {@code object:}. Quando a referencia tem essa forma e o
 * resolvedor foi construido com acesso ao banco, ela resolve pela
 * linha {@code platform.saas_nfse} (só {@code AUTHORIZED} com XML):
 * o XML autorizado (gzip) e descomprimido e servido como
 * {@code application/xml} com o {@code xml_sha256} registrado — que
 * o servico confere contra os bytes lidos (fail-closed), exatamente
 * como no caminho {@code object:}. A migration da P2.13 ja previa
 * este leitor ("o download da P2.14 le daqui"). Sem acesso ao banco
 * (construtor de 2 argumentos), a forma DPS resolve para vazio.
 *
 * <p>Sem anotacao Spring de proposito (mesmo padrao do
 * {@code DocumentService} da P1.20): a fiação vive em
 * {@code TenantContractConfig}, na app.
 */
public final class ObjectStorageSaaSContractDocumentResolver implements SaaSContractDocumentResolver {

    private static final String PREFIX = "object:";
    private static final String HASH_MARKER = "#sha256=";
    private static final Pattern DPS_ID = Pattern.compile("[0-9A-Z]{42}");

    private final ObjectStorage storage;
    private final String bucket;
    private final JdbcTemplate jdbc;

    public ObjectStorageSaaSContractDocumentResolver(ObjectStorage storage, String bucket) {
        this(storage, bucket, null);
    }

    public ObjectStorageSaaSContractDocumentResolver(ObjectStorage storage, String bucket, JdbcTemplate jdbc) {
        if (storage == null || bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("armazenamento e bucket sao obrigatorios");
        }
        this.storage = storage;
        this.bucket = bucket;
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ResolvedDocument> resolve(String documentRef) {
        if (documentRef == null) {
            return Optional.empty();
        }
        if (documentRef.startsWith(PREFIX)) {
            return resolveObject(documentRef);
        }
        return resolveDps(documentRef);
    }

    private Optional<ResolvedDocument> resolveObject(String documentRef) {
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

    /**
     * Ponte P2-A: referencia da NFS-e na forma Id da DPS (P2.13). O
     * escopo do tenant ja foi conferido antes de chegar aqui (o
     * servico so resolve a referencia da cobrança do proprio tenant),
     * por isso a busca e so pela DPS — e, por defesa, so serve XML
     * de NFS-e {@code AUTHORIZED} com gzip presente. O hash devolvido
     * e o {@code xml_sha256} registrado na autorizacao; o servico o
     * confere contra os bytes descomprimidos e recusa em divergencia.
     */
    private Optional<ResolvedDocument> resolveDps(String documentRef) {
        if (jdbc == null || !DPS_ID.matcher(documentRef).matches()) {
            return Optional.empty();
        }
        return jdbc.query(
                        "SELECT xml_gzip, xml_sha256 FROM platform.saas_nfse"
                                + " WHERE dps_id = ? AND status = 'AUTHORIZED' AND xml_gzip IS NOT NULL",
                        (rs, i) -> {
                            byte[] gzip = rs.getBytes(1);
                            String sha256 = rs.getString(2);
                            return new ResolvedDocument(gunzip(gzip), "application/xml",
                                    "nfse-" + documentRef + ".xml", sha256);
                        },
                        documentRef)
                .stream().findFirst();
    }

    private static byte[] gunzip(byte[] gzip) {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gzip))) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("falha ao descomprimir o XML da NFS-e", e);
        }
    }
}

package br.com.ziipsan.platform.infra.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * P0.45: implementacao local de {@link ObjectStorage} sobre o sistema de arquivos.
 * Layout: {@code <raiz>/<bucket>/<chave>}. Usada nos testes de contrato e no
 * ambiente local quando o emulador de S3 nao esta ativo; o emulador de S3 do compose
 * (SeaweedFS, perfil {@code storage}) e o alvo de homologacao local, via
 * {@link S3ObjectStorage}. Nada aqui sai da maquina.
 */
public final class LocalFileObjectStorage implements ObjectStorage {

    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    private final Path root;

    public LocalFileObjectStorage(Path root) {
        if (root == null) {
            throw new IllegalArgumentException("raiz do armazenamento local e obrigatoria");
        }
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public void put(String bucket, String key, InputStream content, String contentType) {
        if (content == null) {
            throw new IllegalArgumentException("conteudo obrigatorio");
        }
        Path target = resolve(bucket, key);
        try {
            Files.createDirectories(target.getParent());
            Path tmp = Files.createTempFile(target.getParent(), ".put-", ".tmp");
            try {
                Files.copy(content, tmp, StandardCopyOption.REPLACE_EXISTING);
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("falha ao gravar objeto " + bucket + "/" + key, e);
        }
    }

    @Override
    public Optional<InputStream> get(String bucket, String key) {
        Path target = resolve(bucket, key);
        if (!Files.isRegularFile(target)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.newInputStream(target));
        } catch (IOException e) {
            throw new UncheckedIOException("falha ao ler objeto " + bucket + "/" + key, e);
        }
    }

    @Override
    public boolean exists(String bucket, String key) {
        return Files.isRegularFile(resolve(bucket, key));
    }

    @Override
    public void delete(String bucket, String key) {
        try {
            Files.deleteIfExists(resolve(bucket, key));
        } catch (IOException e) {
            throw new UncheckedIOException("falha ao apagar objeto " + bucket + "/" + key, e);
        }
    }

    /** Resolve dentro da raiz; qualquer tentativa de fuga ({@code ..}) e recusada. */
    private Path resolve(String bucket, String key) {
        validateBucket(bucket);
        if (key == null || key.isBlank() || key.startsWith("/") || key.contains("\\")) {
            throw new IllegalArgumentException("chave de objeto invalida: " + key);
        }
        Path resolved = root.resolve(bucket).normalize();
        for (String segment : key.split("/")) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")
                    || !SEGMENT.matcher(segment).matches()) {
                throw new IllegalArgumentException("chave de objeto invalida: " + key);
            }
            resolved = resolved.resolve(segment);
        }
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("chave de objeto fora da raiz: " + key);
        }
        return resolved;
    }

    private static void validateBucket(String bucket) {
        if (bucket == null || bucket.isBlank() || !SEGMENT.matcher(bucket).matches()
                || bucket.equals(".") || bucket.equals("..")) {
            throw new IllegalArgumentException("bucket invalido: " + bucket);
        }
    }
}

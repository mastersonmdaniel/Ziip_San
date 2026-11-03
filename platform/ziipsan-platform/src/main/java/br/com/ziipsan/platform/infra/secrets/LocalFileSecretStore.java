package br.com.ziipsan.platform.infra.secrets;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Optional;
import java.util.Set;

/**
 * P0.45: cofre local sobre o sistema de arquivos. Layout: {@code <raiz>/<chave>} com
 * um arquivo por segredo. A raiz deve ficar FORA do repositorio (ex.:
 * {@code $HOME/.ziipsan-vault} ou o volume {@code vault-data} do compose); segredos
 * nunca sao escritos em log. Em sistemas POSIX o arquivo nasce com 0600; em outros,
 * o diretorio continua sendo responsabilidade de quem opera o ambiente (documentado
 * em docs/operacao/local-env.md). A KEK do cofre local vive aqui; a KEK gerenciada
 * (KMS) e da LC.4.
 */
public final class LocalFileSecretStore implements SecretStore {

    private static final Set<PosixFilePermission> OWNER_ONLY =
            Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    private final Path root;

    public LocalFileSecretStore(Path root) {
        if (root == null) {
            throw new IllegalArgumentException("raiz do cofre local e obrigatoria");
        }
        this.root = root.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new UncheckedIOException("falha ao criar a raiz do cofre local", e);
        }
    }

    @Override
    public void put(String key, byte[] value) {
        if (value == null) {
            throw new IllegalArgumentException("valor do segredo e obrigatorio");
        }
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Path tmp = Files.createTempFile(target.getParent(), ".secret-", ".tmp");
            try {
                Files.write(tmp, value);
                restrict(tmp);
                Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                restrict(target);
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("falha ao gravar segredo (chave nao registrada no log)", e);
        }
    }

    @Override
    public Optional<byte[]> get(String key) {
        Path target = resolve(key);
        if (!Files.isRegularFile(target)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(target));
        } catch (IOException e) {
            throw new UncheckedIOException("falha ao ler segredo (chave nao registrada no log)", e);
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.isRegularFile(resolve(key));
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("falha ao apagar segredo (chave nao registrada no log)", e);
        }
    }

    private Path resolve(String key) {
        if (key == null || key.isBlank() || key.startsWith("/") || key.contains("\\")) {
            throw new IllegalArgumentException("chave de segredo invalida");
        }
        Path resolved = root;
        for (String segment : key.split("/")) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("chave de segredo invalida");
            }
            resolved = resolved.resolve(segment);
        }
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("chave de segredo fora da raiz");
        }
        return resolved;
    }

    private static void restrict(Path file) {
        try {
            Files.setPosixFilePermissions(file, OWNER_ONLY);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Sistema sem POSIX (ex.: Windows): a restricao fica a cargo do diretorio do
            // ambiente, documentado no roteiro do ambiente local. Nunca falhar a escrita
            // por causa disso — o segredo ja estaria perdido se a escrita falhasse aqui.
        }
    }
}

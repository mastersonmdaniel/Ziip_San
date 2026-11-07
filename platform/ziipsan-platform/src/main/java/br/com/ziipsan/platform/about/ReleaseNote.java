package br.com.ziipsan.platform.about;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Novidade de uma versão do produto (P1.24, ADR-0021), como publicada em
 * {@code content/release-notes/<version>.yaml} (schema da P0.41).
 *
 * <p>O campo {@code affects} é a lista de tenants (UUID) a quem a novidade se
 * aplica — ADR-0021, decisão 5. Ele é <b>obrigatório</b>: novidade sem
 * {@code affects} não existe (ausência = erro explícito, nunca "vale para
 * todos" por presunção). Quem decide a exibição é
 * {@link ReleaseNoteFilter}: a novidade só aparece para um tenant se o
 * {@code affects} o inclui (P2.16). Uma lista vazia é válida e significa
 * "ainda não se aplica a nenhum tenant" — a novidade não aparece para
 * ninguém até o {@code affects} ser preenchido na publicação.
 *
 * @param version SemVer {@code X.Y.Z}, o mesmo padrão de
 *                {@code support.release_note_view} (V9_5)
 * @param title   título da novidade, como publicado
 * @param body    texto da novidade, como publicado
 * @param affects tenants a quem a novidade se aplica (nunca {@code null})
 */
public record ReleaseNote(String version, String title, String body, List<UUID> affects) {

    private static final Pattern SEMVER = Pattern.compile("^[0-9]+\\.[0-9]+\\.[0-9]+$");

    public ReleaseNote {
        if (version == null || !SEMVER.matcher(version).matches()) {
            throw new IllegalArgumentException(
                    "Novidade exige versão SemVer X.Y.Z (padrão de support.release_note_view, V9_5)");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Novidade exige título");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Novidade exige texto");
        }
        Objects.requireNonNull(affects,
                "Novidade exige o campo affects (ADR-0021 decisão 5): ausência não presume 'todos os tenants'");
        affects = List.copyOf(affects);
    }

    /** {@code true} se o {@code affects} inclui o tenant. */
    public boolean affectsTenant(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenant é obrigatório para decidir a exibição da novidade");
        return affects.contains(tenantId);
    }
}

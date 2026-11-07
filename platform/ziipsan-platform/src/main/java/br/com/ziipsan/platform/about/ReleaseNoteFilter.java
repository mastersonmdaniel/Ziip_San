package br.com.ziipsan.platform.about;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Filtro de novidades por tenant da P2.16 (ADR-0021, decisão 5), sobre as
 * novidades da P1.24: uma novidade só aparece para o tenant se o campo
 * {@code affects} dela o inclui.
 *
 * <p>Regra pura, sem leitura de dados: quem chama passa as novidades
 * publicadas (de {@code content/release-notes}) e o tenant da sessão; a
 * exibição "uma vez por versão por usuário" continua sendo a da P1.24
 * ({@code support.release_note_view}, V9_5) — este filtro decide
 * <i>quais</i> novidades entram naquela exibição para este tenant, sem mudar
 * a tabela nem o registro de visualização.
 */
public final class ReleaseNoteFilter {

    private ReleaseNoteFilter() {
    }

    /**
     * As novidades visíveis para o tenant, na ordem de publicação recebida:
     * exatamente as cujo {@code affects} inclui o tenant. Novidade cujo
     * {@code affects} não inclui o tenant não aparece para ele — nem como
     * item, nem como contagem (a lista devolvida simplesmente não a contém).
     */
    public static List<ReleaseNote> visibleFor(UUID tenantId, List<ReleaseNote> published) {
        Objects.requireNonNull(tenantId, "tenant é obrigatório para filtrar novidades (P2.16)");
        Objects.requireNonNull(published, "lista de novidades publicadas é obrigatória");
        return published.stream()
                .filter(note -> note.affectsTenant(tenantId))
                .toList();
    }
}

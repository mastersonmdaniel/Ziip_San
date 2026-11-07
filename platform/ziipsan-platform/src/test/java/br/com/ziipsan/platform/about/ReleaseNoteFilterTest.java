package br.com.ziipsan.platform.about;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Aceite da P2.16 (ADR-0021 decisão 5): novidade cujo {@code affects} não
 * inclui o tenant não aparece para ele.
 */
class ReleaseNoteFilterTest {

    private static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static ReleaseNote note(String version, UUID... affects) {
        return new ReleaseNote(version, "Novidade " + version, "Texto da novidade " + version + ".",
                List.of(affects));
    }

    @Test
    @DisplayName("novidade cujo affects não inclui o tenant não aparece para ele")
    void noteForAnotherTenantDoesNotAppear() {
        ReleaseNote onlyB = note("1.1.0", TENANT_B);
        assertThat(ReleaseNoteFilter.visibleFor(TENANT_A, List.of(onlyB))).isEmpty();
        assertThat(ReleaseNoteFilter.visibleFor(TENANT_B, List.of(onlyB))).containsExactly(onlyB);
    }

    @Test
    @DisplayName("novidade que afeta o tenant aparece; a dos outros é filtrada, na ordem de publicação")
    void mixedNotesAreFilteredInOrder() {
        ReleaseNote first = note("1.0.0", TENANT_A, TENANT_B);
        ReleaseNote foreign = note("1.1.0", TENANT_B);
        ReleaseNote second = note("1.2.0", TENANT_A);
        assertThat(ReleaseNoteFilter.visibleFor(TENANT_A, List.of(first, foreign, second)))
                .containsExactly(first, second);
    }

    @Test
    @DisplayName("affects vazio não aparece para nenhum tenant (lista vazia não presume 'todos')")
    void emptyAffectsAppearsToNoOne() {
        ReleaseNote nobody = note("2.0.0");
        assertThat(ReleaseNoteFilter.visibleFor(TENANT_A, List.of(nobody))).isEmpty();
        assertThat(ReleaseNoteFilter.visibleFor(TENANT_B, List.of(nobody))).isEmpty();
    }

    @Test
    @DisplayName("affects ausente é erro explícito na construção; tenant nulo é erro no filtro")
    void absenceIsExplicitError() {
        assertThatThrownBy(() -> new ReleaseNote("1.0.0", "Título", "Texto.", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("affects");
        assertThatThrownBy(() -> ReleaseNoteFilter.visibleFor(null, List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tenant");
    }

    @Test
    @DisplayName("versão fora do SemVer X.Y.Z é recusada (mesmo padrão de release_note_view)")
    void nonSemVerVersionIsRejected() {
        assertThatThrownBy(() -> note("1.0", TENANT_A))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SemVer");
    }
}

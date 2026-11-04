package br.com.ziipsan.app.about;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P1.24: loaders do Sobre sem banco e sem Spring.
 *
 * <p>O recurso de teste {@code release-notes/release-notes.json} (versão
 * sintética 9.9.9) e {@code third-party-licenses.json} (componente sintético)
 * vivem em src/test/resources: são eles que o IT da aplicação também lê
 * (test-classes precede classes no classpath), para o fluxo feliz não
 * depender de uma release real — que ainda não existe.
 */
class AboutLoadersTest {

    @Test
    @DisplayName("release-notes de teste: uma nota sintética 9.9.9, ordenada e com entradas")
    void testReleaseNotesLoad() {
        ReleaseNotesLoader loader = new ReleaseNotesLoader();
        assertThat(loader.notes()).hasSize(1);
        ReleaseNotesLoader.ReleaseNote note = loader.notes().get(0);
        assertThat(note.version()).isEqualTo("9.9.9");
        assertThat(note.date()).isEqualTo("2026-11-03");
        assertThat(note.entries()).hasSize(1);
        assertThat(note.entries().get(0).title()).contains("sintética");
        assertThat(loader.note("9.9.9")).isPresent();
        assertThat(loader.note("1.2.3")).isEmpty();
    }

    @Test
    @DisplayName("release-notes ausente é lista vazia, não erro (imagem antes da primeira release)")
    void missingReleaseNotesIsEmpty() {
        ReleaseNotesLoader loader = new ReleaseNotesLoader("release-notes/inexistente.json");
        assertThat(loader.notes()).isEmpty();
    }

    @Test
    @DisplayName("licenças de teste: componente sintético parseado com nome, versão e licença")
    void testLicensesLoad() {
        ThirdPartyLicensesLoader loader = new ThirdPartyLicensesLoader();
        assertThat(loader.licenses().available()).isTrue();
        assertThat(loader.licenses().licenses()).hasSize(1);
        assertThat(loader.licenses().licenses().get(0).name()).contains("sintética");
        assertThat(loader.licenses().licenses().get(0).licenses()).containsExactly("ISC");
    }

    @Test
    @DisplayName("licenças ausentes: available=false com lista vazia, sem derrubar o boot")
    void missingLicensesIsUnavailable() {
        ThirdPartyLicensesLoader loader = new ThirdPartyLicensesLoader("licencas-inexistentes.json");
        assertThat(loader.licenses().available()).isFalse();
        assertThat(loader.licenses().licenses()).isEmpty();
    }

    @Test
    @DisplayName("release-notes malformado derruba o loader (nota errada não aparece pela metade)")
    void malformedReleaseNotesFailsFast() {
        // Recurso de teste inexistente como arquivo válido: o loader de um
        // recurso que não é array deve falhar — exercido via classpath com
        // o próprio third-party-licenses.json (objeto, não array).
        assertThatThrownBy(() -> new ReleaseNotesLoader("third-party-licenses.json"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("release-notes ordenadas da mais recente para a mais antiga, por número e não por texto")
    void releaseNotesSortedNewestFirstNumerically() {
        ReleaseNotesLoader loader = new ReleaseNotesLoader("release-notes/multi.json");
        assertThat(loader.notes()).extracting(ReleaseNotesLoader.ReleaseNote::version)
                .containsExactly("2.0.0", "1.10.0", "1.2.3");
    }
}

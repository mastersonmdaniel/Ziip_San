package br.com.ziipsan.core.certification;

import br.com.ziipsan.core.api.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ED-76: comparacao de clausulas entre edicoes. Dados sinteticos
 * minimos, como em {@link ClauseCurationTest}.
 */
class ClauseMapRevisionTest {

    private static ClauseCuration.ClauseEntry entry(String number, String title, String summary, int order) {
        return new ClauseCuration.ClauseEntry(number, title, summary, order);
    }

    private static ClauseCuration.EditionClauseMap editionOne() {
        return new ClauseCuration.EditionClauseMap("SYN-EXEMPLO-1", "1", List.of(
                entry("1.1", "Titulo ficticio um", "Resumo ficticio um.", 1),
                entry("2.1", "Titulo ficticio dois", "Resumo ficticio dois.", 2),
                entry("3.1", "Titulo ficticio tres", "Resumo ficticio tres.", 3),
                entry("4.1", "Titulo ficticio quatro", "Resumo ficticio quatro.", 4)));
    }

    private static ClauseCuration.EditionClauseMap editionTwo() {
        return new ClauseCuration.EditionClauseMap("SYN-EXEMPLO-1", "2", List.of(
                entry("1.1", "Titulo ficticio um", "Resumo ficticio um.", 1),
                entry("2.1", "Titulo ficticio dois revisto", "Resumo ficticio dois.", 2),
                entry("4.1", "Titulo ficticio quatro", "Resumo ficticio quatro.", 3),
                entry("5.1", "Titulo ficticio cinco", "Resumo ficticio cinco.", 4)));
    }

    @Test
    @DisplayName("a comparação separa incluídas, removidas, alteradas e inalteradas")
    void diffSeparatesAddedRemovedChangedUnchanged() {
        ClauseMapRevision.ClauseMapDiff diff = ClauseMapRevision.compare(editionOne(), editionTwo());

        assertThat(diff.standardCode()).isEqualTo("SYN-EXEMPLO-1");
        assertThat(diff.fromEdition()).isEqualTo("1");
        assertThat(diff.toEdition()).isEqualTo("2");
        assertThat(diff.added()).extracting(ClauseCuration.ClauseEntry::clauseNumber)
                .containsExactly("5.1");
        assertThat(diff.removed()).extracting(ClauseCuration.ClauseEntry::clauseNumber)
                .containsExactly("3.1");
        assertThat(diff.changed()).extracting(ClauseMapRevision.ChangedClause::clauseNumber)
                .containsExactly("2.1");
        assertThat(diff.changed().get(0).before().title()).isEqualTo("Titulo ficticio dois");
        assertThat(diff.changed().get(0).after().title()).isEqualTo("Titulo ficticio dois revisto");
        assertThat(diff.unchanged()).extracting(ClauseCuration.ClauseEntry::clauseNumber)
                .containsExactly("1.1", "4.1");
        assertThat(diff.hasChanges()).isTrue();
    }

    @Test
    @DisplayName("mudança só de ordem não marca cláusula como alterada")
    void sortOrderOnlyChangeIsUnchanged() {
        ClauseCuration.EditionClauseMap reordered = new ClauseCuration.EditionClauseMap(
                "SYN-EXEMPLO-1", "2", List.of(
                        entry("1.1", "Titulo ficticio um", "Resumo ficticio um.", 7),
                        entry("2.1", "Titulo ficticio dois", "Resumo ficticio dois.", 8),
                        entry("3.1", "Titulo ficticio tres", "Resumo ficticio tres.", 9),
                        entry("4.1", "Titulo ficticio quatro", "Resumo ficticio quatro.", 10)));
        ClauseMapRevision.ClauseMapDiff diff = ClauseMapRevision.compare(editionOne(), reordered);
        assertThat(diff.hasChanges()).isFalse();
        assertThat(diff.unchanged()).hasSize(4);
    }

    @Test
    @DisplayName("comparar edições da mesma edição não produz mudança")
    void sameMapHasNoChanges() {
        ClauseMapRevision.ClauseMapDiff diff = ClauseMapRevision.compare(editionOne(), editionOne());
        assertThat(diff.hasChanges()).isFalse();
        assertThat(diff.added()).isEmpty();
        assertThat(diff.removed()).isEmpty();
        assertThat(diff.changed()).isEmpty();
    }

    @Test
    @DisplayName("diff montado com lista nula é recusado com erro claro, não NPE")
    void diffRefusesNullLists() {
        assertThatThrownBy(() -> new ClauseMapRevision.ClauseMapDiff("SYN-EXEMPLO-1", "1", "2",
                null, List.of(), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClauseMapRevision.ClauseMapDiff("SYN-EXEMPLO-1", "1", "2",
                List.of(), List.of(), null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("comparar edições de normas diferentes é recusado")
    void differentStandardsAreRefused() {
        ClauseCuration.EditionClauseMap other = new ClauseCuration.EditionClauseMap(
                "SYN-EXEMPLO-2", "1", List.of(entry("1.1", "Titulo ficticio", null, 1)));
        assertThatThrownBy(() -> ClauseMapRevision.compare(editionOne(), other))
                .isInstanceOf(ApiException.Validation.class);
    }
}

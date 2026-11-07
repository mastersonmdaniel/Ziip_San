package br.com.ziipsan.core.restore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes puros da comparacao extraido x atual (P3.33). Hashes de
 * exemplo: 64 hex minusculos quaisquer, distintos por tabela.
 */
class RestoreDiffTest {

    private static final String H1 = "a".repeat(64);
    private static final String H2 = "b".repeat(64);
    private static final String H3 = "c".repeat(64);

    private static RestoreDiff.TableSnapshot snap(String table, long rows, String hash) {
        return new RestoreDiff.TableSnapshot(table, rows, hash);
    }

    @Test
    @DisplayName("mesmo hash = UNCHANGED; hash diverso = RESTORE; so num lado = ONLY_IN_*; resumo conta por acao")
    void compareClassifies() {
        List<RestoreDiff.TableDiff> diff = RestoreDiff.compare(
                List.of(snap("t_igual", 10, H1), snap("t_mudou", 5, H2), snap("t_so_ext", 3, H3)),
                List.of(snap("t_igual", 10, H1), snap("t_mudou", 7, H3), snap("t_so_atual", 1, H1)));
        assertThat(diff).extracting(RestoreDiff.TableDiff::action)
                .containsExactly(RestoreDiff.TableAction.UNCHANGED,
                        RestoreDiff.TableAction.RESTORE,
                        RestoreDiff.TableAction.ONLY_IN_CURRENT,
                        RestoreDiff.TableAction.ONLY_IN_EXTRACTED);
        assertThat(RestoreDiff.hasChanges(diff)).isTrue();
        assertThat(RestoreDiff.summary(diff)).hasSize(4)
                .containsEntry("UNCHANGED", 1L).containsEntry("RESTORE", 1L)
                .containsEntry("ONLY_IN_EXTRACTED", 1L).containsEntry("ONLY_IN_CURRENT", 1L);
    }

    @Test
    @DisplayName("mesmo hash com contagem diversa nao e UNCHANGED: fotografia inconsistente entra como RESTORE")
    void sameHashDifferentRowsIsRestore() {
        List<RestoreDiff.TableDiff> diff = RestoreDiff.compare(
                List.of(snap("t", 5, H1)), List.of(snap("t", 9, H1)));
        assertThat(diff).singleElement()
                .extracting(RestoreDiff.TableDiff::action)
                .isEqualTo(RestoreDiff.TableAction.RESTORE);
    }

    @Test
    @DisplayName("fotografias identicas nao tem mudanca")
    void identicalHasNoChanges() {
        List<RestoreDiff.TableDiff> diff = RestoreDiff.compare(
                List.of(snap("t", 1, H1)), List.of(snap("t", 1, H1)));
        assertThat(RestoreDiff.hasChanges(diff)).isFalse();
    }

    @Test
    @DisplayName("tabela criada depois do alvo (ONLY_IN_CURRENT) nunca e proposta para remocao: a acao nao e RESTORE nem exclusao")
    void currentOnlyTableIsNeverDropped() {
        List<RestoreDiff.TableDiff> diff = RestoreDiff.compare(
                List.of(), List.of(snap("t_nova", 42, H1)));
        assertThat(diff).singleElement()
                .extracting(RestoreDiff.TableDiff::action)
                .isEqualTo(RestoreDiff.TableAction.ONLY_IN_CURRENT);
    }

    @Test
    @DisplayName("sem hash valido nao ha comparacao; tabela repetida na mesma fotografia e erro")
    void invalidSnapshotsRefused() {
        assertThatThrownBy(() -> snap("t", 1, "nao-e-hash"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RestoreDiff.compare(
                List.of(snap("t", 1, H1), snap("t", 2, H2)), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

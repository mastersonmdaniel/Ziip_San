package br.com.ziipsan.core.certification;

import br.com.ziipsan.core.api.ApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Revisao do mapa de clausulas a cada nova edicao (ED-76): a comparacao
 * entre o mapa da edicao anterior e o mapa da edicao nova de uma mesma
 * norma.
 *
 * <p>A identidade de uma clausula entre edicoes e o seu numero — e o que
 * a V9_8 garante unico por edicao ({@code uq_cert_clause_edition_number})
 * e o que o mapa do tenant copia (V21_1). Clausula presente nas duas
 * edicoes com titulo ou resumo em palavras proprias diferentes e
 * ALTERADA; so de um lado, e INCLUIDA (nova) ou REMOVIDA. Mudanca so de
 * {@code sortOrder} nao marca alteracao: reordenar sem mudar o conteudo
 * em palavras proprias nao pede revalidacao de evidencia nenhuma — a
 * revalidacao nasce do conteudo, nao da posicao.
 *
 * <p>Comparar edicoes de normas diferentes e recusado: um diff entre
 * normas nao tem significado e produziria uma lista de revalidacao
 * falsa. O resultado alimenta {@link EvidenceRevalidation}, que cruza o
 * diff com os vinculos de evidencia dos tenants.
 */
public final class ClauseMapRevision {

    private ClauseMapRevision() {
    }

    /** Uma clausula presente nas duas edicoes, com o conteudo em palavras proprias mudado. */
    public record ChangedClause(String clauseNumber,
                                ClauseCuration.ClauseEntry before,
                                ClauseCuration.ClauseEntry after) {
    }

    /**
     * O resultado da comparacao: incluidas, removidas, alteradas e as que
     * ficaram iguais. As quatro listas sao disjuntas e cobrem todas as
     * clausulas das duas edicoes.
     */
    public record ClauseMapDiff(String standardCode, String fromEdition, String toEdition,
                                List<ClauseCuration.ClauseEntry> added,
                                List<ClauseCuration.ClauseEntry> removed,
                                List<ChangedClause> changed,
                                List<ClauseCuration.ClauseEntry> unchanged) {

        public ClauseMapDiff {
            if (added == null || removed == null || changed == null || unchanged == null) {
                throw new IllegalArgumentException("diff lists are required");
            }
            added = List.copyOf(added);
            removed = List.copyOf(removed);
            changed = List.copyOf(changed);
            unchanged = List.copyOf(unchanged);
        }

        /** Verdadeiro quando a nova edicao muda alguma coisa no mapa. */
        public boolean hasChanges() {
            return !added.isEmpty() || !removed.isEmpty() || !changed.isEmpty();
        }
    }

    /**
     * Compara o mapa da edicao anterior com o da edicao nova da MESMA
     * norma. As listas saem na ordem do mapa novo (incluidas e alteradas)
     * e do mapa anterior (removidas), para a revisao ler como a curadoria
     * escreveu.
     */
    public static ClauseMapDiff compare(ClauseCuration.EditionClauseMap before,
                                        ClauseCuration.EditionClauseMap after) {
        if (before == null || after == null) {
            throw new IllegalArgumentException("before and after maps are required");
        }
        if (!before.standardCode().equals(after.standardCode())) {
            throw new ApiException.Validation(
                    "A comparação de cláusulas exige duas edições da mesma norma.",
                    List.of(new ApiException.FieldError("standardCode", "mismatch",
                            "A comparação de cláusulas exige duas edições da mesma norma: "
                                    + before.standardCode() + " × " + after.standardCode() + ".")),
                    List.of());
        }
        List<ClauseCuration.ClauseEntry> added = new ArrayList<>();
        List<ClauseCuration.ClauseEntry> removed = new ArrayList<>();
        List<ChangedClause> changed = new ArrayList<>();
        List<ClauseCuration.ClauseEntry> unchanged = new ArrayList<>();
        for (ClauseCuration.ClauseEntry entry : after.clauses()) {
            ClauseCuration.ClauseEntry previous = before.clause(entry.clauseNumber());
            if (previous == null) {
                added.add(entry);
            } else if (!Objects.equals(previous.title(), entry.title())
                    || !Objects.equals(previous.summary(), entry.summary())) {
                changed.add(new ChangedClause(entry.clauseNumber(), previous, entry));
            } else {
                unchanged.add(entry);
            }
        }
        for (ClauseCuration.ClauseEntry entry : before.clauses()) {
            if (after.clause(entry.clauseNumber()) == null) {
                removed.add(entry);
            }
        }
        return new ClauseMapDiff(before.standardCode(), before.edition(), after.edition(),
                added, removed, changed, unchanged);
    }
}

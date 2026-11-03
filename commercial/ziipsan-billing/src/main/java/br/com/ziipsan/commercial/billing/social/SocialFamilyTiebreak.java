package br.com.ziipsan.commercial.billing.social;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Desempate de uma economia por familia (P1.5; relatorio 08, R-14; regra da NR 13 a
 * confirmar no catalogo {@code social.family_tiebreak}, classe d). Ordem aplicada, da
 * mais forte para a mais fraca:
 * <ol>
 *   <li>responsavel no CadUnico;</li>
 *   <li>mesmo endereco da familia;</li>
 *   <li>titular da familia;</li>
 *   <li>ligacao mais recente.</li>
 * </ol>
 * <p>Empate total e resolvido pelo identificador da unidade, so para o resultado ser
 * deterministico: nao e regra normativa, e o empate total indica cadastro a revisar.
 * Todos os campos sao identificadores e flags; nenhum dado pessoal em claro passa aqui.
 */
public final class SocialFamilyTiebreak {

    /** Uma unidade candidata a ser a economia beneficiada da familia. */
    public record Candidate(UUID unitId, boolean cadunicoResponsible, boolean sameAddress,
                            boolean familyHead, LocalDate connectionDate) {
        public Candidate {
            if (unitId == null) {
                throw new IllegalArgumentException("Candidato sem unidade");
            }
        }
    }

    // Cada criterio usa a sua propria ordem invertida: um reversed() depois de
    // thenComparing inverteria a cadeia inteira, nao so o criterio novo.
    private static final Comparator<Candidate> ORDER = Comparator
            .comparing(SocialFamilyTiebreak.Candidate::cadunicoResponsible, Comparator.reverseOrder())
            .thenComparing(SocialFamilyTiebreak.Candidate::sameAddress, Comparator.reverseOrder())
            .thenComparing(SocialFamilyTiebreak.Candidate::familyHead, Comparator.reverseOrder())
            .thenComparing(SocialFamilyTiebreak.Candidate::connectionDate,
                    Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(c -> c.unitId().toString());

    private SocialFamilyTiebreak() {
    }

    /** A unidade vencedora; vazio quando nao ha candidatos. */
    public static java.util.Optional<Candidate> pick(List<Candidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(candidates.stream().sorted(ORDER).findFirst().orElseThrow());
    }
}

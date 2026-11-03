package br.com.ziipsan.core.parameters;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Regras de uma versão de parâmetro (P0.19), espelhadas nos CHECKs e no EXCLUDE da V1_5.
 *
 * <p>Esta classe existe para que as regras sejam testáveis sem banco; o banco continua
 * sendo a trava final (CHECK e EXCLUDE recusam o que passar daqui). Nada aqui persiste:
 * a versão aprovada e o aceite vivem em {@code parameter_versions} /
 * {@code parameter_acceptances}.
 */
public final class ParameterVersionRule {

    private ParameterVersionRule() {
    }

    /**
     * Vigência fechada ou aberta: {@code validTo == null} significa "sem fim".
     * Dois intervalos se sobrepõem quando se intersectam, com fim incluso
     * (o daterange da V1_5 usa {@code '[]'}).
     */
    public record Validity(LocalDate from, LocalDate to) {
        public Validity {
            if (from == null) {
                throw new IllegalArgumentException("valid_from é obrigatório");
            }
            if (to != null && to.isBefore(from)) {
                throw new IllegalArgumentException("valid_to não pode ser anterior a valid_from");
            }
        }

        public boolean overlaps(Validity other) {
            LocalDate thisEnd = to != null ? to : LocalDate.MAX;
            LocalDate otherEnd = other.to() != null ? other.to() : LocalDate.MAX;
            return !from.isAfter(otherEnd) && !other.from().isAfter(thisEnd);
        }

        public boolean covers(LocalDate date) {
            return !date.isBefore(from) && (to == null || !date.isAfter(to));
        }
    }

    /**
     * Quem propõe não aprova; aprovação exige data; efeito só futuro
     * ({@code validFrom >= data da aprovação}).
     */
    public static void validateApproval(UUID proposedBy, UUID approvedBy,
                                        LocalDate approvedOn, LocalDate validFrom) {
        if (proposedBy == null) {
            throw new IllegalArgumentException("Quem propõe é obrigatório");
        }
        if (approvedBy == null) {
            throw new IllegalArgumentException("Aprovação exige aprovador");
        }
        if (approvedBy.equals(proposedBy)) {
            throw new IllegalArgumentException("Quem propõe não aprova o próprio parâmetro");
        }
        if (approvedOn == null) {
            throw new IllegalArgumentException("Aprovação exige data");
        }
        if (validFrom == null) {
            throw new IllegalArgumentException("valid_from é obrigatório");
        }
        if (validFrom.isBefore(approvedOn)) {
            throw new IllegalArgumentException(
                    "Efeito só futuro: valid_from não pode ser anterior à data da aprovação");
        }
    }

    /** A nova vigência não pode sobrepor nenhuma vigência existente da mesma chave. */
    public static void validateNoOverlap(Validity candidate, List<Validity> existing) {
        for (Validity other : existing) {
            if (candidate.overlaps(other)) {
                throw new IllegalArgumentException(
                        "Vigência sobreposta para a mesma chave: " + candidate + " intersecta " + other);
            }
        }
    }

    /** O aceite formal só vale para a versão cujo hash ele registra. */
    public static void validateAcceptanceHash(String versionContentHash, String acceptanceContentHash) {
        if (versionContentHash == null || acceptanceContentHash == null
                || !versionContentHash.equals(acceptanceContentHash)) {
            throw new IllegalArgumentException(
                    "O aceite registra outro conteúdo (hash divergente da versão)");
        }
    }
}

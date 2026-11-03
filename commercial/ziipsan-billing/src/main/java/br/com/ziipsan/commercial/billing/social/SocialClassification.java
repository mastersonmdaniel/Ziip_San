package br.com.ziipsan.commercial.billing.social;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Classificacao automatica, sem pedido, pela lista oficial (P1.5).
 *
 * <p>Entrada: registros da lista oficial importada (so indices cegos: hash da familia e
 * hash do documento da pessoa, papel HOLDER/MEMBER, base e data do CadUnico) e, por
 * unidade, os hashes do titular da fatura e dos membros da familia mais as flags do
 * desempate. Saida: inscricoes a criar (uma por familia, na unidade vencedora do
 * desempate), registros sem unidade (lista de busca ativa, {@code social.active_search})
 * e registros inelegiveis (CadUnico alem da idade maxima).
 *
 * <p>Uma familia com varias unidades candidatas gera UMA inscricao, na vencedora; as
 * demais unidades nao recebem inscricao por esta lista. Uma familia sem nenhuma unidade
 * correspondente nao some: vai para a busca ativa.
 */
public final class SocialClassification {

    /** Um registro da lista oficial importada (so indices cegos, nunca documento em claro). */
    public record BaseRecord(String familyCodeHash, String personDocumentHash, String personRole,
                             SocialEligibility.Basis basis, LocalDate cadunicoUpdatedOn) {
    }

    /** O que o cadastro sabe de uma unidade: hashes do titular e dos membros, e desempate. */
    public record UnitInfo(UUID unitId, Set<String> holderAndMemberDocumentHashes,
                           boolean cadunicoResponsible, boolean sameAddress, boolean familyHead,
                           LocalDate connectionDate) {
    }

    /** Inscricao a criar para uma familia. */
    public record EnrollmentCandidate(UUID unitId, String familyCodeHash, SocialEligibility.Basis basis) {
    }

    /** Resultado da classificacao de uma importacao. */
    public record Result(List<EnrollmentCandidate> enrollments, List<BaseRecord> unmatchedForActiveSearch,
                         List<BaseRecord> ineligible) {
    }

    private SocialClassification() {
    }

    public static Result classify(List<BaseRecord> baseRecords, List<UnitInfo> units,
            LocalDate referenceDate, int cadunicoMaxAgeMonths) {
        if (baseRecords == null || units == null || referenceDate == null) {
            throw new IllegalArgumentException("Registros da base, unidades e data de referencia sao obrigatorios");
        }
        Map<String, List<BaseRecord>> byFamily = new LinkedHashMap<>();
        for (BaseRecord record : baseRecords) {
            byFamily.computeIfAbsent(record.familyCodeHash(), k -> new ArrayList<>()).add(record);
        }
        List<EnrollmentCandidate> enrollments = new ArrayList<>();
        List<BaseRecord> unmatched = new ArrayList<>();
        List<BaseRecord> ineligible = new ArrayList<>();
        for (Map.Entry<String, List<BaseRecord>> family : byFamily.entrySet()) {
            List<BaseRecord> records = family.getValue();
            List<BaseRecord> eligible = new ArrayList<>();
            for (BaseRecord record : records) {
                if (SocialEligibility.isEligible(record.basis(), record.cadunicoUpdatedOn(),
                        referenceDate, cadunicoMaxAgeMonths)) {
                    eligible.add(record);
                } else {
                    ineligible.add(record);
                }
            }
            if (eligible.isEmpty()) {
                continue;
            }
            Set<String> familyHashes = new java.util.HashSet<>();
            for (BaseRecord record : eligible) {
                familyHashes.add(record.personDocumentHash());
            }
            List<SocialFamilyTiebreak.Candidate> candidates = new ArrayList<>();
            for (UnitInfo unit : units) {
                boolean matches = unit.holderAndMemberDocumentHashes().stream().anyMatch(familyHashes::contains);
                if (matches) {
                    candidates.add(new SocialFamilyTiebreak.Candidate(unit.unitId(), unit.cadunicoResponsible(),
                            unit.sameAddress(), unit.familyHead(), unit.connectionDate()));
                }
            }
            if (candidates.isEmpty()) {
                unmatched.addAll(eligible);
            } else {
                SocialFamilyTiebreak.Candidate winner = SocialFamilyTiebreak.pick(candidates).orElseThrow();
                SocialEligibility.Basis basis = eligible.get(0).basis();
                enrollments.add(new EnrollmentCandidate(winner.unitId(), family.getKey(), basis));
            }
        }
        return new Result(List.copyOf(enrollments), List.copyOf(unmatched), List.copyOf(ineligible));
    }
}

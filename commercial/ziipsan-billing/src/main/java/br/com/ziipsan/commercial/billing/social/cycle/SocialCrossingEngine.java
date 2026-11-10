package br.com.ziipsan.commercial.billing.social.cycle;

import br.com.ziipsan.commercial.billing.social.SocialClassification;
import br.com.ziipsan.commercial.billing.social.SocialEligibility;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * II-3: cruzamento da lista importada com a base de clientes/UCs por
 * chave documental (indice cego), com registo de nao-correspondencias.
 *
 * <p>Este motor NAO reimplementa a classificacao da P1.5: converte as
 * linhas importadas e as unidades para os tipos da
 * {@link SocialClassification} e DELEGA - uma familia gera no maximo
 * uma correspondencia, na unidade vencedora do desempate da P1.5
 * ({@code SocialFamilyTiebreak}), e a elegibilidade de cada registro e
 * a da P1.5 ({@code SocialEligibility}) aplicada por ela. O que este
 * motor acrescenta e o que a II-3 pede e a P1.5 nao devolve: o contexto
 * da unidade vencedora (titular, municipio, rota) para a concessao e
 * para a busca activa, e o REGISTO DE DIVERGENCIAS por familia
 * (nao-correspondencia e inelegibilidade), uma entrada por familia.
 */
public final class SocialCrossingEngine {

    /**
     * O que o cadastro sabe de uma unidade para o cruzamento: os indices
     * cegos do titular e dos membros (a chave documental), as flags do
     * desempate da P1.5 e o contexto de busca activa (municipio e rota
     * da unidade; rota pode ser nula quando a unidade nao esta em rota).
     */
    public record UnitView(UUID unitId, UUID holderCustomerId, Set<String> holderAndMemberDocumentHashes,
                           boolean cadunicoResponsible, boolean sameAddress, boolean familyHead,
                           LocalDate connectionDate, String municipalityCode, String routeCode) {
        public UnitView {
            if (unitId == null || holderCustomerId == null || holderAndMemberDocumentHashes == null) {
                throw new IllegalArgumentException("Unidade sem id, titular ou indices cegos");
            }
            holderAndMemberDocumentHashes = Set.copyOf(holderAndMemberDocumentHashes);
        }

        SocialClassification.UnitInfo toUnitInfo() {
            return new SocialClassification.UnitInfo(unitId, holderAndMemberDocumentHashes,
                    cadunicoResponsible, sameAddress, familyHead, connectionDate);
        }
    }

    /** Familia que casou com uma unidade: o necessario para conceder sem pedido. */
    public record CrossedFamily(String familyCodeHash, SocialEligibility.Basis basis,
                                LocalDate cadunicoUpdatedOn, LocalDate benefitValidFrom, LocalDate benefitValidTo,
                                UUID unitId, UUID holderCustomerId, String municipalityCode, String routeCode,
                                int personCount) {
    }

    /** Familia elegivel sem unidade correspondente: a materia da busca activa. */
    public record UnmatchedFamily(String familyCodeHash, SocialEligibility.Basis basis,
                                  String municipalityCode, int personCount) {
    }

    /** Familia sem nenhum registro elegivel na referencia (ex.: CadUnico vencido). */
    public record IneligibleFamily(String familyCodeHash, SocialEligibility.Basis basis, int personCount) {
    }

    /** Natureza da divergencia registada por familia. */
    public enum DivergenceKind {
        /** Elegivel na lista, nenhuma unidade da base casou. */
        NO_UNIT_MATCH,
        /** Nenhum registro da familia elegivel na data de referencia. */
        INELIGIBLE
    }

    /** Uma linha do registo de nao-correspondencias/divergencias do cruzamento. */
    public record Divergence(String familyCodeHash, DivergenceKind kind, String detail) {
    }

    /** Resultado completo do cruzamento de um lote importado. */
    public record CrossingResult(List<CrossedFamily> matched, List<UnmatchedFamily> unmatched,
                                 List<IneligibleFamily> ineligible, List<Divergence> divergences) {
        public CrossingResult {
            matched = List.copyOf(matched);
            unmatched = List.copyOf(unmatched);
            ineligible = List.copyOf(ineligible);
            divergences = List.copyOf(divergences);
        }
    }

    private SocialCrossingEngine() {
    }

    /**
     * Cruza as linhas aceites (ja deduplicadas pela importacao) com as
     * unidades, delegando a classificacao a P1.5.
     */
    public static CrossingResult cross(List<SocialListImportPort.ImportedBeneficiaryRow> rows,
                                       List<UnitView> units, LocalDate referenceDate,
                                       SocialTariffCycleParameters parameters) {
        if (rows == null || units == null || referenceDate == null || parameters == null) {
            throw new IllegalArgumentException("Linhas importadas, unidades, data de referencia"
                    + " e parametros sao obrigatorios");
        }
        List<SocialClassification.BaseRecord> baseRecords = new ArrayList<>();
        Map<String, SocialListImportPort.ImportedBeneficiaryRow> firstRowByFamily = new LinkedHashMap<>();
        Map<String, SocialListImportPort.ImportedBeneficiaryRow> firstEligibleRowByFamily = new LinkedHashMap<>();
        Map<String, Integer> rowsByFamily = new LinkedHashMap<>();
        for (SocialListImportPort.ImportedBeneficiaryRow row : rows) {
            baseRecords.add(new SocialClassification.BaseRecord(row.familyCodeHash(), row.personDocumentHash(),
                    row.personRole(), row.basis(), row.cadunicoUpdatedOn()));
            firstRowByFamily.putIfAbsent(row.familyCodeHash(), row);
            rowsByFamily.merge(row.familyCodeHash(), 1, Integer::sum);
            // Elegibilidade continua a ser a da P1.5: esta chamada so escolhe
            // QUAL registro elegivel da familia carrega as datas da concessao.
            if (SocialEligibility.isEligible(row.basis(), row.cadunicoUpdatedOn(), referenceDate,
                    parameters.cadunicoMaxAgeMonths())) {
                firstEligibleRowByFamily.putIfAbsent(row.familyCodeHash(), row);
            }
        }
        Map<UUID, UnitView> unitById = new LinkedHashMap<>();
        List<SocialClassification.UnitInfo> unitInfos = new ArrayList<>();
        for (UnitView unit : units) {
            unitById.put(unit.unitId(), unit);
            unitInfos.add(unit.toUnitInfo());
        }

        SocialClassification.Result classified = SocialClassification.classify(
                baseRecords, unitInfos, referenceDate, parameters.cadunicoMaxAgeMonths());

        List<CrossedFamily> matched = new ArrayList<>();
        for (SocialClassification.EnrollmentCandidate candidate : classified.enrollments()) {
            UnitView winner = unitById.get(candidate.unitId());
            SocialListImportPort.ImportedBeneficiaryRow first =
                    firstEligibleRowByFamily.get(candidate.familyCodeHash());
            matched.add(new CrossedFamily(candidate.familyCodeHash(), candidate.basis(),
                    first.cadunicoUpdatedOn(), first.benefitValidFrom(), first.benefitValidTo(),
                    candidate.unitId(), winner.holderCustomerId(), winner.municipalityCode(), winner.routeCode(),
                    rowsByFamily.getOrDefault(candidate.familyCodeHash(), 0)));
        }

        // Nao-correspondencias por familia (a P1.5 devolve registros; o
        // registo de divergencias e a busca activa trabalham por familia).
        Map<String, UnmatchedFamily> unmatchedByFamily = new LinkedHashMap<>();
        for (SocialClassification.BaseRecord record : classified.unmatchedForActiveSearch()) {
            unmatchedByFamily.computeIfAbsent(record.familyCodeHash(), k -> {
                SocialListImportPort.ImportedBeneficiaryRow first = firstRowByFamily.get(k);
                return new UnmatchedFamily(k, record.basis(), first.municipalityCode(),
                        rowsByFamily.getOrDefault(k, 0));
            });
        }
        Map<String, IneligibleFamily> ineligibleByFamily = new LinkedHashMap<>();
        for (SocialClassification.BaseRecord record : classified.ineligible()) {
            ineligibleByFamily.computeIfAbsent(record.familyCodeHash(),
                    k -> new IneligibleFamily(k, record.basis(), rowsByFamily.getOrDefault(k, 0)));
        }

        List<Divergence> divergences = new ArrayList<>();
        for (UnmatchedFamily family : unmatchedByFamily.values()) {
            divergences.add(new Divergence(family.familyCodeHash(), DivergenceKind.NO_UNIT_MATCH,
                    "Familia elegivel da lista sem unidade correspondente na base ("
                            + family.personCount() + " pessoa(s) no lote)."));
        }
        for (IneligibleFamily family : ineligibleByFamily.values()) {
            divergences.add(new Divergence(family.familyCodeHash(), DivergenceKind.INELIGIBLE,
                    "Familia sem registro elegivel na data de referencia pela regra da P1.5."));
        }
        return new CrossingResult(matched, List.copyOf(unmatchedByFamily.values()),
                List.copyOf(ineligibleByFamily.values()), divergences);
    }
}

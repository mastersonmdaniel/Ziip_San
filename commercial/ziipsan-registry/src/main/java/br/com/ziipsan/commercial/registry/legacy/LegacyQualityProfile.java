package br.com.ziipsan.commercial.registry.legacy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Perfil de qualidade do staging (P1.2): completude, duplicidade,
 * integridade referencial e documento inválido — as quatro dimensões do
 * aceite — calculadas sobre as linhas encenadas, sem tocar no cadastro.
 *
 * <p>Completude é por campo obrigatório do negócio (não por coluna do
 * mapeamento): matrícula, setor, rota, categoria, saldo, data de ligação e
 * documento do titular. Uma linha sem erro é "válida"; uma linha com erro
 * é "recusada" (fora do lote, listada no relatório, nunca aplicada).
 */
public record LegacyQualityProfile(
        int totalUnits,
        int validUnits,
        int rejectedUnits,
        int totalInstallments,
        Map<String, Double> completenessPct,
        List<String> duplicateLegacyKeys,
        int invalidDocumentCount,
        int referenceIssueCount) {

    private static final List<String> COMPLETENESS_FIELDS = List.of(
            "matricula", "sector", "route", "category", "balance", "connectedOn", "holderDocument");

    public static LegacyQualityProfile compute(List<StagedUnit> units, List<StagedInstallment> installments) {
        List<StagedUnit> rows = units == null ? List.of() : units;
        int valid = 0;
        int invalidDocuments = 0;
        int referenceIssues = 0;
        Map<String, Integer> filled = new LinkedHashMap<>();
        for (String field : COMPLETENESS_FIELDS) {
            filled.put(field, 0);
        }
        Map<String, Integer> keyCounts = new LinkedHashMap<>();
        for (StagedUnit unit : rows) {
            if (!unit.hasErrors()) {
                valid++;
            }
            if (Boolean.FALSE.equals(unit.holderDocumentValid())) {
                invalidDocuments++;
            }
            referenceIssues += (int) unit.issues().stream()
                    .filter(issue -> issue.code().startsWith("REFERENCE_")).count();
            keyCounts.merge(unit.legacyKey(), 1, Integer::sum);
            if (unit.matricula() != null) {
                filled.merge("matricula", 1, Integer::sum);
            }
            if (unit.sectorCode() != null) {
                filled.merge("sector", 1, Integer::sum);
            }
            if (unit.routeCode() != null) {
                filled.merge("route", 1, Integer::sum);
            }
            if (unit.categoryCode() != null) {
                filled.merge("category", 1, Integer::sum);
            }
            if (unit.balanceCents() != null) {
                filled.merge("balance", 1, Integer::sum);
            }
            if (unit.connectedOn() != null) {
                filled.merge("connectedOn", 1, Integer::sum);
            }
            if (unit.holderDocumentKind() != null && !unit.holderDocumentKind().equals("NONE")) {
                filled.merge("holderDocument", 1, Integer::sum);
            }
        }
        List<String> duplicates = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : keyCounts.entrySet()) {
            if (entry.getValue() > 1) {
                duplicates.add(entry.getKey());
            }
        }
        Map<String, Double> completeness = new LinkedHashMap<>();
        for (String field : COMPLETENESS_FIELDS) {
            double pct = rows.isEmpty() ? 0.0 : 100.0 * filled.get(field) / rows.size();
            completeness.put(field, pct);
        }
        return new LegacyQualityProfile(
                rows.size(), valid, rows.size() - valid,
                installments == null ? 0 : installments.size(),
                Map.copyOf(completeness), List.copyOf(duplicates),
                invalidDocuments, referenceIssues);
    }
}

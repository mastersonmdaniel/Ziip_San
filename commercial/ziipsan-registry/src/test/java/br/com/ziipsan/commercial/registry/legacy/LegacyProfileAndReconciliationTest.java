package br.com.ziipsan.commercial.registry.legacy;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyProfileAndReconciliationTest {

    private static StagedUnit unit(String key, String sector, String route, String category,
                                   Long balance, LocalDate connectedOn, Boolean docValid,
                                   List<LegacyIssue> issues) {
        return new StagedUnit(UUID.randomUUID(), UUID.randomUUID(), 1, key,
                "1001", null, "1", sector, "5", route, category,
                10L, 0L, 1L, balance, connectedOn,
                "Fulano Sintético", "CPF", "***.***.777-**", docValid,
                issues, StagedUnit.Status.PENDING, null);
    }

    @Test
    @DisplayName("perfil: completude por campo, duplicidade, documento inválido e referência")
    void qualityProfile() {
        List<StagedUnit> units = List.of(
                unit("L1", "10", "20", "RES", 1000L, LocalDate.of(2020, 1, 1), true, List.of()),
                unit("L2", "10", null, "RES", null, null, false, List.of(
                        LegacyIssue.error("doc", "INVALID_DOCUMENT", "Documento inválido."),
                        LegacyIssue.error("route", "REFERENCE_ROUTE_INVALID", "Rota inválida."))),
                unit("L1", "20", "20", "COM", 500L, LocalDate.of(2021, 1, 1), true, List.of()));
        LegacyQualityProfile profile = LegacyQualityProfile.compute(units, List.of());
        assertThat(profile.totalUnits()).isEqualTo(3);
        assertThat(profile.validUnits()).isEqualTo(2);
        assertThat(profile.rejectedUnits()).isEqualTo(1);
        assertThat(profile.duplicateLegacyKeys()).containsExactly("L1");
        assertThat(profile.invalidDocumentCount()).isEqualTo(1);
        assertThat(profile.referenceIssueCount()).isEqualTo(1);
        assertThat(profile.completenessPct().get("route")).isEqualTo(100.0 * 2 / 3);
        assertThat(profile.completenessPct().get("balance")).isEqualTo(100.0 * 2 / 3);
        assertThat(profile.completenessPct().get("matricula")).isEqualTo(100.0);
    }

    @Test
    @DisplayName("conciliação: tudo igual quando o staging reproduz o legado")
    void reconciliationMatch() {
        List<StagedUnit> units = List.of(
                unit("L1", "10", "20", "RES", 1000L, null, true, List.of()),
                unit("L2", "10", "20", "COM", 500L, null, true, List.of()),
                unit("L3", "20", "30", "RES", 200L, null, true, List.of()));
        LegacyReconciliation.LegacyTotals totals = new LegacyReconciliation.LegacyTotals(
                Map.of("10", 2L, "20", 1L), Map.of("20", 2L, "30", 1L), Map.of("RES", 2L, "COM", 1L),
                1700L, Map.of("10", 1500L, "20", 200L));
        LegacyReconciliation.Report report = LegacyReconciliation.reconcile(units, totals);
        assertThat(report.allMatch()).isTrue();
        assertThat(report.rows()).allMatch(LegacyReconciliation.Row::matches);
    }

    @Test
    @DisplayName("conciliação: divergência de contagem, de saldo e chave só de um lado aparecem")
    void reconciliationMismatch() {
        List<StagedUnit> units = List.of(
                unit("L1", "10", "20", "RES", 1000L, null, true, List.of()));
        LegacyReconciliation.LegacyTotals totals = new LegacyReconciliation.LegacyTotals(
                Map.of("10", 2L), Map.of("20", 1L, "99", 3L), Map.of("RES", 1L),
                999L, Map.of("10", 1000L));
        LegacyReconciliation.Report report = LegacyReconciliation.reconcile(units, totals);
        assertThat(report.allMatch()).isFalse();
        assertThat(report.rows()).filteredOn(row ->
                        row.dimension() == LegacyReconciliation.Dimension.SECTOR
                                && row.key().equals("10") && !row.matches())
                .hasSize(1);
        assertThat(report.rows()).filteredOn(row ->
                        row.dimension() == LegacyReconciliation.Dimension.ROUTE
                                && row.key().equals("99") && row.stagedCount() == 0L)
                .hasSize(1);
        assertThat(report.rows()).filteredOn(row ->
                        row.dimension() == LegacyReconciliation.Dimension.BALANCE && !row.matches())
                .hasSize(1);
    }
}

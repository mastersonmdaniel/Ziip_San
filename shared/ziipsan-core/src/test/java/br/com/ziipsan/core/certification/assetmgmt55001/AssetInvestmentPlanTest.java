package br.com.ziipsan.core.certification.assetmgmt55001;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssetInvestmentPlanTest {

    @Test
    @DisplayName("total soma o custo apropriado da OS (ED-36) e dos materiais (ED-58)")
    void totalsFromAppropriatedCosts() {
        var line = new AssetInvestmentPlan.PlanLine(
                AssetInvestmentPlan.ActionKind.PREVENTIVE_MAINTENANCE, "Revisão da bomba",
                "OS-123", new BigDecimal("1500.00"), "MAT-9",
                new BigDecimal("3"), new BigDecimal("200.00"), null);
        assertThat(line.materialsTotal()).isEqualByComparingTo("600.00");
        assertThat(line.lineTotal()).isEqualByComparingTo("2100.00");
        var plan = new AssetInvestmentPlan(Iso55001Fixtures.on(), Iso55001Fixtures.ASSET,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), List.of(line));
        assertThat(plan.planTotal()).isEqualByComparingTo("2100.00");
    }

    @Test
    @DisplayName("linha sem custo apropriado exige motivo: custo zero nunca é presumido")
    void noCostNeedsReason() {
        assertThatThrownBy(() -> new AssetInvestmentPlan.PlanLine(
                AssetInvestmentPlan.ActionKind.INVESTMENT, "Estudo", null, null, null, null,
                null, null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("noCostReason");
        assertThatThrownBy(() -> new AssetInvestmentPlan.PlanLine(
                AssetInvestmentPlan.ActionKind.INVESTMENT, "Estudo", null,
                new BigDecimal("10.00"), null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("workOrderRef");
    }

    @Test
    @DisplayName("horizonte é declarado pelo tenant: fim antes do início é recusado, plano vazio também")
    void horizonDeclared() {
        var line = new AssetInvestmentPlan.PlanLine(AssetInvestmentPlan.ActionKind.INVESTMENT,
                "Estudo", null, null, null, null, null, "ação de estudo sem custo apropriado");
        assertThatThrownBy(() -> new AssetInvestmentPlan(Iso55001Fixtures.on(),
                Iso55001Fixtures.ASSET, LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 1),
                List.of(line))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AssetInvestmentPlan(Iso55001Fixtures.on(),
                Iso55001Fixtures.ASSET, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}

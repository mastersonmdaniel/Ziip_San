package br.com.ziipsan.commercial.billing.largeuser;

import br.com.ziipsan.commercial.billing.special.SpecialSituationParameters;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ED-22: contrato especial com minimo e excesso, calculados a mao.
 * Contrato: contratado 1000 m3, minimo 800 m3, tarifa 2,50/m3, penalidade 20%.
 */
class LargeUserBillingCalculatorTest {

    private static final LocalDate IN_FORCE = LocalDate.of(2026, 6, 15);

    private static SpecialSituationParameters params(boolean minRequired, boolean approvalRequired,
                                                     boolean reportEnabled) {
        return SpecialSituationParameters.fromValues(Map.of(
                SpecialSituationParameters.LARGE_MIN_REQUIRED, String.valueOf(minRequired),
                SpecialSituationParameters.LARGE_EXCESS_PENALTY_PERCENT, "20.00",
                SpecialSituationParameters.LARGE_AGENCY_APPROVAL_REQUIRED, String.valueOf(approvalRequired),
                SpecialSituationParameters.LARGE_REPORT_ENABLED, String.valueOf(reportEnabled)));
    }

    private static LargeUserContract contract(boolean approved) {
        return new LargeUserContract(UUID.randomUUID(), UUID.randomUUID(),
                LargeUserContract.Modality.LARGE_USER, 1000, 800, new BigDecimal("2.50"),
                LocalDate.of(2026, 1, 1), null, true, "indice declarado no contrato",
                new BigDecimal("20.00"), List.of(UUID.randomUUID()), approved);
    }

    @Test
    @DisplayName("abaixo do contratado: medido 600 paga o minimo 800 x 2,50 = 2000,00, sem excesso")
    void minimumApplies() {
        LargeUserBillingCalculator.Result result = LargeUserBillingCalculator.calculate(
                contract(true), 600, IN_FORCE, params(true, true, true));
        assertThat(result.billedBaseVolumeM3()).isEqualTo(800);
        assertThat(result.baseValue()).isEqualByComparingTo("2000.00");
        assertThat(result.excessVolumeM3()).isEqualTo(0);
        assertThat(result.penaltyValue()).isEqualByComparingTo("0.00");
        assertThat(result.total()).isEqualByComparingTo("2000.00");
    }

    @Test
    @DisplayName("excesso: medido 1200 -> base 1200 x 2,50 = 3000,00; excesso 200 x 2,50 x 20% = 100,00; total 3100,00")
    void excessPenalty() {
        LargeUserBillingCalculator.Result result = LargeUserBillingCalculator.calculate(
                contract(true), 1200, IN_FORCE, params(true, true, true));
        assertThat(result.billedBaseVolumeM3()).isEqualTo(1200);
        assertThat(result.baseValue()).isEqualByComparingTo("3000.00");
        assertThat(result.excessVolumeM3()).isEqualTo(200);
        assertThat(result.penaltyValue()).isEqualByComparingTo("100.00");
        assertThat(result.total()).isEqualByComparingTo("3100.00");
    }

    @Test
    @DisplayName("com large_user.min_volume_required desligado, medido 600 fatura 600 x 2,50 = 1500,00")
    void minimumDisabled() {
        LargeUserBillingCalculator.Result result = LargeUserBillingCalculator.calculate(
                contract(true), 600, IN_FORCE, params(false, true, true));
        assertThat(result.billedBaseVolumeM3()).isEqualTo(600);
        assertThat(result.total()).isEqualByComparingTo("1500.00");
    }

    @Test
    @DisplayName("sem aprovacao da agencia (exigida) nao fatura; venda em bloco usa a mesma regra")
    void approvalAndBulkModality() {
        assertThatThrownBy(() -> LargeUserBillingCalculator.calculate(
                contract(false), 900, IN_FORCE, params(true, true, true)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("agencia");
        LargeUserContract bulk = new LargeUserContract(UUID.randomUUID(), UUID.randomUUID(),
                LargeUserContract.Modality.BULK_TREATED_WATER_SALE, 1000, 800, new BigDecimal("2.50"),
                LocalDate.of(2026, 1, 1), null, false, "indice declarado no contrato",
                new BigDecimal("20.00"), List.of(UUID.randomUUID()), true);
        assertThat(LargeUserBillingCalculator.calculate(bulk, 600, IN_FORCE, params(true, true, true)).total())
                .isEqualByComparingTo("2000.00");
    }

    @Test
    @DisplayName("relatorio a agencia: ligado gera CSV com o contrato; desligado e recusado")
    void agencyReport() {
        String csv = LargeUserBillingCalculator.agencyReportCsv(List.of(contract(true)), params(true, true, true));
        assertThat(csv).contains("volume_contratado_m3").contains("1000").contains("LARGE_USER");
        assertThatThrownBy(() -> LargeUserBillingCalculator.agencyReportCsv(
                List.of(contract(true)), params(true, true, false)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("contrato invalido: minimo acima do contratado e sem ponto de medicao sao recusados")
    void contractValidation() {
        assertThatThrownBy(() -> new LargeUserContract(UUID.randomUUID(), UUID.randomUUID(),
                LargeUserContract.Modality.LARGE_USER, 1000, 1001, new BigDecimal("2.50"),
                LocalDate.of(2026, 1, 1), null, true, "indice", new BigDecimal("20.00"),
                List.of(UUID.randomUUID()), true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LargeUserContract(UUID.randomUUID(), UUID.randomUUID(),
                LargeUserContract.Modality.LARGE_USER, 1000, 800, new BigDecimal("2.50"),
                LocalDate.of(2026, 1, 1), null, true, "indice", new BigDecimal("20.00"),
                List.of(), true)).isInstanceOf(IllegalArgumentException.class);
    }
}

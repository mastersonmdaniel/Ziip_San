package br.com.ziipsan.operations.metering.hydrometry;

import br.com.ziipsan.operations.metering.hydrometry.HydrometryEnums.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Troca por volume×classe e idade, dimensionamento, amostragem, bancada e renovação (ED-14). */
class ReplacementSizingSamplingBenchRenewalTest {
    private static final Map<String, Long> BY_CLASS = Map.of("1", 30_000L, "2", 20_000L);

    @Test @DisplayName("troca dispara por volume da classe, por idade, ou ambos")
    void replacement() {
        LocalDate ref = LocalDate.of(2026, 1, 1);
        assertThat(ReplacementRule.evaluate(LocalDate.of(2025, 1, 1), ref, 7, 20_000, 2, BY_CLASS)).isEqualTo(ReplacementRule.Reason.VOLUME);
        assertThat(ReplacementRule.evaluate(LocalDate.of(2018, 1, 1), ref, 7, 0, 2, BY_CLASS)).isEqualTo(ReplacementRule.Reason.AGE);
        assertThat(ReplacementRule.evaluate(LocalDate.of(2018, 1, 1), ref, 7, 99_999, 1, BY_CLASS)).isEqualTo(ReplacementRule.Reason.AGE_AND_VOLUME);
        assertThat(ReplacementRule.evaluate(LocalDate.of(2025, 1, 1), ref, 7, 0, 2, BY_CLASS)).isEqualTo(ReplacementRule.Reason.NONE);
        assertThatThrownBy(() -> ReplacementRule.evaluate(ref, ref, 7, 0, 2, Map.of())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test @DisplayName("A6: sem limiar parametrizado não há veredito; acima de Q3 = UNDERSIZED, abaixo de Q1 = OVERSIZED")
    void sizing() {
        var thresholds = new SizingRule.SizingThresholds(new BigDecimal("0.10"), new BigDecimal("0.60"));
        // perfil acima de Q3 alem do limiar: medidor pequeno p/ a demanda
        assertThat(SizingRule.evaluate(new BigDecimal("0.20"), BigDecimal.ZERO, thresholds)).isEqualTo(SizingVerdict.UNDERSIZED);
        // perfil persistente abaixo de Q1 alem do limiar: medidor grande demais (sub-registro)
        assertThat(SizingRule.evaluate(BigDecimal.ZERO, new BigDecimal("0.80"), thresholds)).isEqualTo(SizingVerdict.OVERSIZED);
        assertThat(SizingRule.evaluate(new BigDecimal("0.05"), new BigDecimal("0.30"), thresholds)).isEqualTo(SizingVerdict.ADEQUATE);
        assertThatThrownBy(() -> SizingRule.evaluate(BigDecimal.ZERO, BigDecimal.ZERO, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SizingRule.SizingThresholds(null, new BigDecimal("0.5")))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test @DisplayName("amostragem = teto de lote × pct, mínimo 1 com lote e pct positivos")
    void sampling() {
        assertThat(SamplingPlan.sampleSize(100, 10)).isEqualTo(10);
        assertThat(SamplingPlan.sampleSize(101, 10)).isEqualTo(11);
        assertThat(SamplingPlan.sampleSize(3, 10)).isEqualTo(1);
        assertThat(SamplingPlan.sampleSize(100, 0)).isEqualTo(0);
    }
    @Test @DisplayName("ensaio: erro por vazão, curva de erro e aprovação de lote só com toda a amostra aprovada")
    void bench() {
        var results = List.of(
                new BenchTestRule.FlowResult(BenchFlowPoint.Q1, new BigDecimal("100"), new BigDecimal("99")),
                new BenchTestRule.FlowResult(BenchFlowPoint.Q3, new BigDecimal("100"), new BigDecimal("101")));
        assertThat(BenchTestRule.errorCurve(results).get(BenchFlowPoint.Q1)).isEqualByComparingTo("-1.00");
        var max = Map.of(BenchFlowPoint.Q1, new BigDecimal("5"), BenchFlowPoint.Q3, new BigDecimal("2"));
        assertThat(BenchTestRule.meterApproved(results, max)).isTrue();
        assertThat(BenchTestRule.lotApproved(List.of(true, true), 100)).isTrue();
        assertThat(BenchTestRule.lotApproved(List.of(true, false), 100)).isFalse();
        assertThat(BenchTestRule.lotApproved(List.of(), 100)).isFalse();
    }
    @Test @DisplayName("renovação: benefício = perda evitada × tarifa; líquido desconta a troca e ordena")
    void renewal() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var plan = RenewalPlanCalculator.plan(List.of(
                new RenewalPlanCalculator.Candidate(a, new BigDecimal("120"), new BigDecimal("5"), new BigDecimal("400")),
                new RenewalPlanCalculator.Candidate(b, new BigDecimal("10"), new BigDecimal("5"), new BigDecimal("400"))));
        assertThat(plan.get(0).meterId()).isEqualTo(a);
        assertThat(plan.get(0).annualBenefit()).isEqualByComparingTo("600");
        assertThat(plan.get(0).netBenefit()).isEqualByComparingTo("200");
        assertThat(plan.get(1).netBenefit()).isEqualByComparingTo("-350");
    }
    @Test @DisplayName("ativo: hidrômetro exige asset_id da P1.35")
    void assetLink() {
        assertThatThrownBy(() -> HydrometryAssetLink.requireAssetLink(null)).isInstanceOf(IllegalArgumentException.class);
        HydrometryAssetLink.requireAssetLink(UUID.randomUUID());
    }
}

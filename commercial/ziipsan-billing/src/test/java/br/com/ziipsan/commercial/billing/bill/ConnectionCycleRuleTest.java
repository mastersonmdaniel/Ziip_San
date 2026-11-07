package br.com.ziipsan.commercial.billing.bill;

import br.com.ziipsan.commercial.billing.bill.ConnectionCycleRule.ConnectionFacts;
import br.com.ziipsan.commercial.billing.bill.ConnectionCycleRule.ConnectionRole;
import br.com.ziipsan.commercial.billing.bill.ConnectionCycleRule.CycleReading;
import br.com.ziipsan.commercial.billing.parameters.BillingParameters;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ED-13: o ciclo lê consumo mínimo e situação DA LIGAÇÃO (teste do
 * aceite) e a fonte alternativa da P1.3 na cobrança e no esgoto.
 * Casos calculados à mão sobre os fatos da ligação.
 */
class ConnectionCycleRuleTest {

    private static BillingParameters params(String rule) {
        return BillingParameters.fromValues(
                Map.of(BillingParameters.NO_WATER_VOLUME_RULE, "\"" + rule + "\""));
    }

    private static ConnectionFacts facts(boolean altSource, BigDecimal altVolume, boolean sewerOutOfUse) {
        return new ConnectionFacts(
                ConnectionRole.ACTIVE, true, true, 10,
                ConnectionRole.ACTIVE, true, new BigDecimal("10"),
                altSource, altVolume, sewerOutOfUse);
    }

    @Test
    @DisplayName("mínimo da ligação: medido 7 fatura 10 de água e base de esgoto 10; medido 15 fatura 15")
    void minimumAndStatusComeFromConnection() {
        CycleReading low = ConnectionCycleRule.read(
                facts(false, null, false), params("MINIMUM_VOLUME"), 7);
        assertThat(low.billedWaterConsumption()).isEqualTo(10);
        assertThat(low.sewerBaseVolume()).isEqualTo(10);
        assertThat(low.sewerBilled()).isTrue();
        CycleReading high = ConnectionCycleRule.read(
                facts(false, null, false), params("MINIMUM_VOLUME"), 15);
        assertThat(high.billedWaterConsumption()).isEqualTo(15);
        assertThat(high.sewerBaseVolume()).isEqualTo(15);
    }

    @Test
    @DisplayName("situação da ligação que não fatura: água cortada sem bills não fatura nem dá base ao esgoto")
    void nonBillingStatusBillsNothing() {
        ConnectionFacts cut = new ConnectionFacts(
                ConnectionRole.CUT, false, false, null,
                ConnectionRole.ACTIVE, true, new BigDecimal("10"),
                false, null, false);
        CycleReading r = ConnectionCycleRule.read(cut, params("MINIMUM_VOLUME"), 7);
        assertThat(r.billedWaterConsumption()).isNull();
        // Sem água faturada e sem fonte alternativa, a regra da P2.5 cai no mínimo da ligação.
        assertThat(r.sewerBaseVolume()).isEqualTo(10);
    }

    @Test
    @DisplayName("fonte alternativa (P1.3): sem água da rede, esgoto cobra pelo volume da fonte (18 m³)")
    void alternativeSourceFeedsSewerCharge() {
        ConnectionFacts noWater = new ConnectionFacts(
                ConnectionRole.NO_CONNECTION, false, false, null,
                ConnectionRole.ACTIVE, true, new BigDecimal("10"),
                true, new BigDecimal("18"), false);
        CycleReading r = ConnectionCycleRule.read(
                noWater, params("ALTERNATIVE_SOURCE_VOLUME"), 0);
        assertThat(r.billedWaterConsumption()).isNull();
        assertThat(r.sewerBilled()).isTrue();
        assertThat(r.sewerBaseVolume()).isEqualTo(18);
    }

    @Test
    @DisplayName("A3: regra que não usa o mínimo não o exige — fonte alternativa e sem-cobrança sem mínimo no catálogo")
    void minimumOnlyRequiredInMinimumBranch() {
        ConnectionFacts noSewerMinimum = new ConnectionFacts(
                ConnectionRole.NO_CONNECTION, false, false, null,
                ConnectionRole.ACTIVE, true, null,
                true, new BigDecimal("18"), false);
        CycleReading alt = ConnectionCycleRule.read(
                noSewerMinimum, params("ALTERNATIVE_SOURCE_VOLUME"), 0);
        assertThat(alt.sewerBaseVolume()).isEqualTo(18);
        CycleReading none = ConnectionCycleRule.read(
                noSewerMinimum, params("NO_SEWER_CHARGE"), 0);
        assertThat(none.sewerBaseVolume()).isZero();
        // No ramo do mínimo, a ausência continua erro explícito.
        assertThatThrownBy(() -> ConnectionCycleRule.read(
                noSewerMinimum, params("MINIMUM_VOLUME"), 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("minimum_billing_volume");
    }

    @Test
    @DisplayName("A3: mínimo fracionário (NUMERIC(10,2)) é erro explícito da regra, não ArithmeticException crua")
    void fractionalMinimumIsExplicitRuleError() {
        ConnectionFacts fractional = new ConnectionFacts(
                ConnectionRole.NO_CONNECTION, false, false, null,
                ConnectionRole.ACTIVE, true, new BigDecimal("10.50"),
                false, null, false);
        assertThatThrownBy(() -> ConnectionCycleRule.read(
                fractional, params("MINIMUM_VOLUME"), 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fracionario");
    }

    @Test
    @DisplayName("esgoto fora de uso: quem governa é a situação do esgoto no catálogo, sem bifurcação inventada")
    void sewerOutOfUseFollowsSewerStatusFlags() {
        // Fora de uso com situacao que nao fatura (bills=false no catalogo): nao cobra.
        ConnectionFacts outNotBilling = new ConnectionFacts(
                ConnectionRole.CUT, false, false, null,
                ConnectionRole.DISCONNECTED, false, new BigDecimal("10"),
                false, null, true);
        CycleReading r1 = ConnectionCycleRule.read(outNotBilling, params("MINIMUM_VOLUME"), 15);
        assertThat(r1.sewerBilled()).isFalse();
        assertThat(r1.sewerBaseVolume()).isNull();
        // Fora de uso com situacao que fatura: base pela regra normal (sem agua, o minimo).
        ConnectionFacts outBilling = new ConnectionFacts(
                ConnectionRole.CUT, false, false, null,
                ConnectionRole.ACTIVE, true, new BigDecimal("10"),
                false, null, true);
        CycleReading r2 = ConnectionCycleRule.read(outBilling, params("MINIMUM_VOLUME"), 15);
        assertThat(r2.sewerBilled()).isTrue();
        assertThat(r2.sewerBaseVolume()).isEqualTo(10);
    }

    @Test
    @DisplayName("mínimo marcado na ligação sem valor no catálogo é erro explícito")
    void minimumWithoutCatalogValueIsExplicitError() {
        ConnectionFacts broken = new ConnectionFacts(
                ConnectionRole.ACTIVE, true, true, null,
                ConnectionRole.ACTIVE, true, new BigDecimal("10"),
                false, null, false);
        assertThatThrownBy(() -> ConnectionCycleRule.read(
                broken, params("MINIMUM_VOLUME"), 7))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("minimum_billing_consumption");
    }
}

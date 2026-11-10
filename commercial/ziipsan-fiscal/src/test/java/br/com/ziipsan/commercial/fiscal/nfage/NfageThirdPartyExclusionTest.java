package br.com.ziipsan.commercial.fiscal.nfage;

import br.com.ziipsan.commercial.billing.catalog.CalculationModel;
import br.com.ziipsan.commercial.billing.catalog.ChargeOwner;
import br.com.ziipsan.commercial.billing.composition.BillChargeLine;
import br.com.ziipsan.commercial.billing.composition.BillComposition;
import br.com.ziipsan.commercial.billing.servicecatalog.RevenueNature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Camada 3: INV-C3-08 — total proprio para a NFAg-e = total da conta
 * menos as linhas de terceiro, sobre a forma da Camada 2
 * ({@link BillComposition}/{@link BillChargeLine}). Valores ficticios
 * de fixture, somas calculadas a mao.
 */
class NfageThirdPartyExclusionTest {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private static BillChargeLine line(ChargeOwner owner, String amount) {
        return new BillChargeLine(owner == ChargeOwner.THIRD_PARTY ? "COBILL-FIXTURE" : "AGUA-FIXTURE",
                UUID.randomUUID(), owner, RevenueNature.TARIFF, CalculationModel.EXTERNAL_VALUE,
                new BigDecimal(amount), null, null);
    }

    private static BillComposition composition(List<BillChargeLine> lines, String ownSubtotal,
                                               String thirdPartyTotal, String total) {
        return new BillComposition(lines, null, null, ZERO, ZERO, ZERO, new BigDecimal(ownSubtotal),
                new BigDecimal(thirdPartyTotal), List.of(), ZERO, List.of(), ZERO, ZERO, List.of(),
                ZERO, new BigDecimal(total));
    }

    @Test
    @DisplayName("INV-C3-08: conta sem terceiro — exclusao e IDENTIDADE")
    void semTerceiroIdentidade() {
        BillComposition c = composition(List.of(line(ChargeOwner.OWN, "100.00")), "100.00", "0.00", "100.00");
        assertThat(NfageThirdPartyExclusion.ownTotal(c)).isEqualTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("INV-C3-08: total proprio = total - terceiro (135.00 - 35.00 = 100.00)")
    void excluiTerceiro() {
        BillComposition c = composition(
                List.of(line(ChargeOwner.OWN, "100.00"), line(ChargeOwner.THIRD_PARTY, "35.00")),
                "100.00", "35.00", "135.00");
        assertThat(NfageThirdPartyExclusion.ownTotal(c)).isEqualTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("INV-C3-08: fronteira terceiro == total — devolve ZERO")
    void terceiroIgualAoTotalZero() {
        BillComposition c = composition(List.of(line(ChargeOwner.THIRD_PARTY, "50.00")),
                "0.00", "50.00", "50.00");
        assertThat(NfageThirdPartyExclusion.ownTotal(c)).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("sem linhas: identidade sobre o total")
    void semLinhasIdentidade() {
        BillComposition c = composition(List.of(), "42.00", "0.00", "42.00");
        assertThat(NfageThirdPartyExclusion.ownTotal(c)).isEqualTo(new BigDecimal("42.00"));
    }

    @Test
    @DisplayName("thirdPartyTotal soma SO as linhas de terceiro")
    void somaSoTerceiro() {
        List<BillChargeLine> lines = List.of(line(ChargeOwner.OWN, "100.00"),
                line(ChargeOwner.THIRD_PARTY, "20.00"), line(ChargeOwner.THIRD_PARTY, "15.00"));
        assertThat(NfageThirdPartyExclusion.thirdPartyTotal(lines)).isEqualTo(new BigDecimal("35.00"));
    }

    @Test
    @DisplayName("forma (total, linhas): mesmo resultado da forma por composicao")
    void formaTotalELinhas() {
        List<BillChargeLine> lines = List.of(line(ChargeOwner.OWN, "100.00"),
                line(ChargeOwner.THIRD_PARTY, "35.00"));
        assertThat(NfageThirdPartyExclusion.ownTotal(new BigDecimal("135.00"), lines))
                .isEqualTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("composicao inconsistente (thirdPartyTotal declarado != soma das linhas): falha fechada")
    void composicaoInconsistenteRecusada() {
        BillComposition c = composition(
                List.of(line(ChargeOwner.OWN, "100.00"), line(ChargeOwner.THIRD_PARTY, "35.00")),
                "100.00", "30.00", "135.00");
        assertThatThrownBy(() -> NfageThirdPartyExclusion.ownTotal(c))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inconsistente");
    }

    @Test
    @DisplayName("terceiro maior que o total: recusado, nunca total fiscal negativo")
    void terceiroMaiorQueTotalRecusado() {
        List<BillChargeLine> lines = List.of(line(ChargeOwner.THIRD_PARTY, "60.00"));
        assertThatThrownBy(() -> NfageThirdPartyExclusion.ownTotal(new BigDecimal("50.00"), lines))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

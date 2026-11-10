package br.com.ziipsan.commercial.billing.servicecatalog;
import br.com.ziipsan.commercial.billingrules.servicecatalog.RevenueNature;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PriceTableTest {
    private static final LocalDate D = LocalDate.of(2026, 3, 10);
    private static ServicePriceItem item(String nature, String sharing, boolean homReq, String homRef) {
        return new ServicePriceItem("LIGACAO_AGUA", "Ligacao de agua", RevenueNature.valueOf(nature),
                new BigDecimal("300.00"), LocalDate.of(2026, 1, 1), null, homReq, homRef,
                sharing == null ? null : new BigDecimal(sharing));
    }

    @Test @DisplayName("POC-41: TRES tipos de servico, cada um com todos os parametros")
    void poc41AllParameters() {
        // tres tipos distintos, todos parametrizados por completo na mesma tabela vigente
        var ligacao = new ServicePriceItem("LIGACAO_AGUA", "Ligacao de agua", RevenueNature.TARIFF,
                new BigDecimal("300.00"), LocalDate.of(2026, 1, 1), null, false, null, null);
        var religacao = new ServicePriceItem("RELIGACAO", "Religacao", RevenueNature.COMPLEMENTARY,
                new BigDecimal("80.00"), LocalDate.of(2026, 1, 1), null, true, "ATO-AGENCIA-001/2026", new BigDecimal("10.00"));
        var vistoria = new ServicePriceItem("VISTORIA_PADRAO", "Vistoria do padrao", RevenueNature.ANCILLARY,
                new BigDecimal("50.00"), LocalDate.of(2026, 1, 1), null, true, "ATO-AGENCIA-002/2026", new BigDecimal("5.00"));
        PriceTable t = new PriceTable(List.of(ligacao, religacao, vistoria), List.of());
        assertThat(t.service("LIGACAO_AGUA", D).revenueNature()).isEqualTo(RevenueNature.TARIFF);
        assertThat(t.service("RELIGACAO", D).billable()).isTrue();
        assertThat(t.service("RELIGACAO", D).sharingPct()).isEqualByComparingTo("10.00");
        assertThat(t.service("VISTORIA_PADRAO", D).billable()).isTrue();
        assertThat(t.service("VISTORIA_PADRAO", D).homologationRef()).isEqualTo("ATO-AGENCIA-002/2026");
    }

    @Test @DisplayName("receita nao tarifaria sem rev.sharing_pct e recusada (art. 10-A, II)")
    void sharingRequiredForNonTariff() {
        assertThatThrownBy(() -> item("ANCILLARY", null, false, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> item("TARIFF", "100.01", false, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("homologacao exigida sem registro: item existe mas nao e faturavel")
    void homologationPendingNotBillable() {
        assertThat(item("TARIFF", null, true, null).billable()).isFalse();
    }

    @Test @DisplayName("vigencia: resolve na data, recusa fora da vigencia e sobreposicao")
    void validityResolution() {
        var old = new ServicePriceItem("X", "x", RevenueNature.TARIFF, new BigDecimal("100.00"),
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), false, null, null);
        var cur = new ServicePriceItem("X", "x", RevenueNature.TARIFF, new BigDecimal("120.00"),
                LocalDate.of(2026, 1, 1), null, false, null, null);
        PriceTable t = new PriceTable(List.of(old, cur), List.of());
        assertThat(t.service("X", D).unitPrice()).isEqualByComparingTo("120.00");
        assertThatThrownBy(() -> t.service("X", LocalDate.of(2024, 6, 1))).isInstanceOf(IllegalStateException.class);
        var overlap = new ServicePriceItem("X", "x", RevenueNature.TARIFF, new BigDecimal("1.00"),
                LocalDate.of(2026, 6, 1), null, false, null, null);
        assertThatThrownBy(() -> new PriceTable(List.of(cur, overlap), List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}

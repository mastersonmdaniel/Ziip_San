package br.com.ziipsan.commercial.billing.servicecatalog;
import br.com.ziipsan.commercial.billingrules.servicecatalog.RevenueNature;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceChargeOnCloseTest {
    private static final UUID ORDER = UUID.randomUUID();
    private static final UUID UNIT = UUID.randomUUID();
    private static PriceTable table() {
        var svc = new ServicePriceItem("RELIGACAO", "Religacao", RevenueNature.TARIFF, new BigDecimal("100.00"),
                LocalDate.of(2026, 1, 1), null, false, null, null);
        var mat = new MaterialPriceItem("LACRE", "Lacre", "UN", new BigDecimal("10.00"),
                LocalDate.of(2026, 1, 1), null);
        return new PriceTable(List.of(svc), List.of(mat));
    }
    private static ServiceChargeOnClose.ClosedOrder closed(String status) {
        return new ServiceChargeOnClose.ClosedOrder(ORDER, status, LocalDate.of(2026, 3, 10), UNIT);
    }

    @Test @DisplayName("cobranca lancada no encerramento executado: servico + material, fonte SERVICE_ORDER")
    void chargeOnClose() {
        var c = ServiceChargeOnClose.charge(table(), closed("CLOSED_EXECUTED"), "RELIGACAO", BigDecimal.ONE,
                List.of(new ServiceChargeOnClose.MaterialUse("LACRE", new BigDecimal("2"))), null, null, 0);
        assertThat(c.serviceAmount()).isEqualByComparingTo("100.00");
        assertThat(c.materialsAmount()).isEqualByComparingTo("20.00");
        assertThat(c.total()).isEqualByComparingTo("120.00");
        assertThat(c.sourceType()).isEqualTo("SERVICE_ORDER");
        assertThat(c.idempotencyKey()).isEqualTo(ORDER + ":RELIGACAO");
    }

    @Test @DisplayName("OS nao encerrada como executada nao lanca cobranca")
    void notClosedNoCharge() {
        assertThatThrownBy(() -> ServiceChargeOnClose.charge(table(), closed("EXECUTED"), "RELIGACAO",
                BigDecimal.ONE, List.of(), null, null, 0)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ServiceChargeOnClose.charge(table(), closed("CLOSED_NOT_EXECUTED"), "RELIGACAO",
                BigDecimal.ONE, List.of(), null, null, 0)).isInstanceOf(IllegalStateException.class);
    }

    @Test @DisplayName("D6: zero parcelas tem mensagem correta; unidade nula recusada na construcao")
    void edgeValidation() {
        assertThatThrownBy(() -> ServiceInstallmentPlan.plan(new BigDecimal("100.00"), 0, null, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pelo menos 1");
        assertThatThrownBy(() -> new ServiceChargeOnClose.ClosedOrder(ORDER, "CLOSED_EXECUTED",
                LocalDate.of(2026, 3, 10), null)).isInstanceOf(NullPointerException.class);
    }

    @Test @DisplayName("POC-31: parcelamento de servico soma o total (100 em 3 = 33,33+33,33+33,34) e respeita limites")
    void poc31Installments() {
        var plan = ServiceInstallmentPlan.plan(new BigDecimal("100.00"), 3, null, 0);
        assertThat(plan.installments()).containsExactly(new BigDecimal("33.33"), new BigDecimal("33.33"), new BigDecimal("33.34"));
        assertThatThrownBy(() -> ServiceInstallmentPlan.plan(new BigDecimal("600.00"), 2, new BigDecimal("500.00"), 12))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ServiceInstallmentPlan.plan(new BigDecimal("100.00"), 13, null, 12))
                .isInstanceOf(IllegalArgumentException.class);
        var c = ServiceChargeOnClose.charge(table(), closed("CLOSED_EXECUTED"), "RELIGACAO", BigDecimal.ONE,
                List.of(), 3, null, 0);
        assertThat(c.installmentPlan().installments()).hasSize(3);
    }
}

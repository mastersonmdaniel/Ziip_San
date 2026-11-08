package br.com.ziipsan.commercial.billing.altsolution;

import br.com.ziipsan.commercial.billing.central.CasualUserUnit;
import br.com.ziipsan.commercial.billing.servicecatalog.PriceTable;
import br.com.ziipsan.commercial.billing.servicecatalog.RevenueNature;
import br.com.ziipsan.commercial.billing.servicecatalog.ServicePriceItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CleaningServiceExecutionTest {

    private static final LocalDate EXEC = LocalDate.of(2026, 4, 10);
    private static final AltSolution SOLUTION = AltSolution.register(UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), Set.of(AltSolution.Component.SEPTIC_TANK), new BigDecimal("5000.00"),
            null, "ETE municipal", LocalDate.of(2026, 1, 15));

    private static PriceTable table(boolean homologationBlocked) {
        var item = new ServicePriceItem("LIMPA_FOSSA", "Esgotamento de fossa septica", RevenueNature.TARIFF,
                new BigDecimal("250.00"), LocalDate.of(2026, 1, 1), null,
                homologationBlocked, homologationBlocked ? null : "ATO-001/2026", null);
        return new PriceTable(List.of(item), List.of());
    }

    private static CleaningServiceExecution.CleaningOrder order() {
        return new CleaningServiceExecution.CleaningOrder(UUID.randomUUID(), SOLUTION.id(),
                "CAMINHAO-01", 3, EXEC);
    }

    private static CleaningServiceExecution.DischargeTicket ticket(String volume) {
        return new CleaningServiceExecution.DischargeTicket("TKT-100", "ETE-CENTRAL",
                new BigDecimal(volume), EXEC);
    }

    @Test @DisplayName("execucao completa: preco da ED-34 (250,00 x 2 = 500,00), ticket confere, rota regular")
    void completeHappyPath() {
        var c = CleaningServiceExecution.complete(SOLUTION, order(), true, table(false), "LIMPA_FOSSA",
                new BigDecimal("2"), new BigDecimal("4800.00"), ticket("4800.00"),
                "CATALOGO_SERVICOS", CasualUserUnit.UnitKind.REGULAR, EXEC);
        assertThat(c.amount()).isEqualByComparingTo("500.00");
        assertThat(c.tariffSource()).isEqualTo("CATALOGO_SERVICOS");
        assertThat(c.billingRoute()).isEqualTo(CleaningServiceExecution.BillingRoute.REGULAR_BILL);
        assertThat(c.dischargeTicket().ticketCode()).isEqualTo("TKT-100");
    }

    @Test @DisplayName("usuario avulso da ED-22: rota de guia avulsa; centralizador: agrupadora")
    void casualAndCentralizerRoutes() {
        var casual = CleaningServiceExecution.complete(SOLUTION, order(), true, table(false), "LIMPA_FOSSA",
                BigDecimal.ONE, new BigDecimal("4800.00"), ticket("4800.00"),
                "CATALOGO_SERVICOS", CasualUserUnit.UnitKind.CASUAL, EXEC);
        assertThat(casual.billingRoute()).isEqualTo(CleaningServiceExecution.BillingRoute.CASUAL_GUIDE);
        var grouped = CleaningServiceExecution.complete(SOLUTION, order(), true, table(false), "LIMPA_FOSSA",
                BigDecimal.ONE, new BigDecimal("4800.00"), ticket("4800.00"),
                "CATALOGO_SERVICOS", CasualUserUnit.UnitKind.CENTRALIZER, EXEC);
        assertThat(grouped.billingRoute()).isEqualTo(CleaningServiceExecution.BillingRoute.GROUPED_BILL);
    }

    @Test @DisplayName("servico desligado: solucao so cadastrada nao e executada")
    void disabledServiceRefused() {
        assertThatThrownBy(() -> CleaningServiceExecution.complete(SOLUTION, order(), false, table(false),
                "LIMPA_FOSSA", BigDecimal.ONE, new BigDecimal("4800.00"), ticket("4800.00"),
                "CATALOGO_SERVICOS", CasualUserUnit.UnitKind.REGULAR, EXEC))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("so cadastrada");
    }

    @Test @DisplayName("ticket com volume divergente do coletado e recusado; solucao desativada nao e esgotada")
    void ticketMismatchAndDeactivated() {
        assertThatThrownBy(() -> CleaningServiceExecution.complete(SOLUTION, order(), true, table(false),
                "LIMPA_FOSSA", BigDecimal.ONE, new BigDecimal("4800.00"), ticket("3000.00"),
                "CATALOGO_SERVICOS", CasualUserUnit.UnitKind.REGULAR, EXEC))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("difere do volume coletado");
        AltSolution off = SOLUTION.deactivateByConnection(LocalDate.of(2026, 3, 1), UUID.randomUUID());
        var offOrder = new CleaningServiceExecution.CleaningOrder(UUID.randomUUID(), off.id(), "CAM-1", 1, EXEC);
        assertThatThrownBy(() -> CleaningServiceExecution.complete(off, offOrder, true, table(false),
                "LIMPA_FOSSA", BigDecimal.ONE, new BigDecimal("4800.00"), ticket("4800.00"),
                "CATALOGO_SERVICOS", CasualUserUnit.UnitKind.REGULAR, EXEC))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test @DisplayName("item com homologacao exigida e nao registrada nao fatura (ED-34)")
    void homologationBlocks() {
        assertThatThrownBy(() -> CleaningServiceExecution.complete(SOLUTION, order(), true, table(true),
                "LIMPA_FOSSA", BigDecimal.ONE, new BigDecimal("4800.00"), ticket("4800.00"),
                "CATALOGO_SERVICOS", CasualUserUnit.UnitKind.REGULAR, EXEC))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("homologacao");
    }

    @Test @DisplayName("MTR romaneio: item de agenda da ED-98 so onde exigido; sem exigencia, nenhum item")
    void mtrAgendaItem() {
        var c = CleaningServiceExecution.complete(SOLUTION, order(), true, table(false), "LIMPA_FOSSA",
                BigDecimal.ONE, new BigDecimal("4800.00"), ticket("4800.00"),
                "CATALOGO_SERVICOS", CasualUserUnit.UnitKind.REGULAR, EXEC);
        var item = CleaningServiceExecution.mtrAgendaItem(c, true);
        assertThat(item.itemType()).isEqualTo("MTR_ROMANEIO");
        assertThat(item.status()).isEqualTo("PENDING_ED98_AGENDA");
        assertThat(item.dischargeTicketCode()).isEqualTo("TKT-100");
        assertThat(CleaningServiceExecution.mtrAgendaItem(c, false)).isNull();
    }

    @Test @DisplayName("OS exige caminhao e posicao de roteiro")
    void orderValidations() {
        assertThatThrownBy(() -> new CleaningServiceExecution.CleaningOrder(UUID.randomUUID(), SOLUTION.id(),
                " ", 1, EXEC)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CleaningServiceExecution.CleaningOrder(UUID.randomUUID(), SOLUTION.id(),
                "CAM-1", 0, EXEC)).isInstanceOf(IllegalArgumentException.class);
    }
}

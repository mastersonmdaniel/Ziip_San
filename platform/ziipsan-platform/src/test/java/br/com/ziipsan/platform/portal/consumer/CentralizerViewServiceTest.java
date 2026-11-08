package br.com.ziipsan.platform.portal.consumer;

import br.com.ziipsan.core.api.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ED-22 sobre a P3.20: visao do centralizador/grande cliente - lista, exportacao e agrupadora. */
class CentralizerViewServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID CENTRALIZER = UUID.randomUUID();
    private static final UUID U1 = UUID.randomUUID();
    private static final UUID U2 = UUID.randomUUID();

    private static CentralizerViewService service(boolean access) {
        CentralizerViewService.CentralizerPort port = new CentralizerViewService.CentralizerPort() {
            @Override
            public boolean isCentralizerAccount(UUID tenantId, String subject, UUID centralizerId) {
                return access;
            }

            @Override
            public List<CentralizerViewService.UnitSummary> units(UUID tenantId, UUID centralizerId) {
                return List.of(
                        new CentralizerViewService.UnitSummary(U1, "1001", new BigDecimal("100.00")),
                        new CentralizerViewService.UnitSummary(U2, "1002", new BigDecimal("200.00")));
            }

            @Override
            public CentralizerViewService.GroupedBillView groupedBill(UUID tenantId, UUID centralizerId,
                                                                      YearMonth reference) {
                return new CentralizerViewService.GroupedBillView("AGR-2026-06-X-1", reference,
                        new BigDecimal("300.00"), 2);
            }
        };
        return new CentralizerViewService(port);
    }

    @Test
    @DisplayName("lista das unidades, exportacao CSV e fatura agrupadora da competencia")
    void view() {
        CentralizerViewService service = service(true);
        assertThat(service.units(TENANT, "sujeito", CENTRALIZER)).hasSize(2);
        String csv = service.exportUnitsCsv(TENANT, "sujeito", CENTRALIZER);
        assertThat(csv).contains("1001").contains("100.00").contains("1002").contains("200.00");
        CentralizerViewService.GroupedBillView bill = service.groupedBill(
                TENANT, "sujeito", CENTRALIZER, YearMonth.of(2026, 6));
        assertThat(bill.code()).isEqualTo("AGR-2026-06-X-1");
        assertThat(bill.total()).isEqualByComparingTo("300.00");
    }

    @Test
    @DisplayName("D6: canal vem do chamador - GOV_BR passa pelo guard, GUEST e recusado, OWN_ACCOUNT segue o padrao")
    void channelIsParameter() {
        CentralizerViewService service = service(true);
        assertThat(service.units(TENANT, "sujeito", CENTRALIZER, ConsumerAccessGuard.Channel.GOV_BR))
                .hasSize(2);
        assertThat(service.groupedBill(TENANT, "sujeito", CENTRALIZER, YearMonth.of(2026, 6),
                ConsumerAccessGuard.Channel.GOV_BR).code()).isEqualTo("AGR-2026-06-X-1");
        assertThatThrownBy(() -> service.units(TENANT, "sujeito", CENTRALIZER,
                ConsumerAccessGuard.Channel.GUEST)).isInstanceOf(ApiException.Forbidden.class);
        assertThatThrownBy(() -> service.units(TENANT, "sujeito", CENTRALIZER, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("conta que nao e a do centralizador e recusada antes de qualquer leitura")
    void forbidden() {
        assertThatThrownBy(() -> service(false).units(TENANT, "outro", CENTRALIZER))
                .isInstanceOf(ApiException.Forbidden.class);
    }
}

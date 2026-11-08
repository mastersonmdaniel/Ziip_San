package br.com.ziipsan.platform.portal.consumer;

import br.com.ziipsan.core.api.ApiException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P3.20 - historico de 120 meses, lista unica, origem, so vinculadas, dois titulares. */
class ConsumerHistoryServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID UNIT = UUID.randomUUID();
    private static final YearMonth NOW = YearMonth.of(2026, 11);

    private ConsumerHistoryService service(String linkedSubject) {
        return new ConsumerHistoryService(new ConsumerHistoryService.HistoryPort() {
            @Override public boolean isUnitLinkedToConsumer(UUID t, String s, UUID u) { return s.equals(linkedSubject); }
            @Override public List<ConsumerHistoryService.HistoryEntry> bills(UUID t, UUID u, YearMonth from) {
                return List.of(
                        new ConsumerHistoryService.HistoryEntry(ConsumerHistoryService.EntryKind.BILL,
                                UUID.randomUUID(), u, NOW, LocalDate.of(2026, 11, 10), new BigDecimal("90"),
                                ConsumerHistoryService.DocumentOrigin.ZIIPSAN, false),
                        new ConsumerHistoryService.HistoryEntry(ConsumerHistoryService.EntryKind.BILL,
                                UUID.randomUUID(), u, NOW.minusMonths(200), LocalDate.of(2010, 1, 10),
                                new BigDecimal("50"), ConsumerHistoryService.DocumentOrigin.PREVIOUS_SYSTEM, true));
            }
            @Override public List<ConsumerHistoryService.HistoryEntry> payments(UUID t, UUID u, YearMonth from) {
                return List.of(new ConsumerHistoryService.HistoryEntry(ConsumerHistoryService.EntryKind.PAYMENT,
                        UUID.randomUUID(), u, NOW.minusMonths(1), LocalDate.of(2026, 10, 9), new BigDecimal("88"),
                        ConsumerHistoryService.DocumentOrigin.INTERVAL_ISSUER, true));
            }
        });
    }

    @Test
    void listaUnicaComOrigemEJanelaDe120Meses() {
        var h = service("dono").history(TENANT, "dono", UNIT, NOW);
        assertThat(h).hasSize(2);
        assertThat(h).extracting(ConsumerHistoryService.HistoryEntry::kind)
                .containsExactly(ConsumerHistoryService.EntryKind.BILL, ConsumerHistoryService.EntryKind.PAYMENT);
        assertThat(h).allSatisfy(e -> assertThat(e.origin()).isNotNull());
    }

    @Test
    void outroTitularNaoVeUnidadeNaoVinculada() {
        // Dois titulares: a unidade esta vinculada a "dono"; "outro" e recusado.
        assertThatThrownBy(() -> service("dono").history(TENANT, "outro", UNIT, NOW))
                .isInstanceOf(ApiException.Forbidden.class);
    }

    @Test
    void convidadoNuncaAcessaHistorico() {
        assertThatThrownBy(() -> service("dono").history(TENANT, "dono", UNIT, NOW,
                ConsumerAccessGuard.Channel.GUEST)).isInstanceOf(ApiException.Forbidden.class);
    }

    @Test
    void govBrAcessaHistorico() {
        assertThat(service("dono").history(TENANT, "dono", UNIT, NOW, ConsumerAccessGuard.Channel.GOV_BR))
                .hasSize(2);
    }
}

package br.com.ziipsan.core.support;

import br.com.ziipsan.core.api.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P4.8: nota do tenant por chamado encerrado (CSAT) e por pesquisa
 * periódica (NPS), sem duplicar a gravação da P2.15.
 */
class TenantSatisfactionTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TICKET = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static final class FakeNpsStore implements TenantSatisfaction.NpsStore {
        final List<TenantSatisfaction.NpsResponse> saved = new ArrayList<>();

        @Override
        public boolean existsResponse(UUID tenantId, YearMonth period) {
            return saved.stream().anyMatch(r -> r.tenantId().equals(tenantId) && r.period().equals(period));
        }

        @Override
        public void save(TenantSatisfaction.NpsResponse response) {
            saved.add(response);
        }

        @Override
        public List<TenantSatisfaction.NpsResponse> responsesOfPeriod(YearMonth period) {
            return saved.stream().filter(r -> r.period().equals(period)).toList();
        }
    }

    @Test
    @DisplayName("P4.8 CSAT: chamado ENCERRADO (CLOSED) registra a nota pela porta; outro estado é conflito")
    void csatOnlyOnClosedTicket() {
        List<Integer> recorded = new ArrayList<>();
        TenantSatisfaction satisfaction = new TenantSatisfaction(
                (ticketId, rating, comment) -> recorded.add(rating), new FakeNpsStore());
        satisfaction.recordClosedTicketCsat(TICKET, "CLOSED", 5, null);
        assertThat(recorded).containsExactly(5);
        assertThatThrownBy(() -> satisfaction.recordClosedTicketCsat(TICKET, "IN_PROGRESS", 5, null))
                .isInstanceOf(ApiException.Conflict.class);
        assertThatThrownBy(() -> satisfaction.recordClosedTicketCsat(TICKET, "CLOSED", 6, null))
                .isInstanceOf(ApiException.Validation.class);
        assertThat(recorded).containsExactly(5);
    }

    @Test
    @DisplayName("P4.8 NPS: uma resposta por tenant por período; faixas e índice %promotores − %detratores")
    void npsOnePerTenantPerPeriodAndAggregate() {
        FakeNpsStore store = new FakeNpsStore();
        TenantSatisfaction satisfaction = new TenantSatisfaction((t, r, c) -> {
        }, store);
        YearMonth period = YearMonth.of(2026, 11);
        satisfaction.recordNps(new TenantSatisfaction.NpsResponse(TENANT, period, 10, null));
        assertThatThrownBy(() -> satisfaction.recordNps(
                new TenantSatisfaction.NpsResponse(TENANT, period, 9, null)))
                .isInstanceOf(ApiException.Conflict.class);
        UUID other = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID third = UUID.fromString("66666666-6666-6666-6666-666666666666");
        UUID fourth = UUID.fromString("77777777-7777-7777-7777-777777777777");
        satisfaction.recordNps(new TenantSatisfaction.NpsResponse(other, period, 9, null));
        satisfaction.recordNps(new TenantSatisfaction.NpsResponse(third, period, 8, null));
        satisfaction.recordNps(new TenantSatisfaction.NpsResponse(fourth, period, 2, null));

        TenantSatisfaction.NpsAggregate agg = satisfaction.aggregate(period);
        assertThat(agg.responses()).isEqualTo(4);
        assertThat(agg.promoters()).isEqualTo(2);
        assertThat(agg.passives()).isEqualTo(1);
        assertThat(agg.detractors()).isEqualTo(1);
        assertThat(agg.npsScore()).isEqualTo(25.0);
        assertThat(TenantSatisfaction.Band.of(9)).isEqualTo(TenantSatisfaction.Band.PROMOTER);
        assertThat(TenantSatisfaction.Band.of(7)).isEqualTo(TenantSatisfaction.Band.PASSIVE);
        assertThat(TenantSatisfaction.Band.of(6)).isEqualTo(TenantSatisfaction.Band.DETRACTOR);
        assertThatThrownBy(() -> new TenantSatisfaction.NpsResponse(TENANT, period, 11, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(satisfaction.aggregate(YearMonth.of(2026, 12)).npsScore()).isNull();
    }
}

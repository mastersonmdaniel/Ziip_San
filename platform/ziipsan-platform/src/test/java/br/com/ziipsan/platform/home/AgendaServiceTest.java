package br.com.ziipsan.platform.home;

import br.com.ziipsan.core.agenda.AgendaEvent;
import br.com.ziipsan.core.agenda.AgendaEventSource;
import br.com.ziipsan.core.agenda.AgendaEventTypeRegistry;
import br.com.ziipsan.core.api.ApiException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Agenda da P3.39: so tipos registrados, sem erro quando uma fonte nao
 * existe, janela e tenant conferidos na agregacao, ordem deterministica.
 */
class AgendaServiceTest {

    private static final UUID TENANT = HomeFixtures.TENANT;
    private static final Instant FROM = Instant.parse("2026-03-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-04-01T00:00:00Z");

    private static AgendaEvent event(String type, String at, UUID tenant) {
        return new AgendaEvent(UUID.randomUUID(), tenant, type, "Evento " + type,
                Instant.parse(at), null, "OBJ", UUID.randomUUID(), "/origem");
    }

    private static AgendaEventSource source(String type, List<AgendaEvent> events) {
        return new AgendaEventSource() {
            @Override
            public String typeCode() {
                return type;
            }

            @Override
            public List<AgendaEvent> eventsInWindow(UUID tenantId, Instant from, Instant to) {
                return events;
            }
        };
    }

    @Test
    void missingSourceForRegisteredTypeIsNotAnError() {
        AgendaService agenda = new AgendaService(
                AgendaEventTypeRegistry.withBornTypes(), List.of());
        assertThat(agenda.eventsInWindow(TENANT, FROM, TO)).isEmpty();
        assertThat(agenda.types()).hasSize(2)
                .allSatisfy(t -> assertThat(t.sourceAvailable()).isFalse());
    }

    @Test
    void unregisteredSourceTypeIsNeverShown() {
        AgendaService agenda = new AgendaService(AgendaEventTypeRegistry.withBornTypes(),
                List.of(source("calibration", List.of(event("calibration", "2026-03-10T03:00:00Z", TENANT)))));
        assertThat(agenda.eventsInWindow(TENANT, FROM, TO)).isEmpty();
    }

    @Test
    void eventsAreFilteredByTenantAndWindowAndSorted() {
        AgendaEvent late = event("billing-schedule", "2026-03-20T03:00:00Z", TENANT);
        AgendaEvent early = event("billing-schedule", "2026-03-05T03:00:00Z", TENANT);
        AgendaEvent otherTenant = event("billing-schedule", "2026-03-06T03:00:00Z", UUID.randomUUID());
        AgendaEvent outside = event("billing-schedule", "2026-05-01T03:00:00Z", TENANT);
        AgendaService agenda = new AgendaService(AgendaEventTypeRegistry.withBornTypes(),
                List.of(source("billing-schedule", List.of(late, early, otherTenant, outside))));
        assertThat(agenda.eventsInWindow(TENANT, FROM, TO))
                .extracting(AgendaEvent::id)
                .containsExactly(early.id(), late.id());
    }

    @Test
    void registeredTypeWithSourceReportsAvailable() {
        AgendaService agenda = new AgendaService(AgendaEventTypeRegistry.withBornTypes(),
                List.of(source("internal-audit", List.of())));
        assertThat(agenda.types()).anySatisfy(t -> {
            assertThat(t.type().code()).isEqualTo("internal-audit");
            assertThat(t.sourceAvailable()).isTrue();
        });
    }

    @Test
    void invalidWindowIsRefusedByName() {
        AgendaService agenda = new AgendaService(
                AgendaEventTypeRegistry.withBornTypes(), List.of());
        assertThatThrownBy(() -> agenda.eventsInWindow(TENANT, TO, FROM))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> agenda.eventsInWindow(TENANT, FROM, FROM))
                .isInstanceOf(ApiException.Validation.class);
    }

    @Test
    void listingTwiceDoesNotMutateSource() {
        List<AgendaEvent> backing = new java.util.ArrayList<>(
                List.of(event("billing-schedule", "2026-03-05T03:00:00Z", TENANT)));
        AgendaService agenda = new AgendaService(AgendaEventTypeRegistry.withBornTypes(),
                List.of(source("billing-schedule", backing)));
        agenda.eventsInWindow(TENANT, FROM, TO);
        agenda.eventsInWindow(TENANT, FROM, TO);
        assertThat(backing).hasSize(1);
    }
}

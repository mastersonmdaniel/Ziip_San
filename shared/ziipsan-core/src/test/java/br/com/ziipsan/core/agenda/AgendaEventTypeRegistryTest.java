package br.com.ziipsan.core.agenda;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Contrato de tipo de evento de agenda (P3.39): nascem registrados SO o
 * cronograma da P2.5 e as auditorias da P3.31; os tipos das fontes
 * posteriores (ED-36/35/51/56/98) NAO nascem — entram pela tarefa de cada
 * fonte. Re-registro identico e no-op; definicao divergente no mesmo
 * codigo e conflito.
 */
class AgendaEventTypeRegistryTest {

    @Test
    void bornTypesAreExactlyBillingScheduleAndInternalAudit() {
        AgendaEventTypeRegistry registry = AgendaEventTypeRegistry.withBornTypes();
        assertThat(registry.registeredCodes())
                .containsExactly("billing-schedule", "internal-audit");
        assertThat(registry.find("billing-schedule")).get()
                .extracting(AgendaEventType::originTask).isEqualTo("P2.5");
        assertThat(registry.find("internal-audit")).get()
                .extracting(AgendaEventType::originTask).isEqualTo("P3.31");
    }

    @Test
    void futureSourceTypesAreNotBornRegistered() {
        AgendaEventTypeRegistry registry = AgendaEventTypeRegistry.withBornTypes();
        // Codigos ilustrativos das fontes futuras: nenhum esta registrado.
        assertThat(registry.isRegistered("service-order-schedule")).isFalse();
        assertThat(registry.isRegistered("planned-interruption")).isFalse();
        assertThat(registry.isRegistered("calibration")).isFalse();
        assertThat(registry.isRegistered("preventive-maintenance")).isFalse();
        assertThat(registry.isRegistered("obligation")).isFalse();
    }

    @Test
    void sourceTaskRegistersItsOwnTypeLater() {
        AgendaEventTypeRegistry registry = AgendaEventTypeRegistry.withBornTypes();
        AgendaEventType later = new AgendaEventType(
                "planned-interruption", "Interrupcoes programadas", "ED-35", "operations");
        registry.register(later);
        assertThat(registry.isRegistered("planned-interruption")).isTrue();
    }

    @Test
    void identicalReregistrationIsNoOp() {
        AgendaEventTypeRegistry registry = AgendaEventTypeRegistry.withBornTypes();
        registry.register(AgendaEventTypes.BILLING_SCHEDULE);
        assertThat(registry.registeredCodes()).hasSize(2);
    }

    @Test
    void divergentReregistrationIsRefused() {
        AgendaEventTypeRegistry registry = AgendaEventTypeRegistry.withBornTypes();
        assertThatIllegalStateException().isThrownBy(() -> registry.register(
                new AgendaEventType("billing-schedule", "Outro dono", "ED-99", "operations")));
    }

    @Test
    void eventRejectsInvertedWindowAndMissingAddress() {
        UUID tenant = UUID.randomUUID();
        Instant start = Instant.parse("2026-03-10T03:00:00Z");
        assertThatIllegalArgumentException().isThrownBy(() -> new AgendaEvent(
                UUID.randomUUID(), tenant, "billing-schedule", "x",
                start, start.minusSeconds(1), "ITEM", UUID.randomUUID(), "/x"));
        assertThatIllegalArgumentException().isThrownBy(() -> new AgendaEvent(
                UUID.randomUUID(), tenant, "billing-schedule", "x",
                start, null, "ITEM", UUID.randomUUID(), " "));
    }

    @Test
    void typeRejectsNonKebabCode() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new AgendaEventType("BillingSchedule", "x", "P2.5", "billing"));
    }
}

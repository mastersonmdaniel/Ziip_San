package br.com.ziipsan.compliance.lgpd.incident;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Relógio do incidente, alertas da Central, parâmetros e prazos da ANPD (P3.10).
 * Relógio injetável ({@code Clock.fixed}): nenhum teste dorme nem lê o relógio da máquina.
 * Datas conferidas à mão: conhecimento em 2026-11-06T10:00:00Z (sexta-feira).
 */
class IncidentClockTest {

    private static final Instant KNOWN = Instant.parse("2026-11-06T10:00:00Z");

    private static IncidentClock clock48() {
        return IncidentClock.start(Clock.fixed(KNOWN, ZoneOffset.UTC), 48);
    }

    @Test
    @DisplayName("relógio começa no conhecimento e o prazo do aviso é conhecimento + lgpd.operator_notice_hours")
    void startsAtKnowledgeAndDeadline() {
        IncidentClock clock = clock48();
        assertThat(clock.knownAt()).isEqualTo(KNOWN);
        assertThat(clock.operatorNoticeDeadline()).isEqualTo(Instant.parse("2026-11-08T10:00:00Z"));
    }

    @Test
    @DisplayName("alertas da Central a 24 h e a 48 h do conhecimento")
    void centralAlertInstants() {
        assertThat(clock48().centralAlertInstants()).containsExactly(
                Instant.parse("2026-11-07T10:00:00Z"),
                Instant.parse("2026-11-08T10:00:00Z"));
    }

    @Test
    @DisplayName("alerta devido: antes de 24 h nenhum; a 24 h só o de 24; a 48 h os dois; emitido não repete")
    void dueCentralAlerts() {
        IncidentClock clock = clock48();
        assertThat(clock.dueCentralAlerts(Instant.parse("2026-11-07T09:59:59Z"), List.of())).isEmpty();
        assertThat(clock.dueCentralAlerts(Instant.parse("2026-11-07T10:00:00Z"), List.of()))
                .containsExactly(24);
        assertThat(clock.dueCentralAlerts(Instant.parse("2026-11-08T10:00:00Z"), List.of()))
                .containsExactly(24, 48);
        assertThat(clock.dueCentralAlerts(Instant.parse("2026-11-08T10:00:00Z"), List.of(24)))
                .containsExactly(48);
        assertThat(clock.dueCentralAlerts(Instant.parse("2026-11-09T10:00:00Z"), List.of(24, 48)))
                .isEmpty();
    }

    @Test
    @DisplayName("aviso vencido só depois do prazo, nunca no instante exato")
    void overdue() {
        IncidentClock clock = clock48();
        assertThat(clock.operatorNoticeOverdue(Instant.parse("2026-11-08T10:00:00Z"))).isFalse();
        assertThat(clock.operatorNoticeOverdue(Instant.parse("2026-11-08T10:00:01Z"))).isTrue();
    }

    @Test
    @DisplayName("lgpd.operator_notice_hours classe c: reduzir pode; acima do teto ou acima do aceito, recusa; sem teto (P3.9), recusa")
    void operatorNoticeHoursOnlyReduces() {
        LgpdIncidentParameters.validateOperatorNoticeHours(24, 48, 48);
        LgpdIncidentParameters.validateOperatorNoticeHours(48, 48, null);
        assertThatThrownBy(() -> LgpdIncidentParameters.validateOperatorNoticeHours(72, 48, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("só reduz");
        assertThatThrownBy(() -> LgpdIncidentParameters.validateOperatorNoticeHours(36, 48, 24))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("só reduz");
        assertThatThrownBy(() -> LgpdIncidentParameters.validateOperatorNoticeHours(24, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("P3.9");
        assertThatThrownBy(() -> LgpdIncidentParameters.validateOperatorNoticeHours(0, 48, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ret.incident_record_years: 5 anos exatos; outro valor é recusado e a retenção cai em +5 anos")
    void retentionFiveYears() {
        LgpdIncidentParameters.validateIncidentRecordYears(5);
        assertThatThrownBy(() -> LgpdIncidentParameters.validateIncidentRecordYears(3))
                .isInstanceOf(IllegalArgumentException.class);
        PersonalDataIncident incident = PersonalDataIncident.open(UUID.randomUUID(), clock48(),
                Set.of(DataCategory.ID, DataCategory.SENS),
                List.of("customers.document_number", "customers.health_note"), 5, null);
        assertThat(incident.retainUntil()).isEqualTo(LocalDate.of(2031, 11, 6));
        assertThat(incident.scope()).containsExactlyInAnyOrder(DataCategory.ID, DataCategory.SENS);
    }

    @Test
    @DisplayName("incidente sem ocorrência da P2.25 ou sem escopo DAD-29 é recusado")
    void incidentRequiresOccurrenceAndScope() {
        assertThatThrownBy(() -> PersonalDataIncident.open(null, clock48(),
                Set.of(DataCategory.ID), List.of("customers.document_number"), 5, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PersonalDataIncident.open(UUID.randomUUID(), clock48(),
                Set.of(), List.of("customers.document_number"), 5, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("DAD-29");
    }

    @Test
    @DisplayName("ANPD: 3 dias úteis e complementação em 20 dias úteis (Res. CD/ANPD 15/2024), pulando o fim de semana")
    void anpdBusinessDays() {
        // conhecimento do controlador numa sexta (2026-11-06): 3 dias úteis = qua 2026-11-11
        assertThat(AnpdDeadlines.communicationDeadline(LocalDate.of(2026, 11, 6), Set.of()))
                .isEqualTo(LocalDate.of(2026, 11, 11));
        // 20 dias úteis a partir de sex 2026-11-06 = sex 2026-12-04
        assertThat(AnpdDeadlines.supplementDeadline(LocalDate.of(2026, 11, 6), Set.of()))
                .isEqualTo(LocalDate.of(2026, 12, 4));
        // feriado informado empurra o prazo: qua 2026-11-11 feriado -> 3 dias úteis = qui 2026-11-12
        assertThat(AnpdDeadlines.communicationDeadline(LocalDate.of(2026, 11, 6),
                Set.of(LocalDate.of(2026, 11, 11)))).isEqualTo(LocalDate.of(2026, 11, 12));
    }

    @Test
    @DisplayName("aviso ao controlador se registra no incidente sem perder vínculos nem retenção (cópia; original imutável)")
    void controllerNoticeRegistration() {
        PersonalDataIncident incident = PersonalDataIncident.open(UUID.randomUUID(), clock48(),
                Set.of(DataCategory.CT), List.of("customers.phone"), 5, null);
        assertThat(incident.controllerNotifiedAt()).isNull();
        PersonalDataIncident notified = incident.withControllerNotifiedAt(
                Instant.parse("2026-11-07T09:00:00Z"));
        assertThat(notified.controllerNotifiedAt()).isEqualTo(Instant.parse("2026-11-07T09:00:00Z"));
        assertThat(notified.occurrenceId()).isEqualTo(incident.occurrenceId());
        assertThat(notified.retainUntil()).isEqualTo(incident.retainUntil());
        assertThat(incident.controllerNotifiedAt()).isNull();
    }

    @Test
    @DisplayName("JSON do incidente sai pela classe Json do core com o vínculo preparado da P3.40")
    void incidentJson() {
        PersonalDataIncident incident = PersonalDataIncident.open(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                clock48(), Set.of(DataCategory.FIN), List.of("bills.payer_document"), 5, null);
        String json = incident.toJson();
        assertThat(json).contains("\"occurrenceId\":\"00000000-0000-0000-0000-000000000001\"");
        assertThat(json).contains("\"processingRecordRef\":null");
        assertThat(json).contains("\"retainUntil\":\"2031-11-06\"");
    }
}

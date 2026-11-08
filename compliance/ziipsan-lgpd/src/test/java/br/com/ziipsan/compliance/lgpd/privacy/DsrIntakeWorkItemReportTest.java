package br.com.ziipsan.compliance.lgpd.privacy;

import br.com.ziipsan.core.json.Json;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Abertura como protocolo da P1.36, fila do encarregado na caixa da P2.28 e relatório
 * do encarregado. Códigos de especificação/finalidade e a permissão são dados de teste:
 * no produto vêm do catálogo do tenant e do catálogo de permissões da P0.13.
 */
class DsrIntakeWorkItemReportTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID SUBJECT = UUID.randomUUID();
    private static final LocalDate OPENED = LocalDate.of(2026, 11, 7);

    @Test
    @DisplayName("pedido nasce como protocolo, com prazo de 15 dias do protocolo")
    void requestBornAsProtocol() {
        DsrIntake intake = DsrIntake.open(UUID.randomUUID(), "PRIVACIDADE_TITULAR", 15,
                DsrIntake.Channel.PORTAL, DataSubjectRight.ACCESS, SUBJECT, null, OPENED);
        assertThat(intake.deadline().dueDate()).isEqualTo(LocalDate.of(2026, 11, 22));
        assertThat(intake.channel()).isEqualTo(DsrIntake.Channel.PORTAL);
    }

    @Test
    @DisplayName("especificação de privacidade com prazo diferente do legal é recusada")
    void specWithWrongDeadlineRefused() {
        assertThatThrownBy(() -> DsrIntake.open(UUID.randomUUID(), "PRIVACIDADE_TITULAR", 10,
                DsrIntake.Channel.COUNTER, DataSubjectRight.ACCESS, SUBJECT, null, OPENED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("art. 19, II");
    }

    @Test
    @DisplayName("sem protocolo não há pedido")
    void noProtocolNoRequest() {
        assertThatThrownBy(() -> DsrIntake.open(null, "PRIVACIDADE_TITULAR", 15,
                DsrIntake.Channel.PORTAL, DataSubjectRight.ACCESS, SUBJECT, null, OPENED))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("protocolo");
    }

    @Test
    @DisplayName("fila do encarregado: item HOLDER_REQUEST com responsável e prazo")
    void dpoQueueItemCreated() {
        UUID requestId = UUID.randomUUID();
        String json = DpoWorkItemFactory.createWorkItemJson(TENANT, requestId, SUBJECT,
                null, "lgpd:teste-fila", Instant.parse("2026-11-22T23:59:59Z"),
                Instant.parse("2026-11-07T12:00:00Z"), "teste.evento", 1, UUID.randomUUID());
        var node = Json.readTree(json);
        assertThat(node.get("type").asString()).isEqualTo("HOLDER_REQUEST");
        assertThat(node.get("objectType").asString()).isEqualTo("lgpd_data_subject_request");
        assertThat(node.get("objectId").asString()).isEqualTo(requestId.toString());
        assertThat(node.get("recordAddress").asString()).endsWith(requestId.toString());
        assertThat(node.get("dueAt").asString()).isEqualTo("2026-11-22T23:59:59Z");
    }

    @Test
    @DisplayName("item sem dono (sem responsável e sem permissão) é recusado")
    void ownerlessItemRefused() {
        assertThatThrownBy(() -> DpoWorkItemFactory.createWorkItemJson(TENANT, UUID.randomUUID(),
                SUBJECT, null, " ", Instant.parse("2026-11-22T23:59:59Z"),
                Instant.parse("2026-11-07T12:00:00Z"), "teste.evento", 1, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sem dono");
    }

    @Test
    @DisplayName("relatório: pedidos por tipo/situação/prazo, consentimentos, pendências e alertas")
    void reportAggregates() {
        List<DpoReport.RequestSummary> requests = List.of(
                new DpoReport.RequestSummary(DataSubjectRight.ACCESS, "IN_PROGRESS",
                        LocalDate.of(2026, 11, 20), false),
                new DpoReport.RequestSummary(DataSubjectRight.DELETION, "RECEIVED",
                        LocalDate.of(2026, 11, 1), false),
                new DpoReport.RequestSummary(DataSubjectRight.ACCESS, "COMPLETED",
                        LocalDate.of(2026, 10, 20), true));
        String json = DpoReport.buildJson(requests, LocalDate.of(2026, 11, 7),
                Map.of("COMUNICACAO", 5L), List.of("OP-CADASTRO"), Map.of("APPROVED", 2L),
                List.of(new ProcessingCoverage.ClassifiedColumn("customers", "email", "CT")),
                List.of("alerta de teste da P0.11"), false);
        var node = Json.readTree(json);
        assertThat(node.get("requestsTotal").asInt()).isEqualTo(3);
        assertThat(node.get("requestsByRight").get("ACCESS").asLong()).isEqualTo(2L);
        assertThat(node.get("requestsByStatus").get("RECEIVED").asLong()).isEqualTo(1L);
        assertThat(node.get("requestsOverdue").asLong()).isEqualTo(1L);
        assertThat(node.get("consentsByPurpose").get("COMUNICACAO").asLong()).isEqualTo(5L);
        assertThat(node.get("uncoveredPersonalDataColumns").get(0).get("column").asString())
                .isEqualTo("customers.email");
        assertThat(node.get("anomalousAccessAlerts").get(0).asString())
                .isEqualTo("alerta de teste da P0.11");
        assertThat(node.get("processingFactoryRecordsLoaded").asBoolean()).isFalse();
    }
}

package br.com.ziipsan.core.telemetry;

import br.com.ziipsan.core.api.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P4.8 / PLT-42 — TESTE OBRIGATÓRIO do aceite: "evento de telemetria com
 * identificador de pessoa ou com conteúdo é recusado". Cada recusa é
 * provada em duas camadas: a exceção carrega o código
 * {@code telemetry-forbidden-field} E o sumidouro fica vazio (nada foi
 * gravado antes da recusa virar exceção).
 */
class TelemetryPrivacyTest {

    private static final String TENANT = "11111111-1111-1111-1111-111111111111";
    private static final String BASE = "\"tenantId\":\"" + TENANT + "\",\"role\":\"OPERATOR\","
            + "\"kind\":\"FEATURE_USED\",\"featureKey\":\"billing.cycle\",\"occurredAt\":\"2026-11-07T12:00:00Z\"";

    private static final class ListSink implements ProductUsageTelemetry.TelemetrySink {
        final List<TelemetryEvent> events = new ArrayList<>();

        @Override
        public void accept(TelemetryEvent event) {
            events.add(event);
        }

        @Override
        public List<TelemetryEvent> events() {
            return events;
        }
    }

    private static ProductUsageTelemetry aggregated(ListSink sink) {
        return ProductUsageTelemetry.fromParameter(null, sink);
    }

    @Test
    @DisplayName("P4.8 OBRIGATÓRIO: evento com identificador de pessoa (userId) é recusado e nada é gravado")
    void refusesPersonIdentifier() {
        ListSink sink = new ListSink();
        String json = "{" + BASE + ",\"userId\":\"22222222-2222-2222-2222-222222222222\"}";
        assertThatThrownBy(() -> aggregated(sink).ingest(json))
                .isInstanceOf(ApiException.Validation.class)
                .hasMessageContaining("Há campos inválidos");
        try {
            aggregated(sink).ingest(json);
        } catch (ApiException.Validation v) {
            assertThat(v.errors()).anySatisfy(e ->
                    assertThat(e.code()).isEqualTo("telemetry-forbidden-field"));
        }
        assertThat(sink.events()).isEmpty();
    }

    @Test
    @DisplayName("P4.8 OBRIGATÓRIO: variações do identificador (person_id, email, cpf) são recusadas")
    void refusesPersonIdentifierVariants() {
        ListSink sink = new ListSink();
        for (String extra : List.of(
                "\"person_id\":\"abc\"",
                "\"personIdentifier\":\"abc\"",
                "\"email\":\"alguem@exemplo.invalid\"",
                "\"cpf\":\"00000000000\"",
                "\"actorId\":\"abc\"")) {
            assertThatThrownBy(() -> aggregated(sink).ingest("{" + BASE + "," + extra + "}"))
                    .isInstanceOf(ApiException.Validation.class);
        }
        assertThat(sink.events()).isEmpty();
    }

    @Test
    @DisplayName("P4.8 OBRIGATÓRIO: evento com conteúdo digitado é recusado e nada é gravado")
    void refusesTypedContent() {
        ListSink sink = new ListSink();
        for (String extra : List.of(
                "\"content\":\"texto digitado pelo usuário\"",
                "\"typedContent\":\"abc\"",
                "\"text\":\"abc\"",
                "\"details\":{\"message\":\"abc\"}")) {
            assertThatThrownBy(() -> aggregated(sink).ingest("{" + BASE + "," + extra + "}"))
                    .isInstanceOf(ApiException.Validation.class);
        }
        assertThat(sink.events()).isEmpty();
    }

    @Test
    @DisplayName("P4.8: evento com gravação/identificador de sessão é recusado (sem gravação de sessão, PLT-42)")
    void refusesSessionRecording() {
        ListSink sink = new ListSink();
        for (String extra : List.of(
                "\"sessionId\":\"abc\"",
                "\"sessionRecording\":\"https://exemplo.invalid/rec\"")) {
            assertThatThrownBy(() -> aggregated(sink).ingest("{" + BASE + "," + extra + "}"))
                    .isInstanceOf(ApiException.Validation.class);
        }
        assertThat(sink.events()).isEmpty();
    }

    @Test
    @DisplayName("P4.8: evento agregado válido (tenant + papel, sem pessoa) é aceito; o tipo não tem campo de pessoa")
    void acceptsAggregatedEvent() {
        ListSink sink = new ListSink();
        TelemetryEvent event = aggregated(sink).ingest("{" + BASE + "}");
        assertThat(event.tenantId().toString()).isEqualTo(TENANT);
        assertThat(event.role()).isEqualTo("OPERATOR");
        assertThat(event.featureKey()).isEqualTo("billing.cycle");
        assertThat(sink.events()).containsExactly(event);
        // Garantia estrutural: os componentes do record são só os agregados.
        assertThat(java.util.Arrays.stream(TelemetryEvent.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .containsExactly("tenantId", "role", "kind", "featureKey", "wizardKey",
                        "taskKey", "durationMillis", "occurredAt");
    }

    @Test
    @DisplayName("P4.8: campo desconhecido (fora da lista fechada) também recusa — telemetria não tem atributo livre")
    void refusesUnknownField() {
        ListSink sink = new ListSink();
        assertThatThrownBy(() -> aggregated(sink).ingest("{" + BASE + ",\"favoriteColor\":\"azul\"}"))
                .isInstanceOf(ApiException.Validation.class);
        assertThat(sink.events()).isEmpty();
    }

    @Test
    @DisplayName("P4.8: telemetry.product_usage=OFF não coleta nada; padrão (nulo) é AGGREGATED; valor inválido recusa")
    void modeOffCollectsNothingAndDefaultIsAggregated() {
        ListSink sink = new ListSink();
        assertThat(ProductUsageMode.fromParameter(null)).isEqualTo(ProductUsageMode.AGGREGATED);
        assertThat(ProductUsageMode.fromParameter("aggregated")).isEqualTo(ProductUsageMode.AGGREGATED);
        assertThatThrownBy(() -> ProductUsageMode.fromParameter("DETAILED"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("telemetry.product_usage");
        ProductUsageTelemetry off = ProductUsageTelemetry.fromParameter("OFF", sink);
        assertThatThrownBy(() -> off.ingest("{" + BASE + "}"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(sink.events()).isEmpty();
    }
}

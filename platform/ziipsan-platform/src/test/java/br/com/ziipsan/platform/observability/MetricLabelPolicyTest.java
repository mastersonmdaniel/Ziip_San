package br.com.ziipsan.platform.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * II-8: a política de rótulos — uc_id e identificadores por-UC são
 * proibidos sempre; tenant é restrito à allowlist da P0.8; o vocabulário
 * RED/USE declarado passa; rótulo desconhecido é violação.
 */
class MetricLabelPolicyTest {

    @Test
    @DisplayName("uc_id é violação FORBIDDEN em qualquer métrica")
    void ucIdIsForbidden() {
        List<MetricLabelPolicy.Violation> v =
                MetricLabelPolicy.validate("ziipsan_billing_batch_items_total", Set.of("uc_id"));
        assertThat(v).containsExactly(
                new MetricLabelPolicy.Violation("uc_id", MetricLabelPolicy.Violation.Kind.FORBIDDEN));
    }

    @Test
    @DisplayName("todos os identificadores por entidade e pessoais são FORBIDDEN")
    void allIdentifiersForbidden() {
        for (String label : List.of("consumer_unit_id", "customer_id", "bill_id", "meter_id",
                "reading_id", "service_order_id", "cpf", "cnpj", "email", "trace_id")) {
            assertThat(MetricLabelPolicy.validate("qualquer_metrica", Set.of(label)))
                    .as("rótulo %s", label)
                    .containsExactly(new MetricLabelPolicy.Violation(
                            label, MetricLabelPolicy.Violation.Kind.FORBIDDEN));
        }
    }

    @Test
    @DisplayName("tenant fora da allowlist é RESTRICTED_NOT_ALLOWLISTED; dentro, passa")
    void tenantRestrictedToAllowlist() {
        assertThat(MetricLabelPolicy.validate("http.server.requests", Set.of("tenant")))
                .containsExactly(new MetricLabelPolicy.Violation(
                        "tenant", MetricLabelPolicy.Violation.Kind.RESTRICTED_NOT_ALLOWLISTED));
        assertThat(MetricLabelPolicy.validate("ziipsan_business_requests_total",
                Set.of("tenant", "route_group"))).isEmpty();
    }

    @Test
    @DisplayName("vocabulário RED/USE declarado passa sem violações")
    void redUseVocabularyPasses() {
        assertThat(MetricLabelPolicy.validate("http.server.requests",
                Set.of("method", "uri", "status", "outcome", "exception"))).isEmpty();
        assertThat(MetricLabelPolicy.validate("ziipsan_billing_batch_items_total",
                Set.of("outcome"))).isEmpty();
        assertThat(MetricLabelPolicy.validate("hikaricp_connections", Set.of("pool"))).isEmpty();
    }

    @Test
    @DisplayName("rótulo desconhecido é violação UNKNOWN (vocabulário é fechado)")
    void unknownLabelIsViolation() {
        assertThat(MetricLabelPolicy.validate("metrica", Set.of("cidade")))
                .containsExactly(new MetricLabelPolicy.Violation(
                        "cidade", MetricLabelPolicy.Violation.Kind.UNKNOWN));
    }

    @Test
    @DisplayName("nome de métrica vazio e rótulos null são erro explícito")
    void explicitErrors() {
        assertThatThrownBy(() -> MetricLabelPolicy.validate(" ", Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MetricLabelPolicy.validate("metrica", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

package br.com.ziipsan.app.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** P0.8: o grupo de rota é o primeiro segmento após /api/v1 — nunca a rota completa. */
class BusinessMetricsInterceptorTest {

    @Test
    @DisplayName("grupo = primeiro segmento apos /api/v1; fora de /api/v1 nao conta")
    void routeGroup() {
        assertThat(BusinessMetricsInterceptor.routeGroup("/api/v1/registry/customers/search"))
                .isEqualTo("registry");
        assertThat(BusinessMetricsInterceptor.routeGroup("/api/v1/me")).isEqualTo("me");
        assertThat(BusinessMetricsInterceptor.routeGroup("/api/v1/me?x=1")).isEqualTo("me");
        assertThat(BusinessMetricsInterceptor.routeGroup("/actuator/health")).isNull();
        assertThat(BusinessMetricsInterceptor.routeGroup("/api/v1/")).isNull();
        assertThat(BusinessMetricsInterceptor.routeGroup(null)).isNull();
    }
}

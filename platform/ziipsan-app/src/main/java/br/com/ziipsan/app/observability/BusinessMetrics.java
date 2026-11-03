package br.com.ziipsan.app.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * P0.8: a ÚNICA métrica de negócio com rótulo de tenant (ver metrics-catalog.md).
 *
 * <p>{@code ziipsan_business_requests_total{route_group, tenant}}: um contador agregado por
 * grupo de rota (primeiro segmento após {@code /api/v1}, ex.: {@code registry}, {@code me}),
 * não por rota individual e não por usuário. O grupo limita a cardinalidade de um lado e o
 * número de tenants do outro; o orçamento em metrics-budget.yml assume no máximo 20 grupos
 * × 200 tenants = 4.000 séries para este contador. Um grupo novo ou um tenant além disso
 * exige revisão do orçamento, não silêncio.
 *
 * <p>Telemetria de produto com privacidade é P4.8: aqui ela é só DECLARADA no catálogo,
 * não implementada — este contador é de operação (volume por grupo/tenant), não de produto.
 */
@Component
public class BusinessMetrics {

    static final String BUSINESS_COUNTER = "ziipsan_business_requests_total";

    private final MeterRegistry registry;

    public BusinessMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    void record(String routeGroup, UUID tenantId) {
        registry.counter(BUSINESS_COUNTER,
                "route_group", routeGroup,
                "tenant", tenantId == null ? "anonymous" : tenantId.toString()).increment();
    }
}

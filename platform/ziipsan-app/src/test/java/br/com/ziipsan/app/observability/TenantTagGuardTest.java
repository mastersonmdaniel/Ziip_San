package br.com.ziipsan.app.observability;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0.8: o guarda de rótulo de tenant, sem Spring e sem banco — puro Micrometer.
 * Um medidor qualquer com tag tenant/tenant_id perde a tag; o contador de negócio
 * listado mantém a sua.
 */
class TenantTagGuardTest {

    private SimpleMeterRegistry registry() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        registry.config().meterFilter(new TenantTagGuard());
        return registry;
    }

    private static List<String> keys(Iterable<Tag> tags) {
        return java.util.stream.StreamSupport.stream(tags.spliterator(), false)
                .map(Tag::getKey).toList();
    }

    @Test
    @DisplayName("timer HTTP com tag tenant perde a tag; demais tags ficam")
    void httpTimerLosesTenantTag() {
        SimpleMeterRegistry registry = registry();
        var timer = registry.timer("http.server.requests",
                "method", "GET", "uri", "/api/v1/me", "tenant", "11111111-1111-1111-1111-111111111111");
        assertThat(keys(timer.getId().getTags())).containsExactlyInAnyOrder("method", "uri");
    }

    @Test
    @DisplayName("timer HTTP com tag tenant_id perde a tag")
    void httpTimerLosesTenantIdTag() {
        SimpleMeterRegistry registry = registry();
        var timer = registry.timer("http.server.requests", "tenant_id", "abc", "status", "200");
        assertThat(keys(timer.getId().getTags())).containsExactly("status");
    }

    @Test
    @DisplayName("contador de negócio listado mantém tenant e route_group")
    void businessCounterKeepsTenantTag() {
        SimpleMeterRegistry registry = registry();
        var counter = registry.counter(BusinessMetrics.BUSINESS_COUNTER,
                "route_group", "me", "tenant", "11111111-1111-1111-1111-111111111111");
        assertThat(keys(counter.getId().getTags())).containsExactlyInAnyOrder("route_group", "tenant");
    }
}

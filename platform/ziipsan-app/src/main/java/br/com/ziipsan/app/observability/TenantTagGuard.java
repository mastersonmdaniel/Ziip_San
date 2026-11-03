package br.com.ziipsan.app.observability;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;

import java.util.List;
import java.util.Set;

/**
 * P0.8: guarda de cardinalidade do rótulo de tenant.
 *
 * <p>Regra do plano: latência por rota NÃO leva rótulo de tenant; o rótulo de tenant só existe
 * em contadores agregados por grupo de rota e num conjunto curto e LISTADO de métricas de
 * negócio (ver docs/observability/metrics-catalog.md). Este filtro a impõe no código, em vez
 * de confiar em convenção: qualquer medidor fora da allowlist que chegue com tag
 * {@code tenant} ou {@code tenant_id} tem a tag removida antes de ser registrado. A allowlist
 * é exatamente {@link BusinessMetrics#BUSINESS_COUNTER}; nada mais pode carregar tenant.
 *
 * <p>Sem este filtro, bastaria um {@code .tag("tenant_id", ...)} esquecido num timer HTTP para
 * multiplicar as séries ativas por tenant — e o orçamento de séries (metrics-budget.yml) ser
 * estourado em silêncio.
 */
public final class TenantTagGuard implements MeterFilter {

    static final Set<String> TENANT_TAGS = Set.of("tenant", "tenant_id");
    static final Set<String> TENANT_TAGGED_METERS = Set.of(BusinessMetrics.BUSINESS_COUNTER);

    @Override
    public Meter.Id map(Meter.Id id) {
        if (TENANT_TAGGED_METERS.contains(id.getName())) {
            return id;
        }
        List<Tag> kept = id.getTags().stream()
                .filter(tag -> !TENANT_TAGS.contains(tag.getKey()))
                .toList();
        if (kept.size() == id.getTags().size()) {
            return id;
        }
        return id.replaceTags(kept);
    }
}

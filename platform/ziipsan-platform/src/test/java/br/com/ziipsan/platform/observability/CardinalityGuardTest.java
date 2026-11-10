package br.com.ziipsan.platform.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * II-8, teste §4.2 da estimativa: a tentativa de registar uma métrica
 * com rótulo proibido é RECUSADA pela guarda — o medidor não existe no
 * registry depois da tentativa. Métricas conformes registam normalmente.
 */
class CardinalityGuardTest {

    private SimpleMeterRegistry registry() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        registry.config().meterFilter(new CardinalityGuard());
        return registry;
    }

    @Test
    @DisplayName("contador com uc_id é recusado: não existe no registry")
    void counterWithUcIdIsDenied() {
        SimpleMeterRegistry registry = registry();
        registry.counter("lote_itens", "uc_id", "12345").increment();
        assertThat(registry.find("lote_itens").counter()).isNull();
        assertThat(registry.getMeters()).isEmpty();
    }

    @Test
    @DisplayName("timer com bill_id é recusado")
    void timerWithBillIdIsDenied() {
        SimpleMeterRegistry registry = registry();
        registry.timer("lote_duracao", "bill_id", "abc");
        assertThat(registry.find("lote_duracao").timer()).isNull();
    }

    @Test
    @DisplayName("métrica conforme (outcome) regista e conta")
    void conformingMeterRegisters() {
        SimpleMeterRegistry registry = registry();
        registry.counter("lote_itens", "outcome", "SUCCESS").increment();
        registry.counter("lote_itens", "outcome", "SUCCESS").increment();
        assertThat(registry.find("lote_itens").tag("outcome", "SUCCESS").counter().count())
                .isEqualTo(2.0);
    }

    @Test
    @DisplayName("métrica com tenant NÃO é negada por esta guarda (domínio do TenantTagGuard da P0.8)")
    void tenantTagIsNotThisGuardsDomain() {
        SimpleMeterRegistry registry = registry();
        registry.counter("http.server.requests", "tenant", "t-1").increment();
        assertThat(registry.find("http.server.requests").counter()).isNotNull();
    }
}

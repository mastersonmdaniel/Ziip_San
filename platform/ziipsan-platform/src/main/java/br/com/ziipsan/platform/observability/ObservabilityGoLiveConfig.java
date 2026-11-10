package br.com.ziipsan.platform.observability;

import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * II-8: fiação da guarda de cardinalidade do go-live.
 *
 * <p>Configuração própria e sem dependências, pelo mesmo motivo da
 * {@code TenantTagGuardConfig} da P0.8 (módulo app): o registry precisa
 * de todos os {@code MeterFilter} para ser criado; um filtro com
 * dependências criaria referência circular. A app varre
 * {@code br.com.ziipsan.platform} (ver {@code ZiipsanApplication}), logo
 * este bean é apanhado sem tocar em nenhuma configuração existente.
 * Compor com o {@code TenantTagGuard} é seguro: filtros de negação e de
 * remapeamento actuam sobre conjuntos de rótulos disjuntos (proibidos
 * sempre × restritos de tenant).
 */
@Configuration
public class ObservabilityGoLiveConfig {

    @Bean
    MeterFilter cardinalityGuard() {
        return new CardinalityGuard();
    }
}

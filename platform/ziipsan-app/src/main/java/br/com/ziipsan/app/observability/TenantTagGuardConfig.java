package br.com.ziipsan.app.observability;

import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * P0.8: o guarda de rótulo de tenant como bean, em configuração PRÓPRIA e sem
 * dependências — de propósito, não por organização.
 *
 * <p>O PrometheusMeterRegistry precisa de todos os MeterFilters para ser criado; se o
 * filtro vivesse na ObservabilityConfig (que precisa do interceptor, que precisa do
 * BusinessMetrics, que precisa do registry), o contexto morreria em referência circular
 * (observabilityConfig → interceptor → businessMetrics → registry → observabilityConfig).
 * Separado, a ordem é: filtro → registry → métricas → interceptor → config MVC.
 */
@Configuration
public class TenantTagGuardConfig {

    @Bean
    MeterFilter tenantTagGuard() {
        return new TenantTagGuard();
    }
}

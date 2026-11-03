package br.com.ziipsan.app.observability;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * P0.8: fiação da observabilidade mínima (só o interceptor do contador de negócio).
 *
 * <p>As métricas HTTP e do Hikari, o endpoint Prometheus e os traces vêm da auto-configuração
 * do Boot (ver application.yml: exposure + tracing); o guarda de rótulo de tenant vive em
 * TenantTagGuardConfig, separado para não criar referência circular com o MeterRegistry.
 * Nada aqui cria série por usuário, por ID de recurso ou por tenant fora da allowlist.
 */
@Configuration
public class ObservabilityConfig implements WebMvcConfigurer {

    private final BusinessMetricsInterceptor interceptor;

    public ObservabilityConfig(BusinessMetricsInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/v1/**");
    }
}

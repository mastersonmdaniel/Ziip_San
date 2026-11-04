package br.com.ziipsan.integration;

import br.com.ziipsan.integration.webhook.WebhookHttpSender;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Beans da API pública que não são serviços (P2.26). Os serviços
 * ({@code ApiClientService}, {@code ApiTokenService},
 * {@code PublicApiService}, {@code WebhookService},
 * {@code TenantParameterReader}, {@code ApiRateLimiter}) são
 * {@code @Service} e entram pelo scan de
 * {@code br.com.ziipsan.integration} na aplicação.
 */
@Configuration
public class IntegrationConfig {

    @Bean
    public WebhookHttpSender webhookHttpSender() {
        return new WebhookHttpSender.Default();
    }

    @Bean
    public PublicApiVersions publicApiVersions() {
        return PublicApiVersions.current();
    }
}

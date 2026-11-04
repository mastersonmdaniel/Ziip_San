package br.com.ziipsan.app.importer;

import br.com.ziipsan.commercial.registry.importer.ImportService;
import br.com.ziipsan.platform.infra.queue.MessageHandler;
import br.com.ziipsan.platform.tenant.TenantCatalog;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * P1.40: os três handlers da importação (um por tipo da outbox, porque a
 * interface da P1.9 tem um tipo por handler). O worker futuro os recebe num
 * mapa tipo → handler; sem o runner, eles existem no contexto e são
 * exercidos por chamada direta no IT.
 */
@Configuration
public class ImportJobHandlerConfig {

    @Bean
    public MessageHandler importValidateHandler(TenantCatalog catalog, ImportService imports) {
        return new ImportJobHandler(catalog, imports, ImportService.VALIDATE_JOB_TYPE);
    }

    @Bean
    public MessageHandler importApplyHandler(TenantCatalog catalog, ImportService imports) {
        return new ImportJobHandler(catalog, imports, ImportService.APPLY_JOB_TYPE);
    }

    @Bean
    public MessageHandler importReverseHandler(TenantCatalog catalog, ImportService imports) {
        return new ImportJobHandler(catalog, imports, ImportService.REVERSE_JOB_TYPE);
    }
}

package br.com.ziipsan.app.job;

import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.platform.tenant.TenantCatalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Disparo do job da P0.12, no padrão do job {@code migrate}: com o perfil
 * {@code mask-recalculation} ativo, resolve o tenant pedido em
 * {@code ziipsan.mask-recalculation.tenant-id}, roda o {@link MaskRecalculationJob} e
 * encerra o processo com código 0 (desligável por
 * {@code ziipsan.mask-recalculation.exit-after=false}, para os testes de perfil não
 * matarem a JVM do Surefire).
 */
@Slf4j
@Component
@Profile("mask-recalculation")
@RequiredArgsConstructor
public class MaskRecalculationRunner implements ApplicationRunner {

    private final MaskRecalculationJob job;
    private final TenantCatalog catalog;
    private final ApplicationContext context;

    @Override
    public void run(ApplicationArguments args) {
        String tenantIdText = context.getEnvironment().getProperty("ziipsan.mask-recalculation.tenant-id");
        if (tenantIdText == null || tenantIdText.isBlank()) {
            throw new IllegalStateException(
                    "job mask-recalculation sem tenant: defina ziipsan.mask-recalculation.tenant-id");
        }
        UUID tenantId = UUID.fromString(tenantIdText.trim());
        TenantCatalog.Entry entry = catalog.find(tenantId)
                .filter(TenantCatalog.Entry::active)
                .orElseThrow(() -> new IllegalStateException("tenant inexistente ou inativo: " + tenantId));
        try {
            TenantContextHolder.setTenant(entry.schema());
            TenantContextHolder.setTenantId(tenantId);
            MaskRecalculationJob.Report report = job.run();
            log.info("Job mask-recalculation concluído para o tenant {}: {}", tenantId, report);
        } finally {
            TenantContextHolder.clear();
        }
        boolean exitAfter = Boolean.parseBoolean(
                context.getEnvironment().getProperty("ziipsan.mask-recalculation.exit-after", "true"));
        if (!exitAfter) {
            log.info("Job mask-recalculation: exit-after=false, processo fica no ar (modo teste)");
            return;
        }
        int code = SpringApplication.exit(context, () -> 0);
        log.info("Job mask-recalculation concluído, saindo com código {}", code);
        System.exit(code);
    }
}

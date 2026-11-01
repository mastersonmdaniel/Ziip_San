package br.com.sigsan.app.dev;

import br.com.sigsan.platform.tenant.TenantCatalog;
import br.com.sigsan.platform.tenant.TenantProvisioningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * SÓ NO PERFIL local: provisiona um tenant de desenvolvimento se ele ainda não existir.
 *
 * <p>Ainda não há API de provisionamento; sem tenant no catálogo, um banco de desenvolvimento novo não
 * atende nenhuma requisição (o JwtTenantFilter recusa tenant desconhecido). O tenant é sintético:
 * nome e e-mail de exemplo, sem CNPJ, e o administrador local recebe o hash de uma senha aleatória
 * descartada (o login é pelo Keycloak). O UUID precisa ser o mesmo do atributo tenant_id do usuário
 * de desenvolvimento (scripts/kc-dev-user.sh).
 *
 * <p>Variáveis: SIGSAN_DEV_TENANT_ID (UUID) e SIGSAN_DEV_TENANT_SCHEMA (ex.: dev_local).
 */
@Slf4j
@Component
@Profile("local")
@ConditionalOnProperty(name = "sigsan.dev.tenant.id")
@RequiredArgsConstructor
public class DevTenantBootstrap implements ApplicationRunner {

    private final TenantCatalog catalog;
    private final TenantProvisioningService provisioning;
    private final org.springframework.core.env.Environment env;

    @Override
    public void run(ApplicationArguments args) {
        UUID tenantId = UUID.fromString(env.getRequiredProperty("sigsan.dev.tenant.id"));
        String schema = env.getRequiredProperty("sigsan.dev.tenant.schema");
        if (catalog.find(tenantId).isPresent()) {
            log.info("Tenant de desenvolvimento {} já existe", schema);
            return;
        }
        var result = provisioning.provisionTenant(TenantProvisioningService.TenantProvisioningRequest.builder()
                .tenantId(tenantId)
                .schemaName(schema)
                .displayName("Tenant de desenvolvimento (dados sintéticos)")
                .email("dev@sigsan.invalid")
                .plan("DEV")
                .build());
        if (!result.success()) {
            throw new IllegalStateException("Falha ao provisionar o tenant de desenvolvimento: " + result.error());
        }
        log.info("Tenant de desenvolvimento {} ({}) provisionado", schema, tenantId);
    }
}

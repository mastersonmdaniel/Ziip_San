package br.com.ziipsan.app.dev;

import br.com.ziipsan.platform.tenant.TenantCatalog;
import br.com.ziipsan.platform.tenant.TenantProvisioningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * SÓ NO PERFIL migrate (P0.6, antes: só local): provisiona um tenant de desenvolvimento
 * se ele ainda não existir.
 *
 * <p>Motivo: o provisionamento injeta TenantProvisioningService, que só existe no perfil
 * migrate. Com o perfil antigo (só local), a api local falharia por bean ausente. O fluxo
 * de dev agora é em 2 passos (scripts/run-app.sh): o job migrate roda com os perfis
 * {@code local,migrate} (e provisiona o tenant de dev, se ZIIPSAN_DEV_TENANT_ID estiver
 * definido) e só depois a api sobe com o perfil {@code local} sozinho.
 *
 * <p>Ainda não há API de provisionamento; sem tenant no catálogo, um banco de desenvolvimento novo não
 * atende nenhuma requisição (o JwtTenantFilter recusa tenant desconhecido). O tenant é sintético:
 * nome e e-mail de exemplo, sem CNPJ, e o administrador local recebe o hash de uma senha aleatória
 * descartada (o login é pelo Keycloak). O UUID precisa ser o mesmo do atributo tenant_id do usuário
 * de desenvolvimento (scripts/kc-dev-user.sh).
 *
 * <p>Variáveis: ZIIPSAN_DEV_TENANT_ID (UUID) e ZIIPSAN_DEV_TENANT_SCHEMA (ex.: dev_local).
 */
@Slf4j
@Component
@Profile("migrate")
@ConditionalOnProperty(name = "ziipsan.dev.tenant.id")
@RequiredArgsConstructor
public class DevTenantBootstrap implements ApplicationRunner {

    private final TenantCatalog catalog;
    private final TenantProvisioningService provisioning;
    private final org.springframework.core.env.Environment env;

    @Override
    public void run(ApplicationArguments args) {
        UUID tenantId = UUID.fromString(env.getRequiredProperty("ziipsan.dev.tenant.id"));
        String schema = env.getRequiredProperty("ziipsan.dev.tenant.schema");
        if (catalog.find(tenantId).isPresent()) {
            log.info("Tenant de desenvolvimento {} já existe", schema);
            return;
        }
        var result = provisioning.provisionTenant(TenantProvisioningService.TenantProvisioningRequest.builder()
                .tenantId(tenantId)
                .schemaName(schema)
                .displayName("Tenant de desenvolvimento (dados sintéticos)")
                .email("dev@ziipsan.invalid")
                .plan("DEV")
                .build());
        if (!result.success()) {
            throw new IllegalStateException("Falha ao provisionar o tenant de desenvolvimento: " + result.error());
        }
        log.info("Tenant de desenvolvimento {} ({}) provisionado", schema, tenantId);
    }
}

package br.com.sigsan.app;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Aplicação inteira (contexto Spring real, filtros de segurança reais) sobre o banco de integração
 * descartável e o tenant de {@link ProvisionedTenant}. Só o emissor dos tokens é do teste.
 *
 * <p>migrate-on-startup fica desligado: o banco de integração é compartilhado pelos módulos do build
 * e contém tenants de outros testes; o tenant usado aqui já foi migrado por ProvisionedTenant.
 */
@SpringBootTest(classes = SigsanApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
abstract class AppIntegrationTestBase {

    @Autowired
    protected MockMvc mvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        ProvisionedTenant.ensure();
        registry.add("sigsan.db.url", () -> ProvisionedTenant.url);
        registry.add("sigsan.db.app-user", () -> ProvisionedTenant.appUser);
        registry.add("sigsan.db.app-password", () -> ProvisionedTenant.appPassword);
        registry.add("sigsan.db.migrator-user", () -> ProvisionedTenant.migratorUser);
        registry.add("sigsan.db.migrator-password", () -> ProvisionedTenant.migratorPassword);
        registry.add("sigsan.db.migrate-on-startup", () -> "false");
        registry.add("sigsan.oidc.issuer", () -> TestTokens.ISSUER);
        registry.add("sigsan.oidc.audience", () -> TestTokens.AUDIENCE);
        registry.add("sigsan.oidc.public-key", TestTokens::publicKeyBase64);
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }
}

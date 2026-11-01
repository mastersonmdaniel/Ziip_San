package br.com.ziipsan.app;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Guardas de arquitetura (P0.3, ArchUnit). Nao exigem banco: analisam o bytecode do
 * proprio modulo app. As duas regras congelam remocoes da P0.25 — se alguem reintroduzir
 * o client admin do Keycloak ou o Jackson 2 no codigo da aplicacao, este teste quebra
 * antes de qualquer teste de integracao.
 */
class ArchitectureTest {

    private static final String APP_PACKAGE = "br.com.ziipsan.app";

    @Test
    void appCodeDoesNotUseJackson2() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(APP_PACKAGE + "..")
                .should().dependOnClassesThat().resideInAnyPackage("com.fasterxml.jackson.databind..")
                .because("P0.25 migrou o JSON para o Json do core (Jackson 3, tools.jackson); "
                        + "o Jackson 2 foi removido do pom do core e nao pode voltar pela app");
        rule.check(new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(APP_PACKAGE));
    }

    @Test
    void appCodeDoesNotUseKeycloakAdminClient() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(APP_PACKAGE + "..")
                .should().dependOnClassesThat().resideInAnyPackage("org.keycloak..")
                .because("P0.25 apagou KeycloakAdminService/KeycloakConfig e a dependencia "
                        + "keycloak-admin-client; o acesso continua via resource server OAuth2, "
                        + "sem client admin no processo da API");
        rule.check(new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(APP_PACKAGE));
    }
}

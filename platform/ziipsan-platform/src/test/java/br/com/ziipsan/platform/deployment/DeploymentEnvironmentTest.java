package br.com.ziipsan.platform.deployment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** P0.42: resolução do ambiente registrado, sem inventar nome de produção. */
class DeploymentEnvironmentTest {

    @Test
    @DisplayName("valor configurado vence o perfil")
    void configuredWins() {
        assertThat(DeploymentEnvironment.resolve("prod", new String[]{"local"})).isEqualTo("prod");
    }

    @Test
    @DisplayName("perfil de modo (migrate, worker) nunca é ambiente; o próximo perfil vale")
    void modeProfilesAreSkipped() {
        assertThat(DeploymentEnvironment.resolve(null, new String[]{"migrate", "prod"})).isEqualTo("prod");
        assertThat(DeploymentEnvironment.resolve("  ", new String[]{"worker"})).isEqualTo("dev");
    }

    @Test
    @DisplayName("sem configuração e sem perfil de ambiente: dev")
    void fallbackIsDev() {
        assertThat(DeploymentEnvironment.resolve(null, null)).isEqualTo("dev");
        assertThat(DeploymentEnvironment.resolve(null, new String[]{"migrate"})).isEqualTo("dev");
        assertThat(DeploymentEnvironment.resolve("", new String[0])).isEqualTo("dev");
    }
}

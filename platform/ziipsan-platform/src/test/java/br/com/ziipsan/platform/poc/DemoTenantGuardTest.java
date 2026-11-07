package br.com.ziipsan.platform.poc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemoTenantGuardTest {

    @Test
    @DisplayName("configuração válida passa: tenant próprio, fiscal falso, 12 mil unidades, tudo marcado")
    void configuracaoValida() {
        assertThatCode(() -> DemoTenantGuard.check(
                "demo-tenant", "demo_poc", "dev-tenant", "dev_local",
                false, 12_000, 12_000, 12_000)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("tenant de demonstração igual ao de desenvolvimento é recusado")
    void tenantIgualRecusado() {
        assertThatThrownBy(() -> DemoTenantGuard.check(
                "mesmo", "demo_poc", "mesmo", "dev_local", false, 12_000, 12_000, 12_000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("separada da homologação");
    }

    @Test
    @DisplayName("schema de demonstração igual ao de desenvolvimento é recusado")
    void schemaIgualRecusado() {
        assertThatThrownBy(() -> DemoTenantGuard.check(
                "demo-tenant", "dev_local", "dev-tenant", "dev_local",
                false, 12_000, 12_000, 12_000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("separada da homologação");
    }

    @Test
    @DisplayName("fiscal liberado em perfil local é recusado")
    void fiscalLiberadoRecusado() {
        assertThatThrownBy(() -> DemoTenantGuard.check(
                "demo-tenant", "demo_poc", "dev-tenant", "dev_local",
                true, 12_000, 12_000, 12_000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fiscal");
    }

    @Test
    @DisplayName("9.999 unidades ficam abaixo do piso de 10 mil do aceite")
    void abaixoDoPisoRecusado() {
        assertThatThrownBy(() -> DemoTenantGuard.check(
                "demo-tenant", "demo_poc", "dev-tenant", "dev_local",
                false, 9_999, 9_999, 9_999))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10000");
    }

    @Test
    @DisplayName("um único documento sem a marca de sintético recusa a massa inteira")
    void documentoSemMarcaRecusado() {
        assertThatThrownBy(() -> DemoTenantGuard.check(
                "demo-tenant", "demo_poc", "dev-tenant", "dev_local",
                false, 12_000, 12_000, 11_999))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("marca de sintético");
    }
}

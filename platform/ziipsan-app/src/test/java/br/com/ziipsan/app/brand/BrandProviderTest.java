package br.com.ziipsan.app.brand;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P0.34: o provider le o pacote empacotado (copia sincronizada de
 * branding/ziipsan) e o controller expoe so campos de exibicao.
 * Nao usa contexto Spring: roda sem banco e sem Keycloak.
 */
class BrandProviderTest {

    @Test
    @DisplayName("pacote empacotado: nome, descricao, cores e textos do pacote")
    void packagedBrandMatchesPackage() {
        var provider = new BrandProvider("ziipsan");
        assertThat(provider.brand().productName()).isEqualTo("ZIIPSAN");
        assertThat(provider.brand().shortName()).isEqualTo("ZIIPSAN");
        assertThat(provider.brand().description()).isEqualTo("Sistema Integrado de Gestão de Saneamento");
        assertThat(provider.brand().brandPrimary()).isEqualTo("#0369a0");
        assertThat(provider.brand().themeColor().light()).isEqualTo("#0369a0");
        assertThat(provider.brand().themeColor().dark()).isEqualTo("#0b111e");
        assertThat(provider.texts().footer()).contains("ZIIPSAN");
    }

    @Test
    @DisplayName("controller: atribuicao resolve {productName} e nao vaza campo legal")
    void controllerExposesOnlyDisplayFields() {
        var response = new BrandController(new BrandProvider("ziipsan")).brand();
        assertThat(response.attributionText()).isEqualTo("operado com ZIIPSAN");
        assertThat(response.productName()).isEqualTo("ZIIPSAN");
    }

    @Test
    @DisplayName("pasta de marca inexistente impede subir (recurso ausente)")
    void missingBrandDirFailsFast() {
        assertThatThrownBy(() -> new BrandProvider("marca-que-nao-existe"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Pacote de marca ausente");
    }
}

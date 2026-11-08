package br.com.ziipsan.commercial.registry.recadastramento;

import br.com.ziipsan.commercial.registry.application.CadastralChangeService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ED-16 — os campos coletaveis em campo sao os PRE-DEFINIDOS do mapa da
 * P1.3/P1.31 (TR-234/SG-232): nenhum campo novo, nenhum tipo de OS novo.
 */
class RecadastramentoFieldCatalogTest {

    @Test
    void camposDoMapaDaP13SaoColetaveisComOTipoDeOsDoMapa() {
        assertThat(RecadastramentoFieldCatalog.isCollectable("CUSTOMER", "name")).isTrue();
        assertThat(RecadastramentoFieldCatalog.isCollectable("CONSUMER_UNIT", "builtAreaM2")).isTrue();
        assertThat(RecadastramentoFieldCatalog.osTypeFor("CONSUMER_UNIT", "dueDay"))
                .isEqualTo("ALTERNATIVE_DUE_DAY");
        assertThat(RecadastramentoFieldCatalog.osTypeFor("CONSUMER_UNIT", "subcategories"))
                .isEqualTo("ECONOMIES_CATEGORY_CHANGE");
        assertThat(RecadastramentoFieldCatalog.osTypeFor("CUSTOMER", "emails"))
                .isEqualTo("CUSTOMER_CONTACT_UPDATE");
    }

    @Test
    void campoForaDoMapaNaoEColetavel() {
        assertThat(RecadastramentoFieldCatalog.isCollectable("CUSTOMER", "campoInventado")).isFalse();
        assertThat(RecadastramentoFieldCatalog.isCollectable("CONSUMER_UNIT", "name")).isFalse();
        assertThatThrownBy(() -> RecadastramentoFieldCatalog.osTypeFor("CUSTOMER", "campoInventado"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fora do mapa");
    }

    @Test
    void documentoExigeValorMascaradoEOutrosCamposNao() {
        assertThat(RecadastramentoFieldCatalog.requiresMaskedValue("CUSTOMER", "document")).isTrue();
        assertThat(RecadastramentoFieldCatalog.requiresMaskedValue("CUSTOMER", "name")).isFalse();
        assertThat(RecadastramentoFieldCatalog.requiresMaskedValue("CONSUMER_UNIT", "builtAreaM2")).isFalse();
    }

    @Test
    void catalogoExpoeExatamenteAsChavesDoMapaDaP13() {
        assertThat(RecadastramentoFieldCatalog.collectableFieldKeys())
                .hasSize(CadastralChangeService.FIELD_OS_TYPES.size())
                .contains("CUSTOMER.name", "CUSTOMER.document", "CONSUMER_UNIT.dueDay",
                        "CONSUMER_UNIT.subcategories");
    }
}

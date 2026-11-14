package br.com.ziipsan.commercial.fiscal.relief;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Grupo 10 — Fiscal: cadastros governados CFOP/NCM, tipos de substituicao e contingencia. */
class FiscalRegistriesTest {

    @Test
    @DisplayName("CFOP: 4 digitos; usado nao se apaga (so inactiva); inactivo recusado na resolucao")
    void cfopGovernance() {
        var registry = new FiscalCodeRegistry(FiscalCodeRegistry.Kind.CFOP);
        registry.register("5102", "Venda de producao do estabelecimento (ficticio)");
        assertThatThrownBy(() -> registry.register("510", "curto"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.delete("5102", code -> true))
                .isInstanceOf(IllegalStateException.class);
        registry.delete("5102", code -> false); // nunca usado: apaga
        registry.register("5102", "Venda ficticia");
        registry.deactivate("5102");
        assertThatThrownBy(() -> registry.resolveActive("5102"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("NCM: 8 digitos")
    void ncmGovernance() {
        var registry = new FiscalCodeRegistry(FiscalCodeRegistry.Kind.NCM);
        registry.register("22011000", "Aguas minerais (ficticio)");
        assertThat(registry.resolveActive("22011000").code()).isEqualTo("22011000");
        assertThatThrownBy(() -> registry.register("2201100", "curto"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("tipos de substituicao 01-09: carga exige fonte citada; so descricao/estado mudam; inactivo bloqueia")
    void substitutionTypesLoadWithSource() {
        var types = new SubstitutionTypes();
        assertThatThrownBy(() -> types.loadReference(" "))
                .isInstanceOf(IllegalArgumentException.class);
        types.loadReference("F-16/art. 898 (base J-Tech, LOTE-03 parte 13)");
        assertThat(types.loadSourceRef()).contains("898");
        assertThat(types.all()).hasSize(9);
        assertThat(types.resolveActive("02").description()).isEqualTo("erro de preco/tarifa");
        types.updateDescription("02", "erro de preco/tarifa (revisto)");
        assertThat(types.resolveActive("02").description()).contains("revisto");
        assertThat(types.resolveActive("02").code()).isEqualTo("02");
        types.setActive("02", false);
        assertThatThrownBy(() -> types.resolveActive("02"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("contingencia (V-43): cadastro analogo; SEM timeout — o tipo nao tem campo de timeout (V-31)")
    void contingencyRegistryHasNoTimeout() {
        var registry = new ContingencyTypeRegistry();
        registry.register("FALHA-SEFAZ", "Falha de comunicacao (ficticio)");
        assertThat(registry.resolveActive("FALHA-SEFAZ").code()).isEqualTo("FALHA-SEFAZ");
        var components = Arrays.stream(ContingencyTypeRegistry.ContingencyType.class
                        .getRecordComponents()).map(java.lang.reflect.RecordComponent::getName)
                .toList();
        assertThat(components).containsExactly("code", "description", "active");
        assertThat(components).noneMatch(name -> name.toLowerCase().contains("timeout"));
    }
}

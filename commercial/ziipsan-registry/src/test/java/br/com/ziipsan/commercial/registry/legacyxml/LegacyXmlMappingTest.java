package br.com.ziipsan.commercial.registry.legacyxml;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Declaração do importador de XML de legado (P2.19) no framework declarativo da P1.2. */
class LegacyXmlMappingTest {

    @Test
    @DisplayName("declaração embutida v1 é lida pelo Json do core")
    void declaracaoEmbutida() {
        LegacyXmlMapping mapping = LegacyXmlMapping.v1();
        assertThat(mapping.name()).isEqualTo("legacy-xml");
        assertThat(mapping.version()).isEqualTo("1");
        assertThat(mapping.sourceSystem()).isEqualTo("SISTEMA_ANTIGO");
        assertThat(mapping.acceptedModels()).containsExactlyInAnyOrder("55", "75");
    }

    @Test
    @DisplayName("declaração sem modelos ou com modelo fora do leiaute é recusada na leitura")
    void declaracaoInvalida() {
        assertThatThrownBy(() -> LegacyXmlMapping.parse(
                "{\"name\":\"x\",\"version\":\"1\",\"sourceSystem\":\"S\"}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LegacyXmlMapping("x", "1", "S", java.util.Set.of("5")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2 dígitos");
    }
}

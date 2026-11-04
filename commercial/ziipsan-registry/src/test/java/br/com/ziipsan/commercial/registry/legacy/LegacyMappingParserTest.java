package br.com.ziipsan.commercial.registry.legacy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegacyMappingParserTest {

    private static String resource(String path) {
        try (InputStream in = LegacyMappingParserTest.class.getResourceAsStream(path)) {
            assertThat(in).as("recurso %s", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("os dois mapeamentos de exemplo passam no parse estrito")
    void shippedMappingsParse() {
        LegacyMapping piloto = LegacyMapping.parse(resource("/legacy/mappings/piloto-v1.json"));
        assertThat(piloto.name()).isEqualTo("piloto-v1");
        assertThat(piloto.sourceSystem()).isEqualTo("PILOTO");
        assertThat(piloto.delimiter()).isEqualTo(';');
        assertThat(piloto.hasInstallments()).isTrue();

        LegacyMapping gsan = LegacyMapping.parse(resource("/legacy/mappings/gsan-v1.json"));
        assertThat(gsan.name()).isEqualTo("gsan-v1");
        assertThat(gsan.sourceSystem()).isEqualTo("GSAN");
        assertThat(gsan.hasInstallments()).isFalse();
    }

    @Test
    @DisplayName("alvo desconhecido é erro, não campo ignorado")
    void unknownTargetFails() {
        assertThatThrownBy(() -> LegacyMapping.parse("""
                {"name": "x", "version": "1", "sourceSystem": "X",
                 "fields": [{"source": "a", "target": "NOPE"}]}"""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Alvo de unidade desconhecido");
    }

    @Test
    @DisplayName("transformação desconhecida é erro")
    void unknownTransformFails() {
        assertThatThrownBy(() -> LegacyMapping.parse("""
                {"name": "x", "version": "1", "sourceSystem": "X",
                 "fields": [{"source": "a", "target": "MATRICULA", "transform": "MAGIC"}]}"""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Transformação desconhecida");
    }

    @Test
    @DisplayName("alvo duplicado na mesma seção é erro")
    void duplicateTargetFails() {
        assertThatThrownBy(() -> LegacyMapping.parse("""
                {"name": "x", "version": "1", "sourceSystem": "X",
                 "fields": [{"source": "a", "target": "MATRICULA"},
                            {"source": "b", "target": "MATRICULA"}]}"""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Alvo duplicado");
    }

    @Test
    @DisplayName("nome, versão e sistema de origem são obrigatórios")
    void blankIdentityFails() {
        assertThatThrownBy(() -> LegacyMapping.parse("""
                {"name": " ", "version": "1", "sourceSystem": "X",
                 "fields": [{"source": "a", "target": "LEGACY_KEY"}]}"""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    @DisplayName("DOCUMENT só vale para HOLDER_DOCUMENT, e HOLDER_DOCUMENT exige DOCUMENT")
    void documentTransformRules() {
        assertThatThrownBy(() -> LegacyMapping.parse("""
                {"name": "x", "version": "1", "sourceSystem": "X",
                 "fields": [{"source": "a", "target": "HOLDER_NAME", "transform": "DOCUMENT"}]}"""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DOCUMENT");
        assertThatThrownBy(() -> LegacyMapping.parse("""
                {"name": "x", "version": "1", "sourceSystem": "X",
                 "fields": [{"source": "a", "target": "HOLDER_DOCUMENT", "transform": "TRIM"}]}"""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DOCUMENT");
    }

    @Test
    @DisplayName("seção de unidades vazia é erro")
    void emptyFieldsFails() {
        assertThatThrownBy(() -> LegacyMapping.parse("""
                {"name": "x", "version": "1", "sourceSystem": "X", "fields": []}"""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ao menos um campo");
    }
}

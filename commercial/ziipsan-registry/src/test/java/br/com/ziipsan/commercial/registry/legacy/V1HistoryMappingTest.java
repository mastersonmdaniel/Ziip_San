package br.com.ziipsan.commercial.registry.legacy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P2.10: a declaração de mapeamento do histórico da v1 é dado, não código —
 * estes testes provam que a declaração embutida carrega, que alvo ou
 * transformação fora do vocabulário são recusados na leitura e que uma
 * declaração nova (outro legado) entra só como arquivo novo, sem motor novo.
 */
class V1HistoryMappingTest {

    @Test
    @DisplayName("declaração embutida da v1 carrega com os cinco alvos do staging de histórico")
    void embeddedV1MappingLoads() {
        V1HistoryMapping mapping = V1HistoryMapping.v1();
        assertThat(mapping.name()).isEqualTo("v1-history");
        assertThat(mapping.fields()).extracting(V1HistoryMapping.FieldSpec::target)
                .containsExactly("UNIT_LEGACY_KEY", "REFERENCE_MONTH", "CONSUMPTION_M3",
                        "BILLED_AMOUNT_CENTS", "DUE_DATE");
        assertThat(mapping.fields()).filteredOn(V1HistoryMapping.FieldSpec::required).hasSize(2);
    }

    @Test
    @DisplayName("declaração com alvo ou transformação desconhecidos é recusada")
    void unknownTargetOrTransformRejected() {
        String badTarget = """
                {"name": "x", "version": "1", "history": [
                  {"source": "a", "target": "BOGUS", "transform": "TEXT", "required": true},
                  {"source": "b", "target": "REFERENCE_MONTH", "transform": "MONTH_YEAR_BR", "required": true}
                ]}
                """;
        assertThatThrownBy(() -> V1HistoryMapping.parse(badTarget))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BOGUS");
        String badTransform = """
                {"name": "x", "version": "1", "history": [
                  {"source": "a", "target": "UNIT_LEGACY_KEY", "transform": "MAGIC", "required": true},
                  {"source": "b", "target": "REFERENCE_MONTH", "transform": "MONTH_YEAR_BR", "required": true}
                ]}
                """;
        assertThatThrownBy(() -> V1HistoryMapping.parse(badTransform))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MAGIC");
    }

    @Test
    @DisplayName("outro legado entra como declaração nova no mesmo formato, sem código novo")
    void anotherLegacyIsAnotherDeclaration() {
        String other = """
                {"name": "outro-legado-history", "version": "2", "history": [
                  {"source": "cod_imovel", "target": "UNIT_LEGACY_KEY", "transform": "TEXT", "required": true},
                  {"source": "ano_mes", "target": "REFERENCE_MONTH", "transform": "MONTH_YEAR_BR", "required": true},
                  {"source": "vl_faturado", "target": "BILLED_AMOUNT_CENTS", "transform": "CENTS_BR", "required": false}
                ]}
                """;
        V1HistoryMapping mapping = V1HistoryMapping.parse(other);
        assertThat(mapping.name()).isEqualTo("outro-legado-history");
        assertThat(mapping.fields()).hasSize(3);
    }

    @Test
    @DisplayName("declaração sem chave da unidade ou sem competência é recusada")
    void missingKeyOrMonthRejected() {
        String noKey = """
                {"name": "x", "version": "1", "history": [
                  {"source": "b", "target": "REFERENCE_MONTH", "transform": "MONTH_YEAR_BR", "required": true}
                ]}
                """;
        assertThatThrownBy(() -> V1HistoryMapping.parse(noKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNIT_LEGACY_KEY");
    }
}

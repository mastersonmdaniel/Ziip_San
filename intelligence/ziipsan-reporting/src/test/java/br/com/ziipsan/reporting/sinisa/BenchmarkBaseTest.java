package br.com.ziipsan.reporting.sinisa;

import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Base de benchmark: carga da base publica versionada e comparacao com a
 * mediana. Os numeros do documento sao MASSA SINTETICA declarada no
 * formato de importacao — nenhuma mediana real e afirmada neste teste.
 */
class BenchmarkBaseTest {

    private static final String DOC = """
            [{"indicatorCode": "SINISA.AGUA.AGUA_NAO_FATURADA", "sizeClass": "PORTE-TESTE",
              "region": "REGIAO-TESTE", "legalNature": "AUTARQUIA", "median": "35",
              "baseVersion": "TESTE-v1", "sourceDescription": "documento de teste (massa sintetica)"},
             {"indicatorCode": "SINISA.AGUA.LIGACOES_ATIVAS", "sizeClass": "PORTE-TESTE",
              "region": "REGIAO-TESTE", "legalNature": "AUTARQUIA", "median": 1200,
              "baseVersion": "TESTE-v1", "sourceDescription": "documento de teste (massa sintetica)"}]
            """;

    @Test
    void loadsVersionedBaseAndFindsTheExactSegment() {
        var base = BenchmarkBase.fromJson(DOC);
        assertThat(base.baseVersion()).isEqualTo("TESTE-v1");
        assertThat(base.find("SINISA.AGUA.AGUA_NAO_FATURADA", "PORTE-TESTE", "REGIAO-TESTE", "AUTARQUIA"))
                .isPresent();
        // Recorte diferente: vazio declarado, nunca a mediana de outro recorte.
        assertThat(base.find("SINISA.AGUA.AGUA_NAO_FATURADA", "OUTRO-PORTE", "REGIAO-TESTE", "AUTARQUIA"))
                .isEmpty();
    }

    @Test
    void comparisonIsDifferenceAndPositionAgainstTheMedian() {
        var base = BenchmarkBase.fromJson(DOC);
        var above = base.compare(new BigDecimal("42"), "SINISA.AGUA.AGUA_NAO_FATURADA",
                "PORTE-TESTE", "REGIAO-TESTE", "AUTARQUIA").orElseThrow();
        assertThat(above.difference()).isEqualByComparingTo("7");
        assertThat(above.position()).isEqualTo(BenchmarkComparison.Position.ABOVE_MEDIAN);
        var at = base.compare(new BigDecimal("35"), "SINISA.AGUA.AGUA_NAO_FATURADA",
                "PORTE-TESTE", "REGIAO-TESTE", "AUTARQUIA").orElseThrow();
        assertThat(at.position()).isEqualTo(BenchmarkComparison.Position.AT_MEDIAN);
        assertThat(at.difference()).isEqualByComparingTo("0");
        var below = base.compare(new BigDecimal("20"), "SINISA.AGUA.AGUA_NAO_FATURADA",
                "PORTE-TESTE", "REGIAO-TESTE", "AUTARQUIA").orElseThrow();
        assertThat(below.position()).isEqualTo(BenchmarkComparison.Position.BELOW_MEDIAN);
        assertThat(below.difference()).isEqualByComparingTo("-15");
    }

    @Test
    void referenceWithoutSourceIsRefused() {
        String doc = """
                [{"indicatorCode": "X", "sizeClass": "P", "region": "R", "legalNature": "A",
                  "median": "1", "baseVersion": "v1", "sourceDescription": ""}]
                """;
        assertThatThrownBy(() -> BenchmarkBase.fromJson(doc))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("BENCHMARK_SOURCE_REQUIRED");
    }

    @Test
    void mixedBaseVersionsInOneImportAreRefused() {
        String doc = """
                [{"indicatorCode": "X", "sizeClass": "P", "region": "R", "legalNature": "A",
                  "median": "1", "baseVersion": "v1", "sourceDescription": "fonte"},
                 {"indicatorCode": "Y", "sizeClass": "P", "region": "R", "legalNature": "A",
                  "median": "2", "baseVersion": "v2", "sourceDescription": "fonte"}]
                """;
        assertThatThrownBy(() -> BenchmarkBase.fromJson(doc))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("BENCHMARK_BASE_VERSION_MIXED");
    }

    @Test
    void duplicatedSegmentIsRefused() {
        String doc = """
                [{"indicatorCode": "X", "sizeClass": "P", "region": "R", "legalNature": "A",
                  "median": "1", "baseVersion": "v1", "sourceDescription": "fonte"},
                 {"indicatorCode": "X", "sizeClass": "P", "region": "R", "legalNature": "A",
                  "median": "2", "baseVersion": "v1", "sourceDescription": "fonte"}]
                """;
        assertThatThrownBy(() -> BenchmarkBase.fromJson(doc))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("BENCHMARK_REFERENCE_DUPLICATED");
    }
}

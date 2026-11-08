package br.com.ziipsan.reporting.sinisa;

import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import java.math.BigDecimal;

/**
 * Uma referencia da base de benchmark do SINISA (GES-29): a MEDIANA publica
 * de um indicador para um recorte — porte, regiao e natureza juridica.
 *
 * <p>A base e GLOBAL e VERSIONADA: {@code baseVersion} identifica a
 * publicacao do SINISA de onde a mediana saiu e {@code sourceDescription}
 * nomeia a fonte publica (publicacao/arquivo). Sem fonte declarada a
 * referencia e RECUSADA: mediana sem procedencia seria dado inventado.
 * Porte, regiao e natureza juridica sao os rotulos DA BASE IMPORTADA
 * (texto declarado no arquivo publico), nao um vocabulario presumido
 * neste codigo — as faixas de porte do SINISA sao as da publicacao.
 */
public record BenchmarkReference(String indicatorCode, String sizeClass, String region,
                                 String legalNature, BigDecimal median,
                                 String baseVersion, String sourceDescription) {

    public BenchmarkReference {
        if (indicatorCode == null || indicatorCode.isBlank()) {
            throw new IndicatorValidationException("BENCHMARK_INDICATOR_REQUIRED",
                    "referencia de benchmark sem indicador");
        }
        if (sizeClass == null || sizeClass.isBlank()) {
            throw new IndicatorValidationException("BENCHMARK_SIZE_CLASS_REQUIRED",
                    "referencia de " + indicatorCode + " sem o porte do recorte (GES-29)");
        }
        if (region == null || region.isBlank()) {
            throw new IndicatorValidationException("BENCHMARK_REGION_REQUIRED",
                    "referencia de " + indicatorCode + " sem a regiao do recorte (GES-29)");
        }
        if (legalNature == null || legalNature.isBlank()) {
            throw new IndicatorValidationException("BENCHMARK_LEGAL_NATURE_REQUIRED",
                    "referencia de " + indicatorCode + " sem a natureza juridica do recorte (GES-29)");
        }
        if (median == null) {
            throw new IndicatorValidationException("BENCHMARK_MEDIAN_REQUIRED",
                    "referencia de " + indicatorCode + " sem mediana: mediana nao e presumida");
        }
        if (baseVersion == null || baseVersion.isBlank()) {
            throw new IndicatorValidationException("BENCHMARK_BASE_VERSION_REQUIRED",
                    "referencia de " + indicatorCode + " sem a versao da base publica (base versionada)");
        }
        if (sourceDescription == null || sourceDescription.isBlank()) {
            throw new IndicatorValidationException("BENCHMARK_SOURCE_REQUIRED",
                    "referencia de " + indicatorCode + " sem a fonte publica declarada: recusada");
        }
    }
}

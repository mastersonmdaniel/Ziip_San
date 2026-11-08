package br.com.ziipsan.reporting.sinisa;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.reporting.indicator.IndicatorValidationException;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Base global e versionada de benchmark (GES-29), importada de dado
 * PUBLICO do SINISA. Como o catalogo da P4.2, a base e DADO carregavel
 * (JSON lido so pela classe {@code Json} do core), nao constante de
 * codigo: nenhuma mediana e escrita neste repositorio.
 *
 * <p>Formato do documento de importacao (um array por publicacao):
 * <pre>
 * [{"indicatorCode": "SINISA.AGUA.AGUA_NAO_FATURADA", "sizeClass": "...",
 *   "region": "...", "legalNature": "...", "median": "123.45",
 *   "baseVersion": "...", "sourceDescription": "..."}]
 * </pre>
 * Recusas da carga: documento vazio/que nao seja array, recorte duplicado,
 * versoes de base misturadas no mesmo documento (uma importacao = uma
 * publicacao versionada) e qualquer referencia invalida de
 * {@link BenchmarkReference} (sem mediana, sem fonte, sem recorte).
 */
public final class BenchmarkBase {

    private final String baseVersion;
    private final Map<String, BenchmarkReference> byKey;

    private BenchmarkBase(String baseVersion, Map<String, BenchmarkReference> byKey) {
        this.baseVersion = baseVersion;
        this.byKey = byKey;
    }

    public static BenchmarkBase fromJson(String json) {
        if (json == null || json.isBlank()) {
            throw new IndicatorValidationException("BENCHMARK_BASE_EMPTY",
                    "base de benchmark vazia: documento de importacao obrigatorio");
        }
        JsonNode root = Json.readTree(json);
        if (!root.isArray()) {
            throw new IndicatorValidationException("BENCHMARK_BASE_INVALID",
                    "base de benchmark invalida: esperado array de referencias");
        }
        Map<String, BenchmarkReference> byKey = new LinkedHashMap<>();
        String version = null;
        for (JsonNode item : root) {
            BenchmarkReference reference = parse(item);
            if (version == null) {
                version = reference.baseVersion();
            } else if (!version.equals(reference.baseVersion())) {
                throw new IndicatorValidationException("BENCHMARK_BASE_VERSION_MIXED",
                        "base de benchmark com versoes misturadas (" + version + " e "
                                + reference.baseVersion() + "): uma importacao e uma publicacao");
            }
            String key = key(reference.indicatorCode(), reference.sizeClass(),
                    reference.region(), reference.legalNature());
            if (byKey.putIfAbsent(key, reference) != null) {
                throw new IndicatorValidationException("BENCHMARK_REFERENCE_DUPLICATED",
                        "referencia duplicada na base: " + key);
            }
        }
        if (byKey.isEmpty()) {
            throw new IndicatorValidationException("BENCHMARK_BASE_INVALID",
                    "base de benchmark sem nenhuma referencia");
        }
        return new BenchmarkBase(version, Map.copyOf(byKey));
    }

    private static BenchmarkReference parse(JsonNode item) {
        if (item == null || !item.isObject()) {
            throw new IndicatorValidationException("BENCHMARK_BASE_INVALID",
                    "referencia de benchmark deve ser um objeto");
        }
        return new BenchmarkReference(
                text(item, "indicatorCode"), text(item, "sizeClass"), text(item, "region"),
                text(item, "legalNature"), decimal(item, "median"),
                text(item, "baseVersion"), text(item, "sourceDescription"));
    }

    private static String text(JsonNode item, String field) {
        JsonNode node = item.get(field);
        if (node == null || !node.isTextual()) {
            throw new IndicatorValidationException("BENCHMARK_BASE_INVALID",
                    "campo '" + field + "' da referencia de benchmark deve ser texto");
        }
        return node.asText();
    }

    private static BigDecimal decimal(JsonNode item, String field) {
        JsonNode node = item.get(field);
        if (node == null || node.isNull()) {
            // A ausencia cai na validacao nomeada de BenchmarkReference.
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isTextual()) {
            try {
                return new BigDecimal(node.asText());
            } catch (NumberFormatException e) {
                throw new IndicatorValidationException("BENCHMARK_BASE_INVALID",
                        "campo '" + field + "' nao e um decimal: " + node.asText());
            }
        }
        throw new IndicatorValidationException("BENCHMARK_BASE_INVALID",
                "campo '" + field + "' da referencia de benchmark deve ser numero ou texto decimal");
    }

    private static String key(String indicatorCode, String sizeClass, String region, String legalNature) {
        return indicatorCode + "|" + sizeClass + "|" + region + "|" + legalNature;
    }

    public String baseVersion() {
        return baseVersion;
    }

    public List<BenchmarkReference> references() {
        return List.copyOf(byKey.values());
    }

    /** Referencia do recorte exato; recorte sem referencia = vazio declarado, nunca outra mediana. */
    public Optional<BenchmarkReference> find(String indicatorCode, String sizeClass,
                                             String region, String legalNature) {
        return Optional.ofNullable(byKey.get(key(indicatorCode, sizeClass, region, legalNature)));
    }

    /**
     * Compara o valor do tenant com a mediana do recorte. Sem referencia
     * para o recorte, devolve vazio: comparar com o recorte errado seria
     * pior do que nao comparar.
     */
    public Optional<BenchmarkComparison> compare(BigDecimal tenantValue, String indicatorCode,
                                                   String sizeClass, String region, String legalNature) {
        if (tenantValue == null) {
            throw new IndicatorValidationException("BENCHMARK_TENANT_VALUE_REQUIRED",
                    "comparacao de benchmark sem o valor do tenant de " + indicatorCode);
        }
        return find(indicatorCode, sizeClass, region, legalNature).map(ref -> {
            int cmp = tenantValue.compareTo(ref.median());
            BenchmarkComparison.Position position = cmp > 0
                    ? BenchmarkComparison.Position.ABOVE_MEDIAN
                    : cmp < 0 ? BenchmarkComparison.Position.BELOW_MEDIAN
                    : BenchmarkComparison.Position.AT_MEDIAN;
            return new BenchmarkComparison(indicatorCode, tenantValue, ref.median(),
                    tenantValue.subtract(ref.median()), position, ref.baseVersion());
        });
    }
}

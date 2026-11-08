package br.com.ziipsan.intelligence.gis.thematic;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.intelligence.gis.thematic.GisThematicDtos.ClassDef;
import br.com.ziipsan.intelligence.gis.thematic.GisThematicDtos.LegendEntry;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Regras puras da GIS-08 (sem banco, sem contexto): leitura e
 * validação das classes e dos filtros da definição, legenda
 * automática, classificação numérica, decisão de supressão da
 * célula pública e o piso do parâmetro de agregação. Tudo que é
 * JSON passa pela classe {@link Json} do core — nunca por
 * {@code toString()} de mapa nem por parser próprio.
 *
 * <p>Decisões registradas:
 * <ul>
 *   <li>Legenda automática: com classes declaradas, a legenda sai
 *       delas (na ordem declarada) com a contagem observada; sem
 *       classes, sai das categorias observadas nos dados, em ordem
 *       alfabética, com rótulo = a própria chave (o tenant nomeia
 *       ao declarar classes).</li>
 *   <li>Classe numérica é o intervalo {@code [min, max)}; {@code max}
 *       nulo é faixa aberta. Faixas que se sobrepõem ou fora de
 *       ordem são recusadas na gravação — classe ambígua coloriria
 *       o mesmo ponto de duas cores.</li>
 *   <li>Célula pública abaixo do mínimo é SUPRIMIDA (não sai com
 *       contagem mascarada): qualquer número menor que o mínimo,
 *       publicado, ainda informa que há unidades ali (§12, item 3).</li>
 *   <li>{@code gis.public_aggregation_min_units} é classe c com
 *       piso 5 (core V9_6): o valor efetivo é
 *       {@code max(5, configurado)} — o piso é reaplicado aqui
 *       mesmo que uma gravação fora do console tenha posto menos.</li>
 * </ul>
 */
public final class GisThematicRules {

    /** Piso do parâmetro de agregação pública (classe c, core V9_6). */
    public static final int AGGREGATION_FLOOR = 5;

    private static final Pattern TOKEN = Pattern.compile("^map-[a-z0-9][a-z0-9-]*$");
    private static final Pattern KEY = Pattern.compile("^[a-z0-9][a-z0-9-]{2,59}$");

    private GisThematicRules() {
    }

    // ------------------------------------------------------------------ classes

    /** Lê classes_json (array) como lista de ClassDef; JSON inválido lança. */
    public static List<ClassDef> parseClasses(String classesJson) {
        JsonNode root = Json.readTree(classesJson);
        if (root.isMissingNode()) {
            return List.of();
        }
        if (!root.isArray()) {
            throw new IllegalArgumentException("classes_json deve ser um array JSON");
        }
        List<ClassDef> classes = new ArrayList<>();
        for (JsonNode node : root) {
            String key = textOrNull(node, "key");
            String label = textOrNull(node, "label");
            String token = textOrNull(node, "colorToken");
            BigDecimal min = decimalOrNull(node, "min");
            BigDecimal max = decimalOrNull(node, "max");
            classes.add(new ClassDef(key, label, token, min, max));
        }
        return List.copyOf(classes);
    }

    /** Grava as classes como JSON pela classe Json do core. */
    public static String writeClasses(List<ClassDef> classes) {
        if (classes == null || classes.isEmpty()) {
            return "[]";
        }
        List<Map<String, Object>> raw = new ArrayList<>();
        for (ClassDef c : classes) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", c.key());
            item.put("label", c.label());
            if (c.colorToken() != null) {
                item.put("colorToken", c.colorToken());
            }
            if (c.min() != null) {
                item.put("min", c.min());
            }
            if (c.max() != null) {
                item.put("max", c.max());
            }
            raw.add(item);
        }
        return Json.write(raw);
    }

    /**
     * Valida a definição completa contra a fonte (lista fechada do
     * {@link GisThematicSources}). Devolve a lista de problemas —
     * vazia é válida. Quem chama transforma problema em 400.
     */
    public static List<String> validateDefinition(String key, GisThematicSources source,
                                                  String categoryField, String measureField,
                                                  List<ClassDef> classes, String filterJson) {
        List<String> problems = new ArrayList<>();
        if (key == null || !KEY.matcher(key).matches()) {
            problems.add("key fora do padrão [a-z0-9-]{3,60}: " + key);
        }
        if (categoryField != null && !source.isCategoryField(categoryField)) {
            problems.add("categoryField não é campo de categoria da fonte "
                    + source.name() + ": " + categoryField);
        }
        if (measureField != null && !source.isMeasureField(measureField)) {
            problems.add("measureField não é campo de medida da fonte "
                    + source.name() + ": " + measureField);
        }
        if (categoryField == null && measureField == null) {
            problems.add("temático sem categoryField nem measureField: nada para colorir");
        }
        problems.addAll(validateClasses(source, categoryField, measureField, classes));
        problems.addAll(validateFilter(source, filterJson));
        return List.copyOf(problems);
    }

    private static List<String> validateClasses(GisThematicSources source, String categoryField,
                                                String measureField, List<ClassDef> classes) {
        List<String> problems = new ArrayList<>();
        if (classes == null) {
            return problems;
        }
        boolean numeric = measureField != null && categoryField == null;
        List<ClassDef> ranges = new ArrayList<>();
        for (ClassDef c : classes) {
            if (c.key() == null || c.key().isBlank()) {
                problems.add("classe sem key");
            }
            if (c.label() == null || c.label().isBlank()) {
                problems.add("classe sem label: " + c.key());
            }
            if (c.colorToken() != null && !TOKEN.matcher(c.colorToken()).matches()) {
                problems.add("colorToken fora do vocabulário map-*: " + c.colorToken());
            }
            if (numeric) {
                if (c.min() == null && c.max() == null) {
                    problems.add("classe numérica sem min nem max: " + c.key());
                }
                if (c.min() != null && c.max() != null && c.min().compareTo(c.max()) >= 0) {
                    problems.add("classe numérica com min >= max: " + c.key());
                }
                ranges.add(c);
            } else if (c.min() != null || c.max() != null) {
                problems.add("classe categórica com min/max numérico: " + c.key());
            }
        }
        ranges.sort(Comparator.comparing(c -> c.min() == null ? BigDecimal.valueOf(Long.MIN_VALUE) : c.min()));
        for (int i = 1; i < ranges.size(); i++) {
            ClassDef prev = ranges.get(i - 1);
            ClassDef curr = ranges.get(i);
            if (prev.max() == null
                    || (curr.min() != null && curr.min().compareTo(prev.max()) < 0)) {
                problems.add("classes numéricas sobrepostas: " + prev.key() + " x " + curr.key());
            }
        }
        return problems;
    }

    private static List<String> validateFilter(GisThematicSources source, String filterJson) {
        List<String> problems = new ArrayList<>();
        if (filterJson == null || filterJson.isBlank()) {
            return problems;
        }
        Map<String, Object> filter;
        try {
            filter = Json.readMap(filterJson);
        } catch (RuntimeException e) {
            problems.add("filter_json deve ser um objeto JSON");
            return problems;
        }
        filter.keySet().forEach(field -> {
            if (!source.isFilterField(field)) {
                problems.add("filtro fora da lista da fonte " + source.name() + ": " + field);
            }
        });
        return problems;
    }

    // ------------------------------------------------------- configuração pendente

    /**
     * O temático exige configuração do tenant antes de renderizar?
     * Dois casos do aceite, ambos sem dado de fábrica possível:
     * cenário de OS com {@code serviceTypeCodes} declarado e vazio
     * (SG-877 a SG-879: os códigos são do catálogo do tenant) e
     * temático numérico sem nenhuma classe (SG-882: nenhum documento
     * fixa o corte de grande consumidor).
     */
    public static boolean requiresConfiguration(GisThematicSources source, String categoryField,
                                                String measureField, List<ClassDef> classes,
                                                String filterJson) {
        if (source == GisThematicSources.SERVICE_ORDER && filterJson != null
                && !filterJson.isBlank()) {
            Object codes = Json.readMap(filterJson).get("serviceTypeCodes");
            if (codes instanceof List<?> list && list.isEmpty()) {
                return true;
            }
        }
        return measureField != null && categoryField == null
                && (classes == null || classes.isEmpty());
    }

    // ------------------------------------------------------------------ legenda

    /**
     * Legenda automática: classes declaradas viram a legenda (com a
     * contagem observada, inclusive zero); sem classes, a legenda
     * nasce das categorias observadas.
     */
    public static List<LegendEntry> legend(List<ClassDef> classes,
                                           Map<String, Long> observedCounts) {
        Map<String, Long> observed = observedCounts == null ? Map.of() : observedCounts;
        if (classes != null && !classes.isEmpty()) {
            List<LegendEntry> entries = new ArrayList<>();
            for (ClassDef c : classes) {
                entries.add(new LegendEntry(c.key(), c.label(), c.colorToken(),
                        observed.getOrDefault(c.key(), 0L)));
            }
            return List.copyOf(entries);
        }
        return observed.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new LegendEntry(e.getKey(), e.getKey(), null, e.getValue()))
                .toList();
    }

    /** Classe numérica de um valor: a faixa [min, max) que o contém, ou null. */
    public static String classifyNumeric(List<ClassDef> classes, BigDecimal value) {
        if (classes == null || value == null) {
            return null;
        }
        for (ClassDef c : classes) {
            boolean aboveMin = c.min() == null || value.compareTo(c.min()) >= 0;
            boolean belowMax = c.max() == null || value.compareTo(c.max()) < 0;
            if (aboveMin && belowMax) {
                return c.key();
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ público

    /** Valor efetivo do mínimo de agregação: piso de classe c reaplicado. */
    public static int effectiveMinUnits(Integer configured) {
        if (configured == null) {
            return AGGREGATION_FLOOR;
        }
        return Math.max(AGGREGATION_FLOOR, configured);
    }

    /** A célula publica? Só com contagem de unidades >= o mínimo efetivo. */
    public static boolean cellPublishable(long unitCount, int effectiveMinUnits) {
        return unitCount >= effectiveMinUnits;
    }

    // ------------------------------------------------------------------ internos

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static BigDecimal decimalOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.decimalValue();
    }
}

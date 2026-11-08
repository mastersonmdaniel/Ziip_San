package br.com.ziipsan.intelligence.gis.webedit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Formulário gerado do JSON Schema (GIS-09): deriva os campos do
 * formulário web a partir de {@code gis_feature_type.attributes_schema}
 * (V13_1, JSONB) e valida os atributos submetidos contra o mesmo
 * esquema. Subconjunto suportado — o que o catálogo de fábrica usa:
 * {@code type} (string/number/integer/boolean), {@code enum},
 * {@code minimum/maximum}, {@code maxLength} e {@code required}.
 * Palavras do JSON Schema fora do subconjunto são simplesmente
 * ignoradas: não viram regra de validação nem campo do descritor
 * (o record {@link Field} não tem campo {@code raw} — correção D7
 * da revisão ED-53, que alinhou este javadoc ao record real).
 */
public final class GisFormSchema {

    public record Field(
            String name,
            String label,
            String type,
            boolean required,
            List<Object> options,
            Number minimum,
            Number maximum,
            Integer maxLength) {
    }

    public record FormDescriptor(List<Field> fields) {
    }

    private GisFormSchema() {
    }

    @SuppressWarnings("unchecked")
    public static FormDescriptor formFromSchema(Map<String, Object> schema) {
        if (schema == null) {
            return new FormDescriptor(List.of());
        }
        Map<String, Object> properties = schema.get("properties")
                instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        List<String> required = schema.get("required") instanceof List<?> l
                ? l.stream().map(String::valueOf).toList() : List.of();
        List<Field> fields = new ArrayList<>();
        for (Map.Entry<String, Object> e : properties.entrySet()) {
            Map<String, Object> prop = e.getValue() instanceof Map<?, ?> m
                    ? (Map<String, Object>) m : Map.of();
            String type = prop.get("type") == null
                    ? "string" : String.valueOf(prop.get("type"));
            String label = prop.get("title") == null
                    ? e.getKey() : String.valueOf(prop.get("title"));
            List<Object> options = prop.get("enum") instanceof List<?> l
                    ? List.copyOf((List<Object>) l) : List.of();
            Number min = prop.get("minimum") instanceof Number n ? n : null;
            Number max = prop.get("maximum") instanceof Number n ? n : null;
            Integer maxLen = prop.get("maxLength") instanceof Number n
                    ? n.intValue() : null;
            fields.add(new Field(e.getKey(), label, type,
                    required.contains(e.getKey()), options, min, max, maxLen));
        }
        return new FormDescriptor(List.copyOf(fields));
    }

    /** Valida atributos contra o esquema; devolve os problemas. */
    public static List<String> validate(Map<String, Object> schema,
                                        Map<String, Object> attributes) {
        FormDescriptor form = formFromSchema(schema);
        Map<String, Object> attrs = attributes == null ? Map.of() : attributes;
        List<String> problems = new ArrayList<>();
        for (Field f : form.fields()) {
            Object value = attrs.get(f.name());
            if (value == null) {
                if (f.required()) {
                    problems.add(f.name() + ": obrigatorio");
                }
                continue;
            }
            switch (f.type()) {
                case "number", "integer" -> {
                    if (!(value instanceof Number n)) {
                        problems.add(f.name() + ": deve ser numero");
                    } else {
                        if (f.type().equals("integer")
                                && n.doubleValue() != Math.floor(n.doubleValue())) {
                            problems.add(f.name() + ": deve ser inteiro");
                        }
                        if (f.minimum() != null
                                && n.doubleValue() < f.minimum().doubleValue()) {
                            problems.add(f.name() + ": abaixo do minimo");
                        }
                        if (f.maximum() != null
                                && n.doubleValue() > f.maximum().doubleValue()) {
                            problems.add(f.name() + ": acima do maximo");
                        }
                    }
                }
                case "boolean" -> {
                    if (!(value instanceof Boolean)) {
                        problems.add(f.name() + ": deve ser booleano");
                    }
                }
                default -> {
                    if (f.maxLength() != null
                            && String.valueOf(value).length() > f.maxLength()) {
                        problems.add(f.name() + ": acima do tamanho maximo");
                    }
                }
            }
            if (!f.options().isEmpty() && !containsValue(f.options(), value)) {
                problems.add(f.name() + ": fora da lista permitida");
            }
        }
        return List.copyOf(problems);
    }

    private static boolean containsValue(List<Object> options, Object value) {
        for (Object o : options) {
            if (String.valueOf(o).equals(String.valueOf(value))) {
                return true;
            }
        }
        return false;
    }
}

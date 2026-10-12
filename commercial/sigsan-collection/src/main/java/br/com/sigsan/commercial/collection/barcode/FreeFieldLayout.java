package br.com.sigsan.commercial.collection.barcode;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static br.com.sigsan.commercial.collection.barcode.InvalidBarcodeException.Reason;

/**
 * Descrição, como dado, do campo livre de um código de barras: o que cada trecho significa.
 *
 * <p>Este é o mecanismo que substitui "uma subclasse por empresa" (o GSAN mantinha 6 variantes
 * de código de barras, uma por convênio impresso). O campo livre é uma lista ordenada de campos
 * de 4 tipos:
 * <ul>
 *   <li>{@link Kind#DUE_DATE}: vencimento {@code AAAAMMDD}, 8 posições. O layout FEBRABAN manda
 *       que, se existir, ele seja o <b>primeiro</b> trecho do campo livre; por isso só é aceito
 *       como primeiro campo.</li>
 *   <li>{@link Kind#NUMERIC}: número com tamanho fixo, alinhado à direita com zeros à esquerda
 *       (como o layout FEBRABAN define os campos numéricos);</li>
 *   <li>{@link Kind#CONSTANT}: algarismos fixos do convênio;</li>
 *   <li>{@link Kind#CHECK_DIGIT}: dígito verificador (1 posição) calculado sobre outros campos
 *       do próprio layout, com {@link CheckDigitAlgorithm}.</li>
 * </ul>
 * O layout é serializável em JSON (coluna {@code barcode_layout} do convênio) com
 * {@link #toJson()} e {@link #fromJson(String)}.
 */
public final class FreeFieldLayout {

    public enum Kind { DUE_DATE, NUMERIC, CONSTANT, CHECK_DIGIT }

    public enum CheckDigitAlgorithm {
        MOD10, MOD11_COLLECTION, MOD11_BILLING;

        int compute(String digits) {
            return switch (this) {
                case MOD10 -> CheckDigits.mod10(digits);
                case MOD11_COLLECTION -> CheckDigits.mod11Collection(digits);
                case MOD11_BILLING -> CheckDigits.mod11Billing(digits);
            };
        }
    }

    /** Um trecho do campo livre. Use as fábricas estáticas. */
    public record Field(String name, Kind kind, int length, String constant,
                        CheckDigitAlgorithm algorithm, List<String> over) {

        public Field {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Todo campo do layout precisa de nome");
            }
            if (kind == null) {
                throw new IllegalArgumentException("Tipo do campo '" + name + "' ausente");
            }
            switch (kind) {
                case DUE_DATE -> {
                    if (length != 8) {
                        throw new IllegalArgumentException("Vencimento ocupa 8 posições (AAAAMMDD)");
                    }
                }
                case NUMERIC -> {
                    if (length < 1) {
                        throw new IllegalArgumentException("Campo numérico '" + name + "' precisa de tamanho");
                    }
                }
                case CONSTANT -> {
                    if (!Digits.isAllDigits(constant) || constant.length() != length) {
                        throw new IllegalArgumentException("Constante '" + name + "' precisa ter " + length
                                + " algarismos");
                    }
                }
                case CHECK_DIGIT -> {
                    if (length != 1 || algorithm == null || over == null || over.isEmpty()) {
                        throw new IllegalArgumentException("Dígito '" + name
                                + "' ocupa 1 posição e precisa de algoritmo e dos campos que cobre");
                    }
                }
            }
            over = over == null ? List.of() : List.copyOf(over);
        }

        public static Field dueDate(String name) {
            return new Field(name, Kind.DUE_DATE, 8, null, null, null);
        }

        public static Field numeric(String name, int length) {
            return new Field(name, Kind.NUMERIC, length, null, null, null);
        }

        public static Field constant(String name, String digits) {
            return new Field(name, Kind.CONSTANT, digits == null ? 0 : digits.length(), digits, null, null);
        }

        public static Field checkDigit(String name, CheckDigitAlgorithm algorithm, String... over) {
            return new Field(name, Kind.CHECK_DIGIT, 1, null, algorithm, List.of(over));
        }
    }

    /** Resultado de {@link #decode(String)}. */
    public record Decoded(Optional<LocalDate> dueDate, Map<String, String> values) {
    }

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final List<Field> fields;
    private final int length;

    public FreeFieldLayout(List<Field> fields) {
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("O layout do campo livre precisa de ao menos um campo");
        }
        this.fields = List.copyOf(fields);
        Set<String> seen = new HashSet<>();
        int total = 0;
        for (int i = 0; i < this.fields.size(); i++) {
            Field f = this.fields.get(i);
            if (!seen.add(f.name())) {
                throw new IllegalArgumentException("Nome de campo repetido: " + f.name());
            }
            if (f.kind() == Kind.DUE_DATE && i != 0) {
                throw new IllegalArgumentException(
                        "Se houver vencimento no campo livre, ele deve ser o primeiro trecho (FEBRABAN)");
            }
            if (f.kind() == Kind.CHECK_DIGIT) {
                for (String target : f.over()) {
                    Field t = byName(this.fields.subList(0, i), target);
                    if (t == null) {
                        throw new IllegalArgumentException("O dígito '" + f.name() + "' cobre '" + target
                                + "', que não existe antes dele");
                    }
                    if (t.kind() == Kind.CHECK_DIGIT) {
                        throw new IllegalArgumentException("Dígito verificador não cobre outro dígito: " + target);
                    }
                }
            }
            total += f.length();
        }
        this.length = total;
    }

    public List<Field> fields() {
        return fields;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FreeFieldLayout other && fields.equals(other.fields);
    }

    @Override
    public int hashCode() {
        return fields.hashCode();
    }

    @Override
    public String toString() {
        return "FreeFieldLayout" + fields;
    }

    /** Soma dos tamanhos dos campos; precisa ser igual ao tamanho do campo livre do código. */
    public int length() {
        return length;
    }

    public boolean hasDueDate() {
        return fields.get(0).kind() == Kind.DUE_DATE;
    }

    /**
     * Monta o campo livre.
     *
     * @param dueDate vencimento; obrigatório se o layout tem campo de vencimento, proibido se não tem
     * @param values  valores dos campos {@code NUMERIC}, por nome (zeros à esquerda são acrescentados)
     */
    public String encode(LocalDate dueDate, Map<String, String> values) {
        if (hasDueDate() != (dueDate != null)) {
            throw new InvalidBarcodeException(Reason.LAYOUT_MISMATCH, hasDueDate()
                    ? "O layout exige a data de vencimento"
                    : "O layout não tem campo de vencimento");
        }
        Map<String, String> encoded = new LinkedHashMap<>();
        Set<String> used = new HashSet<>();
        StringBuilder out = new StringBuilder(length);
        for (Field f : fields) {
            String part;
            switch (f.kind()) {
                case DUE_DATE -> {
                    if (dueDate.getYear() < 1000 || dueDate.getYear() > 9999) {
                        throw new InvalidBarcodeException(Reason.INVALID_DUE_DATE, "Ano de vencimento fora de 1000-9999");
                    }
                    part = YYYYMMDD.format(dueDate);
                }
                case NUMERIC -> {
                    String v = values == null ? null : values.get(f.name());
                    if (v == null) {
                        throw new InvalidBarcodeException(Reason.FIELD_OUT_OF_RANGE,
                                "Valor ausente para o campo '" + f.name() + "'");
                    }
                    if (!Digits.isAllDigits(v) || v.length() > f.length()) {
                        throw new InvalidBarcodeException(Reason.FIELD_OUT_OF_RANGE, "O campo '" + f.name()
                                + "' aceita até " + f.length() + " algarismos: '" + v + "'");
                    }
                    part = "0".repeat(f.length() - v.length()) + v;
                    used.add(f.name());
                }
                case CONSTANT -> part = f.constant();
                case CHECK_DIGIT -> {
                    StringBuilder base = new StringBuilder();
                    for (String target : f.over()) {
                        base.append(encoded.get(target));
                    }
                    part = Integer.toString(f.algorithm().compute(base.toString()));
                }
                default -> throw new IllegalStateException();
            }
            encoded.put(f.name(), part);
            out.append(part);
        }
        if (values != null) {
            for (String key : values.keySet()) {
                if (!used.contains(key)) {
                    throw new InvalidBarcodeException(Reason.LAYOUT_MISMATCH,
                            "O layout não tem campo numérico '" + key + "'");
                }
            }
        }
        return out.toString();
    }

    /**
     * Lê o campo livre conforme o layout, conferindo constantes, dígitos verificadores internos e
     * a validade da data de vencimento.
     */
    public Decoded decode(String freeField) {
        if (!Digits.isAllDigits(freeField) || freeField.length() != length) {
            throw new InvalidBarcodeException(Reason.LAYOUT_MISMATCH,
                    "O campo livre deste layout tem " + length + " algarismos");
        }
        Map<String, String> values = new LinkedHashMap<>();
        Map<String, String> raw = new LinkedHashMap<>();
        Optional<LocalDate> due = Optional.empty();
        int pos = 0;
        for (Field f : fields) {
            String part = freeField.substring(pos, pos + f.length());
            pos += f.length();
            raw.put(f.name(), part);
            switch (f.kind()) {
                case DUE_DATE -> {
                    try {
                        due = Optional.of(LocalDate.parse(part, YYYYMMDD));
                    } catch (DateTimeException e) {
                        throw new InvalidBarcodeException(Reason.INVALID_DUE_DATE,
                                "Vencimento inválido no campo livre: " + part);
                    }
                }
                case NUMERIC -> values.put(f.name(), part);
                case CONSTANT -> {
                    if (!part.equals(f.constant())) {
                        throw new InvalidBarcodeException(Reason.LAYOUT_MISMATCH, "Constante '" + f.name()
                                + "' esperada " + f.constant() + ", recebida " + part);
                    }
                }
                case CHECK_DIGIT -> {
                    StringBuilder base = new StringBuilder();
                    for (String target : f.over()) {
                        base.append(raw.get(target));
                    }
                    int expected = f.algorithm().compute(base.toString());
                    if (expected != part.charAt(0) - '0') {
                        throw new InvalidBarcodeException(Reason.BAD_CHECK_DIGIT, "Dígito '" + f.name()
                                + "' do campo livre: esperado " + expected + ", recebido " + part);
                    }
                    values.put(f.name(), part);
                }
            }
        }
        return new Decoded(due, values);
    }

    private static Field byName(List<Field> list, String name) {
        for (Field f : list) {
            if (f.name().equals(name)) {
                return f;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- JSON

    public String toJson() {
        ObjectNode root = JSON.createObjectNode();
        ArrayNode arr = root.putArray("fields");
        for (Field f : fields) {
            ObjectNode n = arr.addObject();
            n.put("name", f.name());
            n.put("kind", f.kind().name());
            switch (f.kind()) {
                case NUMERIC -> n.put("length", f.length());
                case CONSTANT -> n.put("value", f.constant());
                case CHECK_DIGIT -> {
                    n.put("algorithm", f.algorithm().name());
                    ArrayNode over = n.putArray("over");
                    f.over().forEach(over::add);
                }
                case DUE_DATE -> { /* tamanho fixo */ }
            }
        }
        try {
            return JSON.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static FreeFieldLayout fromJson(String json) {
        try {
            JsonNode root = JSON.readTree(json);
            JsonNode arr = root == null ? null : root.get("fields");
            if (arr == null || !arr.isArray()) {
                throw new IllegalArgumentException("JSON do layout sem a lista 'fields'");
            }
            List<Field> list = new ArrayList<>();
            for (JsonNode n : arr) {
                String name = text(n, "name");
                Kind kind = Kind.valueOf(text(n, "kind"));
                switch (kind) {
                    case DUE_DATE -> list.add(Field.dueDate(name));
                    case NUMERIC -> list.add(Field.numeric(name, intOf(n, "length")));
                    case CONSTANT -> list.add(Field.constant(name, text(n, "value")));
                    case CHECK_DIGIT -> {
                        List<String> over = new ArrayList<>();
                        JsonNode o = n.get("over");
                        if (o == null || !o.isArray()) {
                            throw new IllegalArgumentException("Dígito '" + name + "' sem a lista 'over'");
                        }
                        o.forEach(x -> over.add(x.asText()));
                        list.add(Field.checkDigit(name, CheckDigitAlgorithm.valueOf(text(n, "algorithm")),
                                over.toArray(String[]::new)));
                    }
                }
            }
            return new FreeFieldLayout(list);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON de layout inválido: " + e.getOriginalMessage(), e);
        }
    }

    private static String text(JsonNode n, String key) {
        JsonNode v = n.get(key);
        if (v == null || !v.isTextual()) {
            throw new IllegalArgumentException("Campo '" + key + "' ausente no JSON do layout");
        }
        return v.asText();
    }

    private static int intOf(JsonNode n, String key) {
        JsonNode v = n.get(key);
        if (v == null || !v.canConvertToInt()) {
            throw new IllegalArgumentException("Campo '" + key + "' ausente no JSON do layout");
        }
        return v.asInt();
    }
}

package br.com.ziipsan.commercial.registry.legacy;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * P2.10: mapeamento declarativo da seção de HISTÓRICO da v1 (até 120 meses
 * por unidade), no espírito do framework declarativo de migração da P1.2
 * (PLT-16): legado novo entra como declaração de mapeamento, não como
 * código novo. A declaração vive em arquivo
 * ({@code legacy/mappings/v1-history-v1.json}, em resources) e este tipo é
 * só o seu leitor/validador — o motor que a consome é o
 * {@link V1ImportService}, genérico sobre qualquer declaração neste formato.
 *
 * <p>Cada campo declara: coluna de origem na fonte ({@code source}), alvo
 * no staging do histórico ({@code target}), transformação
 * ({@code transform}) e obrigatoriedade ({@code required}). As
 * transformações são o vocabulário fechado desta seção:
 *
 * <ul>
 *   <li>{@code TEXT}: texto aparado;</li>
 *   <li>{@code MONTH_YEAR_BR}: competência {@code MM/AAAA} → primeiro dia
 *       do mês (ISO);</li>
 *   <li>{@code DECIMAL_BR}: decimal pt-BR ({@code 1.234,56}) preservado
 *       como texto normalizado com ponto, para virar {@code BigDecimal}
 *       sem perda;</li>
 *   <li>{@code CENTS_BR}: monetário pt-BR ({@code 1.234,56}) → centavos
 *       (inteiro longo), como o {@code BALANCE_CENTS} da P1.2;</li>
 *   <li>{@code DATE_BR}: data {@code dd/MM/aaaa} → ISO.</li>
 * </ul>
 *
 * <p>O parser é propositalmente mínimo (objeto JSON plano com um array de
 * campos), no mesmo espírito enxuto dos mapeamentos da P1.2: qualquer
 * desvio de formato é erro de declaração, recusado na leitura — nunca uma
 * linha de histórico silenciosamente errada.
 */
public final class V1HistoryMapping {

    /** Alvos que o staging do histórico (V14_2) aceita. */
    public static final List<String> TARGETS = List.of(
            "UNIT_LEGACY_KEY", "REFERENCE_MONTH", "CONSUMPTION_M3", "BILLED_AMOUNT_CENTS", "DUE_DATE");

    public static final List<String> TRANSFORMS = List.of(
            "TEXT", "MONTH_YEAR_BR", "DECIMAL_BR", "CENTS_BR", "DATE_BR");

    public record FieldSpec(String source, String target, String transform, boolean required) {
        public FieldSpec {
            if (source == null || source.isBlank()) {
                throw new IllegalArgumentException("Campo do mapeamento de histórico sem source.");
            }
            if (!TARGETS.contains(target)) {
                throw new IllegalArgumentException("Alvo de histórico desconhecido: " + target
                        + ". Válidos: " + TARGETS);
            }
            if (!TRANSFORMS.contains(transform)) {
                throw new IllegalArgumentException("Transformação de histórico desconhecida: " + transform
                        + ". Válidas: " + TRANSFORMS);
            }
        }
    }

    private final String name;
    private final String version;
    private final List<FieldSpec> fields;

    public V1HistoryMapping(String name, String version, List<FieldSpec> fields) {
        if (name == null || name.isBlank() || version == null || version.isBlank()) {
            throw new IllegalArgumentException("Mapeamento de histórico exige name e version.");
        }
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("Mapeamento de histórico sem campos.");
        }
        if (fields.stream().noneMatch(f -> f.target().equals("UNIT_LEGACY_KEY"))) {
            throw new IllegalArgumentException("Mapeamento de histórico sem alvo UNIT_LEGACY_KEY:"
                    + " sem a chave da unidade, não há staging.");
        }
        if (fields.stream().noneMatch(f -> f.target().equals("REFERENCE_MONTH"))) {
            throw new IllegalArgumentException("Mapeamento de histórico sem alvo REFERENCE_MONTH:"
                    + " sem a competência, não há mês de histórico.");
        }
        long distinctTargets = fields.stream().map(FieldSpec::target).distinct().count();
        if (distinctTargets != fields.size()) {
            throw new IllegalArgumentException("Mapeamento de histórico com alvo repetido.");
        }
        this.name = name;
        this.version = version;
        this.fields = List.copyOf(fields);
    }

    public String name() {
        return name;
    }

    public String version() {
        return version;
    }

    public List<FieldSpec> fields() {
        return fields;
    }

    /** A declaração embutida da v1 (resources), a que o aceite da P2.10 importa. */
    public static V1HistoryMapping v1() {
        try (InputStream in = V1HistoryMapping.class.getResourceAsStream("/legacy/mappings/v1-history-v1.json")) {
            if (in == null) {
                throw new IllegalStateException("Recurso /legacy/mappings/v1-history-v1.json não encontrado.");
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Leitura da declaração. Formato: {@code {"name": "...", "version": "...",
     * "history": [{"source": "...", "target": "...", "transform": "...",
     * "required": true}, ...]}}.
     */
    public static V1HistoryMapping parse(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Declaração de mapeamento de histórico vazia.");
        }
        String name = stringValue(json, "name");
        String version = stringValue(json, "version");
        int arrayStart = json.indexOf("\"history\"");
        if (arrayStart < 0) {
            throw new IllegalArgumentException("Declaração sem a seção \"history\".");
        }
        int open = json.indexOf('[', arrayStart);
        int close = json.lastIndexOf(']');
        if (open < 0 || close <= open) {
            throw new IllegalArgumentException("Seção \"history\" malformada na declaração.");
        }
        String array = json.substring(open + 1, close);
        List<FieldSpec> fields = new ArrayList<>();
        int cursor = 0;
        while (true) {
            int objOpen = array.indexOf('{', cursor);
            if (objOpen < 0) {
                break;
            }
            int objClose = array.indexOf('}', objOpen);
            if (objClose < 0) {
                throw new IllegalArgumentException("Objeto de campo malformado na declaração.");
            }
            String obj = array.substring(objOpen, objClose + 1);
            fields.add(new FieldSpec(
                    stringValue(obj, "source"),
                    stringValue(obj, "target"),
                    stringValue(obj, "transform"),
                    booleanValue(obj, "required")));
            cursor = objClose + 1;
        }
        return new V1HistoryMapping(name, version, fields);
    }

    private static String stringValue(String json, String key) {
        String token = "\"" + key + "\"";
        int at = json.indexOf(token);
        if (at < 0) {
            return null;
        }
        int colon = json.indexOf(':', at + token.length());
        int quote = json.indexOf('"', colon + 1);
        if (colon < 0 || quote < 0) {
            return null;
        }
        int end = json.indexOf('"', quote + 1);
        if (end < 0) {
            return null;
        }
        return json.substring(quote + 1, end);
    }

    private static boolean booleanValue(String json, String key) {
        String token = "\"" + key + "\"";
        int at = json.indexOf(token);
        if (at < 0) {
            return false;
        }
        int colon = json.indexOf(':', at + token.length());
        if (colon < 0) {
            return false;
        }
        String rest = json.substring(colon + 1).strip();
        return rest.startsWith("true");
    }
}

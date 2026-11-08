package br.com.ziipsan.commercial.collection.dunning.ruler;

import br.com.ziipsan.commercial.collection.dunning.DunningParameters;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * A regua de cobranca (CAD-100) no parametro {@code dun.ruler}: passos e
 * canais SEGMENTADOS por tipo de debito, idade, categoria e classe
 * protegida. Formato JSON do parametro (interpretado sempre pela classe
 * {@code Json} do core):
 *
 * <pre>
 * { "steps": [
 *   { "code": "D7_MSG", "dayOffset": 7, "channel": "WHATSAPP",
 *     "segment": { "debtType": "TARIFF", "minAgeDays": 1, "maxAgeDays": 30,
 *                  "categories": ["RESIDENTIAL"], "protectedClasses": [] },
 *     "templateCode": "DUNNING_D7" },
 *   { "code": "D15_LETTER", "dayOffset": 15, "channel": "LETTER",
 *     "segment": { "debtType": "TARIFF" }, "templateCode": "DUNNING_D15" }
 * ] }
 * </pre>
 *
 * Passo de MENSAGEM ({@code templateCode} presente) carrega link Pix na
 * mensagem (ver {@code DunningEngine}); passo sem modelo e de outro canal
 * (ligacao, carta registrada pelo operador etc.), conforme o canal.
 */
public record DunningRuler(List<Step> steps) {

    public DunningRuler {
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("regua de cobranca exige ao menos um passo");
        }
        steps = List.copyOf(steps);
    }

    /** Um passo da regua, com o seu segmento. */
    public record Step(String code, int dayOffset, String channel, Segment segment, String templateCode) {
        public Step {
            if (code == null || code.isBlank() || channel == null || channel.isBlank() || segment == null) {
                throw new IllegalArgumentException("passo da regua exige codigo, canal e segmento");
            }
            if (dayOffset < 0) {
                throw new IllegalArgumentException("passo da regua exige dayOffset nao negativo");
            }
        }

        /** Passo de mensagem (modelo da P2.30): exige link Pix ao ser emitido. */
        public boolean isMessage() {
            return templateCode != null && !templateCode.isBlank();
        }
    }

    /** Segmento: tipo de debito, faixa de idade, categorias e classes protegidas. */
    public record Segment(String debtType, Integer minAgeDays, Integer maxAgeDays,
                          List<String> categories, List<String> protectedClasses) {
        public Segment {
            categories = categories == null ? List.of() : List.copyOf(categories);
            protectedClasses = protectedClasses == null ? List.of() : List.copyOf(protectedClasses);
        }

        public boolean matches(String debtTypeValue, long ageDays, String category, String protectedClass) {
            if (debtType != null && !debtType.equals(debtTypeValue)) {
                return false;
            }
            if (minAgeDays != null && ageDays < minAgeDays) {
                return false;
            }
            if (maxAgeDays != null && ageDays > maxAgeDays) {
                return false;
            }
            if (!categories.isEmpty() && !categories.contains(category)) {
                return false;
            }
            if (!protectedClasses.isEmpty()
                    && (protectedClass == null || !protectedClasses.contains(protectedClass))) {
                return false;
            }
            return true;
        }
    }

    /** Passos aplicaveis ao segmento do debito, em ordem de dayOffset. */
    public List<Step> stepsFor(String debtType, long ageDays, String category, String protectedClass) {
        return steps.stream()
                .filter(s -> s.segment().matches(debtType, ageDays, category, protectedClass))
                .sorted(java.util.Comparator.comparingInt(Step::dayOffset))
                .toList();
    }

    /** Le e valida {@code dun.ruler} dos parametros vigentes. */
    public static DunningRuler fromParameters(DunningParameters params) {
        JsonNode root = params.requiredNode(DunningParameters.RULER);
        JsonNode array = root.get("steps");
        if (array == null || !array.isArray() || array.isEmpty()) {
            throw new IllegalStateException(DunningParameters.RULER
                    + ": esperado objeto com array 'steps' nao vazio");
        }
        List<Step> steps = new ArrayList<>();
        for (JsonNode node : array) {
            steps.add(parseStep(node));
        }
        return new DunningRuler(steps);
    }

    private static Step parseStep(JsonNode node) {
        String code = textOrNull(node.get("code"));
        String channel = textOrNull(node.get("channel"));
        String template = textOrNull(node.get("templateCode"));
        JsonNode offset = node.get("dayOffset");
        if (offset == null || !offset.isIntegralNumber()) {
            throw new IllegalStateException(DunningParameters.RULER + ": passo sem dayOffset inteiro");
        }
        JsonNode seg = node.get("segment");
        Segment segment = seg == null || seg.isNull()
                ? new Segment(null, null, null, List.of(), List.of())
                : new Segment(textOrNull(seg.get("debtType")),
                        intOrNull(seg.get("minAgeDays")), intOrNull(seg.get("maxAgeDays")),
                        textList(seg.get("categories")), textList(seg.get("protectedClasses")));
        return new Step(code, offset.intValue(), channel, segment, template);
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.stringValue();
    }

    private static Integer intOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.intValue();
    }

    private static List<String> textList(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new IllegalStateException(DunningParameters.RULER + ": segmento com lista invalida");
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> values.add(item.stringValue()));
        return values;
    }

}

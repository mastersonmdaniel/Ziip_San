package br.com.ziipsan.commercial.collection.cutoff;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.parameters.MissingParameterException;
import tools.jackson.databind.JsonNode;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parametros do tenant dos complementos de corte da ED-29, no padrao de
 * {@code DunningParameters} (P4.1) e {@code CutoffParameters} (P3.4): o
 * chamador resolve os valores vigentes e aceitos (P0.19) e entrega o
 * JSON de cada chave; esta classe interpreta e valida.
 *
 * <p>NENHUM destes parametros tem padrao de fabrica nas fontes — a
 * ausencia e erro explicito ({@link MissingParameterException}), nunca
 * valor assumido:
 * <ul>
 *   <li>{@code cut.daily_order_limit} (TR-410/SG-394): quantidade maxima
 *       de OS de corte geradas por dia. A fonte fixa a EXISTENCIA do
 *       limite, nao o numero: o numero e do tenant/ERI.</li>
 *   <li>{@code cut.priority_order} (TR-409/SG-393): ordem dos criterios
 *       de prioridade (localizacao, vencimento, maior valor, tarifa,
 *       categoria, perfil do devedor) e os postos (ranks) de tarifa,
 *       categoria e perfil declarados pelo tenant. Sem ordem declarada
 *       nao ha prioridade calculavel — inventar pesos seria inventar a
 *       politica de corte do ente.</li>
 *   <li>{@code cut.level_schedule} (TR-415/SG-398 e TR-417/SG-400):
 *       dias da semana, faixa horaria e intervalo minimo (dias) desde o
 *       nivel anterior, POR NIVEL. Nivel sem linha no parametro nao
 *       pode ser gerado.</li>
 *   <li>codigos de modelo da P2.30 (JOR-06) das cartas e comunicados:
 *       {@code cut.collection_letter_template} (cartas de cobranca,
 *       TR-390/SG-375), {@code cut.cut_unit_notice_template}
 *       (notificacao para unidade ja cortada, TR-401/SG-386 = POC-32) e
 *       {@code cut.sewer_release_template} (carta de liberacao de
 *       esgoto; comunicado de esgoto de texto parametrizado,
 *       TR-581/SG-558). O codigo e do catalogo de modelos do tenant.</li>
 *   <li>{@code cut.ruler_cutoff_step_code}: codigo, NA REGUA do tenant
 *       ({@code dun.ruler} da P4.1), do passo que manda gerar a OS de
 *       corte. A regua e do tenant; o passo de corte nao se presume.</li>
 * </ul>
 *
 * <p>Formato JSON de cada chave (o mesmo de {@code parameter_versions.value}):
 * <pre>
 *   cut.daily_order_limit        INT     120
 *   cut.priority_order           JSON    {"order":["DUE_DATE","AMOUNT","LOCATION","TARIFF","CATEGORY","DEBTOR_PROFILE"],
 *                                         "ranks":{"TARIFF":{"SOCIAL":1},"CATEGORY":{},"DEBTOR_PROFILE":{}}}
 *   cut.level_schedule           JSON    {"VALVE":{"weekdays":["MONDAY"],"from":"08:00","to":"18:00","minDaysSincePreviousLevel":0}, ...}
 *   cut.collection_letter_template  STRING (JSON) "CARTA_COBRANCA"
 *   cut.cut_unit_notice_template    STRING (JSON) "NOTIFICACAO_POS_CORTE"
 *   cut.sewer_release_template      STRING (JSON) "LIBERACAO_ESGOTO"
 *   cut.ruler_cutoff_step_code      STRING (JSON) "D60_CORTE"
 * </pre>
 */
public final class CutoffExtParameters {

    public static final String DAILY_ORDER_LIMIT = "cut.daily_order_limit";
    public static final String PRIORITY_ORDER = "cut.priority_order";
    public static final String LEVEL_SCHEDULE = "cut.level_schedule";
    public static final String COLLECTION_LETTER_TEMPLATE = "cut.collection_letter_template";
    public static final String CUT_UNIT_NOTICE_TEMPLATE = "cut.cut_unit_notice_template";
    public static final String SEWER_RELEASE_TEMPLATE = "cut.sewer_release_template";
    public static final String RULER_CUTOFF_STEP_CODE = "cut.ruler_cutoff_step_code";

    /** Criterios de prioridade de TR-409/SG-393, na ordem do edital. */
    public enum PriorityFactor {
        LOCATION,
        DUE_DATE,
        AMOUNT,
        TARIFF,
        CATEGORY,
        DEBTOR_PROFILE
    }

    /**
     * Politica de prioridade declarada pelo tenant: a ORDEM dos
     * criterios (comparacao lexicografica) e os postos das dimensoes
     * nominais. Direcoes naturais dos criterios quantitativos, do texto
     * de TR-409: vencimento = a divida mais antiga primeiro; valor =
     * maior valor primeiro; localizacao = sequencia da rota primeiro.
     * Valor nominal sem posto declarado ordena DEPOIS dos com posto
     * (regra declarada aqui, nao presumida do tenant).
     */
    public record PriorityPolicy(List<PriorityFactor> order,
                                 Map<PriorityFactor, Map<String, Integer>> ranks) {
        public PriorityPolicy {
            if (order == null || order.isEmpty()) {
                throw new IllegalArgumentException(PRIORITY_ORDER + ": ordem de criterios vazia");
            }
            if (order.stream().distinct().count() != order.size()) {
                throw new IllegalArgumentException(PRIORITY_ORDER + ": criterio repetido na ordem");
            }
            order = List.copyOf(order);
            Map<PriorityFactor, Map<String, Integer>> copy = new EnumMap<>(PriorityFactor.class);
            if (ranks != null) {
                ranks.forEach((k, v) -> copy.put(k, v == null ? Map.of() : Map.copyOf(v)));
            }
            ranks = Map.copyOf(copy);
        }

        /** Posto de um valor nominal; sem posto declarado, vai para o fim. */
        public int rankOf(PriorityFactor factor, String value) {
            Map<String, Integer> byValue = ranks.getOrDefault(factor, Map.of());
            Integer rank = value == null ? null : byValue.get(value);
            return rank == null ? Integer.MAX_VALUE : rank;
        }
    }

    /** Calendario de geracao de UM nivel (TR-415/SG-398; TR-417/SG-400). */
    public record LevelSchedule(Set<DayOfWeek> weekdays, LocalTime from, LocalTime to,
                                int minDaysSincePreviousLevel) {
        public LevelSchedule {
            if (weekdays == null || weekdays.isEmpty() || from == null || to == null
                    || !to.isAfter(from) || minDaysSincePreviousLevel < 0) {
                throw new IllegalArgumentException(LEVEL_SCHEDULE
                        + ": nivel exige dias da semana, faixa horaria valida e intervalo >= 0");
            }
            weekdays = Set.copyOf(weekdays);
        }

        /** Verdadeiro quando o instante local cai na janela deste nivel. */
        public boolean contains(java.time.LocalDateTime when) {
            return weekdays.contains(when.getDayOfWeek())
                    && !when.toLocalTime().isBefore(from)
                    && when.toLocalTime().isBefore(to);
        }
    }

    private final Map<String, String> jsonByKey;

    private CutoffExtParameters(Map<String, String> jsonByKey) {
        this.jsonByKey = jsonByKey;
    }

    public static CutoffExtParameters fromValues(Map<String, String> jsonByKey) {
        if (jsonByKey == null) {
            throw new IllegalArgumentException("Mapa de parametros e obrigatorio");
        }
        return new CutoffExtParameters(Map.copyOf(jsonByKey));
    }

    /** Cota diaria de OS de corte (TR-410/SG-394): obrigatoria, sem padrao. */
    public int dailyOrderLimit() {
        JsonNode node = required(DAILY_ORDER_LIMIT);
        if (!node.isIntegralNumber() || node.longValue() <= 0 || node.longValue() > Integer.MAX_VALUE) {
            throw new IllegalStateException(DAILY_ORDER_LIMIT + ": esperado inteiro maior que zero");
        }
        return node.intValue();
    }

    /** Politica de prioridade declarada (TR-409/SG-393): obrigatoria, sem padrao. */
    public PriorityPolicy priorityPolicy() {
        JsonNode root = required(PRIORITY_ORDER);
        JsonNode orderNode = root.get("order");
        if (orderNode == null || !orderNode.isArray() || orderNode.isEmpty()) {
            throw new IllegalStateException(PRIORITY_ORDER + ": esperado objeto com array 'order' nao vazio");
        }
        List<PriorityFactor> order = new ArrayList<>();
        for (JsonNode item : orderNode) {
            try {
                order.add(PriorityFactor.valueOf(item.stringValue()));
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new IllegalStateException(PRIORITY_ORDER + ": criterio desconhecido: " + item);
            }
        }
        Map<PriorityFactor, Map<String, Integer>> ranks = new EnumMap<>(PriorityFactor.class);
        JsonNode ranksNode = root.get("ranks");
        if (ranksNode != null && !ranksNode.isNull()) {
            for (PriorityFactor factor : List.of(PriorityFactor.TARIFF, PriorityFactor.CATEGORY,
                    PriorityFactor.DEBTOR_PROFILE)) {
                JsonNode byFactor = ranksNode.get(factor.name());
                if (byFactor == null || byFactor.isNull()) {
                    continue;
                }
                Map<String, Integer> byValue = new LinkedHashMap<>();
                byFactor.properties().forEach(e -> {
                    if (!e.getValue().isIntegralNumber()) {
                        throw new IllegalStateException(PRIORITY_ORDER
                                + ": posto nao inteiro para " + factor + "/" + e.getKey());
                    }
                    byValue.put(e.getKey(), e.getValue().intValue());
                });
                ranks.put(factor, byValue);
            }
        }
        return new PriorityPolicy(order, ranks);
    }

    /** Calendario de geracao de um nivel; nivel sem linha = erro explicito. */
    public LevelSchedule levelSchedule(CutoffLevel level) {
        JsonNode root = required(LEVEL_SCHEDULE);
        JsonNode node = root.get(level.name());
        if (node == null || node.isNull()) {
            throw new MissingParameterException(LEVEL_SCHEDULE,
                    "sem calendario declarado para o nivel " + level + ": o nivel nao pode ser gerado");
        }
        JsonNode days = node.get("weekdays");
        if (days == null || !days.isArray() || days.isEmpty()) {
            throw new IllegalStateException(LEVEL_SCHEDULE + ": nivel " + level + " sem dias da semana");
        }
        Set<DayOfWeek> weekdays = EnumSet.noneOf(DayOfWeek.class);
        for (JsonNode day : days) {
            try {
                weekdays.add(DayOfWeek.valueOf(day.stringValue()));
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new IllegalStateException(LEVEL_SCHEDULE + ": dia desconhecido: " + day);
            }
        }
        LocalTime from = parseTime(node.get("from"), level);
        LocalTime to = parseTime(node.get("to"), level);
        JsonNode interval = node.get("minDaysSincePreviousLevel");
        if (interval == null || !interval.isIntegralNumber()) {
            throw new IllegalStateException(LEVEL_SCHEDULE
                    + ": nivel " + level + " sem minDaysSincePreviousLevel inteiro");
        }
        return new LevelSchedule(weekdays, from, to, interval.intValue());
    }

    public String collectionLetterTemplate() {
        return requiredText(COLLECTION_LETTER_TEMPLATE);
    }

    public String cutUnitNoticeTemplate() {
        return requiredText(CUT_UNIT_NOTICE_TEMPLATE);
    }

    public String sewerReleaseTemplate() {
        return requiredText(SEWER_RELEASE_TEMPLATE);
    }

    public String rulerCutoffStepCode() {
        return requiredText(RULER_CUTOFF_STEP_CODE);
    }

    private static LocalTime parseTime(JsonNode node, CutoffLevel level) {
        if (node == null || node.isNull() || !node.isTextual()) {
            throw new IllegalStateException(LEVEL_SCHEDULE + ": nivel " + level + " sem horario textual");
        }
        try {
            return LocalTime.parse(node.stringValue());
        } catch (RuntimeException e) {
            throw new IllegalStateException(LEVEL_SCHEDULE + ": horario invalido no nivel " + level
                    + ": " + node.stringValue());
        }
    }

    private String requiredText(String key) {
        JsonNode node = required(key);
        if (!node.isTextual() || node.stringValue().isBlank()) {
            throw new IllegalStateException(key + ": esperado texto nao vazio (codigo de modelo da P2.30)");
        }
        return node.stringValue();
    }

    private JsonNode required(String key) {
        String json = jsonByKey.get(key);
        if (json == null || json.isBlank()) {
            throw new MissingParameterException(key, "nenhum valor entregue aos complementos de corte");
        }
        JsonNode node = Json.readTree(json);
        if (node.isMissingNode() || node.isNull()) {
            throw new MissingParameterException(key, "valor JSON e nulo");
        }
        return node;
    }
}

package br.com.ziipsan.commercial.registry.quality;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.parameters.MissingParameterException;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Leitura tipada de {@code quality.rules} (P3.32, classe d): regras e
 * limiares da qualidade cadastral continua, por tenant, no mesmo padrao de
 * {@code CollectionParameters} (P3.1) e {@code BillingParameters} (P2.5): o
 * chamador resolve o valor vigente e aceito e entrega o JSON; esta classe
 * interpreta — sempre pela classe {@link Json} do core — e valida.
 *
 * <p>Principio: parametro ausente e erro explicito
 * ({@link MissingParameterException}), nunca valor assumido. NENHUM limiar
 * numerico tem padrao de fabrica nesta classe: ciclo sem leitura, intervalo
 * de verificacao do hidrometro, idade de possivel obito, janela da conta nao
 * entregue, area minima por economia e compatibilidade CNAE x categoria sao
 * politica do tenant (e, no caso do intervalo do hidrometro, da norma do
 * tenant lida pela ED-14). Regra habilitada sem o limiar de que precisa NAO
 * roda: o motor a registra como nao configurada na varredura, em vez de
 * presumir um numero.
 *
 * <p>Formato do JSON de {@code quality.rules} (o de
 * {@code parameter_versions.value}):
 * <pre>
 * {
 *   "rules": {
 *     "UNIT_WITHOUT_READING": {"enabled": true, "severity": "HIGH",
 *                              "thresholds": {"cyclesWithoutReading": 3}},
 *     "CONTACT_UNVERIFIED":   {"enabled": false}
 *   },
 *   "thresholds": {
 *     "meterVerificationIntervalYears": 7,
 *     "possiblyDeceasedAgeYears": 100,
 *     "undeliveredWindowDays": 90
 *   },
 *   "minAreaPerEconomyM2": {"RESIDENTIAL": 20, "COMMERCIAL": 30},
 *   "cnaeCompatibility":   {"47": ["COMMERCIAL"], "10": ["INDUSTRIAL"]}
 * }
 * </pre>
 *
 * <ul>
 *   <li>{@code rules.<CODIGO>.enabled}: sem declaracao, a regra nasce
 *       habilitada com a severidade de fabrica de {@link QualityRuleCode};</li>
 *   <li>{@code rules.<CODIGO>.severity}: sobrescreve a severidade;</li>
 *   <li>limiar de regra: procurado primeiro em
 *       {@code rules.<CODIGO>.thresholds}, depois em {@code thresholds}
 *       global;</li>
 *   <li>{@code minAreaPerEconomyM2}: area minima (m2) por economia, por
 *       classe regulatoria da categoria (NR 13, art. 6o: RESIDENTIAL,
 *       SOCIAL_RESIDENTIAL, COMMERCIAL, INDUSTRIAL, PUBLIC, OTHER);</li>
 *   <li>{@code cnaeCompatibility}: divisao CNAE (2 primeiros digitos do
 *       codigo de 7 da unidade, PD-06) -&gt; classes regulatorias compativeis.
 *       Divisao fora do mapa NAO gera achado: sem politica declarada, a
 *       regra nao julga aquela divisao.</li>
 * </ul>
 */
public final class QualityParameters {

    public static final String RULES_KEY = "quality.rules";

    public static final String THRESHOLD_CYCLES_WITHOUT_READING = "cyclesWithoutReading";
    public static final String THRESHOLD_METER_VERIFICATION_INTERVAL_YEARS = "meterVerificationIntervalYears";
    public static final String THRESHOLD_POSSIBLY_DECEASED_AGE_YEARS = "possiblyDeceasedAgeYears";
    public static final String THRESHOLD_UNDELIVERED_WINDOW_DAYS = "undeliveredWindowDays";

    private final JsonNode root;

    private QualityParameters(JsonNode root) {
        this.root = root;
    }

    public static QualityParameters fromValues(Map<String, String> jsonByKey) {
        if (jsonByKey == null) {
            throw new IllegalArgumentException("Mapa de parametros e obrigatorio");
        }
        String json = jsonByKey.get(RULES_KEY);
        if (json == null || json.isBlank()) {
            throw new MissingParameterException(RULES_KEY,
                    "nenhum valor entregue a qualidade cadastral");
        }
        JsonNode node = Json.readTree(json);
        if (node.isMissingNode() || node.isNull() || !node.isObject()) {
            throw new MissingParameterException(RULES_KEY, "valor JSON nao e um objeto de regras");
        }
        return new QualityParameters(node);
    }

    /** A regra esta habilitada para o tenant? Sem declaracao, habilitada. */
    public boolean ruleEnabled(QualityRuleCode code) {
        JsonNode enabled = ruleNode(code).path("enabled");
        if (enabled.isMissingNode() || enabled.isNull()) {
            return true;
        }
        if (!enabled.isBoolean()) {
            throw new IllegalStateException(RULES_KEY + ": rules." + code.key()
                    + ".enabled esperado booleano, veio " + enabled.getNodeType());
        }
        return enabled.booleanValue();
    }

    /** Severidade efetiva: a do parametro, ou a de fabrica da regra. */
    public QualitySeverity severityFor(QualityRuleCode code) {
        JsonNode severity = ruleNode(code).path("severity");
        if (severity.isMissingNode() || severity.isNull()) {
            return code.defaultSeverity();
        }
        if (!severity.isTextual()) {
            throw new IllegalStateException(RULES_KEY + ": rules." + code.key()
                    + ".severity esperado texto, veio " + severity.getNodeType());
        }
        return QualitySeverity.fromKey(severity.asText());
    }

    /** Ciclos sem leitura (N da regra UNIT_WITHOUT_READING). Vazio = regra nao configurada. */
    public OptionalInt cyclesWithoutReading() {
        return thresholdInt(QualityRuleCode.UNIT_WITHOUT_READING, THRESHOLD_CYCLES_WITHOUT_READING);
    }

    /**
     * Intervalo de verificacao do hidrometro, em anos. A trava e a da ED-14
     * ({@code MeterParameterRules.validateVerificationIntervalYears}:
     * 1..7 anos, "padrao 7, so reduz") — o registry nao depende do modulo
     * metering, por isso o limite e redeclarado aqui com a fonte citada; um
     * intervalo fora de 1..7 em {@code quality.rules} e erro explicito,
     * nunca aceito em silencio (sem esta trava, o parametro do tenant
     * viraria uma segunda fonte do mesmo intervalo, sem o teto da norma).
     */
    public OptionalInt meterVerificationIntervalYears() {
        OptionalInt value = thresholdInt(QualityRuleCode.METER_VERIFICATION_EXPIRED,
                THRESHOLD_METER_VERIFICATION_INTERVAL_YEARS);
        if (value.isPresent() && (value.getAsInt() < 1 || value.getAsInt() > 7)) {
            throw new IllegalStateException(RULES_KEY + ": limiar "
                    + THRESHOLD_METER_VERIFICATION_INTERVAL_YEARS
                    + " deve estar entre 1 e 7 anos (ED-14: padrao 7, so reduz): "
                    + value.getAsInt());
        }
        return value;
    }

    /** Idade (anos) a partir da qual o titular e sinalizado como possivelmente falecido. */
    public OptionalInt possiblyDeceasedAgeYears() {
        return thresholdInt(QualityRuleCode.HOLDER_POSSIBLY_DECEASED,
                THRESHOLD_POSSIBLY_DECEASED_AGE_YEARS);
    }

    /** Janela (dias) em que um retorno de nao entrega da P2.8 ainda gera achado. */
    public OptionalInt undeliveredWindowDays() {
        return thresholdInt(QualityRuleCode.BILL_UNDELIVERED, THRESHOLD_UNDELIVERED_WINDOW_DAYS);
    }

    /** Area minima por economia (m2) para a classe regulatoria. Vazio = classe nao julgada. */
    public Optional<BigDecimal> minAreaPerEconomyM2(String regulatoryClass) {
        JsonNode node = root.path("minAreaPerEconomyM2").path(regulatoryClass == null ? "" : regulatoryClass);
        if (node.isMissingNode() || node.isNull()) {
            return Optional.empty();
        }
        if (!node.isNumber()) {
            throw new IllegalStateException(RULES_KEY + ": minAreaPerEconomyM2."
                    + regulatoryClass + " esperado numero, veio " + node.getNodeType());
        }
        BigDecimal value = node.decimalValue();
        if (value.signum() <= 0) {
            throw new IllegalStateException(RULES_KEY + ": minAreaPerEconomyM2."
                    + regulatoryClass + " deve ser positivo: " + value);
        }
        return Optional.of(value);
    }

    /** O mapa de area por economia foi declarado (a regra de economias pode julgar)? */
    public boolean hasAreaThresholds() {
        JsonNode node = root.path("minAreaPerEconomyM2");
        return node.isObject() && node.size() > 0;
    }

    /**
     * Classes regulatorias compativeis com a divisao CNAE (2 digitos).
     * Vazio = divisao sem politica declarada (a regra nao julga a divisao).
     */
    public Optional<Set<String>> allowedClassesForCnaeDivision(String division) {
        JsonNode node = root.path("cnaeCompatibility").path(division == null ? "" : division);
        if (node.isMissingNode() || node.isNull()) {
            return Optional.empty();
        }
        if (!node.isArray()) {
            throw new IllegalStateException(RULES_KEY + ": cnaeCompatibility."
                    + division + " esperado array de classes, veio " + node.getNodeType());
        }
        Set<String> classes = new LinkedHashSet<>();
        for (JsonNode item : node) {
            if (!item.isTextual() || item.asText().isBlank()) {
                throw new IllegalStateException(RULES_KEY + ": cnaeCompatibility."
                        + division + " contem classe vazia ou nao textual");
            }
            classes.add(item.asText().strip());
        }
        return Optional.of(Set.copyOf(classes));
    }

    /** O mapa CNAE x categoria foi declarado (a regra de categoria pode julgar)? */
    public boolean hasCnaeCompatibility() {
        JsonNode node = root.path("cnaeCompatibility");
        return node.isObject() && node.size() > 0;
    }

    private JsonNode ruleNode(QualityRuleCode code) {
        return root.path("rules").path(code.key());
    }

    private OptionalInt thresholdInt(QualityRuleCode code, String thresholdKey) {
        JsonNode node = ruleNode(code).path("thresholds").path(thresholdKey);
        if (node.isMissingNode() || node.isNull()) {
            node = root.path("thresholds").path(thresholdKey);
        }
        if (node.isMissingNode() || node.isNull()) {
            return OptionalInt.empty();
        }
        if (!node.isIntegralNumber()) {
            throw new IllegalStateException(RULES_KEY + ": limiar " + thresholdKey
                    + " esperado inteiro, veio " + node.getNodeType());
        }
        int value = node.intValue();
        if (value <= 0) {
            throw new IllegalStateException(RULES_KEY + ": limiar " + thresholdKey
                    + " deve ser positivo: " + value);
        }
        return OptionalInt.of(value);
    }
}

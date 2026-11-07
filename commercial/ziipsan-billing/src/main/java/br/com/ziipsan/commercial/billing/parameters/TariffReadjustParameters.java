package br.com.ziipsan.commercial.billing.parameters;

import br.com.ziipsan.commercial.billing.tariff.ReadjustFormula;
import br.com.ziipsan.commercial.billing.tariff.TariffConfigurationException;
import br.com.ziipsan.commercial.billing.tariff.TariffHistory;
import br.com.ziipsan.commercial.billing.tariff.TariffPublicationRule;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.parameters.MissingParameterException;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Leitura tipada dos parâmetros do reajuste tarifário (P2.29), no mesmo
 * padrão de {@link BillingParameters}: o chamador resolve os valores
 * vigentes e aceitos e entrega o JSON de cada chave; esta classe só
 * interpreta — <b>sempre</b> pela classe {@code Json} do core — e valida.
 *
 * <p>Chaves (catálogo registrado na V4_9, todas classe d, aprovação REG):
 * <pre>
 *   bill.tariff_readjust       OBJECT  {"baseDate": "2025-01-01", "indexCode": "IPCA",
 *                                       "indexPct": "5.1000", "factorXPct": "1.0000",
 *                                       "formula": "INDEX_MINUS_FACTOR_X",
 *                                       "regulatoryAct": "Res. ARESC 000/2026",
 *                                       "publishedOn": "2026-01-05",
 *                                       "applicationDate": "2026-02-10",
 *                                       "tacitApprovalAllowed": true,
 *                                       "publicationMinDays": 30}
 *   tariff.readjust_formula    ENUM    "INDEX_ONLY" | "INDEX_MINUS_FACTOR_X"
 *   tariff.tacit_approval_days INT     15
 *   tariff.history_years_public INT    5
 * </pre>
 *
 * <p>Travas que a leitura já impõe (o parâmetro não afrouxa o produto):
 * {@code publicationMinDays} abaixo de 30 é recusado (piso travado da
 * NR 10 — a regra completa, com elevação ao piso, é de
 * {@link TariffPublicationRule}); {@code tariff.history_years_public}
 * abaixo de 5 é recusado (NR 13, arts. 61 e 62); prazo de homologação
 * tácita, quando presente, é ≥ 1. Ausência de chave obrigatória é erro
 * explícito ({@link MissingParameterException}), nunca valor assumido —
 * exceto {@code publicationMinDays} ausente dentro do objeto, que vale o
 * piso travado de 30 (o piso é da norma, não uma escolha inventada), e
 * {@code tariff.tacit_approval_days} ausente, que significa "homologação
 * tácita não configurada" ({@link OptionalInt#empty()}).
 */
public final class TariffReadjustParameters {

    public static final String TARIFF_READJUST = "bill.tariff_readjust";
    public static final String READJUST_FORMULA = "tariff.readjust_formula";
    public static final String TACIT_APPROVAL_DAYS = "tariff.tacit_approval_days";
    public static final String HISTORY_YEARS_PUBLIC = "tariff.history_years_public";

    /**
     * Fatos do reajuste declarados pelo tenant/agência em
     * {@code bill.tariff_readjust}. {@code indexPct}/{@code factorXPct} são
     * os percentuais do ato para o período desde a data-base: quem os
     * apura é a agência (ARESC/AGIR editam por prestador); o sistema não
     * busca índice em fonte externa nem inventa percentual.
     */
    public record ReadjustSettings(LocalDate baseDate, String indexCode, BigDecimal indexPct,
                                   BigDecimal factorXPct, ReadjustFormula formula, String regulatoryAct,
                                   LocalDate publishedOn, LocalDate applicationDate,
                                   boolean tacitApprovalAllowed, int publicationMinDays) {
        public ReadjustSettings {
            if (baseDate == null || indexCode == null || indexCode.isBlank() || indexPct == null
                    || formula == null || publishedOn == null || applicationDate == null) {
                throw new TariffConfigurationException(
                        "bill.tariff_readjust incompleto: baseDate, indexCode, indexPct, formula, "
                                + "publishedOn e applicationDate são obrigatórios");
            }
            if (publicationMinDays < TariffPublicationRule.MIN_PUBLICATION_DAYS) {
                throw new TariffConfigurationException(
                        "bill.tariff_readjust.publicationMinDays = " + publicationMinDays
                                + " abaixo do piso travado de " + TariffPublicationRule.MIN_PUBLICATION_DAYS
                                + " dias (NR 10): o parâmetro não reduz o mínimo");
            }
        }
    }

    private final Map<String, String> jsonByKey;

    private TariffReadjustParameters(Map<String, String> jsonByKey) {
        this.jsonByKey = jsonByKey;
    }

    /** @param jsonByKey chave do parâmetro → JSON vigente e aceito (texto da coluna {@code value}) */
    public static TariffReadjustParameters fromValues(Map<String, String> jsonByKey) {
        if (jsonByKey == null) {
            throw new IllegalArgumentException("Mapa de parâmetros é obrigatório");
        }
        return new TariffReadjustParameters(Map.copyOf(jsonByKey));
    }

    /** {@code bill.tariff_readjust} (classe d, REG): o objeto ampliado do reajuste. */
    public ReadjustSettings readjustSettings() {
        JsonNode node = require(TARIFF_READJUST);
        if (!node.isObject()) {
            throw invalid(TARIFF_READJUST, "esperado objeto do reajuste");
        }
        int publicationMinDays = node.hasNonNull("publicationMinDays")
                ? parseInt(TARIFF_READJUST, node.get("publicationMinDays"))
                : TariffPublicationRule.MIN_PUBLICATION_DAYS;
        return new ReadjustSettings(
                parseDate(TARIFF_READJUST, node.get("baseDate")),
                parseText(TARIFF_READJUST, node.get("indexCode")),
                parseDecimal(TARIFF_READJUST, node.get("indexPct")),
                node.hasNonNull("factorXPct") ? parseDecimal(TARIFF_READJUST, node.get("factorXPct")) : null,
                parseEnum(TARIFF_READJUST, ReadjustFormula.class, node.get("formula")),
                node.hasNonNull("regulatoryAct") ? parseText(TARIFF_READJUST, node.get("regulatoryAct")) : null,
                parseDate(TARIFF_READJUST, node.get("publishedOn")),
                parseDate(TARIFF_READJUST, node.get("applicationDate")),
                node.hasNonNull("tacitApprovalAllowed")
                        && parseBoolean(TARIFF_READJUST, node.get("tacitApprovalAllowed")),
                publicationMinDays);
    }

    /** {@code tariff.readjust_formula} (classe d, REG): a fórmula vigente do tenant. */
    public ReadjustFormula readjustFormula() {
        return parseEnum(READJUST_FORMULA, ReadjustFormula.class, require(READJUST_FORMULA));
    }

    /**
     * {@code tariff.tacit_approval_days} (classe d, REG): vazio quando a
     * chave não foi configurada — homologação tácita não se presume.
     */
    public OptionalInt tacitApprovalDays() {
        String json = jsonByKey.get(TACIT_APPROVAL_DAYS);
        if (json == null || json.isBlank()) {
            return OptionalInt.empty();
        }
        int days = parseInt(TACIT_APPROVAL_DAYS, Json.readTree(json));
        if (days < 1) {
            throw invalid(TACIT_APPROVAL_DAYS, "prazo de homologação tácita deve ser ao menos 1: " + days);
        }
        return OptionalInt.of(days);
    }

    /** {@code tariff.history_years_public} (classe d, REG): janela do histórico, piso travado de 5 anos. */
    public int historyYearsPublic() {
        int years = parseInt(HISTORY_YEARS_PUBLIC, require(HISTORY_YEARS_PUBLIC));
        if (years < TariffHistory.MIN_HISTORY_YEARS) {
            throw invalid(HISTORY_YEARS_PUBLIC,
                    "histórico público de " + years + " anos abaixo do piso travado de "
                            + TariffHistory.MIN_HISTORY_YEARS + " anos (NR 13, arts. 61 e 62)");
        }
        return years;
    }

    // ------------------------------------------------------------------------------------------
    // Leitura e conversão (JSON só pela classe Json do core, como em BillingParameters)
    // ------------------------------------------------------------------------------------------

    private JsonNode require(String key) {
        String json = jsonByKey.get(key);
        if (json == null || json.isBlank()) {
            throw new MissingParameterException(key, "nenhum valor entregue ao motor");
        }
        JsonNode node = Json.readTree(json);
        if (node.isMissingNode() || node.isNull()) {
            throw new MissingParameterException(key, "valor JSON é nulo");
        }
        return node;
    }

    private static IllegalArgumentException invalid(String key, String detail) {
        return new IllegalArgumentException("Parâmetro '" + key + "' inválido: " + detail);
    }

    private static String parseText(String key, JsonNode node) {
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            throw invalid(key, "esperado texto não vazio");
        }
        return node.asText();
    }

    private static boolean parseBoolean(String key, JsonNode node) {
        if (node == null || !node.isBoolean()) {
            throw invalid(key, "esperado booleano");
        }
        return node.booleanValue();
    }

    private static <E extends Enum<E>> E parseEnum(String key, Class<E> type, JsonNode node) {
        String text = parseText(key, node);
        try {
            return Enum.valueOf(type, text);
        } catch (IllegalArgumentException e) {
            throw invalid(key, "valor fora do catálogo: " + text);
        }
    }

    private static int parseInt(String key, JsonNode node) {
        if (node == null) {
            throw invalid(key, "número ausente");
        }
        try {
            return new BigDecimal(node.asText()).intValueExact();
        } catch (RuntimeException e) {
            throw invalid(key, "esperado inteiro: " + node.asText());
        }
    }

    private static BigDecimal parseDecimal(String key, JsonNode node) {
        if (node == null) {
            throw invalid(key, "percentual ausente");
        }
        try {
            return new BigDecimal(node.asText());
        } catch (RuntimeException e) {
            throw invalid(key, "esperado percentual decimal: " + node.asText());
        }
    }

    private static LocalDate parseDate(String key, JsonNode node) {
        String text = parseText(key, node);
        try {
            return LocalDate.parse(text);
        } catch (RuntimeException e) {
            throw invalid(key, "esperado data ISO (aaaa-mm-dd): " + text);
        }
    }
}

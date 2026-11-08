package br.com.ziipsan.commercial.billing.special;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.parameters.MissingParameterException;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Leitura tipada dos parametros do tenant da ED-22 (PARAMETROS-DO-TENANT),
 * no mesmo desenho de {@code BillingParameters} da P2.5: o chamador resolve
 * os valores vigentes e aceitos e entrega o JSON de cada chave; esta classe
 * so interpreta - <b>sempre</b> pela classe {@code Json} do core - e valida.
 *
 * <p>Principio do catalogo (P2.5): parametro ausente e erro explicito
 * ({@link MissingParameterException}), nunca valor assumido em silencio.
 * Nenhuma chave da ED-22 declara padrao de fabrica no acceptance.
 *
 * <p>Formato JSON de cada chave:
 * <pre>
 *   special_situation.max_months            INT      12
 *   quality.noncompliance_compensation      ENUM     "NONE" | "CREDIT_PERCENT" | "FIXED_CREDIT"
 *   quality.noncompliance_compensation_percent DECIMAL 10.00  (correcao pos-revisao, D7: o percentual
 *                                                             do modo CREDIT_PERCENT vem DESTA chave
 *                                                             nomeada do tenant - nao "do chamador")
 *   bill.advance_billing                    BOOLEAN  true
 *   fiscal.central_billing_invoice_mode     ENUM     "SINGLE" | "PER_UNIT" | "NONE"
 *   large_user.min_volume_required          BOOLEAN  true
 *   large_user.excess_penalty_percent       DECIMAL  20.00   (percentual do contrato/parametro;
 *                                                             nunca presumido - ver LargeUserContract)
 *   large_user.agency_approval_required     BOOLEAN  true
 *   large_user.report_enabled               BOOLEAN  true
 * </pre>
 * Os valores de {@code large_user.excess_penalty_percent} e das demais
 * chaves sao politica do tenant/agencia: nada e semeado nem presumido aqui.
 */
public final class SpecialSituationParameters {

    public static final String MAX_MONTHS = "special_situation.max_months";
    public static final String QUALITY_COMPENSATION = "quality.noncompliance_compensation";
    public static final String QUALITY_COMPENSATION_PERCENT = "quality.noncompliance_compensation_percent";
    public static final String ADVANCE_BILLING = "bill.advance_billing";
    public static final String CENTRAL_INVOICE_MODE = "fiscal.central_billing_invoice_mode";
    public static final String LARGE_MIN_REQUIRED = "large_user.min_volume_required";
    public static final String LARGE_EXCESS_PENALTY_PERCENT = "large_user.excess_penalty_percent";
    public static final String LARGE_AGENCY_APPROVAL_REQUIRED = "large_user.agency_approval_required";
    public static final String LARGE_REPORT_ENABLED = "large_user.report_enabled";

    /** Como a nao-conformidade de qualidade vira compensacao na conta. */
    public enum QualityCompensationMode {
        NONE,
        CREDIT_PERCENT,
        FIXED_CREDIT
    }

    /** Documento fiscal da fatura centralizadora (acceptance da ED-22). */
    public enum CentralBillingInvoiceMode {
        /** Um unico documento fiscal para a fatura agrupadora. */
        SINGLE,
        /** Um documento fiscal por unidade agrupada. */
        PER_UNIT,
        /** Sem documento fiscal (so documento de cobranca agrupador). */
        NONE
    }

    private final Map<String, String> jsonByKey;

    private SpecialSituationParameters(Map<String, String> jsonByKey) {
        this.jsonByKey = jsonByKey;
    }

    public static SpecialSituationParameters fromValues(Map<String, String> jsonByKey) {
        if (jsonByKey == null) {
            throw new IllegalArgumentException("Mapa de parametros e obrigatorio");
        }
        return new SpecialSituationParameters(Map.copyOf(jsonByKey));
    }

    /** {@code special_situation.max_months}: duracao maxima de uma situacao especial, em meses (>= 1). */
    public int maxMonths() {
        int value = parseInt(MAX_MONTHS, require(MAX_MONTHS));
        if (value < 1) {
            throw invalid(MAX_MONTHS, "deve ser ao menos 1 mes: " + value);
        }
        return value;
    }

    /** {@code quality.noncompliance_compensation}: modo da compensacao por nao-conformidade de qualidade. */
    public QualityCompensationMode qualityCompensationMode() {
        return parseEnum(QUALITY_COMPENSATION, QualityCompensationMode.class, require(QUALITY_COMPENSATION));
    }

    /**
     * {@code quality.noncompliance_compensation_percent}: percentual da
     * compensacao no modo {@code CREDIT_PERCENT} (0..100). Fonte nomeada
     * (correcao pos-revisao, D7): parametro do tenant lido por esta classe,
     * no padrao {@code BillingParameters} da P2.5 - ausencia e
     * {@link MissingParameterException}, nunca valor solto do chamador.
     * So e exigido quando o modo vigente e {@code CREDIT_PERCENT}.
     */
    public BigDecimal qualityCompensationPercent() {
        BigDecimal value = parseDecimal(QUALITY_COMPENSATION_PERCENT, require(QUALITY_COMPENSATION_PERCENT));
        if (value.signum() < 0 || value.compareTo(new BigDecimal("100")) > 0) {
            throw invalid(QUALITY_COMPENSATION_PERCENT, "percentual fora de 0..100: " + value);
        }
        return value;
    }

    /** {@code bill.advance_billing}: faturamento antecipado (antes do fim do periodo) ligado? */
    public boolean advanceBilling() {
        return parseBoolean(ADVANCE_BILLING, require(ADVANCE_BILLING));
    }

    /** {@code fiscal.central_billing_invoice_mode}: documento fiscal da centralizadora. */
    public CentralBillingInvoiceMode centralBillingInvoiceMode() {
        return parseEnum(CENTRAL_INVOICE_MODE, CentralBillingInvoiceMode.class, require(CENTRAL_INVOICE_MODE));
    }

    /** {@code large_user.min_volume_required}: o contrato especial cobra o volume minimo? */
    public boolean largeUserMinVolumeRequired() {
        return parseBoolean(LARGE_MIN_REQUIRED, require(LARGE_MIN_REQUIRED));
    }

    /** {@code large_user.excess_penalty_percent}: percentual de penalidade sobre o excesso (0..100). */
    public BigDecimal largeUserExcessPenaltyPercent() {
        BigDecimal value = parseDecimal(LARGE_EXCESS_PENALTY_PERCENT, require(LARGE_EXCESS_PENALTY_PERCENT));
        if (value.signum() < 0 || value.compareTo(new BigDecimal("100")) > 0) {
            throw invalid(LARGE_EXCESS_PENALTY_PERCENT, "percentual fora de 0..100: " + value);
        }
        return value;
    }

    /** {@code large_user.agency_approval_required}: contrato especial exige aprovacao da agencia? */
    public boolean largeUserAgencyApprovalRequired() {
        return parseBoolean(LARGE_AGENCY_APPROVAL_REQUIRED, require(LARGE_AGENCY_APPROVAL_REQUIRED));
    }

    /** {@code large_user.report_enabled}: relatorio dos contratos a agencia habilitado? */
    public boolean largeUserReportEnabled() {
        return parseBoolean(LARGE_REPORT_ENABLED, require(LARGE_REPORT_ENABLED));
    }

    private JsonNode require(String key) {
        String json = jsonByKey.get(key);
        if (json == null || json.isBlank()) {
            throw new MissingParameterException(key, "nenhum valor entregue ao motor");
        }
        JsonNode node = Json.readTree(json);
        if (node.isMissingNode() || node.isNull()) {
            throw new MissingParameterException(key, "valor JSON e nulo");
        }
        return node;
    }

    private static IllegalArgumentException invalid(String key, String detail) {
        return new IllegalArgumentException("Parametro '" + key + "' invalido: " + detail);
    }

    private static boolean parseBoolean(String key, JsonNode node) {
        if (node == null || !node.isBoolean()) {
            throw invalid(key, "esperado booleano");
        }
        return node.booleanValue();
    }

    private static <E extends Enum<E>> E parseEnum(String key, Class<E> type, JsonNode node) {
        if (node == null || !node.isTextual()) {
            throw invalid(key, "esperado texto");
        }
        try {
            return Enum.valueOf(type, node.asText());
        } catch (IllegalArgumentException e) {
            throw invalid(key, "valor fora do catalogo: " + node.asText());
        }
    }

    private static int parseInt(String key, JsonNode node) {
        try {
            return new BigDecimal(node.asText()).intValueExact();
        } catch (RuntimeException e) {
            throw invalid(key, "esperado inteiro: " + node.asText());
        }
    }

    private static BigDecimal parseDecimal(String key, JsonNode node) {
        try {
            return new BigDecimal(node.asText()).setScale(2, RoundingMode.UNNECESSARY);
        } catch (RuntimeException e) {
            throw invalid(key, "esperado decimal com ate 2 casas: " + node.asText());
        }
    }
}

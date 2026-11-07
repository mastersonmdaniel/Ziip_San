package br.com.ziipsan.platform.regulatory;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.parameters.ParameterLimits;
import br.com.ziipsan.platform.regulatory.RegulatoryProfileDef.Confirmation;
import br.com.ziipsan.platform.regulatory.RegulatoryProfileDef.ProfileValue;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Guarda de carga dos perfis regulatórios (P2.31): o que entra como valor
 * de fábrica tem de passar pelos limites travados da P0.20 (RAS-16) e pela
 * hierarquia de camadas do mapa de ERIs (§6, item 1) — lei federal sobre
 * norma da ERI.
 *
 * <p>Duas recusas são estruturais, não configuráveis:
 * <ul>
 *   <li>janela de corte mais frouxa que a Lei 14.015/2020 (ex.: o corte até
 *       12 h de sexta-feira da ARIS, art. 158; da AGIR, art. 148; da ARESC,
 *       art. 85) nunca é valor efetivo: o valor é o piso federal e a regra
 *       local vive só como divergência do perfil;</li>
 *   <li>regra que retire a tarifa social por número de cortes (AGIR,
 *       RN 001/2013, art. 127, §7º) é recusada pela Lei 14.898/2024 e não
 *       pode aparecer como valor em chave alguma.</li>
 * </ul>
 *
 * <p>Os limites por chave reutilizam {@link ParameterLimits} (P0.20), um
 * método nomeado por chave, como manda o acceptance da P0.20.
 */
public final class RegulatoryLoadGuard {

    /** Teto diário de juros das ERIs de SC (mapa de ERIs §4 e §6). */
    public static final BigDecimal SC_INTEREST_DAILY_CAP_PCT = new BigDecimal("0.033");

    /** Multa de fábrica dos perfis: o padrão de 2% (CDC, art. 52, §1º; P0.20). */
    public static final BigDecimal FACTORY_FINE_PCT = new BigDecimal("2.00");

    /** Piso federal do aviso de corte (Lei 11.445/2007, art. 40, §2º; PARAMETROS G9, classe c). */
    public static final int CUT_NOTICE_FLOOR_DAYS = 30;

    /** Regras posteriores à média da P2.5 ({@code AfterCapRule}, metering). */
    public static final Set<String> AFTER_CAP_RULES = Set.of(
            "BILL_MINIMUM", "SETTLE_BALANCE_IN_3_INSTALLMENTS");

    /**
     * Chaves cujo valor de fábrica é um REQUISITO confirmado, não um valor
     * de versão — correção pós-revisão do GRAVE de formato da P2.31.
     *
     * <p>{@code bill.due_date_options} é {@code LIST_INT} no catálogo (core
     * V3) e o consumidor real da P2.5 ({@code BillingParameters.dueDateOptions()})
     * exige um array JSON de dias ({@code [5, 10, 15, ...]}). A norma da
     * ERI, porém, só confirma o NÚMERO mínimo de datas ofertadas (6 nas
     * ERIs lidas de SC; mínimo de 4 no CISAM-SUL) — os dias concretos são
     * escolha do tenant, e inventar uma lista de dias seria fabricar dado
     * regulatório. Por isso o perfil carrega o requisito no formato de
     * catálogo {@code {"minCount":N}} (metadado do perfil, validado por
     * {@link #dueDateMinCount}), que NUNCA vira {@code parameter_versions}:
     * na ativação, {@code RegulatoryActivationRule.planVersions} exige a
     * lista concreta do tenant, valida-a contra o mínimo confirmado
     * ({@link #dueDateDays} + contagem) e planeja ESSA lista como versão —
     * legível pelo billing em qualquer estado válido.
     */
    public static final Set<String> TENANT_COMPLETED_KEYS = Set.of("bill.due_date_options");

    /** Marcadores de regra local que jamais podem ser o valor efetivo de uma chave. */
    private static final Set<String> FORBIDDEN_EFFECTIVE_MARKERS = Set.of(
            "FRIDAY_UNTIL_12H", "FRIDAY_UNTIL_NOON", "ALLOW_FRIDAY_MORNING_CUT",
            "SOCIAL_LOSS_AFTER_TWO_CUTS", "BENEFIT_LOSS_BY_CUTS");

    private RegulatoryLoadGuard() {
    }

    /** Valida todos os valores confirmados de um perfil. Recusa = exceção com a norma citada. */
    public static void validateProfile(RegulatoryProfileDef profile) {
        if (profile == null) {
            throw new IllegalArgumentException("profile é obrigatório");
        }
        for (ProfileValue value : profile.values()) {
            assertNoForbiddenMarker(profile.profileKey(), value);
            if (value.confirmation() == Confirmation.CONFIRMED) {
                validateConfirmedValue(profile.profileKey(), value);
            }
        }
        for (RegulatoryProfileDef.ProfileDivergence divergence : profile.divergences()) {
            if (divergence.resolution() == null) {
                throw new IllegalArgumentException("Divergência sem resolução no perfil "
                        + profile.profileKey() + ": " + divergence.ruleCode());
            }
        }
    }

    /**
     * Um valor avulso (de fábrica ou informado pelo tenant na ativação) é
     * carregável para esta chave? É a mesma trava da carga, aplicada ao
     * valor do tenant no envelope (P0.19): divergir do perfil não pode
     * furar o piso federal nem os limites da P0.20.
     */
    public static void assertValueLoadable(String definitionKey, String valueJson) {
        if (definitionKey == null || valueJson == null) {
            throw new IllegalArgumentException("definitionKey e valueJson são obrigatórios");
        }
        for (String marker : FORBIDDEN_EFFECTIVE_MARKERS) {
            if (valueJson.contains(marker)) {
                throw new IllegalArgumentException("Valor recusado para " + definitionKey
                        + ": a regra marcada " + marker + " contraria lei federal "
                        + "(Lei 14.015/2020 ou Lei 14.898/2024) e só pode constar como "
                        + "divergência do perfil, nunca como valor efetivo");
            }
        }
        if ("cut.forbidden_window".equals(definitionKey)
                && !valueJson.contains("FEDERAL_FLOOR_14_015")) {
            throw new IllegalArgumentException("cut.forbidden_window recusado: o piso da "
                    + "Lei 14.015/2020 é travado (vedado iniciar corte em sexta, sábado, "
                    + "domingo, feriado e véspera); janela local mais frouxa é divergência, "
                    + "não valor");
        }
        if ("bill.due_date_options".equals(definitionKey)) {
            // O valor de VERSÃO desta chave é a lista de dias do tenant, no
            // formato do consumidor da P2.5 — nunca o {"minCount":N} do
            // catálogo, que é requisito do perfil, não valor (ver
            // TENANT_COMPLETED_KEYS).
            dueDateDays(valueJson);
            return;
        }
        validateConfirmedValue("(valor avulso)",
                new ProfileValue(definitionKey, valueJson, null, null,
                        RegulatoryProfileDef.DeadlineBasis.NOT_APPLICABLE,
                        Confirmation.CONFIRMED, null));
    }

    /**
     * Lê a lista concreta de dias de {@code bill.due_date_options} no
     * formato EXATO do consumidor da P2.5: array JSON de inteiros
     * (o {@code BillingParameters.dueDateOptions()} recusa qualquer coisa
     * que não seja array, com "esperado array de dias"). Valida pela trava
     * do produto ({@link ParameterLimits#validateDueDateOptions} sem o
     * piso de concessionária, que depende do tipo do prestador e é
     * conferido em {@link #validateDueDateOptionsFor}): dias 1..31,
     * distintos, lista não vazia.
     */
    public static List<Integer> dueDateDays(String valueJson) {
        JsonNode node = Json.readTree(valueJson);
        if (!node.isArray()) {
            throw new IllegalArgumentException("bill.due_date_options recusado: esperado "
                    + "array de dias (formato do BillingParameters da P2.5), veio " + valueJson);
        }
        List<Integer> days = new ArrayList<>();
        for (JsonNode day : node) {
            if (!day.isIntegralNumber()) {
                throw new IllegalArgumentException("bill.due_date_options recusado: dia "
                        + "não inteiro na lista: " + valueJson);
            }
            days.add(day.asInt());
        }
        ParameterLimits.validateDueDateOptions(days, false, null);
        return List.copyOf(days);
    }

    private static void assertNoForbiddenMarker(String profileKey, ProfileValue value) {
        if (value.valueJson() == null) {
            return;
        }
        for (String marker : FORBIDDEN_EFFECTIVE_MARKERS) {
            if (value.valueJson().contains(marker)) {
                throw new IllegalArgumentException("Carga recusada no perfil " + profileKey
                        + ": a chave " + value.definitionKey() + " traz a regra " + marker
                        + ", que contraria lei federal; ela pertence às divergências do "
                        + "perfil, com o piso federal como valor efetivo");
            }
        }
    }

    private static void validateConfirmedValue(String profileKey, ProfileValue value) {
        String key = value.definitionKey();
        String json = value.valueJson();
        switch (key) {
            case "fee.fine_pct" -> {
                BigDecimal fine = decimal(json, key);
                ParameterLimits.validateFinePercent(fine);
                if (fine.compareTo(FACTORY_FINE_PCT) > 0) {
                    throw new IllegalArgumentException("Carga recusada no perfil " + profileKey
                            + ": fee.fine_pct de fábrica acima de 2% (" + fine + ") exigiria o "
                            + "fluxo da classe b da P0.20 (norma citada, aprovação dupla, aceite "
                            + "formal e alerta de risco), que não é carga de fábrica "
                            + "(CDC, art. 52, §1º)");
                }
            }
            case "fee.interest_mode" -> ParameterLimits.validateInterestMode(text(json, key));
            case "fee.interest_daily_pct" -> {
                BigDecimal daily = decimal(json, key);
                if (daily.signum() < 0 || daily.compareTo(SC_INTEREST_DAILY_CAP_PCT) > 0) {
                    throw new IllegalArgumentException("Carga recusada no perfil " + profileKey
                            + ": fee.interest_daily_pct " + daily + " fora do teto de SC de "
                            + "0,033% ao dia (mapa de ERIs §4 e §6)");
                }
            }
            case "meter.average_months" -> ParameterLimits.validateAverageMonths(integer(json, key));
            case "meter.max_estimated_cycles" ->
                    ParameterLimits.validateMaxEstimatedCycles(integer(json, key));
            case "meter.after_cap_rule" -> {
                String rule = text(json, key);
                if (!AFTER_CAP_RULES.contains(rule)) {
                    throw new IllegalArgumentException("Carga recusada no perfil " + profileKey
                            + ": meter.after_cap_rule '" + rule + "' fora do catálogo da P2.5 "
                            + "(BILL_MINIMUM, SETTLE_BALANCE_IN_3_INSTALLMENTS)");
                }
            }
            case "ret.billing_history_months" ->
                    ParameterLimits.validateBillingHistoryMonths(integer(json, key), null);
            case "cut.min_notice_days" -> {
                int days = integer(json, key);
                if (days < CUT_NOTICE_FLOOR_DAYS) {
                    throw new IllegalArgumentException("Carga recusada no perfil " + profileKey
                            + ": cut.min_notice_days " + days + " abaixo do piso federal de "
                            + "30 dias (Lei 11.445/2007, art. 40, §2º; classe c, só amplia)");
                }
            }
            case "cut.max_debt_age_days" -> {
                int days = integer(json, key);
                if (days < 1) {
                    throw new IllegalArgumentException("Carga recusada no perfil " + profileKey
                            + ": cut.max_debt_age_days precisa ser positivo");
                }
            }
            case "sac.deadline_counting_rule" -> {
                String rule = text(json, key);
                if (!rule.equals("CALENDAR") && !rule.equals("BUSINESS")) {
                    throw new IllegalArgumentException("Carga recusada no perfil " + profileKey
                            + ": sac.deadline_counting_rule '" + rule + "' fora de "
                            + "CALENDAR/BUSINESS");
                }
            }
            case "sac.priority_min_age_years" -> {
                int age = integer(json, key);
                if (age < 1 || age > 120) {
                    throw new IllegalArgumentException("Carga recusada no perfil " + profileKey
                            + ": sac.priority_min_age_years fora de 1..120: " + age);
                }
            }
            case "leak.max_discount_pct_water" -> {
                BigDecimal pct = decimal(json, key);
                if (pct.signum() < 0 || pct.compareTo(new BigDecimal("100")) > 0) {
                    throw new IllegalArgumentException("Carga recusada no perfil " + profileKey
                            + ": leak.max_discount_pct_water fora de 0..100: " + pct);
                }
            }
            case "bill.due_date_options" -> dueDateMinCount(profileKey, json);
            case "meter.reading_cycle_days" -> readingCycleBand(profileKey, json);
            default -> {
                // Chave sem limite travado próprio nesta guarda: a coerência
                // de tipo é do catálogo (core V3/V1_13) e do TenantParameterService.
            }
        }
    }

    /**
     * {@code bill.due_date_options} do PERFIL (catálogo): o mínimo de datas
     * ofertadas, inteiro positivo. Este é o formato do requisito de fábrica
     * {@code {"minCount":N}} — ele vive no catálogo do perfil e na R__, e
     * NUNCA em {@code parameter_versions} (o valor de versão é a lista de
     * dias do tenant, ver {@link #dueDateDays}).
     */
    public static int dueDateMinCount(String profileKey, String valueJson) {
        JsonNode node = Json.readTree(valueJson);
        JsonNode minCount = node.get("minCount");
        if (minCount == null || !minCount.isIntegralNumber()) {
            throw new IllegalArgumentException("Carga recusada no perfil " + profileKey
                    + ": bill.due_date_options do perfil precisa de minCount inteiro");
        }
        int count = minCount.asInt();
        if (count < 1) {
            throw new IllegalArgumentException("Carga recusada no perfil " + profileKey
                    + ": bill.due_date_options com minCount " + count);
        }
        return count;
    }

    /**
     * Para concessionária, o mínimo efetivo de datas é o maior entre o do
     * perfil e o piso federal de 6 (Lei 8.987/1995, art. 7º-A, via
     * {@link ParameterLimits}): o mínimo de 4 do CISAM-SUL vale para os seus
     * prestadores municipais, não reduz o piso de concessionária. Para os
     * demais tipos de prestador vale o mínimo do perfil (o mínimo de
     * autarquia segue pendente no catálogo da P0.20, sem número inventado).
     */
    public static void validateDueDateOptionsFor(RegulatoryProfileDef profile, boolean concessionaire) {
        ProfileValue value = profile.value("bill.due_date_options");
        if (value == null || !value.hasFactoryValue()) {
            return;
        }
        int minCount = dueDateMinCount(profile.profileKey(), value.valueJson());
        if (concessionaire && minCount < ParameterLimits.DUE_DATE_OPTIONS_MIN_FOR_CONCESSIONAIRE) {
            throw new IllegalArgumentException("Perfil " + profile.profileKey() + " recusado "
                    + "para concessionária: bill.due_date_options com mínimo de " + minCount
                    + " datas não reduz o piso de 6 da Lei 8.987/1995, art. 7º-A; o tenant "
                    + "concessionário oferta ao menos 6, com o mínimo do perfil registrado "
                    + "como divergência de ativação");
        }
    }

    /** Faixa do ciclo de leitura do perfil: min ≤ max, ambos positivos (objeto da V1_13). */
    public static int[] readingCycleBand(String profileKey, String valueJson) {
        JsonNode node = Json.readTree(valueJson);
        JsonNode min = node.get("minDays");
        JsonNode max = node.get("maxDays");
        if (min == null || max == null || !min.isIntegralNumber() || !max.isIntegralNumber()) {
            throw new IllegalArgumentException("Carga recusada no perfil " + profileKey
                    + ": meter.reading_cycle_days precisa de minDays/maxDays inteiros");
        }
        int lo = min.asInt();
        int hi = max.asInt();
        if (lo < 1 || hi < lo) {
            throw new IllegalArgumentException("Carga recusada no perfil " + profileKey
                    + ": faixa de leitura inválida " + lo + ".." + hi);
        }
        return new int[]{lo, hi};
    }

    private static BigDecimal decimal(String json, String key) {
        JsonNode node = Json.readTree(json);
        if (!node.isNumber()) {
            throw new IllegalArgumentException(key + ": valor precisa ser número, veio " + json);
        }
        return node.decimalValue();
    }

    private static int integer(String json, String key) {
        JsonNode node = Json.readTree(json);
        if (!node.isIntegralNumber()) {
            throw new IllegalArgumentException(key + ": valor precisa ser inteiro, veio " + json);
        }
        return node.asInt();
    }

    private static String text(String json, String key) {
        JsonNode node = Json.readTree(json);
        if (!node.isTextual()) {
            throw new IllegalArgumentException(key + ": valor precisa ser texto, veio " + json);
        }
        return node.asText();
    }
}

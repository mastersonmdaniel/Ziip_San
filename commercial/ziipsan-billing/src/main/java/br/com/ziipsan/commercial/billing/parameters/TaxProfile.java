package br.com.ziipsan.commercial.billing.parameters;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.parameters.MissingParameterException;
import tools.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Perfil tributário do tenant ({@code tax.profile}, P2.34 — GES-13/RAS-02):
 * parâmetro <b>classe b</b>, aprovação <b>JUR</b>, registrado no catálogo
 * global pela V4_12. É um objeto com três subcampos:
 *
 * <pre>
 *   tax.profile  OBJECT  {"cashback_enabled": true,
 *                         "tax_event_date_rule": "CONTINUOUS_OPERATION",
 *                         "split_payment_mode": "SEGREGATED_AT_SETTLEMENT"}
 * </pre>
 *
 * <p>Princípio do catálogo (como em {@link BillingParameters}): perfil
 * ausente, subcampo ausente ou valor fora do catálogo é <b>erro
 * explícito</b> ({@link MissingParameterException} /
 * {@link IllegalArgumentException}), nunca perfil presumido. A leitura é
 * sempre pela classe {@code Json} do core. As <b>alíquotas não vivem
 * aqui</b>: ficam na tabela com vigência da P2.4 (V4_5), que esta tarefa
 * não duplica nem altera.
 *
 * <p>{@code split_payment_mode}: o catálogo é o aguardado pela conciliação
 * da P3.1 ({@code SplitPaymentReconciler}, RAS-09) — os três códigos são os
 * mesmos lá. Aqui o parâmetro apenas <b>existe e é validado</b>; a
 * conciliação do split payment é da P3.1, fora do escopo da P2.34.
 *
 * <p>{@code tax_event_date_rule}: {@link TaxEventDateRule#CONTINUOUS_OPERATION}
 * nomeia a regra de fato gerador de operação contínua da LC 227/2026
 * (art. 10, §3º). O texto da regra é conferido <b>antes da ativação</b>:
 * este enum nomeia o dispositivo citado no aceite; ele <b>não</b> contém
 * nem presume o texto da LC 227/2026 (ver {@code TaxEventDateResolver}).
 */
public record TaxProfile(boolean cashbackEnabled, TaxEventDateRule taxEventDateRule,
                         SplitPaymentMode splitPaymentMode) {

    /** Chave do parâmetro no catálogo global. */
    public static final String PROFILE_KEY = "tax.profile";

    /**
     * Regra que fixa o {@code tax_event_date}. Único valor: a regra de
     * operação contínua da LC 227/2026, art. 10, §3º — ativação condicionada
     * à conferência do texto (o código não presume o texto).
     */
    public enum TaxEventDateRule {
        CONTINUOUS_OPERATION
    }

    /** Modo do split payment (catálogo compartilhado com a P3.1; a conciliação é dela). */
    public enum SplitPaymentMode {
        SEGREGATED_AT_SETTLEMENT,
        SIMPLIFIED_SEGREGATION,
        NO_SEGREGATION
    }

    public TaxProfile {
        if (taxEventDateRule == null) {
            throw new IllegalArgumentException("tax.profile: tax_event_date_rule é obrigatório");
        }
        if (splitPaymentMode == null) {
            throw new IllegalArgumentException("tax.profile: split_payment_mode é obrigatório");
        }
    }

    /**
     * Interpreta o {@code tax.profile} a partir dos valores vigentes e
     * aceitos entregues pelo chamador (mesmo contrato de
     * {@link BillingParameters#fromValues}).
     *
     * @param jsonByKey chave do parâmetro → JSON vigente e aceito
     */
    public static TaxProfile fromValues(Map<String, String> jsonByKey) {
        if (jsonByKey == null) {
            throw new IllegalArgumentException("Mapa de parâmetros é obrigatório");
        }
        String json = jsonByKey.get(PROFILE_KEY);
        if (json == null || json.isBlank()) {
            throw new MissingParameterException(PROFILE_KEY, "nenhum valor entregue ao motor");
        }
        JsonNode node = Json.readTree(json);
        if (node.isMissingNode() || node.isNull()) {
            throw new MissingParameterException(PROFILE_KEY, "valor JSON é nulo");
        }
        if (!node.isObject()) {
            throw invalid("esperado objeto {cashback_enabled, tax_event_date_rule, split_payment_mode}");
        }
        JsonNode enabled = node.get("cashback_enabled");
        if (enabled == null || !enabled.isBoolean()) {
            throw invalid("cashback_enabled é obrigatório e deve ser booleano");
        }
        return new TaxProfile(enabled.booleanValue(),
                parseEnum("tax_event_date_rule", TaxEventDateRule.class, node.get("tax_event_date_rule")),
                parseEnum("split_payment_mode", SplitPaymentMode.class, node.get("split_payment_mode")));
    }

    private static <E extends Enum<E>> E parseEnum(String field, Class<E> type, JsonNode node) {
        if (node == null || !node.isTextual()) {
            throw invalid(field + " é obrigatório e deve ser texto");
        }
        try {
            return Enum.valueOf(type, node.asText());
        } catch (IllegalArgumentException e) {
            throw invalid(field + " fora do catálogo: " + node.asText());
        }
    }

    private static IllegalArgumentException invalid(String detail) {
        return new IllegalArgumentException("Parâmetro '" + PROFILE_KEY + "' inválido: " + detail);
    }
}

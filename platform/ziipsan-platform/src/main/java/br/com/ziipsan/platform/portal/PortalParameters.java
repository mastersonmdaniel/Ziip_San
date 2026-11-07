package br.com.ziipsan.platform.portal;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.parameters.MissingParameterException;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

/**
 * Parametros {@code portal.*} do tenant (P3.3), no mesmo padrao de
 * {@code PixParameters} (P3.2) e {@code BillingParameters} (P2.5): JSON lido
 * SOMENTE pela classe {@link Json} do core e ausencia = {@link MissingParameterException},
 * nunca valor presumido.
 *
 * <p>Chaves, classes e padroes de fabrica (PARAMETROS-DO-TENANT; ADR-0043):
 * <pre>
 *   portal.guest_payment              classe b  OFF | CODE_ONLY | CODE_AND_DOCUMENT_SUFFIX   fabrica: CODE_ONLY
 *   portal.idp_govbr_enabled          classe d  booleano                                     fabrica: false
 *   portal.min_assurance_by_operation classe b  objeto {OPERACAO: BRONZE|SILVER|GOLD}        fabrica: SILVER nas 4 operacoes sensiveis
 *   portal.bot_defense                classe d  OFF | PASSIVE | CHALLENGE                    (sem fabrica presumida aqui)
 *   portal.account_throttle           classe c  objeto de limites; SO REDUZ contra a fabrica (validado em {@link #accountThrottle()})
 *   portal.static_fallback_enabled    classe d  booleano
 *   portal.accessibility_statement    classe d  objeto {contactChannel, evaluationDate, text}
 *   portal.vlibras_enabled            classe d  booleano; ligar exige consulta formal aos termos (ver PortalAccessibility)
 * </pre>
 *
 * <p>O cadastro destas chaves em {@code public.parameter_definitions} (migration
 * core) NAO e entregue nesta tarefa: o mapa de versoes do plano (secao 3) nao
 * reserva versao para a P3.3 e os proximos slots core V9_x ja estao ocupados
 * (V9_11 pela P3.36, V9_12 pela P2.11). Ver RELATORIO-P3.3, pendencia P-1.
 */
public final class PortalParameters {

    public static final String GUEST_PAYMENT = "portal.guest_payment";
    public static final String IDP_GOVBR_ENABLED = "portal.idp_govbr_enabled";
    public static final String MIN_ASSURANCE_BY_OPERATION = "portal.min_assurance_by_operation";
    public static final String BOT_DEFENSE = "portal.bot_defense";
    public static final String ACCOUNT_THROTTLE = "portal.account_throttle";
    public static final String STATIC_FALLBACK_ENABLED = "portal.static_fallback_enabled";
    public static final String ACCESSIBILITY_STATEMENT = "portal.accessibility_statement";
    public static final String VLIBRAS_ENABLED = "portal.vlibras_enabled";

    /** Modo do pagamento sem conta (ADR-0043). */
    public enum GuestPaymentMode { OFF, CODE_ONLY, CODE_AND_DOCUMENT_SUFFIX }

    /** Defesa contra automacao (WCAG 2.2, criterio 3.3.8). */
    public enum BotDefenseMode { OFF, PASSIVE, CHALLENGE }

    /** Nivel de garantia do gov.br (bronze, prata, ouro). */
    public enum AssuranceLevel {
        BRONZE(1), SILVER(2), GOLD(3);
        private final int rank;
        AssuranceLevel(int rank) { this.rank = rank; }
        public boolean atLeast(AssuranceLevel minimum) { return minimum == null || this.rank >= minimum.rank; }
    }

    /** Operacoes sensiveis do portal (as 4 do aceite + as demais, sem exigencia por padrao). */
    public enum SensitiveOperation {
        HOLDER_CHANGE(true), SOCIAL_TARIFF(true), CONTACT_CHANGE(true), DEBT_NEGOTIATION(true),
        SECOND_COPY(false), REQUEST_OPENING(false);
        private final boolean factorySilver;
        SensitiveOperation(boolean factorySilver) { this.factorySilver = factorySilver; }
        /** Fabrica (PARAMETROS-DO-TENANT): prata nas 4 operacoes sensiveis. */
        public boolean factoryRequiresSilver() { return factorySilver; }
    }

    /**
     * Limites de criacao de conta e de tentativa (classe c, SO REDUZ): nenhum
     * limite vigente pode ser MAIOR (mais frouxo) que o de fabrica entregue
     * junto; a conferencia e feita em {@link #accountThrottle(AccountThrottle)}.
     */
    public record AccountThrottle(int maxAccountsPerEmail, int maxLoginFailuresPerIdentifier,
                                  int maxLoginFailuresPerIp, int maxGuestQueriesPerOrigin,
                                  int maxGuestQueriesPerCode, int windowMinutes) {
        public AccountThrottle {
            if (maxAccountsPerEmail < 1 || maxLoginFailuresPerIdentifier < 1 || maxLoginFailuresPerIp < 1
                    || maxGuestQueriesPerOrigin < 1 || maxGuestQueriesPerCode < 1 || windowMinutes < 1) {
                throw new IllegalArgumentException("portal.account_throttle: todos os limites devem ser >= 1");
            }
        }

        /**
         * Classe c: recusar qualquer configuracao mais frouxa que a fabrica.
         * As contagens sao tetos por janela: teto maior afrouxa. A janela tem
         * semantica inversa (revisao cruzada da P3.3): janela MENOR renova as
         * tentativas mais cedo e afrouxa a taxa (contagem/janela); por isso
         * janela menor que a da fabrica tambem e recusada, e janela maior
         * (mais restritiva) e aceita.
         */
        public void validateOnlyReduces(AccountThrottle factory) {
            if (factory == null) {
                throw new IllegalArgumentException("portal.account_throttle: fabrica e obrigatoria para a trava de classe c");
            }
            if (maxAccountsPerEmail > factory.maxAccountsPerEmail
                    || maxLoginFailuresPerIdentifier > factory.maxLoginFailuresPerIdentifier
                    || maxLoginFailuresPerIp > factory.maxLoginFailuresPerIp
                    || maxGuestQueriesPerOrigin > factory.maxGuestQueriesPerOrigin
                    || maxGuestQueriesPerCode > factory.maxGuestQueriesPerCode
                    || windowMinutes < factory.windowMinutes) {
                throw new IllegalArgumentException(
                        "portal.account_throttle e classe c (so reduz): limite acima do padrao de fabrica recusado");
            }
        }
    }

    /** Declaracao de acessibilidade do tenant (classe d): contato e data da avaliacao sao obrigatorios. */
    public record AccessibilityStatement(String contactChannel, LocalDate evaluationDate, String text) {
        public AccessibilityStatement {
            if (contactChannel == null || contactChannel.isBlank()) {
                throw new IllegalArgumentException("portal.accessibility_statement: canal de contato e obrigatorio");
            }
            if (evaluationDate == null) {
                throw new IllegalArgumentException("portal.accessibility_statement: data da avaliacao e obrigatoria");
            }
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("portal.accessibility_statement: texto da declaracao e obrigatorio");
            }
        }
    }

    private final Map<String, String> jsonByKey;

    private PortalParameters(Map<String, String> jsonByKey) { this.jsonByKey = jsonByKey; }

    public static PortalParameters fromValues(Map<String, String> jsonByKey) {
        if (jsonByKey == null) {
            throw new IllegalArgumentException("Mapa de parametros e obrigatorio");
        }
        return new PortalParameters(Map.copyOf(jsonByKey));
    }

    public GuestPaymentMode guestPaymentMode() {
        return parseEnum(GUEST_PAYMENT, GuestPaymentMode.class);
    }

    public boolean idpGovbrEnabled() { return requiredBoolean(IDP_GOVBR_ENABLED); }

    public boolean staticFallbackEnabled() { return requiredBoolean(STATIC_FALLBACK_ENABLED); }

    public boolean vlibrasEnabled() { return requiredBoolean(VLIBRAS_ENABLED); }

    public BotDefenseMode botDefense() { return parseEnum(BOT_DEFENSE, BotDefenseMode.class); }

    /** Nivel minimo por operacao; operacao sensivel sem entrada usa a fabrica (prata). */
    public AssuranceLevel minAssurance(SensitiveOperation operation) {
        if (operation == null) {
            throw new IllegalArgumentException("operacao e obrigatoria");
        }
        JsonNode node = required(MIN_ASSURANCE_BY_OPERATION);
        if (!node.isObject()) {
            throw new IllegalStateException(MIN_ASSURANCE_BY_OPERATION + ": esperado objeto, veio " + node.getNodeType());
        }
        JsonNode value = node.get(operation.name());
        AssuranceLevel level;
        if (value == null) {
            level = operation.factoryRequiresSilver() ? AssuranceLevel.SILVER : AssuranceLevel.BRONZE;
        } else if (!value.isTextual()) {
            throw new IllegalStateException(MIN_ASSURANCE_BY_OPERATION + ": nivel de " + operation + " deve ser texto");
        } else {
            try {
                level = AssuranceLevel.valueOf(value.textValue());
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(MIN_ASSURANCE_BY_OPERATION + ": nivel desconhecido para "
                        + operation + ": " + value.textValue(), e);
            }
        }
        // Piso do aceite (revisao cruzada da P3.3): nas 4 operacoes sensiveis o
        // BFF exige prata ou ouro quando a entrada e gov.br; a configuracao do
        // tenant (classe b) pode ELEVAR o minimo (ouro), nunca rebaixa-lo a bronze.
        if (operation.factoryRequiresSilver() && !level.atLeast(AssuranceLevel.SILVER)) {
            return AssuranceLevel.SILVER;
        }
        return level;
    }

    public Map<SensitiveOperation, AssuranceLevel> minAssuranceByOperation() {
        Map<SensitiveOperation, AssuranceLevel> out = new EnumMap<>(SensitiveOperation.class);
        for (SensitiveOperation op : SensitiveOperation.values()) {
            out.put(op, minAssurance(op));
        }
        return Map.copyOf(out);
    }

    /** Limites vigentes, conferidos contra a fabrica (classe c, so reduz). */
    public AccountThrottle accountThrottle(AccountThrottle factory) {
        JsonNode node = required(ACCOUNT_THROTTLE);
        if (!node.isObject()) {
            throw new IllegalStateException(ACCOUNT_THROTTLE + ": esperado objeto, veio " + node.getNodeType());
        }
        AccountThrottle value = new AccountThrottle(
                requiredInt(node, "maxAccountsPerEmail"), requiredInt(node, "maxLoginFailuresPerIdentifier"),
                requiredInt(node, "maxLoginFailuresPerIp"), requiredInt(node, "maxGuestQueriesPerOrigin"),
                requiredInt(node, "maxGuestQueriesPerCode"), requiredInt(node, "windowMinutes"));
        value.validateOnlyReduces(factory);
        return value;
    }

    public AccessibilityStatement accessibilityStatement() {
        JsonNode node = required(ACCESSIBILITY_STATEMENT);
        if (!node.isObject()) {
            throw new IllegalStateException(ACCESSIBILITY_STATEMENT + ": esperado objeto, veio " + node.getNodeType());
        }
        JsonNode contact = node.get("contactChannel");
        JsonNode date = node.get("evaluationDate");
        JsonNode text = node.get("text");
        if (contact == null || !contact.isTextual() || date == null || !date.isTextual()
                || text == null || !text.isTextual()) {
            throw new IllegalStateException(ACCESSIBILITY_STATEMENT
                    + ": exige contactChannel, evaluationDate (ISO) e text textuais");
        }
        return new AccessibilityStatement(contact.textValue(), LocalDate.parse(date.textValue()), text.textValue());
    }

    private <E extends Enum<E>> E parseEnum(String key, Class<E> type) {
        JsonNode node = required(key);
        if (!node.isTextual()) {
            throw new IllegalStateException(key + ": esperado texto, veio " + node.getNodeType());
        }
        try {
            return Enum.valueOf(type, node.textValue());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(key + ": valor desconhecido: " + node.textValue(), e);
        }
    }

    private boolean requiredBoolean(String key) {
        JsonNode node = required(key);
        if (!node.isBoolean()) {
            throw new IllegalStateException(key + ": esperado booleano, veio " + node.getNodeType());
        }
        return node.booleanValue();
    }

    private static int requiredInt(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || !value.isIntegralNumber()) {
            throw new IllegalStateException(ACCOUNT_THROTTLE + ": campo inteiro obrigatorio ausente: " + field);
        }
        return value.intValue();
    }

    private JsonNode required(String key) {
        String json = jsonByKey.get(key);
        if (json == null || json.isBlank()) {
            throw new MissingParameterException(key, "nenhum valor entregue ao portal");
        }
        JsonNode node = Json.readTree(json);
        if (node.isMissingNode() || node.isNull()) {
            throw new MissingParameterException(key, "valor JSON e nulo");
        }
        return node;
    }
}

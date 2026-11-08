package br.com.ziipsan.commercial.billing.cobill;

import java.util.Map;

/**
 * Leitura tipada dos parametros do tenant da ED-25 (cofaturamento de
 * residuos solidos, NR 13, arts. 46 a 56), no desenho de
 * {@code CutoffParameters} da P3.4: o chamador resolve os valores vigentes
 * e aceitos e entrega os valores escalares; esta classe so interpreta e
 * valida.
 *
 * <p>Principio do catalogo: parametro ausente e erro explicito
 * ({@link MissingCobillParameterException}), nunca valor assumido em
 * silencio - EXCETO onde o proprio acceptance fixa a semantica:
 * {@code cut.third_party_debt_blocks} e lido pelo seu nome: a
 * inadimplencia do servico cofaturado NAO tem efeito de corte de agua;
 * o parametro existe para o tenant poder ligar o efeito, e a sua
 * ausencia, numa avaliacao de corte, e erro explicito (decisao de corte
 * nunca e tomada sobre parametro presumido - precedente P3.4).
 *
 * <p>Chaves (nomes PROPOSTOS para o catalogo - o acceptance nomeia o
 * prefixo {@code cobill.*} e a chave {@code cut.third_party_debt_blocks},
 * sem enumerar as demais; ver RELATORIO-ED-25):
 * <pre>
 *   cobill.enabled                 BOOLEAN  liga o cofaturamento no tenant
 *   cobill.notice_min_days         INT      antecedencia minima do aviso;
 *                                           piso travado de 60 dias (o
 *                                           acceptance exige "pelo menos
 *                                           60 dias"; o tenant so amplia)
 *   cobill.billing_mode            ENUM     COBILLED | SEPARATE_INVOICE |
 *                                           BOOKLET_MONTHLY | BOOKLET_ANNUAL
 *   cobill.payment_allocation      ENUM     OWN_FIRST | PRO_RATA (ordem de
 *                                           imputacao de pagamento parcial
 *                                           entre valor proprio e de terceiro)
 *   cut.third_party_debt_blocks    BOOLEAN  divida so de terceiro autoriza
 *                                           corte de agua? (nome do acceptance)
 * </pre>
 */
public final class CobillParameters {

    public static final String ENABLED = "cobill.enabled";
    public static final String NOTICE_MIN_DAYS = "cobill.notice_min_days";
    public static final String BILLING_MODE = "cobill.billing_mode";
    public static final String PAYMENT_ALLOCATION = "cobill.payment_allocation";
    public static final String THIRD_PARTY_DEBT_BLOCKS = "cut.third_party_debt_blocks";

    /** Piso travado da antecedencia do aviso (acceptance: "pelo menos 60 dias"). */
    public static final int NOTICE_MIN_DAYS_FLOOR = 60;

    /** Como o valor cofaturado e cobrado (acceptance: conta, fatura desvinculada, carne). */
    public enum BillingMode {
        /** Na propria conta de agua/esgoto, com valor discriminado. */
        COBILLED,
        /** Fatura desvinculada: documento proprio, fora do total da conta. */
        SEPARATE_INVOICE,
        /** Carne mensal: um documento por competencia. */
        BOOKLET_MONTHLY,
        /** Carne anual: o ano em parcelas mensais num unico carne. */
        BOOKLET_ANNUAL
    }

    /** Ordem de imputacao de um pagamento parcial (segregacao do repasse). */
    public enum PaymentAllocation {
        /** Quita primeiro o servico proprio; o residuo vai ao cofaturado. */
        OWN_FIRST,
        /** Rateia proporcionalmente entre proprio e cofaturado. */
        PRO_RATA
    }

    /** Parametro exigido e ausente: erro explicito, nunca padrao presumido. */
    public static final class MissingCobillParameterException extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        public MissingCobillParameterException(String key) {
            super("Parametro '" + key + "' sem valor aceito entregue ao motor da ED-25");
        }
    }

    private final Map<String, String> values;

    private CobillParameters(Map<String, String> values) {
        this.values = values;
    }

    public static CobillParameters fromValues(Map<String, String> values) {
        if (values == null) {
            throw new IllegalArgumentException("Mapa de parametros e obrigatorio");
        }
        return new CobillParameters(Map.copyOf(values));
    }

    /** {@code cobill.enabled}: cofaturamento ligado no tenant? */
    public boolean enabled() {
        return parseBoolean(ENABLED, require(ENABLED));
    }

    /**
     * {@code cobill.notice_min_days}: antecedencia minima do aviso ao
     * usuario. Abaixo do piso de 60 dias e RECUSADO na leitura (o tenant
     * so amplia) - a recusa e do parametro, nao um ajuste silencioso.
     */
    public int noticeMinDays() {
        int value = parseInt(NOTICE_MIN_DAYS, require(NOTICE_MIN_DAYS));
        if (value < NOTICE_MIN_DAYS_FLOOR) {
            throw invalid(NOTICE_MIN_DAYS,
                    "abaixo do piso de 60 dias do acceptance da ED-25 (NR 13): " + value);
        }
        return value;
    }

    /** {@code cobill.billing_mode}: forma de cobranca do valor cofaturado. */
    public BillingMode billingMode() {
        return parseEnum(BILLING_MODE, BillingMode.class, require(BILLING_MODE));
    }

    /** {@code cobill.payment_allocation}: imputacao de pagamento parcial. */
    public PaymentAllocation paymentAllocation() {
        return parseEnum(PAYMENT_ALLOCATION, PaymentAllocation.class, require(PAYMENT_ALLOCATION));
    }

    /**
     * {@code cut.third_party_debt_blocks}: a divida do servico cofaturado
     * (terceiro) conta para autorizar corte de agua? Ausente = erro
     * explicito: nenhuma decisao de corte sai sobre parametro presumido.
     */
    public boolean thirdPartyDebtBlocks() {
        return parseBoolean(THIRD_PARTY_DEBT_BLOCKS, require(THIRD_PARTY_DEBT_BLOCKS));
    }

    private String require(String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new MissingCobillParameterException(key);
        }
        return value.trim();
    }

    private static IllegalArgumentException invalid(String key, String detail) {
        return new IllegalArgumentException("Parametro '" + key + "' invalido: " + detail);
    }

    private static boolean parseBoolean(String key, String raw) {
        if ("true".equals(raw)) return true;
        if ("false".equals(raw)) return false;
        throw invalid(key, "esperado booleano (true/false): " + raw);
    }

    private static int parseInt(String key, String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw invalid(key, "esperado inteiro: " + raw);
        }
    }

    private static <E extends Enum<E>> E parseEnum(String key, Class<E> type, String raw) {
        try {
            return Enum.valueOf(type, raw);
        } catch (IllegalArgumentException e) {
            throw invalid(key, "valor fora do catalogo: " + raw);
        }
    }
}

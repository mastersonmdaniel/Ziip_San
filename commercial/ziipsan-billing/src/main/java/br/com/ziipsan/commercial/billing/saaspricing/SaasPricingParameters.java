package br.com.ziipsan.commercial.billing.saaspricing;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Leitura tipada dos parametros da politica comercial da ED-10, no mesmo
 * desenho de {@code CobillParameters} da ED-25: o chamador resolve os
 * valores vigentes e aceitos e entrega os escalares; esta classe so
 * interpreta e valida.
 *
 * <p>REGRA DURA (DECISOES-DO-DONO / cascata): preco, margem, desconto de
 * reversao, franquias e custos unitarios que NENHUMA fonte fixa nascem
 * como parametro OBRIGATORIO SEM valor de fabrica. Ausencia = erro
 * explicito nomeado ({@link MissingSaasPricingParameterException} com o
 * marcador {@code [A DEFINIR]}), nunca valor presumido.
 *
 * <p>Chaves (prefixo {@code saas.*} PROPOSTO - o acceptance da ED-10 nao
 * nomeia chaves; ver RELATORIO-ED-10):
 * <pre>
 *   saas.margin_percent              DECIMAL  margem sobre o custo (%)
 *   saas.reversion_discount_percent  DECIMAL  desconto da reversao (ADR-0024)
 *   saas.pass_through_mode           ENUM     FRANCHISE | FULL_PASS_THROUGH
 * </pre>
 * Os dois valores que o acceptance FIXA nao sao parametros: garantia de
 * 12 meses e correcao de vicios em 48 h vivem como constantes em
 * {@link WarrantyRule}.
 */
public final class SaasPricingParameters {

    public static final String MARGIN_PERCENT = "saas.margin_percent";
    public static final String REVERSION_DISCOUNT_PERCENT = "saas.reversion_discount_percent";
    public static final String PASS_THROUGH_MODE = "saas.pass_through_mode";

    /** Como mensagens e modelo de linguagem sao repassados ao tenant. */
    public enum PassThroughMode {
        /** Ate a franquia contratada, sem cobranca; so o excedente medido. */
        FRANCHISE,
        /** Todo o uso medido e repassado pelo custo unitario vigente. */
        FULL_PASS_THROUGH
    }

    /** Parametro exigido e ausente: [A DEFINIR], nunca padrao presumido. */
    public static final class MissingSaasPricingParameterException extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        public MissingSaasPricingParameterException(String key) {
            super("Parametro '" + key + "' [A DEFINIR]: sem valor aceito entregue a politica comercial da ED-10");
        }
    }

    private final Map<String, String> values;

    private SaasPricingParameters(Map<String, String> values) { this.values = values; }

    public static SaasPricingParameters fromValues(Map<String, String> values) {
        if (values == null) throw new IllegalArgumentException("Mapa de parametros e obrigatorio");
        return new SaasPricingParameters(Map.copyOf(values));
    }

    public BigDecimal marginPercent() { return parseDecimal(MARGIN_PERCENT, require(MARGIN_PERCENT)); }
    public BigDecimal reversionDiscountPercent() { return parseDecimal(REVERSION_DISCOUNT_PERCENT, require(REVERSION_DISCOUNT_PERCENT)); }
    public PassThroughMode passThroughMode() {
        String raw = require(PASS_THROUGH_MODE);
        try { return PassThroughMode.valueOf(raw); }
        catch (IllegalArgumentException e) { throw invalid(PASS_THROUGH_MODE, "valor fora do catalogo: " + raw); }
    }

    private String require(String key) {
        String v = values.get(key);
        if (v == null || v.isBlank()) throw new MissingSaasPricingParameterException(key);
        return v.trim();
    }
    private static IllegalArgumentException invalid(String key, String detail) {
        return new IllegalArgumentException("Parametro '" + key + "' invalido: " + detail);
    }
    private static BigDecimal parseDecimal(String key, String raw) {
        try {
            BigDecimal v = new BigDecimal(raw);
            if (v.signum() < 0) throw invalid(key, "nao pode ser negativo: " + raw);
            return v;
        } catch (NumberFormatException e) { throw invalid(key, "esperado decimal: " + raw); }
    }
}

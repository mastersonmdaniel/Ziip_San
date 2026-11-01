package br.com.ziipsan.commercial.collection.surcharge;

import java.math.BigDecimal;

/**
 * Multa moratória: percentual único, cobrado uma vez sobre o principal, sem capitalização.
 *
 * <p>O percentual é obrigatório e vem do tenant (contrato/regulador); não há valor padrão. A
 * trava de {@link LegalLimits#MAX_FINE_PERCENT} (2%) vale se não houver {@code capOverride};
 * elevar o teto exige declarar a norma que o permite (texto livre, auditável).
 *
 * @param ratePercent              percentual da multa (ex.: {@code 2.00} é 2%)
 * @param capOverridePercent       teto próprio do tenant, ou {@code null} para usar a trava legal
 * @param capOverrideJustification norma/decisão que sustenta o teto próprio; obrigatória se o teto
 *                                 próprio for maior que a trava legal
 */
public record FineRule(BigDecimal ratePercent, BigDecimal capOverridePercent, String capOverrideJustification) {

    public FineRule {
        if (ratePercent == null || ratePercent.signum() < 0) {
            throw new IllegalArgumentException("O percentual de multa é obrigatório e não pode ser negativo");
        }
        if (capOverridePercent != null) {
            if (capOverridePercent.signum() < 0) {
                throw new IllegalArgumentException("Teto de multa negativo");
            }
            if (capOverridePercent.compareTo(LegalLimits.MAX_FINE_PERCENT) > 0
                    && (capOverrideJustification == null || capOverrideJustification.isBlank())) {
                throw new IllegalArgumentException("Teto de multa acima de " + LegalLimits.MAX_FINE_PERCENT
                        + "% exige a norma que o permite");
            }
        }
        BigDecimal cap = capOverridePercent != null ? capOverridePercent : LegalLimits.MAX_FINE_PERCENT;
        if (ratePercent.compareTo(cap) > 0) {
            throw new IllegalArgumentException("Multa de " + ratePercent.toPlainString()
                    + "% acima do teto de " + cap.toPlainString() + "%");
        }
    }

    /** Multa sob a trava legal de 2%. */
    public static FineRule of(BigDecimal ratePercent) {
        return new FineRule(ratePercent, null, null);
    }

    /** Multa sem cobrança (percentual zero), declarada explicitamente. */
    public static FineRule none() {
        return new FineRule(BigDecimal.ZERO, null, null);
    }

    /** Multa com teto próprio do tenant, justificado. */
    public static FineRule withCapOverride(BigDecimal ratePercent, BigDecimal capPercent, String justification) {
        return new FineRule(ratePercent, capPercent, justification);
    }
}

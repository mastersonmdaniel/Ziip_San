package br.com.ziipsan.platform.notification.multichannel;

import java.math.BigDecimal;

/**
 * Regra pura do teto de gasto mensal por tenant ({@code msg.monthly_cap},
 * ADR-0019), calculável à mão e testada sem banco:
 *
 * <ul>
 *   <li>o nível é apurado sobre o gasto PROJETADO do mês (gasto até aqui +
 *       estimativa deste envio): abaixo de 80% do teto = {@code WITHIN};
 *       de 80% até abaixo do teto = {@code ALERT_80}; no teto ou acima =
 *       {@code REACHED_100} — os alertas de 80% e 100% do aceite/ADR-0019;</li>
 *   <li><b>teto é teto</b>: envio cuja projeção ULTRAPASSA o teto é
 *       bloqueado; envio que pousa EXATAMENTE no teto é admitido (atinge
 *       100%, alerta, e o próximo é bloqueado);</li>
 *   <li>{@code alert80Raised}/{@code alert100Raised} marcam a TRAVESSIA
 *       neste envio (o nível anterior, só com o gasto prévio, era menor):
 *       o serviço emite o alerta sobre essa travessia SOMENTE depois que o
 *       envio é aceito e a tentativa gravada — gasto efetivamente
 *       comprometido (correção D3 da revisão; a regra em si segue pura e
 *       projetiva: quem decide QUANDO alertar é o serviço). Repetir o
 *       alerta a cada envio seguinte seria ruído, e a persistência do
 *       estado do alerta é da integração (ver relatório).</li>
 * </ul>
 *
 * Calculado à mão: teto 1.000,00; gasto 750,00 + estimativa 50,00 =
 * 800,00 = 80% → {@code ALERT_80} com {@code alert80Raised}. Gasto 950,00 +
 * 50,00 = 1.000,00 → {@code REACHED_100}, não bloqueado. Gasto 1.000,00 +
 * 0,01 → bloqueado.
 */
public final class MonthlyCapRule {

    private static final BigDecimal EIGHTY_PCT = new BigDecimal("0.80");

    public enum CapLevel { WITHIN, ALERT_80, REACHED_100 }

    public record CapDecision(CapLevel level, BigDecimal spentBefore, BigDecimal estimatedCost,
                              BigDecimal projectedSpend, boolean blocked,
                              boolean alert80Raised, boolean alert100Raised) {
    }

    private MonthlyCapRule() {
    }

    public static CapLevel levelOf(BigDecimal cap, BigDecimal spend) {
        requireCap(cap);
        if (spend == null || spend.signum() < 0) {
            throw new IllegalArgumentException("o gasto apurado é obrigatório e não pode ser negativo");
        }
        if (spend.compareTo(cap) >= 0) {
            return CapLevel.REACHED_100;
        }
        if (spend.compareTo(cap.multiply(EIGHTY_PCT)) >= 0) {
            return CapLevel.ALERT_80;
        }
        return CapLevel.WITHIN;
    }

    public static CapDecision evaluate(BigDecimal cap, BigDecimal spentBefore, BigDecimal estimatedCost) {
        requireCap(cap);
        if (spentBefore == null || spentBefore.signum() < 0) {
            throw new IllegalArgumentException("o gasto do mês até aqui é obrigatório e não pode ser negativo");
        }
        if (estimatedCost == null || estimatedCost.signum() < 0) {
            throw new IllegalArgumentException("a estimativa do envio é obrigatória e não pode ser negativa");
        }
        BigDecimal projected = spentBefore.add(estimatedCost);
        CapLevel before = levelOf(cap, spentBefore);
        CapLevel after = levelOf(cap, projected);
        boolean blocked = projected.compareTo(cap) > 0;
        boolean alert80 = before.ordinal() < CapLevel.ALERT_80.ordinal()
                && after.ordinal() >= CapLevel.ALERT_80.ordinal();
        boolean alert100 = before.ordinal() < CapLevel.REACHED_100.ordinal()
                && after == CapLevel.REACHED_100;
        return new CapDecision(after, spentBefore, estimatedCost, projected, blocked, alert80, alert100);
    }

    private static void requireCap(BigDecimal cap) {
        if (cap == null || cap.signum() <= 0) {
            throw new IllegalArgumentException("msg.monthly_cap é obrigatório e precisa ser positivo");
        }
    }
}

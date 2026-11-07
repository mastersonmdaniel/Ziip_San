package br.com.ziipsan.commercial.collection.refund;

import br.com.ziipsan.commercial.collection.parameters.RefundParameters.DoubleRefundSetting;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Calculo do valor da devolucao (P3.29), puro e calculado a mao nos testes.
 *
 * <ul>
 *   <li>duplicidade: devolve a base, sem dobro;</li>
 *   <li>cobranca indevida com o dobro LIGADO
 *       ({@code bill.undue_charge_double_refund}): devolve 2 x base;</li>
 *   <li>cobranca indevida com o dobro ligado mas AFASTADO pelo operador:
 *       devolve a base, e o afastamento EXIGE justificativa escrita
 *       ({@code doubleWaiverJustification}); sem ela, o dobro e aplicado -
 *       nao existe caminho para devolver simples em silencio;</li>
 *   <li>cobranca indevida com o dobro DESLIGADO pelo tenant (so com norma
 *       citada, validado em {@code RefundParameters}): devolve a base.</li>
 * </ul>
 */
public final class RefundAmountCalculator {

    /** @param doubleApplied verdadeiro quando o valor final e 2 x base */
    public record RefundAmount(BigDecimal baseAmount, BigDecimal refundAmount, boolean doubleApplied) {
    }

    private RefundAmountCalculator() {
    }

    public static RefundAmount calculate(BigDecimal baseAmount, RefundType type,
                                         DoubleRefundSetting doubleSetting,
                                         String doubleWaiverJustification) {
        Objects.requireNonNull(baseAmount, "base da devolucao e obrigatoria");
        Objects.requireNonNull(type, "tipo da devolucao e obrigatorio");
        Objects.requireNonNull(doubleSetting, "bill.undue_charge_double_refund e obrigatorio");
        if (baseAmount.signum() <= 0) {
            throw new IllegalArgumentException("base da devolucao deve ser positiva: " + baseAmount);
        }
        boolean waiverPresent = doubleWaiverJustification != null && !doubleWaiverJustification.isBlank();
        if (type == RefundType.DUPLICATE_PAYMENT) {
            if (waiverPresent) {
                throw new IllegalArgumentException(
                        "justificativa de afastamento do dobro nao se aplica a duplicidade");
            }
            return new RefundAmount(baseAmount, baseAmount, false);
        }
        // Cobranca indevida.
        if (!doubleSetting.enabled()) {
            if (waiverPresent) {
                throw new IllegalArgumentException("dobro desligado pelo parametro "
                        + "(norma: " + doubleSetting.normCitation()
                        + "): afastamento por justificativa nao se aplica");
            }
            return new RefundAmount(baseAmount, baseAmount, false);
        }
        if (waiverPresent) {
            return new RefundAmount(baseAmount, baseAmount, false);
        }
        return new RefundAmount(baseAmount, baseAmount.multiply(new BigDecimal("2")), true);
    }

    /**
     * A devolucao nasceu com o dobro devido e foi encerrada SEM ele?
     * Entao a justificativa e obrigatoria - guarda LIGADA na abertura
     * ({@code RefundRequestRule.open}, quando o operador pede o afastamento)
     * e no encerramento ({@code RefundClosureService.close}). A V6_5 NAO tem
     * CHECK equivalente de proposito: o banco nao conhece o estado de
     * {@code bill.undue_charge_double_refund} (ver comentario na migration).
     */
    public static void requireWaiverWhenDoubleOmitted(RefundType type, DoubleRefundSetting doubleSetting,
                                                      boolean doubleApplied,
                                                      String doubleWaiverJustification) {
        if (type == RefundType.UNDUE_CHARGE && doubleSetting.enabled() && !doubleApplied
                && (doubleWaiverJustification == null || doubleWaiverJustification.isBlank())) {
            throw new IllegalArgumentException("afastar o dobro da cobranca indevida exige "
                    + "justificativa obrigatoria (bill.undue_charge_double_refund ligado)");
        }
    }
}

package br.com.ziipsan.platform.portal.selfcare;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Explicacao DETERMINISTICA da variacao da conta (ED-38), gerada SOMENTE
 * do CALCULO GRAVADO da conta — nunca de dado recalculado ou estimado:
 * dias de leitura, consumo contra o mes anterior e contra a media, faixa
 * atingida, reajuste aplicado, servicos e encargos. Mesma conta gravada
 * => mesma explicacao, byte a byte (teste de reproducao dedicado).
 *
 * <p>Conciliacao: a soma dos componentes gravados (agua + esgoto +
 * servicos + encargos − creditos) tem de fechar com o total gravado;
 * divergencia recusa a explicacao (nada e "ajustado" em silencio).
 *
 * <p>A mesma funcao pura e a exposta ao assistente da ED-43
 * ({@link AssistantBillFunction}) — a ED-38 expoe a funcao; o assistente
 * em si e da ED-43 e NAO e construido aqui.
 */
public final class BillExplanation {

    /** Calculo gravado da conta (espelho dos valores persistidos no faturamento). */
    public record RecordedCalculation(int readingDays, int previousReadingDays,
                                      BigDecimal consumptionM3, BigDecimal previousConsumptionM3,
                                      BigDecimal averageConsumptionM3, String bandReached,
                                      BigDecimal adjustmentPercent,
                                      BigDecimal waterAmount, BigDecimal sewageAmount,
                                      BigDecimal servicesAmount, BigDecimal chargesAmount,
                                      BigDecimal creditsAmount, BigDecimal totalAmount) {
        public RecordedCalculation {
            if (readingDays < 1 || previousReadingDays < 1 || consumptionM3 == null
                    || previousConsumptionM3 == null || averageConsumptionM3 == null
                    || bandReached == null || bandReached.isBlank() || adjustmentPercent == null
                    || waterAmount == null || sewageAmount == null || servicesAmount == null
                    || chargesAmount == null || creditsAmount == null || totalAmount == null) {
                throw new IllegalArgumentException("calculo gravado incompleto: explicacao recusada");
            }
        }

        public BigDecimal componentsSum() {
            return waterAmount.add(sewageAmount).add(servicesAmount).add(chargesAmount).subtract(creditsAmount);
        }
    }

    public record Factor(String code, String text) { }

    public record Explanation(List<Factor> factors, String fullText) { }

    private BillExplanation() { }

    public static Explanation explain(RecordedCalculation calc) {
        if (calc == null) {
            throw new IllegalArgumentException("calculo gravado e obrigatorio");
        }
        if (calc.componentsSum().compareTo(calc.totalAmount()) != 0) {
            throw new IllegalStateException("componentes do calculo gravado nao fecham com o total gravado");
        }
        List<Factor> factors = new ArrayList<>();
        factors.add(new Factor("READING_DAYS",
                "Periodo de leitura de " + calc.readingDays() + " dias (mes anterior: "
                        + calc.previousReadingDays() + " dias)."));
        factors.add(new Factor("CONSUMPTION_VS_PREVIOUS",
                "Consumo de " + plain(calc.consumptionM3()) + " m3 contra " + plain(calc.previousConsumptionM3())
                        + " m3 no mes anterior (" + signedVariation(calc.consumptionM3(), calc.previousConsumptionM3()) + ")."));
        factors.add(new Factor("CONSUMPTION_VS_AVERAGE",
                "Consumo contra a media gravada de " + plain(calc.averageConsumptionM3()) + " m3 ("
                        + signedVariation(calc.consumptionM3(), calc.averageConsumptionM3()) + ")."));
        factors.add(new Factor("BAND", "Faixa tarifaria atingida: " + calc.bandReached() + "."));
        factors.add(new Factor("ADJUSTMENT",
                calc.adjustmentPercent().signum() == 0
                        ? "Sem reajuste tarifario aplicado nesta conta."
                        : "Reajuste tarifario aplicado de " + plain(calc.adjustmentPercent()) + "%."));
        factors.add(new Factor("COMPONENTS",
                "Composicao: agua R$ " + money(calc.waterAmount()) + ", esgoto R$ " + money(calc.sewageAmount())
                        + ", servicos R$ " + money(calc.servicesAmount()) + ", encargos R$ " + money(calc.chargesAmount())
                        + ", creditos R$ " + money(calc.creditsAmount()) + ". Total R$ " + money(calc.totalAmount()) + "."));
        StringBuilder text = new StringBuilder();
        for (Factor f : factors) {
            if (!text.isEmpty()) {
                text.append(' ');
            }
            text.append(f.text());
        }
        return new Explanation(List.copyOf(factors), text.toString());
    }

    private static String signedVariation(BigDecimal current, BigDecimal base) {
        if (base.signum() == 0) {
            return "sem base de comparacao gravada";
        }
        BigDecimal pct = current.subtract(base).multiply(new BigDecimal("100"))
                .divide(base, 1, java.math.RoundingMode.HALF_UP);
        return (pct.signum() >= 0 ? "+" : "") + plain(pct) + "%";
    }

    private static String plain(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private static String money(BigDecimal v) {
        return v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }
}

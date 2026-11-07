package br.com.ziipsan.integration.sinisa;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Balanco hidrico padrao (metodologia IWA, adotada como base do balanco do SINISA):
 * decomposicao do volume de entrada do sistema em consumo autorizado (faturado e
 * nao faturado) e perdas (aparentes e reais).
 *
 * <pre>
 *   volume de entrada = consumo faturado + consumo autorizado nao faturado
 *                     + perdas aparentes + perdas reais
 *   agua nao faturada (NRW) = entrada - consumo faturado
 *   perdas de agua          = entrada - consumo autorizado (faturado + nao faturado)
 *   perdas reais (CARL)     = perdas de agua - perdas aparentes
 * </pre>
 *
 * <p>Caso da v1 que o aceite da P4.2 manda passar: entrada de 10.000 m3 e consumo
 * faturado de 7.000 m3 dao agua nao faturada de exatamente 3.000 m3
 * (ver {@code WaterBalanceTest}).
 *
 * <p>Todos os volumes sao anuais, em m3, nao negativos; componente maior que o
 * total que o contem e recusado (o balanco nao fecha com dado inconsistente).
 * O balanco por DMC/setor e do modulo de perdas (ED-85); este e o balanco anual
 * do prestador que alimenta as variaveis tecnicas do SINISA e o ILI.
 */
public record WaterBalance(
        BigDecimal systemInputM3,
        BigDecimal billedM3,
        BigDecimal unbilledAuthorizedM3,
        BigDecimal apparentLossesM3,
        BigDecimal nonRevenueM3,
        BigDecimal waterLossesM3,
        BigDecimal realLossesM3) {

    public WaterBalance {
        Objects.requireNonNull(systemInputM3, "Volume de entrada e obrigatorio");
        Objects.requireNonNull(billedM3, "Volume faturado e obrigatorio");
        Objects.requireNonNull(unbilledAuthorizedM3, "Consumo autorizado nao faturado e obrigatorio");
        Objects.requireNonNull(apparentLossesM3, "Perdas aparentes sao obrigatorias");
        Objects.requireNonNull(nonRevenueM3, "Agua nao faturada e obrigatoria");
        Objects.requireNonNull(waterLossesM3, "Perdas de agua sao obrigatorias");
        Objects.requireNonNull(realLossesM3, "Perdas reais sao obrigatorias");
    }

    /**
     * Calcula o balanco a partir dos quatro componentes medidos/declarados.
     * Inconsistencia (soma dos componentes acima da entrada, faturado acima da
     * entrada, perdas aparentes acima das perdas de agua) e erro explicito.
     */
    public static WaterBalance calculate(BigDecimal systemInputM3, BigDecimal billedM3,
                                         BigDecimal unbilledAuthorizedM3, BigDecimal apparentLossesM3) {
        requireNonNegative("volume de entrada", systemInputM3);
        requireNonNegative("volume faturado", billedM3);
        requireNonNegative("consumo autorizado nao faturado", unbilledAuthorizedM3);
        requireNonNegative("perdas aparentes", apparentLossesM3);
        BigDecimal authorized = billedM3.add(unbilledAuthorizedM3);
        if (authorized.compareTo(systemInputM3) > 0) {
            throw new IllegalArgumentException("Balanco hidrico inconsistente: consumo autorizado ("
                    + authorized + " m3) maior que o volume de entrada (" + systemInputM3 + " m3)");
        }
        BigDecimal nonRevenue = systemInputM3.subtract(billedM3);
        BigDecimal waterLosses = systemInputM3.subtract(authorized);
        if (apparentLossesM3.compareTo(waterLosses) > 0) {
            throw new IllegalArgumentException("Balanco hidrico inconsistente: perdas aparentes ("
                    + apparentLossesM3 + " m3) maiores que as perdas de agua (" + waterLosses + " m3)");
        }
        BigDecimal realLosses = waterLosses.subtract(apparentLossesM3);
        return new WaterBalance(systemInputM3, billedM3, unbilledAuthorizedM3, apparentLossesM3,
                nonRevenue, waterLosses, realLosses);
    }

    private static void requireNonNegative(String field, BigDecimal value) {
        Objects.requireNonNull(value, "Balanco hidrico: " + field + " e obrigatorio");
        if (value.signum() < 0) {
            throw new IllegalArgumentException("Balanco hidrico: " + field + " nao pode ser negativo: " + value);
        }
    }

    /** Perdas reais correntes anuais (CARL), entrada do calculo do ILI. */
    public BigDecimal carlM3() {
        return realLossesM3;
    }
}

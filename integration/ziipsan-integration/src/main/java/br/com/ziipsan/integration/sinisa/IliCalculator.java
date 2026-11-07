package br.com.ziipsan.integration.sinisa;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * ILI (Infrastructure Leakage Index), indicador de perdas reais da metodologia
 * IWA usado no balanco do SINISA: razao entre as perdas reais correntes (CARL) e
 * as perdas reais inevitaveis (UARL) do sistema, na pressao media de operacao.
 *
 * <pre>
 *   UARL (litros/dia) = (18 x Lm + 0,8 x Nc + 25 x Lp) x P
 *     Lm = extensao da rede de distribuicao (km)
 *     Nc = numero de ligacoes (ramais) de agua
 *     Lp = extensao total dos ramais, da rede ao hidrometro (km)
 *     P  = pressao media de operacao (metros de coluna de agua)
 *   ILI = CARL / UARL   (adimensional; 1,0 = perdas reais no minimo inevitavel)
 * </pre>
 *
 * <p>Formula de Lambert/IWA Water Loss Task Force. Sem os quatro insumos da
 * UARL ou sem CARL nao ha ILI: insumo ausente, negativo ou UARL nula e erro
 * explicito, nunca ILI presumido (caso da v1 no aceite da P4.2: ver
 * {@code IliCalculatorTest}, com os valores conferidos a mao).
 */
public final class IliCalculator {

    private static final BigDecimal COEF_REDE = new BigDecimal("18");
    private static final BigDecimal COEF_LIGACAO = new BigDecimal("0.8");
    private static final BigDecimal COEF_RAMAL = new BigDecimal("25");
    private static final BigDecimal DIAS_NO_ANO = new BigDecimal("365");
    private static final BigDecimal LITROS_POR_M3 = new BigDecimal("1000");
    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);

    private IliCalculator() {
    }

    /** UARL em litros/dia para os insumos da rede. */
    public static BigDecimal unavoidableAnnualRealLossesLitresPerDay(
            BigDecimal mainsLengthKm, long serviceConnections,
            BigDecimal servicePipeLengthKm, BigDecimal averagePressureM) {
        Objects.requireNonNull(mainsLengthKm, "Extensao da rede (Lm) e obrigatoria para a UARL");
        Objects.requireNonNull(servicePipeLengthKm, "Extensao dos ramais (Lp) e obrigatoria para a UARL");
        Objects.requireNonNull(averagePressureM, "Pressao media (P) e obrigatoria para a UARL");
        if (mainsLengthKm.signum() < 0 || servicePipeLengthKm.signum() < 0
                || averagePressureM.signum() < 0 || serviceConnections < 0) {
            throw new IllegalArgumentException("UARL: insumos nao podem ser negativos (Lm=" + mainsLengthKm
                    + ", Nc=" + serviceConnections + ", Lp=" + servicePipeLengthKm + ", P=" + averagePressureM + ")");
        }
        BigDecimal base = COEF_REDE.multiply(mainsLengthKm)
                .add(COEF_LIGACAO.multiply(BigDecimal.valueOf(serviceConnections)))
                .add(COEF_RAMAL.multiply(servicePipeLengthKm));
        return base.multiply(averagePressureM);
    }

    /**
     * ILI a partir do CARL anual (m3/ano, do {@link WaterBalance}) e dos insumos
     * da UARL. CARL e convertido para litros/dia (x 1000 / 365) antes da razao.
     */
    public static BigDecimal infrastructureLeakageIndex(
            BigDecimal carlM3PerYear, BigDecimal mainsLengthKm, long serviceConnections,
            BigDecimal servicePipeLengthKm, BigDecimal averagePressureM) {
        Objects.requireNonNull(carlM3PerYear, "CARL e obrigatorio para o ILI");
        if (carlM3PerYear.signum() < 0) {
            throw new IllegalArgumentException("ILI: CARL nao pode ser negativo: " + carlM3PerYear);
        }
        BigDecimal uarl = unavoidableAnnualRealLossesLitresPerDay(
                mainsLengthKm, serviceConnections, servicePipeLengthKm, averagePressureM);
        if (uarl.signum() <= 0) {
            throw new IllegalArgumentException("ILI indefinido: UARL nula para os insumos dados "
                    + "(rede, ligacoes, ramais e pressao nao podem ser todos zero)");
        }
        BigDecimal carlLitresPerDay = carlM3PerYear.multiply(LITROS_POR_M3).divide(DIAS_NO_ANO, MC);
        return carlLitresPerDay.divide(uarl, MC);
    }
}

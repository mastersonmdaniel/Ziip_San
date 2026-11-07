package br.com.ziipsan.operations.metering.hydrometry;

import br.com.ziipsan.operations.metering.hydrometry.HydrometryEnums.SizingVerdict;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Sub/superdimensionamento (ED-14, correção A6 da revisão): compara o perfil de consumo
 * da ligação com a faixa do modelo (Q1..Q3), com a semântica correta:
 * <ul>
 *   <li>perfil ACIMA de Q3 → o medidor é pequeno para a demanda: <b>UNDERSIZED</b>
 *       (subdimensionado — desgaste e erro por sobrecarga);</li>
 *   <li>perfil persistentemente ABAIXO de Q1 → o medidor é grande demais para o
 *       consumo: <b>OVERSIZED</b> (superdimensionado — sub-registro em baixa vazão,
 *       perda aparente);</li>
 *   <li>fora desses casos: ADEQUATE.</li>
 * </ul>
 * Os limiares (participação mínima do perfil em cada faixa) são PARÂMETROS do chamador:
 * sem limiar parametrizado NÃO há veredito — erro explícito, nunca número embutido
 * (a versão anterior trazia 0,5 fixo e os vereditos invertidos; corrigido).
 */
public final class SizingRule {
    private SizingRule() {}

    /** Limiares parametrizados da análise (tenant/análise): participações 0..1 do perfil. */
    public record SizingThresholds(BigDecimal minShareAboveQ3, BigDecimal minShareBelowQ1) {
        public SizingThresholds {
            if (minShareAboveQ3 == null || minShareBelowQ1 == null)
                throw new IllegalArgumentException("Sem limiar parametrizado de dimensionamento não há veredito (ED-14)");
            if (minShareAboveQ3.signum() < 0 || minShareAboveQ3.compareTo(BigDecimal.ONE) > 0
                    || minShareBelowQ1.signum() < 0 || minShareBelowQ1.compareTo(BigDecimal.ONE) > 0)
                throw new IllegalArgumentException("Limiares de dimensionamento devem estar entre 0 e 1");
        }
    }

    /**
     * @param shareAboveQ3 participação do perfil com vazão acima de Q3 (0..1, da analítica da ligação)
     * @param shareBelowQ1 participação do perfil com vazão abaixo de Q1 (0..1, da analítica da ligação)
     */
    public static SizingVerdict evaluate(BigDecimal shareAboveQ3, BigDecimal shareBelowQ1, SizingThresholds thresholds) {
        if (thresholds == null) throw new IllegalArgumentException("Sem limiar parametrizado de dimensionamento não há veredito (ED-14)");
        if (shareAboveQ3 == null || shareBelowQ1 == null)
            throw new IllegalArgumentException("Participações do perfil acima de Q3 e abaixo de Q1 são obrigatórias");
        if (shareAboveQ3.compareTo(thresholds.minShareAboveQ3()) >= 0) return SizingVerdict.UNDERSIZED;
        if (shareBelowQ1.compareTo(thresholds.minShareBelowQ1()) >= 0) return SizingVerdict.OVERSIZED;
        return SizingVerdict.ADEQUATE;
    }

    /** Utilização média (perfil/Q3), só para relatório da análise — não é limiar nem veredito. */
    public static BigDecimal utilization(BigDecimal profileAverageFlow, FlowRates rates) {
        if (profileAverageFlow == null || rates == null) throw new IllegalArgumentException("Perfil e vazões são obrigatórios");
        return profileAverageFlow.divide(rates.q3(), 4, RoundingMode.HALF_UP);
    }
}

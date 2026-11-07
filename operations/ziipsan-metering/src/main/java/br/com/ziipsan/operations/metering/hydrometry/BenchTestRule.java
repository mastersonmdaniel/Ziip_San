package br.com.ziipsan.operations.metering.hydrometry;

import br.com.ziipsan.operations.metering.hydrometry.HydrometryEnums.BenchFlowPoint;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * Ensaio em bancada (ED-14): erro percentual por vazão (Q1..Q4), curva de erro e
 * aprovação do lote. O erro máximo admissível por ponto é ENTRADA (do RTM do modelo/
 * laboratório) — esta regra calcula e compara, não fixa tolerância da portaria.
 */
public final class BenchTestRule {
    private BenchTestRule() {}

    public record FlowResult(BenchFlowPoint flow, BigDecimal referenceVolume, BigDecimal indicatedVolume) {
        public BigDecimal errorPct() {
            if (referenceVolume == null || referenceVolume.signum() <= 0)
                throw new IllegalArgumentException("Volume de referência do ensaio é obrigatório e positivo");
            if (indicatedVolume == null) throw new IllegalArgumentException("Volume indicado é obrigatório");
            return indicatedVolume.subtract(referenceVolume)
                    .divide(referenceVolume, 6, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
        }
    }

    /** Curva de erro: erro % por ponto de vazão, na ordem Q1..Q4. */
    public static Map<BenchFlowPoint, BigDecimal> errorCurve(List<FlowResult> results) {
        java.util.EnumMap<BenchFlowPoint, BigDecimal> curve = new java.util.EnumMap<>(BenchFlowPoint.class);
        for (FlowResult r : results) curve.put(r.flow(), r.errorPct());
        return curve;
    }

    /** Um hidrômetro é aprovado quando TODO ponto está dentro do erro máximo daquele ponto. */
    public static boolean meterApproved(List<FlowResult> results, Map<BenchFlowPoint, BigDecimal> maxErrorPctByFlow) {
        if (results == null || results.isEmpty()) throw new IllegalArgumentException("Ensaio sem resultado por vazão não aprova");
        for (FlowResult r : results) {
            BigDecimal max = maxErrorPctByFlow.get(r.flow());
            if (max == null) throw new IllegalArgumentException("Erro máximo não informado para " + r.flow());
            if (r.errorPct().abs().compareTo(max) > 0) return false;
        }
        return true;
    }

    /** Lote aprovado somente com TODA a amostra aprovada (amostra vazia não aprova lote com itens). */
    public static boolean lotApproved(List<Boolean> sampledMeterApprovals, int lotSize) {
        if (lotSize > 0 && sampledMeterApprovals.isEmpty()) return false;
        return sampledMeterApprovals.stream().allMatch(Boolean::booleanValue);
    }
}

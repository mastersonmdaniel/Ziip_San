package br.com.ziipsan.intelligence.gis.hydraulic;

import br.com.ziipsan.intelligence.gis.hydraulic.solver.SolverPort;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleFunction;

/**
 * GIS-12: calibração do modelo com séries de telemetria (pressão, vazão
 * e nível de reservatório — ED-17), quando houver. Sem telemetria, não
 * há calibração: o método recusa série vazia em vez de "calibrar" sem
 * dado. O ajuste é busca em grade declarada sobre dois fatores globais
 * (multiplicador de demanda e fator de rugosidade), reexecutando o
 * solver a cada candidato pela função injetada; as métricas são as do
 * melhor candidato contra as observações pareadas por código e tipo.
 */
public final class HydraulicCalibration {

    private HydraulicCalibration() {
    }

    public enum SeriesKind {PRESSURE_M, FLOW_LPS, TANK_LEVEL_M}

    /** Uma observação de telemetria pareável com a saída do solver pelo código do elemento.
     *  Para TANK_LEVEL_M, referenceElevationM é a cota do tanque (do cadastro): nível = carga − cota. */
    public record Observation(String elementCode, SeriesKind kind, double value, Double referenceElevationM) {
        public Observation(String elementCode, SeriesKind kind, double value) {
            this(elementCode, kind, value, null);
        }
    }

    /** Política declarada pelo chamador: grade e limiares do grau de confiança (não são norma; são parâmetro). */
    public record CalibrationPolicy(List<Double> demandMultipliers, List<Double> roughnessFactors,
                                    double highRmseMax, double mediumRmseMax) {
        public CalibrationPolicy {
            if (demandMultipliers == null || demandMultipliers.isEmpty()) demandMultipliers = List.of(1.0);
            if (roughnessFactors == null || roughnessFactors.isEmpty()) roughnessFactors = List.of(1.0);
        }

        public static CalibrationPolicy declaredDefault() {
            List<Double> dm = new ArrayList<>();
            for (double v = 0.60; v <= 1.401; v += 0.05) dm.add(Math.round(v * 100.0) / 100.0);
            List<Double> rf = new ArrayList<>();
            for (double v = 0.80; v <= 1.201; v += 0.05) rf.add(Math.round(v * 100.0) / 100.0);
            return new CalibrationPolicy(dm, rf, 2.0, 5.0);
        }
    }

    public record CalibrationOutcome(double demandMultiplier, double roughnessFactor,
                                     double rmse, double mae, double bias, int pairedPoints,
                                     HydraulicPublication.Confidence confidence) {
    }

    /** Compara resultado do solver com observações; devolve métricas (sem ajuste). */
    public static CalibrationOutcome evaluate(SolverPort.SolverResult result, List<Observation> observations,
                                              CalibrationPolicy policy) {
        return score(1.0, 1.0, result, observations, policy);
    }

    /**
     * Calibra reexecutando o solver pela grade de multiplicadores de
     * demanda da política declarada e devolve o melhor candidato pelas
     * métricas contra a telemetria. O fator de rugosidade devolvido é
     * 1.0: rugosidade se ajusta reescrevendo a foto e reexecutando —
     * nunca reescalando a saída do solver.
     */
    public static CalibrationOutcome calibrate(DoubleFunction<SolverPort.SolverResult> rerun,
                                               List<Observation> observations, CalibrationPolicy policy) {
        if (observations == null || observations.isEmpty()) {
            throw new IllegalArgumentException("Sem telemetria nao ha calibracao: serie de observacoes vazia.");
        }
        CalibrationOutcome best = null;
        for (double dm : policy.demandMultipliers()) {
            CalibrationOutcome candidate = score(dm, 1.0, rerun.apply(dm), observations, policy);
            if (candidate.pairedPoints() == 0) continue;
            if (best == null || candidate.rmse() < best.rmse()) best = candidate;
        }
        if (best == null) throw new IllegalArgumentException("Nenhuma observacao pareou com a saida do solver.");
        return best;
    }

    private static CalibrationOutcome score(double dm, double rf, SolverPort.SolverResult result,
                                            List<Observation> observations, CalibrationPolicy policy) {
        double sumSq = 0, sumAbs = 0, sumSigned = 0;
        int n = 0;
        for (Observation o : observations) {
            Double simulated = simulatedValue(result, o);
            if (simulated == null) continue;
            double err = simulated - o.value();
            sumSq += err * err;
            sumAbs += Math.abs(err);
            sumSigned += err;
            n++;
        }
        if (n == 0) return new CalibrationOutcome(dm, rf, Double.POSITIVE_INFINITY, 0, 0, 0,
                HydraulicPublication.Confidence.LOW);
        double rmse = Math.sqrt(sumSq / n);
        HydraulicPublication.Confidence confidence = rmse <= policy.highRmseMax()
                ? HydraulicPublication.Confidence.HIGH
                : rmse <= policy.mediumRmseMax() ? HydraulicPublication.Confidence.MEDIUM
                : HydraulicPublication.Confidence.LOW;
        return new CalibrationOutcome(dm, rf, rmse, sumAbs / n, sumSigned / n, n, confidence);
    }

    private static Double simulatedValue(SolverPort.SolverResult result, Observation o) {
        return switch (o.kind()) {
            case PRESSURE_M -> {
                var nv = result.nodes().get(o.elementCode());
                yield nv == null ? null : nv.pressureM();
            }
            case FLOW_LPS -> {
                var lv = result.links().get(o.elementCode());
                yield lv == null ? null : Math.abs(lv.flowLps());
            }
            case TANK_LEVEL_M -> {
                var nv = result.nodes().get(o.elementCode());
                yield (nv == null || o.referenceElevationM() == null) ? null : nv.headM() - o.referenceElevationM();
            }
        };
    }

    /** Série agrupada por tipo, para o relatório de calibração. */
    public static Map<SeriesKind, Integer> coverage(List<Observation> observations) {
        Map<SeriesKind, Integer> map = new LinkedHashMap<>();
        for (Observation o : observations) map.merge(o.kind(), 1, Integer::sum);
        return map;
    }
}

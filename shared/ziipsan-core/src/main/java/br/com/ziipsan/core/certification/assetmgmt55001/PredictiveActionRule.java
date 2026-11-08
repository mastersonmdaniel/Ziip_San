package br.com.ziipsan.core.certification.assetmgmt55001;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Ação preditiva por REGRA ESTATÍSTICA SIMPLES sobre séries históricas
 * (ED-72): média e desvio-padrão de uma janela de observações do tenant;
 * o sinal nasce quando a observação mais recente se afasta da média da
 * janela por mais de {@code k} desvios — para cima ou para baixo,
 * conforme a direção configurada.
 *
 * <p><b>Parâmetros sem fábrica:</b> tamanho mínimo da janela
 * ({@code minSamples}), o multiplicador {@code k} e a direção são
 * parâmetros DO TENANT, declarados na regra. Nenhuma fonte fixa quantas
 * amostras bastam nem quantos desvios disparam ação preditiva — sem os
 * parâmetros declarados, a regra não se constrói; série mais curta que o
 * mínimo NÃO gera sinal: o resultado é {@link Verdict#INSUFFICIENT_DATA},
 * nunca uma média presumida.
 *
 * <p>O sinal é uma SUGESTÃO de ação registrada (não é OS): quem transforma
 * sinal em ordem de serviço é a operação (porta da ED-36, como a OPE-38 da
 * ED-56 faz com os seus gatilhos) — fronteira declarada no relatório.
 */
public final class PredictiveActionRule {

    /** Direção do afastamento que dispara o sinal. */
    public enum Direction { ABOVE, BELOW, EITHER }

    /** Veredito da avaliação de uma série. */
    public enum Verdict { SIGNAL, NO_SIGNAL, INSUFFICIENT_DATA }

    /** Uma observação da série histórica (grandeza com unidade declarada). */
    public record Observation(LocalDate on, double value) {
        public Observation {
            Objects.requireNonNull(on, "data da observação é obrigatória");
        }
    }

    /** Regra declarada pelo tenant: janela mínima, multiplicador k e direção. */
    public record RuleParameters(int minSamples, double deviationMultiplier, Direction direction) {
        public RuleParameters {
            if (minSamples < 2) {
                throw new IllegalArgumentException("minSamples (parâmetro do tenant) deve ser "
                        + ">= 2: desvio-padrão de uma amostra só não existe");
            }
            if (!(deviationMultiplier > 0)) {
                throw new IllegalArgumentException("deviationMultiplier k (parâmetro do "
                        + "tenant) deve ser positivo: " + deviationMultiplier);
            }
            Objects.requireNonNull(direction, "direction da regra é obrigatória "
                    + "(parâmetro do tenant)");
        }
    }

    /** Sinal preditivo: a observação, a média e o desvio da janela que o geraram. */
    public record PredictiveSignal(UUID assetId, String measurement, String unit,
                                   LocalDate observedOn, double observedValue,
                                   double windowMean, double windowStdDev,
                                   double deviationMultiplier) {
    }

    /** Resultado da avaliação: veredito e, quando há sinal, o sinal. */
    public record Evaluation(Verdict verdict, PredictiveSignal signal) {
    }

    private PredictiveActionRule() {
    }

    /**
     * Avalia a série (ordenada por data pelo chamador): a janela é a série
     * SEM a observação mais recente; o sinal compara a mais recente com a
     * estatística da janela. Série com menos de {@code minSamples + 1}
     * observações = INSUFFICIENT_DATA.
     */
    public static Evaluation evaluate(Iso55001Activation activation, UUID assetId,
                                      String measurement, String unit,
                                      List<Observation> series, RuleParameters params) {
        Objects.requireNonNull(activation, "activation é obrigatória");
        activation.requireEnabled();
        Objects.requireNonNull(assetId, "assetId do sinal preditivo é obrigatório");
        if (measurement == null || measurement.isBlank()) {
            throw new IllegalArgumentException("measurement do sinal é obrigatória");
        }
        if (unit == null || unit.isBlank()) {
            throw new IllegalArgumentException("unit da série é obrigatória (nenhuma unidade "
                    + "presumida)");
        }
        Objects.requireNonNull(params, "params da regra (do tenant) são obrigatórios");
        if (series == null || series.size() < params.minSamples() + 1) {
            return new Evaluation(Verdict.INSUFFICIENT_DATA, null);
        }
        List<Observation> ordered = series.stream()
                .sorted(java.util.Comparator.comparing(Observation::on)).toList();
        List<Observation> window = ordered.subList(0, ordered.size() - 1);
        Observation latest = ordered.get(ordered.size() - 1);
        double mean = window.stream().mapToDouble(Observation::value).average().orElseThrow();
        double variance = window.stream()
                .mapToDouble(o -> (o.value() - mean) * (o.value() - mean)).average().orElseThrow();
        double stdDev = Math.sqrt(variance);
        if (stdDev == 0.0) {
            // Janela constante: qualquer afastamento é desvio infinito; só
            // há sinal se a mais recente difere da média, na direção certa.
            boolean differs = latest.value() != mean;
            boolean dirOk = switch (params.direction()) {
                case ABOVE -> latest.value() > mean;
                case BELOW -> latest.value() < mean;
                case EITHER -> differs;
            };
            return dirOk
                    ? new Evaluation(Verdict.SIGNAL, new PredictiveSignal(assetId,
                            measurement.trim(), unit.trim(), latest.on(), latest.value(),
                            mean, stdDev, params.deviationMultiplier()))
                    : new Evaluation(Verdict.NO_SIGNAL, null);
        }
        double z = (latest.value() - mean) / stdDev;
        boolean fires = switch (params.direction()) {
            case ABOVE -> z > params.deviationMultiplier();
            case BELOW -> z < -params.deviationMultiplier();
            case EITHER -> Math.abs(z) > params.deviationMultiplier();
        };
        return fires
                ? new Evaluation(Verdict.SIGNAL, new PredictiveSignal(assetId,
                        measurement.trim(), unit.trim(), latest.on(), latest.value(),
                        mean, stdDev, params.deviationMultiplier()))
                : new Evaluation(Verdict.NO_SIGNAL, null);
    }
}

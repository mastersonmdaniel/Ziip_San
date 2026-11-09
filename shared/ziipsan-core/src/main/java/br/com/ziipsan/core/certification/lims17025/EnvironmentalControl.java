package br.com.ziipsan.core.certification.lims17025;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Condicoes ambientais do laboratorio (aceite da ED-51): os limites
 * aceitaveis sao DADO DECLARADO (requisito declarado para a area ou
 * para o metodo — nenhum limite de temperatura/umidade nasce de
 * fabrica nesta classe, porque nenhuma fonte do projeto os fixa) e
 * cada leitura registrada e AVALIADA contra o requisito declarado.
 *
 * <p>Vereditos:
 * <ul>
 *   <li>sem requisito declarado, a leitura e registrada mas o
 *       veredito e {@code NOT_DECLARED} — nunca conformidade
 *       presumida;</li>
 *   <li>com requisito, leitura fora de qualquer faixa declarada (ou
 *       sem a grandeza que o requisito exige) e
 *       {@code OUT_OF_LIMITS}, com as violacoes nomeadas.</li>
 * </ul>
 * O ensaio recusa resultado quando o veredito e {@code OUT_OF_LIMITS}
 * (ver {@code AssayExecution}); {@code NOT_DECLARED} nao bloqueia,
 * mas fica registrado no ensaio — declarado, nao escondido.
 */
public final class EnvironmentalControl {

    private EnvironmentalControl() {}

    /**
     * Requisito ambiental DECLARADO: faixas de temperatura (graus
     * Celsius) e umidade relativa (percentual), cada uma opcional —
     * mas um requisito sem nenhuma faixa nao e requisito e e recusado.
     */
    public record Requirement(BigDecimal temperatureMin, BigDecimal temperatureMax,
                              BigDecimal humidityMin, BigDecimal humidityMax,
                              String declaredByRef) {
        public Requirement {
            if (temperatureMin == null && temperatureMax == null
                    && humidityMin == null && humidityMax == null) {
                throw new IllegalArgumentException("requisito ambiental sem nenhuma faixa "
                        + "declarada nao e requisito");
            }
            if (temperatureMin != null && temperatureMax != null
                    && temperatureMin.compareTo(temperatureMax) > 0) {
                throw new IllegalArgumentException("faixa de temperatura invertida: min > max");
            }
            if (humidityMin != null && humidityMax != null
                    && humidityMin.compareTo(humidityMax) > 0) {
                throw new IllegalArgumentException("faixa de umidade invertida: min > max");
            }
            if (declaredByRef == null || declaredByRef.isBlank())
                throw new IllegalArgumentException("declaredByRef e obrigatorio (quem declarou o requisito)");
        }
    }

    /** Uma leitura ambiental registrada (ao menos uma grandeza medida). */
    public record Reading(BigDecimal temperature, BigDecimal humidity, Instant at, String recordedByRef) {
        public Reading {
            if (temperature == null && humidity == null)
                throw new IllegalArgumentException("leitura ambiental sem nenhuma grandeza medida");
            Objects.requireNonNull(at, "instante da leitura ambiental e obrigatorio");
            if (recordedByRef == null || recordedByRef.isBlank())
                throw new IllegalArgumentException("recordedByRef e obrigatorio (quem registrou a leitura)");
        }
    }

    public enum Verdict { WITHIN_LIMITS, OUT_OF_LIMITS, NOT_DECLARED }

    /** Resultado da avaliacao: veredito + violacoes nomeadas (vazia quando dentro ou nao declarado). */
    public record Evaluation(Verdict verdict, List<String> violations) {
        public Evaluation {
            Objects.requireNonNull(verdict, "verdict e obrigatorio");
            violations = violations == null ? List.of() : List.copyOf(violations);
        }
    }

    /** Avalia a leitura contra o requisito declarado (nulo = nao declarado). */
    public static Evaluation evaluate(Requirement requirement, Reading reading) {
        Objects.requireNonNull(reading, "reading e obrigatoria");
        if (requirement == null) return new Evaluation(Verdict.NOT_DECLARED, List.of());
        List<String> violations = new ArrayList<>();
        check(violations, "temperatura", reading.temperature(),
                requirement.temperatureMin(), requirement.temperatureMax());
        check(violations, "umidade", reading.humidity(),
                requirement.humidityMin(), requirement.humidityMax());
        return new Evaluation(violations.isEmpty() ? Verdict.WITHIN_LIMITS : Verdict.OUT_OF_LIMITS,
                violations);
    }

    private static void check(List<String> violations, String name, BigDecimal measured,
                              BigDecimal min, BigDecimal max) {
        if (min == null && max == null) return;
        if (measured == null) {
            violations.add(name + ": grandeza exigida pelo requisito nao foi medida");
            return;
        }
        if (min != null && measured.compareTo(min) < 0)
            violations.add(name + " " + measured.toPlainString() + " abaixo do minimo " + min.toPlainString());
        if (max != null && measured.compareTo(max) > 0)
            violations.add(name + " " + measured.toPlainString() + " acima do maximo " + max.toPlainString());
    }
}

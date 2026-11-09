package br.com.ziipsan.core.certification.lims17025;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Controle interno de qualidade pelas MULTIRREGRAS DE WESTGARD
 * (aceite da ED-51: "controle de Westgard"; levantamento 18g,
 * SG-822: "regras de Westgard implementadas" — no sistema legado
 * existiam so em comentario).
 *
 * <p>As regras implementadas sao as definicoes classicas das
 * multirregras, expressas em multiplos do desvio-padrao ATRIBUIDO do
 * material de controle (media e desvio sao dados do material, do
 * certificado/atribuicao dele — nunca constantes desta classe):
 * <ul>
 *   <li>{@code 1_2s} — um controle alem de 2 DP: ALERTA (warning),
 *       nao rejeita sozinho;</li>
 *   <li>{@code 1_3s} — um controle alem de 3 DP: REJEITA a corrida;</li>
 *   <li>{@code 2_2s} — dois controles consecutivos alem de 2 DP do
 *       mesmo lado: REJEITA;</li>
 *   <li>{@code R_4s} — dois controles consecutivos, um acima de +2 DP
 *       e outro abaixo de -2 DP (amplitude maior que 4 DP): REJEITA;</li>
 *   <li>{@code 4_1s} — quatro consecutivos alem de 1 DP do mesmo
 *       lado: REJEITA;</li>
 *   <li>{@code 10_x} — dez consecutivos do mesmo lado da media:
 *       REJEITA.</li>
 * </ul>
 * A avaliacao e sobre a corrida em ordem cronologica e julga o
 * ULTIMO ponto com o historico que o precede (o uso classico: a
 * corrida so libera resultado se o controle nao estiver rejeitado).
 * Comparacoes sao feitas em aritmetica exata sobre os desvios
 * (|valor - media| contra multiplos do desvio-padrao), sem divisao.
 */
public final class WestgardControl {

    private WestgardControl() {}

    /** Material de controle com media e desvio-padrao ATRIBUIDOS (dados do material). */
    public record QcMaterial(String code, String parameterKey, BigDecimal assignedMean,
                             BigDecimal assignedSd, String unit) {
        public QcMaterial {
            if (code == null || code.isBlank())
                throw new IllegalArgumentException("code do material de controle e obrigatorio");
            if (parameterKey == null || parameterKey.isBlank())
                throw new IllegalArgumentException("parameterKey do material de controle e obrigatorio");
            Objects.requireNonNull(assignedMean, "assignedMean (media atribuida) e obrigatoria");
            Objects.requireNonNull(assignedSd, "assignedSd (desvio atribuido) e obrigatorio");
            if (assignedSd.signum() <= 0)
                throw new IllegalArgumentException("assignedSd deve ser maior que zero");
        }
    }

    /** Uma observacao do controle numa corrida. */
    public record QcObservation(BigDecimal value, Instant at) {
        public QcObservation {
            Objects.requireNonNull(value, "value da observacao de controle e obrigatorio");
            Objects.requireNonNull(at, "instante da observacao de controle e obrigatorio");
        }
    }

    /** Regras de Westgard avaliadas (nome da regra, nao valor regulatorio). */
    public enum Rule { R_1_2S, R_1_3S, R_2_2S, R_R_4S, R_4_1S, R_10_X }

    public enum Status { IN_CONTROL, WARNING, OUT_OF_CONTROL }

    /** Avaliacao da corrida: estado + regras violadas no ultimo ponto. */
    public record Evaluation(Status status, List<Rule> violatedRules, int pointsEvaluated) {
        public Evaluation {
            Objects.requireNonNull(status, "status e obrigatorio");
            violatedRules = violatedRules == null ? List.of() : List.copyOf(violatedRules);
        }
        /** A corrida libera resultados quando nao esta fora de controle (alerta libera, registrado). */
        public boolean releasesResults() { return status != Status.OUT_OF_CONTROL; }
    }

    /**
     * Avalia a corrida (observacoes em ordem cronologica) julgando o
     * ultimo ponto com o historico. Corrida vazia e recusada: sem
     * controle observado, nao ha estado de controle.
     */
    public static Evaluation evaluate(QcMaterial material, List<QcObservation> run) {
        Objects.requireNonNull(material, "material de controle e obrigatorio");
        if (run == null || run.isEmpty())
            throw new IllegalArgumentException("corrida de controle vazia: sem observacao, "
                    + "nao ha estado de controle a declarar");
        for (int i = 1; i < run.size(); i++) {
            if (run.get(i).at().isBefore(run.get(i - 1).at()))
                throw new IllegalArgumentException("observacoes do controle fora de ordem cronologica");
        }
        BigDecimal sd = material.assignedSd();
        BigDecimal sd1 = sd;
        BigDecimal sd2 = sd.multiply(BigDecimal.valueOf(2));
        BigDecimal sd3 = sd.multiply(BigDecimal.valueOf(3));
        // desvios assinados (valor - media) de cada ponto
        List<BigDecimal> dev = new ArrayList<>();
        for (QcObservation o : run) dev.add(o.value().subtract(material.assignedMean()));
        int n = dev.size();
        BigDecimal last = dev.get(n - 1);
        List<Rule> violated = new ArrayList<>();

        boolean beyond2 = last.abs().compareTo(sd2) > 0;
        boolean beyond3 = last.abs().compareTo(sd3) > 0;
        boolean reject = false;

        if (beyond3) { violated.add(Rule.R_1_3S); reject = true; }
        if (n >= 2) {
            BigDecimal prev = dev.get(n - 2);
            boolean prevBeyond2 = prev.abs().compareTo(sd2) > 0;
            if (beyond2 && prevBeyond2 && sameSide(last, prev)) {
                violated.add(Rule.R_2_2S); reject = true;
            }
            if (beyond2 && prevBeyond2 && !sameSide(last, prev)) {
                violated.add(Rule.R_R_4S); reject = true;
            }
        }
        if (n >= 4 && last.abs().compareTo(sd1) > 0) {
            boolean all = true;
            for (int i = n - 4; i < n; i++) {
                BigDecimal d = dev.get(i);
                if (d.abs().compareTo(sd1) <= 0 || !sameSide(d, last)) { all = false; break; }
            }
            if (all) { violated.add(Rule.R_4_1S); reject = true; }
        }
        if (n >= 10 && last.signum() != 0) {
            boolean all = true;
            for (int i = n - 10; i < n; i++) {
                if (!sameSide(dev.get(i), last) || dev.get(i).signum() == 0) { all = false; break; }
            }
            if (all) { violated.add(Rule.R_10_X); reject = true; }
        }
        if (!reject && beyond2) violated.add(Rule.R_1_2S);

        Status status = reject ? Status.OUT_OF_CONTROL
                : (!violated.isEmpty() ? Status.WARNING : Status.IN_CONTROL);
        return new Evaluation(status, violated, n);
    }

    private static boolean sameSide(BigDecimal a, BigDecimal b) {
        return (a.signum() > 0 && b.signum() > 0) || (a.signum() < 0 && b.signum() < 0);
    }
}

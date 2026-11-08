package br.com.ziipsan.platform.implantation;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Implantação simulada com tenant sintético (ED-09): o ensaio que prova o
 * plano-modelo de 60 dias antes do primeiro cliente real.
 *
 * <p>O ensaio executa a jornada inteira sobre um tenant marcado como
 * sintético (o clone mascarado da ED-09 ou massa sintética) e responde a
 * três perguntas, sem atalho:
 * <ol>
 *   <li>o tenant do ensaio é sintético? (simulação sobre tenant real é
 *       recusada — ela completaria passos e aceitaria parâmetros de um
 *       tenant de verdade);</li>
 *   <li>todos os passos obrigatórios do plano de 60 dias foram concluídos?</li>
 *   <li>a conclusão coube no plano? (último passo concluído até o dia 60
 *       contado do início do ensaio).</li>
 * </ol>
 * O resultado registra o dia de conclusão de cada passo — é a evidência do
 * ensaio, não um carimbo: simulação que estourou o plano ou pulou passo
 * devolve {@code concludedWithinPlan == false} com os motivos nomeados.
 * Execução de passo fora do plano-modelo ou de passo repetido também é
 * nomeada como motivo e não move o relógio do ensaio (que corre só sobre
 * os passos do plano).
 */
public final class ImplantationSimulation {

    private ImplantationSimulation() {
    }

    /** Execução de um passo no ensaio: quando ele foi concluído (dia do relógio do ensaio). */
    public record StepRun(String stepKey, LocalDate completedOn) {
    }

    public record Result(boolean concludedWithinPlan, int elapsedDays,
                         List<String> failures) {
    }

    /**
     * Avalia o ensaio contra o plano-modelo de 60 dias.
     *
     * @param syntheticTenant o tenant do ensaio é sintético (clone mascarado ou massa sintética)
     * @param startedOn       início do ensaio
     * @param runs            conclusão de cada passo executado
     */
    public static Result evaluate(boolean syntheticTenant, LocalDate startedOn, List<StepRun> runs) {
        if (startedOn == null || runs == null) {
            throw new IllegalArgumentException("Início e execuções do ensaio são obrigatórios.");
        }
        ImplantationPlan.Plan plan = ImplantationPlan.template60();
        List<String> failures = new ArrayList<>();
        if (!syntheticTenant) {
            failures.add("A implantação simulada exige tenant sintético (clone mascarado ou massa "
                    + "sintética); tenant real não é ambiente de ensaio.");
        }
        Map<String, LocalDate> completed = new java.util.LinkedHashMap<>();
        LocalDate last = startedOn;
        for (StepRun run : runs) {
            if (run.stepKey() == null || run.completedOn() == null) {
                throw new IllegalArgumentException("Execução de passo sem chave ou sem data.");
            }
            if (run.completedOn().isBefore(startedOn)) {
                throw new IllegalArgumentException(
                        "Passo concluído antes do início do ensaio: " + run.stepKey());
            }
            // Passo fora do plano-modelo é nomeado e NÃO move o relógio do
            // ensaio: o veredito corre só sobre os passos do plano.
            if (!ImplantationPlan.MANDATORY_STEP_KEYS.contains(run.stepKey())) {
                failures.add("Passo fora do plano-modelo executado no ensaio: " + run.stepKey());
                continue;
            }
            // Passo repetido é nomeado; vale a primeira conclusão registrada.
            if (completed.containsKey(run.stepKey())) {
                failures.add("Passo executado mais de uma vez no ensaio: " + run.stepKey());
                continue;
            }
            completed.put(run.stepKey(), run.completedOn());
            if (run.completedOn().isAfter(last)) {
                last = run.completedOn();
            }
        }
        for (String key : ImplantationPlan.MANDATORY_STEP_KEYS) {
            if (!completed.containsKey(key)) {
                failures.add("Passo obrigatório não concluído no ensaio: " + key);
            }
        }
        int elapsed = (int) (last.toEpochDay() - startedOn.toEpochDay()) + 1;
        if (elapsed > plan.totalDays()) {
            failures.add("O ensaio levou " + elapsed + " dias, além do plano-modelo de "
                    + plan.totalDays() + " dias.");
        }
        return new Result(failures.isEmpty(), elapsed, List.copyOf(failures));
    }
}

package br.com.ziipsan.core.backoffice;

import br.com.ziipsan.core.sac.NewConnectionWorkflowPort;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Ligacao nova executada pela OS (ED-36) PELAS ETAPAS DO FLUXO ENCADEADO DA
 * ED-34: as etapas chegam pela porta da ED-35
 * ({@link NewConnectionWorkflowPort}, que o adaptador comercial liga a
 * NewConnectionWorkflow.definition da ED-34) — nenhuma etapa e redefinida
 * aqui. Esta classe e o guarda de execucao da OS sobre o fluxo: uma etapa
 * so se conclui com as dependencias concluidas. As dependencias sao as do
 * fluxo publicado da ED-34 (EXECUTION e SEWER_CONNECTION dependem de
 * INSPECTION; ACTIVATION depende de EXECUTION e, quando a etapa existe, de
 * SEWER_CONNECTION); etapa desconhecida cai na regra conservadora de ordem
 * sequencial da lista devolvida pela porta.
 */
public final class NewConnectionExecution {

    public static final String EXECUTION = "EXECUTION";
    public static final String SEWER = "SEWER_CONNECTION";
    public static final String ACTIVATION = "ACTIVATION";
    public static final String INSPECTION = "INSPECTION";

    private final List<String> stepCodes;

    public NewConnectionExecution(NewConnectionWorkflowPort workflow, boolean sewerAvailable,
                                  boolean requiresSewerWhenAvailable, Integer inspectionDeadlineHours,
                                  Integer executionDeadlineHours) {
        Objects.requireNonNull(workflow, "porta do fluxo encadeado da ED-34 obrigatoria");
        List<String> steps = workflow.chainedStepCodes(sewerAvailable, requiresSewerWhenAvailable,
                inspectionDeadlineHours, executionDeadlineHours);
        if (steps == null || steps.isEmpty()) throw new IllegalStateException("fluxo encadeado da ED-34 nao retornou etapas");
        if (!steps.contains(EXECUTION) || !steps.contains(ACTIVATION))
            throw new IllegalStateException("fluxo da ligacao nova sem EXECUTION/ACTIVATION nao e executavel por OS: " + steps);
        this.stepCodes = List.copyOf(steps);
    }

    public List<String> stepCodes() { return stepCodes; }
    public boolean hasSewerStep() { return stepCodes.contains(SEWER); }

    /** Dependencias de uma etapa no fluxo da ED-34 (ver classe). */
    public Set<String> dependenciesOf(String stepCode) {
        if (!stepCodes.contains(stepCode)) throw new IllegalArgumentException("etapa fora do fluxo: " + stepCode);
        Map<String, Set<String>> known = Map.of(
                EXECUTION, Set.of(INSPECTION),
                SEWER, Set.of(INSPECTION),
                ACTIVATION, hasSewerStep() ? Set.of(EXECUTION, SEWER) : Set.of(EXECUTION));
        Set<String> deps = known.get(stepCode);
        if (deps != null) return deps;
        int idx = stepCodes.indexOf(stepCode);
        return idx == 0 ? Set.of() : Set.of(stepCodes.get(idx - 1));
    }

    public void assertCompletable(String stepCode, Set<String> completedSteps) {
        Objects.requireNonNull(completedSteps, "etapas concluidas sao obrigatorias");
        Set<String> missing = new java.util.HashSet<>(dependenciesOf(stepCode));
        missing.removeAll(completedSteps);
        if (!missing.isEmpty())
            throw new IllegalStateException("etapa " + stepCode + " bloqueada: dependencias nao concluidas " + missing + " (fluxo da ED-34)");
    }
}

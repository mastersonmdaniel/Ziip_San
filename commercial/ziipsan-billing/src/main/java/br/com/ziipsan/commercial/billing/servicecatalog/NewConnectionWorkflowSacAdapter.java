package br.com.ziipsan.commercial.billing.servicecatalog;

import br.com.ziipsan.core.sac.NewConnectionWorkflowPort;

import java.util.List;

/**
 * Adaptador ED-35 \u2192 ED-34 (correcao pos-reprovacao, G2): implementa a porta
 * {@link NewConnectionWorkflowPort} do atendimento (ziipsan-core)
 * delegando ao fluxo encadeado REAL da ED-34,
 * {@link NewConnectionWorkflow#definition(boolean, boolean, Integer, Integer)}
 * \u2014 nenhuma etapa e reimplementada; os codigos devolvidos sao os das
 * etapas da {@link WorkflowDefinition} da ED-34, na ordem do fluxo.
 * Vive no modulo comercial porque a dependencia do repo e billing \u2192 core
 * (o core nao pode importar o modulo comercial).
 */
public final class NewConnectionWorkflowSacAdapter implements NewConnectionWorkflowPort {

    @Override
    public List<String> chainedStepCodes(boolean sewerAvailable, boolean requiresSewerWhenAvailable,
                                         Integer inspectionDeadlineHours, Integer executionDeadlineHours) {
        WorkflowDefinition def = NewConnectionWorkflow.definition(sewerAvailable,
                requiresSewerWhenAvailable, inspectionDeadlineHours, executionDeadlineHours);
        return def.steps().stream().map(WorkflowDefinition.Step::code).toList();
    }
}

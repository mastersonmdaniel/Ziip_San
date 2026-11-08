package br.com.ziipsan.core.sac;

import br.com.ziipsan.core.protocol.ProtocolChannel;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Entrada do atendimento para a solicitacao de LIGACAO NOVA (ED-35): o
 * protocolo da P1.36 (UUID + canal, tipos reais do nucleo) e aberto sobre o
 * fluxo encadeado da ED-34 atraves da porta
 * {@link NewConnectionWorkflowPort} \u2014 nenhuma etapa e reimplementada aqui
 * (correcao pos-reprovacao, G2).
 */
public final class NewConnectionIntake {

    private final UUID protocolId;
    private final ProtocolChannel channel;
    private final List<String> stepCodes;

    private NewConnectionIntake(UUID protocolId, ProtocolChannel channel, List<String> stepCodes) {
        this.protocolId = protocolId;
        this.channel = channel;
        this.stepCodes = stepCodes;
    }

    public static NewConnectionIntake open(UUID protocolId, ProtocolChannel channel,
                                           NewConnectionWorkflowPort workflow,
                                           boolean sewerAvailable, boolean requiresSewerWhenAvailable,
                                           Integer inspectionDeadlineHours, Integer executionDeadlineHours) {
        Objects.requireNonNull(protocolId, "protocolo obrigatorio");
        Objects.requireNonNull(channel, "canal do protocolo (P1.36) obrigatorio");
        Objects.requireNonNull(workflow, "porta do fluxo encadeado da ED-34 obrigatoria");
        List<String> steps = workflow.chainedStepCodes(sewerAvailable, requiresSewerWhenAvailable,
                inspectionDeadlineHours, executionDeadlineHours);
        if (steps == null || steps.isEmpty())
            throw new IllegalStateException("fluxo encadeado da ED-34 nao retornou etapas");
        return new NewConnectionIntake(protocolId, channel, List.copyOf(steps));
    }

    public UUID protocolId() { return protocolId; }
    public ProtocolChannel channel() { return channel; }
    /** Etapas do fluxo da ED-34, na ordem devolvida pela porta. */
    public List<String> stepCodes() { return stepCodes; }
}

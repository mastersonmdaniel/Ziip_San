package br.com.ziipsan.core.sac;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Reclamacao, resposta e pesquisa de satisfacao como EVIDENCIA de comunicacao
 * e retorno (ED-35; ISO 9001 e ISO 24510): a pesquisa so se registra sobre
 * protocolo COM RESPOSTA registrada; nota na escala 1..5 validada.
 * Persistencia: reclamacao e resposta tem tabela propria
 * ({@code sac_communication_evidences}) alem da pesquisa
 * ({@code sac_satisfaction_surveys}) \u2014 correcao pos-reprovacao, M5.
 */
public final class CommunicationEvidence {

    public record Satisfaction(int score, String commentOrNull, LocalDateTime recordedAt) {
        public Satisfaction {
            if (score < 1 || score > 5)
                throw new IllegalArgumentException("nota de satisfacao fora da escala 1..5: " + score);
            Objects.requireNonNull(recordedAt, "data/hora da pesquisa obrigatoria");
        }
    }

    private final UUID protocolId;
    private final String complaintSummary;
    private final String responseSummary;
    private Satisfaction satisfaction;

    public CommunicationEvidence(UUID protocolId, String complaintSummary, String responseSummary) {
        this.protocolId = Objects.requireNonNull(protocolId, "protocolo obrigatorio");
        if (complaintSummary == null || complaintSummary.isBlank())
            throw new IllegalArgumentException("reclamacao registrada e obrigatoria na evidencia");
        this.complaintSummary = complaintSummary;
        this.responseSummary = responseSummary;
    }

    public void recordSatisfaction(Satisfaction s) {
        Objects.requireNonNull(s, "pesquisa obrigatoria");
        if (responseSummary == null || responseSummary.isBlank())
            throw new IllegalStateException(
                    "pesquisa de satisfacao exige resposta registrada ao usuario (evidencia de retorno)");
        if (satisfaction != null) throw new IllegalStateException("pesquisa ja registrada para o protocolo");
        this.satisfaction = s;
    }

    public UUID protocolId() { return protocolId; }
    public String complaintSummary() { return complaintSummary; }
    public String responseSummary() { return responseSummary; }
    public Satisfaction satisfaction() { return satisfaction; }
    public boolean complete() { return responseSummary != null && !responseSummary.isBlank()
            && satisfaction != null; }
}

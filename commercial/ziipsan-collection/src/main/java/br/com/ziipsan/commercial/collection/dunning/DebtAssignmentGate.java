package br.com.ziipsan.commercial.collection.dunning;

import br.com.ziipsan.core.api.ApiException;

import java.util.List;
import java.util.UUID;

/**
 * Cessao de divida a programa federal de renegociacao (MP 1.393/2026,
 * RAS-11): nasce DESLIGADA e so e habilitada depois de parecer juridico
 * favoravel. Esta tarefa implementa SO o interruptor — nenhuma cessao,
 * remessa ou baixa e executada aqui.
 *
 * <p>Duas condicoes cumulativas: (1) o parametro
 * {@code dun.federal_assignment_enabled} ligado pelo tenant e (2) parecer
 * juridico favoravel REGISTRADO (com proveniencia) para o assunto
 * {@value #SUBJECT}. Qualquer uma ausente, a habilitacao e recusada.
 */
public final class DebtAssignmentGate {

    /** Assunto do parecer da cessao federal na porta juridica. */
    public static final String SUBJECT = "FEDERAL_DEBT_ASSIGNMENT_MP1393";

    private DebtAssignmentGate() {
    }

    /** Recusa explicita quando a cessao nao esta habilitada nas duas condicoes. */
    public static DunningPorts.LegalOpinionPort.Opinion requireEnabled(
            UUID tenantId, DunningParameters params, DunningPorts.LegalOpinionPort legalOpinions) {
        if (tenantId == null || params == null || legalOpinions == null) {
            throw new IllegalArgumentException("tenant, parametros e porta juridica sao obrigatorios");
        }
        if (!params.federalAssignmentEnabled()) {
            throw new ApiException.Validation(
                    "Cessao a programa federal de renegociacao DESLIGADA neste tenant ("
                            + DunningParameters.FEDERAL_ASSIGNMENT_ENABLED + "): a MP 1.393/2026 so e "
                            + "habilitada depois de parecer juridico favoravel (RAS-11).",
                    List.of(), List.of());
        }
        return legalOpinions.favorableOpinion(tenantId, SUBJECT).orElseThrow(() ->
                new ApiException.Validation(
                        "Cessao federal ligada no parametro, mas SEM parecer juridico favoravel "
                                + "registrado: a habilitacao exige o parecer (RAS-11).",
                        List.of(), List.of()));
    }
}

package br.com.ziipsan.core.certification.env14001;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Um cenario de emergencia ambiental e a sua preparacao (aceite da
 * ED-70: "emergencia ambiental por cenario"): o tenant declara, POR
 * CENARIO, o codigo, a descricao, as acoes de resposta, o responsavel
 * e os aspectos ligados (por codigo de aspecto do proprio tenant).
 *
 * <p>NENHUM cenario, acao ou prazo de fabrica existe neste pacote: o
 * cenario nasce da declaracao do tenant. Cenario sem acao de resposta
 * ou sem responsavel e recusado — preparacao vazia nao e preparacao.
 * A data da ultima revisao do cenario e fato declarado (nula enquanto
 * o cenario nao foi revisado); nenhum intervalo de revisao e
 * presumido.</p>
 *
 * <p>Registro da norma: a ativacao e COMPONENTE e o construtor
 * canonico exige a norma ligada (padrao ED-73/D1). O cenario e o
 * desdobramento declarado dos aspectos em situacao de EMERGENCIA —
 * ele referencia os aspectos, nao os duplica.</p>
 */
public record EnvironmentalEmergencyScenario(Iso14001Activation activation, UUID id, UUID tenantId,
                                             String scenarioCode, String description,
                                             List<String> responseActions, UUID responsibleId,
                                             List<String> linkedAspectCodes,
                                             LocalDate lastReviewedOn) {

    public EnvironmentalEmergencyScenario {
        Objects.requireNonNull(activation, "activation e obrigatoria (componente do registro)");
        activation.requireEnabled();
        Objects.requireNonNull(id, "id do cenario e obrigatorio");
        Objects.requireNonNull(tenantId, "tenantId do cenario e obrigatorio");
        if (scenarioCode == null || scenarioCode.isBlank()) {
            throw new IllegalArgumentException("scenarioCode e obrigatorio "
                    + "(codigo do cenario declarado pelo tenant)");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description do cenario e obrigatoria");
        }
        if (responseActions == null || responseActions.isEmpty()) {
            throw new IllegalArgumentException("o cenario exige ao menos uma acao de resposta "
                    + "declarada pelo tenant: preparacao vazia nao e preparacao");
        }
        if (responseActions.stream().anyMatch(a -> a == null || a.isBlank())) {
            throw new IllegalArgumentException("acao de resposta em branco no cenario " + scenarioCode);
        }
        Objects.requireNonNull(responsibleId, "responsibleId (responsavel pelo cenario) e obrigatorio");
        responseActions = List.copyOf(responseActions);
        linkedAspectCodes = linkedAspectCodes == null ? List.of() : List.copyOf(linkedAspectCodes);
        if (linkedAspectCodes.stream().anyMatch(c -> c == null || c.isBlank())) {
            throw new IllegalArgumentException("codigo de aspecto ligado em branco no cenario " + scenarioCode);
        }
        scenarioCode = scenarioCode.trim();
        description = description.trim();
    }

    /** Cenario declarado pelo tenant, ainda sem revisao registrada. */
    public static EnvironmentalEmergencyScenario declare(Iso14001Activation activation, UUID id,
                                                         UUID tenantId, String scenarioCode,
                                                         String description,
                                                         List<String> responseActions,
                                                         UUID responsibleId,
                                                         List<String> linkedAspectCodes) {
        return new EnvironmentalEmergencyScenario(activation, id, tenantId, scenarioCode,
                description, responseActions, responsibleId, linkedAspectCodes, null);
    }

    /** Verdadeiro quando o cenario ja teve ao menos uma revisao registrada. */
    public boolean reviewed() {
        return lastReviewedOn != null;
    }
}

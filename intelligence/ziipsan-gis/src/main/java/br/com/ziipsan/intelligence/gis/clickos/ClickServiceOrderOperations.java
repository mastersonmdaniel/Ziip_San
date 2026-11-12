package br.com.ziipsan.intelligence.gis.clickos;

import br.com.ziipsan.intelligence.gis.clickos.ClickOsDtos.ClickInput;
import br.com.ziipsan.intelligence.gis.clickos.ClickOsDtos.CreatedView;
import br.com.ziipsan.intelligence.gis.clickos.ClickOsDtos.PayloadView;
import br.com.ziipsan.intelligence.gis.clickos.ClickOsDtos.PropagationView;

import java.util.UUID;

/**
 * Operações da II-1 expostas pela API, no mesmo desenho do
 * {@code ManeuverOperations} da GIS-11: o controller é fino e o
 * núcleo ({@link ClickServiceOrderService}) fica provado sem
 * banco/Spring. O tenant e o solicitante vêm SEMPRE do contexto da
 * requisição — nunca do corpo.
 */
public interface ClickServiceOrderOperations {

    /** Só propagação: o que o clique alcança e quem afecta. */
    PropagationView propagate(UUID tenantId, ClickInput input);

    /** Propagação + payload pré-preenchido (não submete; nomeia as faltas). */
    PayloadView buildPayload(UUID tenantId, UUID requestedBy, ClickInput input);

    /**
     * Propagação + payload + submissão ao núcleo de OS. Payload não
     * pronto é recusado pelo núcleo com as faltas nomeadas e nenhuma
     * OS nasce.
     */
    CreatedView createFromClick(UUID tenantId, UUID requestedBy, ClickInput input);
}

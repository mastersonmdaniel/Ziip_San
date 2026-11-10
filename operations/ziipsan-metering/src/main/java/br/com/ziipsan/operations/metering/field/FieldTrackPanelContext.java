package br.com.ziipsan.operations.metering.field;

import br.com.ziipsan.core.fieldos.Tracking;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Entrada tipada de uma consulta ao painel de acompanhamento da trilha
 * (ED-20): quem chama ({@code requesterRole} — o gate de chefia e aplicado
 * pelo proprio nucleo, em {@code GpsPanel.build}) e os trabalhadores cuja
 * trilha entra no painel nesta consulta. Ambos vem da integracao por
 * {@link FieldTrackContextProvider#panelContextFor}; nenhuma fonte de
 * papeis nem de ambito e presumida nesta frente.
 */
public record FieldTrackPanelContext(Tracking.AccessRole requesterRole,
                                     List<UUID> workerIds) {

    public FieldTrackPanelContext {
        Objects.requireNonNull(requesterRole, "papel do chamador do painel e obrigatorio");
        workerIds = workerIds == null ? List.of() : List.copyOf(workerIds);
    }
}

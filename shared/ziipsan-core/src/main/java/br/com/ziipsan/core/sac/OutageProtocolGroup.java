package br.com.ziipsan.core.sac;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Agrupamento dos protocolos do MESMO evento de interrupcao + aviso ao
 * atendente (ED-35): protocolo entra no grupo quando a unidade esta na area
 * afetada do evento. O aviso ao atendente e gerado sempre que o grupo tem
 * ao menos um protocolo \u2014 o atendente nao atende o mesmo evento N vezes
 * sem saber. Identificadores em {@link UUID} (correcao M4).
 */
public final class OutageProtocolGroup {

    public record ProtocolRef(UUID protocolId, UUID consumerUnitId) {
        public ProtocolRef { Objects.requireNonNull(protocolId); Objects.requireNonNull(consumerUnitId); }
    }

    /** Aviso ao atendente: evento, protocolos agrupados e unidades essenciais no evento. */
    public record AttendantNotice(UUID outageId, int groupedProtocols, boolean hasEssentialUnits,
                                  String message) {}

    private final PlannedOutage outage;
    private final List<ProtocolRef> grouped;

    private OutageProtocolGroup(PlannedOutage outage, List<ProtocolRef> grouped) {
        this.outage = outage; this.grouped = grouped;
    }

    public static OutageProtocolGroup group(PlannedOutage outage, List<ProtocolRef> protocols) {
        Objects.requireNonNull(outage, "evento obrigatorio");
        List<ProtocolRef> mine = protocols.stream()
                .filter(p -> outage.affectedUnitIds().contains(p.consumerUnitId())).toList();
        return new OutageProtocolGroup(outage, List.copyOf(mine));
    }

    public List<ProtocolRef> grouped() { return grouped; }
    public int size() { return grouped.size(); }

    public AttendantNotice attendantNotice() {
        String msg = "Interrupcao " + outage.outageId() + " (" + outage.area().locality()
                + "): " + grouped.size() + " protocolo(s) agrupado(s) neste evento"
                + (outage.emergencySupplyRequired() ? "; unidade essencial afetada" : "")
                + "; previsao de retorno " + outage.expectedReturn() + ".";
        return new AttendantNotice(outage.outageId(), grouped.size(),
                outage.emergencySupplyRequired(), msg);
    }
}

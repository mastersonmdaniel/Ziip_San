package br.com.ziipsan.intelligence.gis.clickos;

import br.com.ziipsan.core.serviceorder.TargetType;
import br.com.ziipsan.intelligence.gis.clickos.ClickOsModel.ClickPropagationConfig;
import br.com.ziipsan.intelligence.gis.clickos.ClickOsModel.PropagationMode;
import br.com.ziipsan.intelligence.gis.clickos.ClickOsModel.PropagationResult;
import br.com.ziipsan.intelligence.gis.clickos.ClickOsPorts.ClickOrderPort;
import br.com.ziipsan.intelligence.gis.clickos.ClickOsPorts.ClickServiceOrderPayload;
import br.com.ziipsan.intelligence.gis.clickos.ClickOsPorts.CoreTargetHint;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.FunctionalConnection;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.NetworkSnapshot;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.Target;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverPorts;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Serviço do clique (II-1): carrega o retrato da GIS-03 pela porta
 * EXISTENTE da GIS-11 ({@code ManeuverPorts.SnapshotPort}), propaga
 * com o {@link ClickPropagationEngine} e monta/submete o payload de
 * OS pela {@link ClickOrderPort}. Sem Spring e sem banco neste
 * núcleo — a fiação (bean/endpoint) é a pendência P-II1-FIACAO,
 * como nas demais frentes do projecto.
 *
 * <p>Alvos do núcleo montados no payload: o elemento clicado como
 * {@code GIS_POINT} (nó) ou {@code GIS_LINEAR} (arco) e cada UC
 * afectada como {@code CONSUMER_UNIT} — a unidade é exclusivamente
 * alvo da ordem, como manda o núcleo (ADR-0039: não há coluna de
 * unidade na OS; o vínculo é pelos alvos).
 */
public final class ClickServiceOrderService {

    private final ManeuverPorts.SnapshotPort snapshotPort;
    private final ClickOrderPort orderPort;

    public ClickServiceOrderService(ManeuverPorts.SnapshotPort snapshotPort, ClickOrderPort orderPort) {
        this.snapshotPort = Objects.requireNonNull(snapshotPort, "servico do clique sem porta de retrato");
        this.orderPort = orderPort;
    }

    /** Só propagação: o que o clique alcança e quem afecta. */
    public PropagationResult propagate(UUID tenantId, Target clicked, PropagationMode mode,
                                       ClickPropagationConfig config) {
        NetworkSnapshot snapshot = snapshotPort.loadSnapshot(
                Objects.requireNonNull(tenantId, "propagacao sem tenant"));
        return ClickPropagationEngine.propagate(snapshot, clicked, mode, config);
    }

    /** Propagação + payload de OS pré-preenchido (não submete). */
    public ClickServiceOrderPayload buildPayload(UUID tenantId, UUID requestedBy, String serviceTypeCode,
                                                 Target clicked, PropagationMode mode,
                                                 ClickPropagationConfig config) {
        PropagationResult r = propagate(tenantId, clicked, mode, config);
        List<CoreTargetHint> targets = new ArrayList<>();
        if (clicked.arcId() != null) targets.add(new CoreTargetHint(TargetType.GIS_LINEAR, clicked.arcId()));
        else targets.add(new CoreTargetHint(TargetType.GIS_POINT, clicked.nodeId()));
        r.affectedUnitIds().forEach(u -> targets.add(new CoreTargetHint(TargetType.CONSUMER_UNIT, u)));

        List<String> missing = new ArrayList<>();
        if (serviceTypeCode == null || serviceTypeCode.isBlank())
            missing.add("serviceTypeCode [A DEFINIR] — tipo do catalogo do tenant");
        if (r.outcome() == ClickOsModel.ClickOutcome.NOT_ISOLABLE)
            missing.add("isolamento impossivel: " + r.isolationFailure());
        if (r.truncated())
            missing.add(r.unitLimitExceeded()
                    ? "teto de UCs afectadas excedido — subdividir a intervencao [A DEFINIR pelo tenant]"
                    : "propagacao truncada por maxHops — rever o limite [A DEFINIR pelo tenant]");

        return new ClickServiceOrderPayload(tenantId, requestedBy, serviceTypeCode,
                clicked, mode, r.outcome(),
                r.affectedNodeIds(), r.affectedArcIds(),
                r.affectedConnections().stream().map(FunctionalConnection::id).toList(),
                r.affectedUnitIds(), r.serviceConnectionIds(), r.totalEconomies(),
                r.essentialUnitIds(), r.sensitiveUnitIds(),
                r.valvesToCloseCodes(), r.truncated(),
                targets, missing.isEmpty(), missing);
    }

    /**
     * Submete o payload PRONTO à porta do núcleo. Payload não pronto
     * é recusado com as faltas nomeadas e a porta NÃO é chamada —
     * nenhuma OS nasce de propagação incompleta em silêncio.
     */
    public UUID submit(ClickServiceOrderPayload payload) {
        if (!payload.readyToSubmit())
            throw new IllegalStateException("payload nao pronto para o nucleo de OS: "
                    + String.join("; ", payload.missingForSubmit()));
        if (orderPort == null)
            throw new IllegalStateException("porta de OS nao fiada (P-II1-FIACAO)");
        return orderPort.createFromClickPayload(payload);
    }
}

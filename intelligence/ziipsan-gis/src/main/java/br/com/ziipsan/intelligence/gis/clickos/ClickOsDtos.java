package br.com.ziipsan.intelligence.gis.clickos;

import br.com.ziipsan.intelligence.gis.clickos.ClickOsModel.PropagationResult;
import br.com.ziipsan.intelligence.gis.clickos.ClickOsPorts.ClickServiceOrderPayload;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.Target;

import java.util.List;
import java.util.UUID;

/**
 * DTOs da superfície HTTP da II-1 (OS por clique): pedidos e visões,
 * sem entidade de banco — espelhos fiéis dos records do núcleo
 * ({@link ClickOsModel.PropagationResult} e
 * {@link ClickOsPorts.ClickServiceOrderPayload}).
 */
public final class ClickOsDtos {

    private ClickOsDtos() {
    }

    /**
     * Pedido de clique: exactamente um de {@code arcId}/{@code nodeId}
     * (a validação é a do record {@code Target} do núcleo).
     * {@code serviceTypeCode} é o tipo do catálogo do tenant — nulo ou
     * vazio não é erro de pedido: o payload nasce não pronto com a
     * falta nomeada, como manda o núcleo. {@code maxHops} e
     * {@code maxAffectedUnits} nulos = sem limite naquele eixo
     * (parâmetros do tenant [A DEFINIR]; nenhum valor nasce aqui).
     */
    public record ClickInput(UUID arcId, UUID nodeId, String mode, String serviceTypeCode,
                             Integer maxHops, Integer maxAffectedUnits) {

        public Target target() {
            return new Target(arcId, nodeId);
        }

        public ClickOsModel.PropagationMode propagationMode() {
            return mode == null || mode.isBlank()
                    ? ClickOsModel.PropagationMode.DOWNSTREAM
                    : ClickOsModel.PropagationMode.valueOf(mode.trim().toUpperCase());
        }

        public ClickOsModel.ClickPropagationConfig config() {
            return new ClickOsModel.ClickPropagationConfig(maxHops, maxAffectedUnits);
        }
    }

    /** Visão de uma propagação por clique (só leitura — nenhuma OS nasce daqui). */
    public record PropagationView(
            String mode, String outcome,
            List<UUID> affectedNodeIds, List<UUID> affectedArcIds,
            List<UUID> affectedConnectionIds, List<UUID> affectedUnitIds,
            List<UUID> serviceConnectionIds, int totalEconomies,
            List<UUID> essentialUnitIds, List<UUID> sensitiveUnitIds,
            List<String> valvesToCloseCodes, String isolationFailure,
            boolean truncated, boolean unitLimitExceeded) {

        public static PropagationView of(PropagationResult r) {
            return new PropagationView(
                    r.mode().name(), r.outcome().name(),
                    r.affectedNodeIds(), r.affectedArcIds(),
                    r.affectedConnections().stream()
                            .map(c -> c.id()).toList(),
                    r.affectedUnitIds(), r.serviceConnectionIds(), r.totalEconomies(),
                    r.essentialUnitIds(), r.sensitiveUnitIds(),
                    r.valvesToCloseCodes(), r.isolationFailure(),
                    r.truncated(), r.unitLimitExceeded());
        }
    }

    /** Visão do payload pré-preenchido: prontidão e faltas nomeadas incluídas. */
    public record PayloadView(
            String serviceTypeCode, String mode, String outcome,
            List<UUID> affectedNodeIds, List<UUID> affectedArcIds,
            List<UUID> affectedConnectionIds, List<UUID> affectedUnitIds,
            int totalEconomies, boolean truncated,
            boolean readyToSubmit, List<String> missingForSubmit) {

        public static PayloadView of(ClickServiceOrderPayload p) {
            return new PayloadView(
                    p.serviceTypeCode(), p.mode().name(), p.outcome().name(),
                    p.affectedNodeIds(), p.affectedArcIds(),
                    p.affectedConnectionIds(), p.affectedUnitIds(),
                    p.totalEconomies(), p.truncated(),
                    p.readyToSubmit(), p.missingForSubmit());
        }
    }

    /** OS criada no núcleo a partir do clique. */
    public record CreatedView(UUID orderId, PayloadView payload) {
    }
}

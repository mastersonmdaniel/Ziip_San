package br.com.ziipsan.intelligence.gis.clickos;

import br.com.ziipsan.core.serviceorder.TargetType;
import br.com.ziipsan.intelligence.gis.clickos.ClickOsModel.ClickOutcome;
import br.com.ziipsan.intelligence.gis.clickos.ClickOsModel.PropagationMode;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.Target;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Payload e porta de OS da II-1.
 *
 * <p>O payload é a OS PRÉ-PREENCHIDA do clique: elemento clicado,
 * propagação (nós/arcos), ligações e UCs afectadas, economias,
 * essenciais e sensíveis — tudo o que o operador viu no mapa,
 * pronto para o núcleo de OS. A II-1 NÃO cria OS nem conhece a
 * máquina de estados da OS (P1.31): a única saída é
 * {@link ClickOrderPort}, cuja fiação (pendência P-II1-FIACAO)
 * traduz o payload para o núcleo —
 * {@code ServiceOrderService.createOrder(OrderInput)} com origem
 * {@code INTERNAL} (clique em backoffice: pela regra do núcleo, OS
 * interna nunca carrega protocolo) e os alvos de
 * {@link ClickServiceOrderPayload#coreTargets()} registados sobre a
 * ordem — e devolve o id criado por ele.
 *
 * <p>O tipo de serviço NÃO é inventado aqui: é o do catálogo do
 * tenant, passado por quem chama. Sem ele, o payload nasce
 * {@code readyToSubmit=false} com a falta nomeada
 * ({@code serviceTypeCode [A DEFINIR]}), nunca com um tipo presumido.
 * Prioridade e prazo seguem a mesma regra: são do tipo/catálogo —
 * nenhum valor nasce neste pacote.
 */
public final class ClickOsPorts {

    private ClickOsPorts() {
    }

    /** Alvo tipado para o núcleo de OS (P1.31): a classe é o {@code TargetType} dele. */
    public record CoreTargetHint(TargetType targetType, UUID targetId) {
        public CoreTargetHint {
            Objects.requireNonNull(targetType, "alvo do nucleo sem classe");
            Objects.requireNonNull(targetId, "alvo do nucleo sem id");
        }
    }

    /**
     * OS pré-preenchida do clique.
     *
     * @param readyToSubmit  pode ir à porta de OS tal como está
     * @param missingForSubmit faltas nomeadas quando {@code readyToSubmit=false}
     *                         (tipo por definir, isolamento impossível, propagação truncada)
     */
    public record ClickServiceOrderPayload(
            UUID tenantId, UUID requestedBy, String serviceTypeCode,
            Target clicked, PropagationMode mode, ClickOutcome outcome,
            List<UUID> affectedNodeIds, List<UUID> affectedArcIds,
            List<UUID> affectedConnectionIds, List<UUID> affectedUnitIds,
            List<UUID> serviceConnectionIds, int totalEconomies,
            List<UUID> essentialUnitIds, List<UUID> sensitiveUnitIds,
            List<String> valvesToCloseCodes, boolean truncated,
            List<CoreTargetHint> coreTargets,
            boolean readyToSubmit, List<String> missingForSubmit) {
        public ClickServiceOrderPayload {
            Objects.requireNonNull(tenantId, "payload sem tenant");
            Objects.requireNonNull(requestedBy, "payload sem solicitante");
            Objects.requireNonNull(clicked, "payload sem elemento clicado");
            affectedNodeIds = List.copyOf(affectedNodeIds);
            affectedArcIds = List.copyOf(affectedArcIds);
            affectedConnectionIds = List.copyOf(affectedConnectionIds);
            affectedUnitIds = List.copyOf(affectedUnitIds);
            serviceConnectionIds = List.copyOf(serviceConnectionIds);
            essentialUnitIds = List.copyOf(essentialUnitIds);
            sensitiveUnitIds = List.copyOf(sensitiveUnitIds);
            valvesToCloseCodes = List.copyOf(valvesToCloseCodes);
            coreTargets = List.copyOf(coreTargets);
            missingForSubmit = List.copyOf(missingForSubmit);
        }
    }

    /** Porta ÚNICA de saída para o núcleo de OS existente (P1.31, via fiação). */
    public interface ClickOrderPort {
        /** Cria a OS no núcleo a partir do payload pronto e devolve o id criado por ele. */
        UUID createFromClickPayload(ClickServiceOrderPayload payload);
    }
}

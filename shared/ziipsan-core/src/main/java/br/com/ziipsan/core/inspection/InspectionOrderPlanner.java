package br.com.ziipsan.core.inspection;

import br.com.ziipsan.core.serviceorder.TargetType;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Decide a abertura da OS de fiscalizacao a partir de um
 * {@link InspectionCriterion.CriterionHit} (aceite: "OS de fiscalizacao
 * gerada por criterio").
 *
 * <p>Regras:
 * <ul>
 *   <li>todo hit valido gera pedido de OS do tipo
 *       {@link InspectionOrderTypes#INSPECTION} com alvo CONSUMER_UNIT
 *       (classe de alvo real do motor da P1.31);</li>
 *   <li>se ja existe OS de fiscalizacao ABERTA para a mesma unidade e o
 *       mesmo criterio, NAO duplica: o hit e anexado a OS existente
 *       (decisao {@link DecisionKind#ATTACH_TO_EXISTING}). A referencia
 *       devolvida pelo chamador e CONFERIDA contra o hit (unidade e
 *       criterio — correcao D4 da revisao cruzada da ED-24): referencia
 *       de outra unidade ou de outro criterio e recusada com excecao,
 *       nunca anexada em silencio;</li>
 *   <li>a abertura em si e do motor de OS da P1.31, pela porta
 *       {@link InspectionOrderGateway} — esta classe nao cria OS, so decide
 *       e monta o pedido tipado.</li>
 * </ul>
 */
public final class InspectionOrderPlanner {

    private InspectionOrderPlanner() {}

    public enum DecisionKind { CREATE_ORDER, ATTACH_TO_EXISTING }

    /** Pedido de abertura de OS de fiscalizacao, pronto para a porta do motor. */
    public record OrderRequest(String orderTypeCode, TargetType targetType, UUID consumerUnitId,
                               InspectionCriterion criterion, String evidenceSummary) {
        public OrderRequest {
            Objects.requireNonNull(orderTypeCode, "tipo da OS e obrigatorio");
            Objects.requireNonNull(targetType, "classe do alvo e obrigatoria");
            Objects.requireNonNull(consumerUnitId, "unidade e obrigatoria");
            Objects.requireNonNull(criterion, "criterio e obrigatorio");
            if (evidenceSummary == null || evidenceSummary.isBlank())
                throw new IllegalArgumentException("evidencia do criterio e obrigatoria no pedido de OS");
        }
    }

    public record Decision(DecisionKind kind, OrderRequest request, UUID existingOrderId) {
        public Decision {
            Objects.requireNonNull(kind, "tipo da decisao e obrigatorio");
            if (kind == DecisionKind.CREATE_ORDER) {
                Objects.requireNonNull(request, "pedido e obrigatorio para criar OS");
                if (existingOrderId != null)
                    throw new IllegalArgumentException("criacao de OS nao carrega OS existente");
            } else {
                Objects.requireNonNull(existingOrderId, "OS existente e obrigatoria para anexar");
                if (request != null)
                    throw new IllegalArgumentException("anexar a OS existente nao carrega pedido novo");
            }
        }
    }

    /**
     * Referencia a OS de fiscalizacao aberta que o chamador encontrou
     * para o hit: id da OS + unidade + criterio DELA. O planner confere
     * unidade e criterio contra o hit antes de anexar (D4) — o chamador
     * nao e confiado cegamente.
     */
    public record OpenOrderReference(UUID orderId, UUID consumerUnitId, InspectionCriterion criterion) {
        public OpenOrderReference {
            Objects.requireNonNull(orderId, "id da OS aberta e obrigatorio");
            Objects.requireNonNull(consumerUnitId, "unidade da OS aberta e obrigatoria");
            Objects.requireNonNull(criterion, "criterio da OS aberta e obrigatorio");
        }
    }

    /**
     * @param hit               ocorrencia do criterio
     * @param openOrderForHit   referencia a OS de fiscalizacao aberta para
     *                          a mesma unidade+criterio, se o chamador a
     *                          encontrou; a referencia e conferida contra
     *                          o hit e divergencia e recusada (D4)
     */
    public static Decision plan(InspectionCriterion.CriterionHit hit, Optional<OpenOrderReference> openOrderForHit) {
        Objects.requireNonNull(hit, "hit do criterio e obrigatorio");
        Objects.requireNonNull(openOrderForHit, "consulta de OS aberta e obrigatoria (use Optional.empty())");
        if (openOrderForHit.isPresent()) {
            OpenOrderReference open = openOrderForHit.get();
            if (!open.consumerUnitId().equals(hit.consumerUnitId()) || open.criterion() != hit.criterion())
                throw new IllegalArgumentException("OS aberta informada nao e da mesma unidade+criterio do hit"
                        + " (hit: unidade " + hit.consumerUnitId() + ", criterio " + hit.criterion()
                        + "; OS " + open.orderId() + ": unidade " + open.consumerUnitId()
                        + ", criterio " + open.criterion() + ") — anexacao recusada");
            return new Decision(DecisionKind.ATTACH_TO_EXISTING, null, open.orderId());
        }
        OrderRequest request = new OrderRequest(InspectionOrderTypes.INSPECTION, TargetType.CONSUMER_UNIT,
                hit.consumerUnitId(), hit.criterion(), hit.evidenceSummary());
        return new Decision(DecisionKind.CREATE_ORDER, request, null);
    }

    /** Porta de abertura de OS no motor da P1.31 (adaptador real vive na integracao). */
    public interface InspectionOrderGateway {
        /** Abre a OS e devolve o id criado pelo motor. */
        UUID openOrder(OrderRequest request, UUID requestedBy);
    }
}

package br.com.ziipsan.intelligence.gis.maneuver;

import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.NetworkSnapshot;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.ValvePosition;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Portas da GIS-11 (ED-54) para as dependências EXECUTADAS — a GIS-11
 * não duplica nenhuma delas:
 *
 * <ul>
 *   <li><b>ED-41</b> (avisos multicanal): {@link NoticePort} é o ÚNICO
 *       caminho de aviso da manobra e da interrupção programada. A
 *       fiação liga esta porta ao {@code MultichannelDispatchService}
 *       da ED-41 (evento do catálogo da P1.38, opt-in por canal,
 *       teto {@code msg.monthly_cap}); o código da GIS-11 não envia
 *       SMS/WhatsApp/push por nenhum outro caminho e não nomeia
 *       provedor.</li>
 *   <li><b>ED-36</b> (OS no backoffice): {@link WorkOrderBatchPort} é
 *       o ÚNICO caminho de OS da manobra — lote de fechamento e lote
 *       de reabertura. A fiação cria as OS sobre o núcleo da P1.31
 *       pela ED-36 ({@code OrderTargets} tipados sobre a válvula);
 *       o tipo de serviço concreto é o do catálogo do tenant no
 *       motor — esta porta carrega a FINALIDADE
 *       ({@link BatchPurpose}), nunca um tipo inventado aqui.</li>
 *   <li><b>P2.21/GIS-03</b>: {@link SnapshotPort} lê a topologia
 *       persistida; {@link ValveStatePort} grava o estado operacional
 *       da válvula na baixa da OS (a escrita passa pelo pacote da
 *       GIS-04 na fiação, como toda escrita na rede em operação).</li>
 * </ul>
 *
 * <p>Recorte O8-1 (DECISOES-DO-DONO): o solver pgRouting de EQUIPES
 * segue bloqueado por ADR — nada aqui pede rota de deslocamento de
 * equipe. Traçado/isolamento/manobra de REDE é o domínio da exceção
 * do ADR-0034 (o mesmo da ED-36 para rede), e é só isso que esta
 * entrega implementa.
 */
public final class ManeuverPorts {

    private ManeuverPorts() {
    }

    /** Leitura da topologia persistida (GIS-03) já resolvida para o cálculo. */
    public interface SnapshotPort {
        NetworkSnapshot loadSnapshot(UUID tenantId);
    }

    /** Finalidade de um lote de OS da manobra (mapeada ao catálogo da ED-36/P1.31 na fiação). */
    public enum BatchPurpose { VALVE_CLOSE, VALVE_REOPEN }

    /** Um item do lote: a válvula-alvo da OS. */
    public record BatchItem(UUID valveNodeId, String valveCode) {
        public BatchItem {
            Objects.requireNonNull(valveNodeId, "item do lote sem valvula");
        }
    }

    /** Pedido de lote à ED-36. {@code dependsOnBatchId} = lote que precisa baixar antes (reabertura ← fechamento). */
    public record BatchRequest(UUID tenantId, UUID planId, BatchPurpose purpose,
                               List<BatchItem> items, UUID dependsOnBatchId,
                               LocalDateTime scheduledStart, UUID requestedBy) {
        public BatchRequest {
            Objects.requireNonNull(tenantId, "lote sem tenant");
            Objects.requireNonNull(planId, "lote sem plano");
            Objects.requireNonNull(purpose, "lote sem finalidade");
            items = List.copyOf(items);
            if (items.isEmpty()) throw new IllegalArgumentException("lote de OS vazio");
            Objects.requireNonNull(requestedBy, "lote sem solicitante");
        }
    }

    /** OS criada pela ED-36 para um item do lote. */
    public record CreatedOrder(UUID workOrderId, UUID valveNodeId) {
    }

    public record BatchResult(UUID batchId, List<CreatedOrder> orders) {
        public BatchResult {
            orders = List.copyOf(orders);
        }
    }

    /** Lote de OS sobre a ED-36 — sem duplicar o núcleo de OS. */
    public interface WorkOrderBatchPort {
        BatchResult createBatch(BatchRequest request);
    }

    /**
     * Pedido de aviso à ED-41: destinatários são as unidades afetadas
     * NA ORDEM do isolamento (sensíveis primeiro); o canal, o modelo
     * (P2.30) e o opt-in são da ED-41 — aqui só o fato e o contexto.
     */
    public record NoticeRequest(UUID tenantId, UUID planId, String eventCode,
                                List<UUID> consumerUnitIdsInNoticeOrder,
                                List<UUID> essentialUnitIds,
                                LocalDateTime scheduledStart, LocalDateTime expectedReturn,
                                String reason) {
        public NoticeRequest {
            Objects.requireNonNull(tenantId, "aviso sem tenant");
            Objects.requireNonNull(planId, "aviso sem plano/interrupcao");
            consumerUnitIdsInNoticeOrder = List.copyOf(consumerUnitIdsInNoticeOrder);
            essentialUnitIds = List.copyOf(essentialUnitIds);
        }
    }

    public record NoticeReceipt(String dispatchReference) {
    }

    /** Canal ÚNICO de aviso: a ED-41. */
    public interface NoticePort {
        NoticeReceipt notifyPlannedInterruption(NoticeRequest request);
    }

    /** Escrita do estado operacional da válvula, disparada pela baixa da OS. */
    public interface ValveStatePort {
        void applyValvePosition(UUID tenantId, UUID valveNodeId, ValvePosition position,
                                UUID workOrderId, LocalDateTime at);
    }
}

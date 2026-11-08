package br.com.ziipsan.intelligence.gis.asset;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Portas da GIS-19 (ED-56) para as dependencias EXECUTADAS — a
 * GIS-19 nao duplica nenhuma delas:
 *
 * <ul>
 *   <li><b>P1.35</b>: {@link InventoryPort} le o retrato do cadastro
 *       minimo de ativos (entidade dona da hierarquia);</li>
 *   <li><b>ED-36</b> (OS no backoffice sobre o nucleo da P1.31):
 *       {@link WorkOrderPort} e o UNICO caminho de OS da manutencao —
 *       a finalidade ({@link OrderPurpose}) e mapeada ao tipo de
 *       servico do catalogo do tenant na fiacao; nenhum tipo de OS
 *       e inventado aqui. Os alvos sao os {@code TargetType} reais
 *       do motor (EQUIPMENT/COMPONENT/OPERATIONAL_SITE/BUILDING/
 *       GIS_LINEAR/GIS_POINT);</li>
 *   <li><b>ED-32</b> (contabilidade): {@link AccountingPort} resolve
 *       o de-para MCRSB da ED-32 (GES-14) e recebe a exportacao da
 *       conciliacao GIS x patrimonio;</li>
 *   <li><b>ED-54</b>: {@link ValveOperabilityPort} e a escrita que
 *       alimenta o retrato da GIS-11 — e a resposta a pendencia
 *       P-ED54-05: a operabilidade real da valvula e DADO DESTA
 *       tarefa (programa de exercicio, OPE-09).</li>
 * </ul>
 */
public final class AssetPorts {

    private AssetPorts() {
    }

    public interface InventoryPort {
        AssetInventoryModel.InventorySnapshot loadInventory(UUID tenantId);
    }

    /** Finalidade de OS da manutencao (mapeada ao catalogo do tenant na fiacao). */
    public enum OrderPurpose {
        PREVENTIVE, CORRECTIVE, VALVE_EXERCISE, HYDRANT_TEST, NETWORK_FLUSH,
        CCTV_INSPECTION, SEWER_CLEANING, CONDITION_CHECK, VALVE_REPLACEMENT
    }

    public record OrderRequest(UUID tenantId, OrderPurpose purpose, String targetType,
                               UUID targetAssetId, String regionCode,
                               LocalDateTime scheduledFor, String planReference,
                               UUID requestedBy) {
        public OrderRequest {
            Objects.requireNonNull(tenantId, "OS sem tenant");
            Objects.requireNonNull(purpose, "OS sem finalidade");
            Objects.requireNonNull(targetAssetId, "OS de manutencao exige o ativo-alvo (P1.35/P1.31)");
            Objects.requireNonNull(requestedBy, "OS sem solicitante");
            if (targetType != null && !List.of("EQUIPMENT", "COMPONENT", "OPERATIONAL_SITE",
                    "BUILDING", "GIS_LINEAR", "GIS_POINT").contains(targetType))
                throw new IllegalArgumentException("classe de alvo fora das classes de ativo/rede da P1.31: " + targetType);
        }
    }

    public record CreatedOrder(UUID workOrderId, UUID targetAssetId) {
    }

    public interface WorkOrderPort {
        CreatedOrder createOrder(OrderRequest request);

        /** Lote agrupado (mesma finalidade e regiao/janela): um pedido por ativo. */
        default List<CreatedOrder> createGrouped(List<OrderRequest> requests) {
            return requests.stream().map(this::createOrder).toList();
        }
    }

    /** De-para MCRSB da ED-32: conta regulatoria ou vazio quando nao ha de-para vigente. */
    public interface AccountingPort {
        java.util.Optional<String> resolveTenantAccount(String regulatoryAccountCode, int exercise);

        void receiveReconciliationExport(UUID tenantId, String exportReference, int exercise);
    }

    /**
     * Estado de operabilidade da valvula com FRESCOR (correcao D3 da
     * revisao da ED-56, resposta completa a P-ED54-05): a ED-54 nao
     * recebe mais um booleano eterno — recebe um destes tres estados
     * mais a data do ultimo exercicio:
     * <ul>
     *   <li>{@code OPERABLE}: ultimo exercicio dentro do prazo da
     *       classe e a valvula fechou;</li>
     *   <li>{@code NOT_OPERABLE}: ultimo exercicio dentro do prazo e
     *       a valvula NAO fechou;</li>
     *   <li>{@code UNKNOWN}: sem exercicio registrado ou exercicio
     *       VENCIDO pela regua de {@code maint.valve_exercise_years_by_class}
     *       (AWWA/tenant) — o ultimo {@code true} velho NAO vale como
     *       operavel.</li>
     * </ul>
     */
    public enum ValveOperabilityState { OPERABLE, NOT_OPERABLE, UNKNOWN }

    /** Retrato de operabilidade entregue a ED-54 (estado + data do ultimo exercicio). */
    public record ValveOperabilitySnapshot(UUID valveAssetId, ValveOperabilityState state,
                                           java.time.LocalDate lastExercisedOn,
                                           java.time.LocalDate exerciseDueOn) {
        public ValveOperabilitySnapshot {
            java.util.Objects.requireNonNull(valveAssetId, "retrato de operabilidade sem valvula");
            java.util.Objects.requireNonNull(state, "retrato de operabilidade sem estado");
        }

        /** So {@code OPERABLE} fresco autoriza tratar a valvula como operavel. */
        public boolean operable() { return state == ValveOperabilityState.OPERABLE; }

        public boolean known() { return state != ValveOperabilityState.UNKNOWN; }
    }

    /** Operabilidade/estado da valvula lidos pela GIS-11 (ED-54). */
    public interface ValveOperabilityPort {
        void applyValveExerciseResult(UUID tenantId, UUID valveAssetId, boolean operable,
                                      String leftPosition, LocalDateTime at, UUID workOrderId);

        /**
         * Entrega do retrato COM FRESCOR a ED-54 (correcao D3).
         * Metodo {@code default} para nao quebrar adaptadores que so
         * implementam a escrita legada do exercicio: quem consome o
         * isolamento (ED-54) deve sobrescrever e ler o estado
         * tri-estado + data; a fiacao esta nomeada em P-ED56-05.
         */
        default void deliverOperabilitySnapshot(UUID tenantId, ValveOperabilitySnapshot snapshot) {
        }
    }
}

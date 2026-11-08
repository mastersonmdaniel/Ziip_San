package br.com.ziipsan.intelligence.gis.maneuver;

import br.com.ziipsan.intelligence.gis.maneuver.IsolationEngine.IsolationResult;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.FunctionalConnection;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.NetworkSnapshot;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.Target;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.ValvePosition;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverPorts.BatchItem;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverPorts.BatchPurpose;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverPorts.BatchRequest;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverPorts.BatchResult;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverPorts.NoticePort;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverPorts.NoticeReceipt;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverPorts.NoticeRequest;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverPorts.ValveStatePort;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverPorts.WorkOrderBatchPort;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Plano de manobra (GIS-11): isolamento calculado → aviso pela ED-41
 * → OS de fechamento e de reabertura EM LOTE pela ED-36 → baixa das
 * OS atualizando o estado das válvulas e o período de
 * desabastecimento por imóvel.
 *
 * <p>Regras de execução (declaradas):
 * <ul>
 *   <li>plano só nasce de isolamento POSSÍVEL; isolamento impossível
 *       recusa com o motivo do motor — nenhum plano parcial;</li>
 *   <li>a reabertura é lote separado, dependente do lote de
 *       fechamento ({@code dependsOnBatchId});</li>
 *   <li>a baixa de CADA OS de fechamento grava a válvula FECHADA na
 *       hora da baixa (porta de estado); o desabastecimento de um
 *       imóvel começa quando o isolamento fica EFETIVO (todas as OS
 *       de fechamento baixadas) e termina quando TODAS as OS de
 *       reabertura baixam — critério conservador e único para todos
 *       os imóveis do plano;</li>
 *   <li>baixa de OS estranha ao plano, duplicada ou fora de ordem
 *       (reabertura antes do isolamento efetivo) é recusada;</li>
 *   <li>válvula inoperável atravessada NÃO ganha OS (não fecha): ela
 *       vai nomeada no plano para a fila de substituição da ED-56.</li>
 * </ul>
 */
public final class ManeuverPlanService {

    /** Evento do catálogo da P1.38 proposto para o aviso de manobra (a ED-41 resolve o modelo). */
    public static final String NOTICE_EVENT_CODE = "PLANNED_INTERRUPTION_NOTICE";

    public enum PlanStatus { PLANNED, NOTICE_SENT, ORDERS_CREATED, ISOLATED, COMPLETED }

    public record ManeuverPlan(UUID planId, UUID tenantId, IsolationResult isolation,
                               LocalDateTime scheduledStart, LocalDateTime expectedReturn,
                               String reason, UUID requestedBy) {
    }

    /** Período de desabastecimento de UM imóvel (aceite: registrado por imóvel). */
    public record PropertyOutagePeriod(UUID propertyId, List<UUID> consumerUnitIds,
                                       LocalDateTime startedAt, LocalDateTime endedAt) {
        public PropertyOutagePeriod {
            consumerUnitIds = List.copyOf(consumerUnitIds);
        }

        public Duration duration() {
            return endedAt == null ? null : Duration.between(startedAt, endedAt);
        }
    }

    private final NoticePort noticePort;
    private final WorkOrderBatchPort batchPort;
    private final ValveStatePort valveStatePort;

    public ManeuverPlanService(NoticePort noticePort, WorkOrderBatchPort batchPort,
                               ValveStatePort valveStatePort) {
        this.noticePort = Objects.requireNonNull(noticePort, "porta de aviso (ED-41) obrigatoria");
        this.batchPort = Objects.requireNonNull(batchPort, "porta de lote de OS (ED-36) obrigatoria");
        this.valveStatePort = Objects.requireNonNull(valveStatePort, "porta de estado de valvula obrigatoria");
    }

    /** Calcula o isolamento e monta o plano (sem disparar nada). */
    public ManeuverPlan plan(UUID planId, UUID tenantId, NetworkSnapshot snapshot, Target target,
                             LocalDateTime scheduledStart, LocalDateTime expectedReturn,
                             String reason, UUID requestedBy) {
        Objects.requireNonNull(snapshot, "retrato da rede obrigatorio");
        if (!expectedReturn.isAfter(scheduledStart))
            throw new IllegalArgumentException("previsao de retorno deve ser posterior ao inicio programado");
        IsolationResult iso = IsolationEngine.isolate(snapshot, target);
        if (!iso.isolable())
            throw new IllegalArgumentException("trecho nao isolavel: " + iso.failure());
        return new ManeuverPlan(planId, tenantId, iso, scheduledStart, expectedReturn, reason, requestedBy);
    }

    /** Execução viva de um plano: lotes criados, baixas e períodos por imóvel. */
    public final class Execution {
        private final ManeuverPlan plan;
        private PlanStatus status = PlanStatus.PLANNED;
        private NoticeReceipt noticeReceipt;
        private BatchResult closeBatch;
        private BatchResult reopenBatch;
        private final Map<UUID, UUID> orderToValve = new HashMap<>(); // workOrderId -> valveNodeId
        private final Map<UUID, LocalDateTime> closedAt = new HashMap<>();
        private final Map<UUID, LocalDateTime> reopenedAt = new HashMap<>();
        private LocalDateTime isolationEffectiveAt;
        private LocalDateTime supplyRestoredAt;

        private Execution(ManeuverPlan plan) { this.plan = plan; }

        public ManeuverPlan plan() { return plan; }

        public PlanStatus status() { return status; }

        public NoticeReceipt noticeReceipt() { return noticeReceipt; }

        public BatchResult closeBatch() { return closeBatch; }

        public BatchResult reopenBatch() { return reopenBatch; }

        /** Aviso pela ED-41 (único canal) aos afetados, sensíveis primeiro. */
        public NoticeReceipt dispatchNotice() {
            if (status != PlanStatus.PLANNED) throw new IllegalStateException("aviso ja disparado ou plano avancado");
            IsolationResult iso = plan.isolation();
            this.noticeReceipt = noticePort.notifyPlannedInterruption(new NoticeRequest(
                    plan.tenantId(), plan.planId(), NOTICE_EVENT_CODE,
                    iso.affectedUnitIds(),
                    iso.essentialConnections().stream()
                            .flatMap(c -> c.consumerUnitIds().stream()).distinct().toList(),
                    plan.scheduledStart(), plan.expectedReturn(), plan.reason()));
            status = PlanStatus.NOTICE_SENT;
            return noticeReceipt;
        }

        /** OS de fechamento e reabertura em lote pela ED-36 (reabertura depende do fechamento). */
        public void createOrders() {
            if (status != PlanStatus.NOTICE_SENT)
                throw new IllegalStateException("lotes de OS exigem o aviso disparado (ED-41 antes da ED-36)");
            List<BatchItem> items = plan.isolation().valvesToClose().stream()
                    .map(v -> new BatchItem(v.nodeId(), v.code())).toList();
            this.closeBatch = batchPort.createBatch(new BatchRequest(plan.tenantId(), plan.planId(),
                    BatchPurpose.VALVE_CLOSE, items, null, plan.scheduledStart(), plan.requestedBy()));
            this.reopenBatch = batchPort.createBatch(new BatchRequest(plan.tenantId(), plan.planId(),
                    BatchPurpose.VALVE_REOPEN, items, closeBatch.batchId(),
                    plan.expectedReturn(), plan.requestedBy()));
            closeBatch.orders().forEach(o -> orderToValve.put(o.workOrderId(), o.valveNodeId()));
            reopenBatch.orders().forEach(o -> orderToValve.put(o.workOrderId(), o.valveNodeId()));
            status = PlanStatus.ORDERS_CREATED;
        }

        /**
         * Baixa de uma OS do plano: atualiza o estado da válvula e,
         * nas fronteiras (último fechamento / última reabertura),
         * abre/fecha o período de desabastecimento dos imóveis.
         */
        public void completeOrder(UUID workOrderId, LocalDateTime completedAt) {
            UUID valveNodeId = orderToValve.get(workOrderId);
            if (valveNodeId == null) throw new IllegalArgumentException("OS fora do plano: " + workOrderId);
            boolean isClose = closeBatch.orders().stream().anyMatch(o -> o.workOrderId().equals(workOrderId));
            if (isClose) {
                if (closedAt.putIfAbsent(workOrderId, completedAt) != null)
                    throw new IllegalStateException("baixa duplicada da OS de fechamento: " + workOrderId);
                valveStatePort.applyValvePosition(plan.tenantId(), valveNodeId, ValvePosition.CLOSED,
                        workOrderId, completedAt);
                if (closedAt.size() == closeBatch.orders().size()) {
                    isolationEffectiveAt = closedAt.values().stream().max(LocalDateTime::compareTo).orElseThrow();
                    status = PlanStatus.ISOLATED;
                }
            } else {
                if (status != PlanStatus.ISOLATED && reopenedAt.isEmpty())
                    throw new IllegalStateException("reabertura antes do isolamento efetivo");
                if (reopenedAt.putIfAbsent(workOrderId, completedAt) != null)
                    throw new IllegalStateException("baixa duplicada da OS de reabertura: " + workOrderId);
                valveStatePort.applyValvePosition(plan.tenantId(), valveNodeId, ValvePosition.OPEN,
                        workOrderId, completedAt);
                if (reopenedAt.size() == reopenBatch.orders().size()) {
                    supplyRestoredAt = reopenedAt.values().stream().max(LocalDateTime::compareTo).orElseThrow();
                    status = PlanStatus.COMPLETED;
                }
            }
        }

        public LocalDateTime isolationEffectiveAt() { return isolationEffectiveAt; }

        public LocalDateTime supplyRestoredAt() { return supplyRestoredAt; }

        /**
         * Períodos de desabastecimento POR IMÓVEL (só depois do
         * isolamento efetivo): agrupados pelo {@code propertyId} —
         * um imóvel com duas conexões funcionais afetadas tem UM
         * período, com as unidades consumidoras fundidas (correção
         * D5 da revisão; antes saía um período por conexão).
         * A ordem dos imóveis é a da primeira ocorrência na ordem
         * do aviso (sensíveis primeiro) das conexões afetadas.
         */
        public List<PropertyOutagePeriod> outagePeriods() {
            if (isolationEffectiveAt == null) return List.of();
            Map<UUID, List<UUID>> unitsByProperty = new java.util.LinkedHashMap<>();
            for (FunctionalConnection c : plan.isolation().affectedConnections()) {
                List<UUID> units = unitsByProperty.computeIfAbsent(c.propertyId(), k -> new ArrayList<>());
                for (UUID u : c.consumerUnitIds()) {
                    if (!units.contains(u)) units.add(u);
                }
            }
            List<PropertyOutagePeriod> out = new ArrayList<>();
            unitsByProperty.forEach((propertyId, units) -> out.add(
                    new PropertyOutagePeriod(propertyId, units, isolationEffectiveAt, supplyRestoredAt)));
            return List.copyOf(out);
        }
    }

    public Execution start(ManeuverPlan plan) {
        return new Execution(Objects.requireNonNull(plan, "plano obrigatorio"));
    }
}

package br.com.ziipsan.intelligence.gis.asset;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Plano preventivo de ativos NAO LINEARES (bombas, motores e paineis
 * de ETA, ETE, elevatoria e reservatorio) — OPE-20: por TEMPO e por
 * USO (horas de funcionamento, volume bombeado), com plano de
 * trabalho (tarefas, materiais, ferramentas e plano de seguranca),
 * geracao antecipada de OS, rotas de inspecao agrupadas, checklists
 * de ronda e indicadores de backlog, MTBF e MTTR.
 *
 * <p>Parametros (mecanismo de parametros do tenant, SQL irmao no
 * core): {@code maint.pm_generation_lead_days} (antecedencia da
 * geracao), {@code maint.pm_overdue_tolerance_days} (tolerancia de
 * atraso) e {@code maint.checklist_templates} (modelos de checklist
 * por classe). <b>Nenhum dos tres tem valor de fabrica</b> — nenhuma
 * fonte das dependencias fixa dias nem modelos: sem valor do tenant,
 * a geracao usa antecedencia ZERO declarada (gera no vencimento,
 * nunca antes por numero inventado), a tolerancia e zero e nao ha
 * checklist para anexar (a OS sai sem checklist e o fato e
 * registravel). Os valores sao SEMPRE entregues pelo chamador a
 * partir do parametro do tenant.
 */
public final class PmPlanService {

    private PmPlanService() {
    }

    public enum TriggerKind { TIME, RUNNING_HOURS, PUMPED_VOLUME }

    /** Plano de trabalho de uma linha do plano preventivo. */
    public record WorkPlan(List<String> tasks, List<String> materials,
                           List<String> tools, String safetyPlan) {
        public WorkPlan {
            tasks = tasks == null ? List.of() : List.copyOf(tasks);
            materials = materials == null ? List.of() : List.copyOf(materials);
            tools = tools == null ? List.of() : List.copyOf(tools);
            if (tasks.isEmpty()) throw new IllegalArgumentException("plano de trabalho sem tarefas");
            if (safetyPlan == null || safetyPlan.isBlank())
                throw new IllegalArgumentException("plano de trabalho sem plano de seguranca (OPE-20)");
        }
    }

    /**
     * Linha do plano preventivo de um ativo nao linear. Exatamente
     * um gatilho: tempo (meses), horas ou volume — o intervalo e do
     * PLANO DO TENANT, nunca de fabrica (sem fonte para periodicidade
     * de bomba/motor/painel nas dependencias).
     *
     * <p><b>Correcao D2 (revisao ED-56):</b> gatilho e intervalo sao
     * AMARRADOS — {@code TIME} exige {@code intervalMonths},
     * {@code RUNNING_HOURS} exige {@code intervalHours} e
     * {@code PUMPED_VOLUME} exige {@code intervalVolumeM3}; qualquer
     * combinacao cruzada e recusada na construcao (antes ela era
     * aceita e {@code statusAt}/{@code dueByUsage} quebravam com
     * {@code NullPointerException}). O CHECK irmao no SQL tenant
     * ({@code ck_asset_pm_plan_line_trigger_interval}) impoe a mesma
     * amarracao no banco.
     */
    public record PmPlanLine(UUID assetId, String equipmentClassCode, TriggerKind trigger,
                             Integer intervalMonths, Double intervalHours, Double intervalVolumeM3,
                             WorkPlan workPlan, String checklistTemplateKey) {
        public PmPlanLine {
            Objects.requireNonNull(assetId, "linha de PM sem ativo");
            Objects.requireNonNull(trigger, "linha de PM sem gatilho");
            int set = (intervalMonths != null ? 1 : 0) + (intervalHours != null ? 1 : 0)
                    + (intervalVolumeM3 != null ? 1 : 0);
            if (set != 1) throw new IllegalArgumentException("linha de PM exige exatamente um intervalo (tempo OU uso)");
            boolean matches = switch (trigger) {
                case TIME -> intervalMonths != null;
                case RUNNING_HOURS -> intervalHours != null;
                case PUMPED_VOLUME -> intervalVolumeM3 != null;
            };
            if (!matches)
                throw new IllegalArgumentException("intervalo nao corresponde ao gatilho " + trigger
                        + " (TIME exige meses, RUNNING_HOURS exige horas, PUMPED_VOLUME exige volume)");
            if (intervalMonths != null && intervalMonths <= 0)
                throw new IllegalArgumentException("intervalo em meses deve ser positivo");
            if (intervalHours != null && intervalHours <= 0)
                throw new IllegalArgumentException("intervalo em horas deve ser positivo");
            if (intervalVolumeM3 != null && intervalVolumeM3 <= 0)
                throw new IllegalArgumentException("intervalo em volume deve ser positivo");
            Objects.requireNonNull(workPlan, "linha de PM sem plano de trabalho");
        }
    }

    /** Estado de uso/tempo de um ativo frente ao plano. */
    public record AssetRuntime(UUID assetId, LocalDate lastPmOn,
                               double hoursSinceLastPm, double volumeSinceLastPmM3) {
    }

    public enum PmStatus { NOT_DUE, DUE_FOR_GENERATION, OVERDUE }

    /** Vencimento por tempo: ultima PM + meses; sem ultima PM, a entrada em operacao + meses. */
    public static Optional<LocalDate> dueDateByTime(PmPlanLine line, AssetRuntime runtime,
                                                    LocalDate inOperationSince) {
        if (line.trigger() != TriggerKind.TIME) return Optional.empty();
        LocalDate base = runtime.lastPmOn() != null ? runtime.lastPmOn() : inOperationSince;
        if (base == null) return Optional.empty();
        return Optional.of(base.plusMonths(line.intervalMonths()));
    }

    public static boolean dueByUsage(PmPlanLine line, AssetRuntime runtime) {
        return switch (line.trigger()) {
            case RUNNING_HOURS -> runtime.hoursSinceLastPm() >= line.intervalHours();
            case PUMPED_VOLUME -> runtime.volumeSinceLastPmM3() >= line.intervalVolumeM3();
            case TIME -> false;
        };
    }

    /**
     * Classifica a linha numa data: DUE_FOR_GENERATION quando o
     * vencimento esta dentro da antecedencia de geracao do tenant
     * (lead = 0 sem parametro: so no vencimento); OVERDUE quando
     * passa do vencimento + tolerancia do tenant (0 sem parametro).
     * Uso nao tem data: devido = DUE_FOR_GENERATION imediato.
     */
    public static PmStatus statusAt(PmPlanLine line, AssetRuntime runtime, LocalDate inOperationSince,
                                    LocalDate referenceDate, int generationLeadDays, int overdueToleranceDays) {
        if (generationLeadDays < 0 || overdueToleranceDays < 0)
            throw new IllegalArgumentException("lead/tolerancia negativos (parametros maint.*)");
        if (line.trigger() != TriggerKind.TIME)
            return dueByUsage(line, runtime) ? PmStatus.DUE_FOR_GENERATION : PmStatus.NOT_DUE;
        Optional<LocalDate> due = dueDateByTime(line, runtime, inOperationSince);
        if (due.isEmpty()) return PmStatus.NOT_DUE;
        if (referenceDate.isAfter(due.get().plusDays(overdueToleranceDays))) return PmStatus.OVERDUE;
        if (!referenceDate.isBefore(due.get().minusDays(generationLeadDays))) return PmStatus.DUE_FOR_GENERATION;
        return PmStatus.NOT_DUE;
    }

    /** Rota de inspecao: grupo de OS preventivas da mesma regiao e janela (agrupamento OPE-20). */
    public record InspectionRoute(String regionCode, LocalDate windowDate, List<UUID> assetIds) {
        public InspectionRoute { assetIds = List.copyOf(assetIds); }
    }

    public static List<InspectionRoute> groupIntoRoutes(List<PmPlanLine> dueLines,
                                                        Map<UUID, String> regionByAsset,
                                                        LocalDate windowDate) {
        Map<String, List<UUID>> byRegion = new java.util.TreeMap<>();
        for (PmPlanLine line : dueLines) {
            String region = regionByAsset.get(line.assetId());
            if (region == null)
                throw new IllegalArgumentException("ativo devido sem regiao para agrupar a rota: " + line.assetId());
            byRegion.computeIfAbsent(region, k -> new java.util.ArrayList<>()).add(line.assetId());
        }
        return byRegion.entrySet().stream()
                .map(e -> new InspectionRoute(e.getKey(), windowDate, e.getValue())).toList();
    }

    /**
     * Checklist de ronda resolvido do parametro
     * {@code maint.checklist_templates} (mapa classe -> itens, do
     * tenant). Sem modelo para a classe: lista vazia — a OS sai sem
     * checklist, nunca com itens inventados.
     */
    public static List<String> checklistFor(String equipmentClassCode,
                                            Map<String, List<String>> templatesByClass) {
        if (templatesByClass == null) return List.of();
        return List.copyOf(templatesByClass.getOrDefault(equipmentClassCode, List.of()));
    }

    // ------------------------------------------------ indicadores

    /**
     * Indicadores de manutencao (OPE-20) sobre contagens do periodo.
     * MTBF = horas de operacao / numero de falhas; MTTR = horas de
     * reparo / numero de reparos concluidos; denominador zero devolve
     * {@code null} (sem base no periodo), nunca numero inventado.
     */
    public record MaintenanceIndicators(long backlogOrders, long overdueOrders,
                                        Double mtbfHours, Double mttrHours) {
    }

    public static MaintenanceIndicators indicators(long backlogOrders, long overdueOrders,
                                                   double operatingHours, long failures,
                                                   double repairHours, long completedRepairs) {
        Double mtbf = failures > 0 ? operatingHours / failures : null;
        Double mttr = completedRepairs > 0 ? repairHours / completedRepairs : null;
        return new MaintenanceIndicators(backlogOrders, overdueOrders, mtbf, mttr);
    }

    /** Gera as OS preventivas devidas pela porta da ED-36 (uma por linha devida). */
    public static List<AssetPorts.CreatedOrder> generateDueOrders(
            AssetPorts.WorkOrderPort orders, UUID tenantId, List<PmPlanLine> lines,
            Map<UUID, AssetRuntime> runtimeByAsset, Map<UUID, LocalDate> inOperationByAsset,
            Map<UUID, String> regionByAsset, LocalDate referenceDate,
            int generationLeadDays, int overdueToleranceDays, UUID requestedBy) {
        Objects.requireNonNull(orders, "porta de OS da ED-36 e obrigatoria");
        List<AssetPorts.CreatedOrder> created = new java.util.ArrayList<>();
        for (PmPlanLine line : lines) {
            AssetRuntime runtime = runtimeByAsset.getOrDefault(line.assetId(),
                    new AssetRuntime(line.assetId(), null, 0, 0));
            PmStatus st = statusAt(line, runtime, inOperationByAsset.get(line.assetId()),
                    referenceDate, generationLeadDays, overdueToleranceDays);
            if (st == PmStatus.NOT_DUE) continue;
            created.add(orders.createOrder(new AssetPorts.OrderRequest(tenantId,
                    AssetPorts.OrderPurpose.PREVENTIVE, "EQUIPMENT", line.assetId(),
                    regionByAsset.get(line.assetId()),
                    referenceDate.atStartOfDay(), "PM:" + line.equipmentClassCode(), requestedBy)));
        }
        return List.copyOf(created);
    }

    /** Instante de referencia para servicos que trabalham com data/hora (baixas). */
    public static LocalDateTime startOf(LocalDate date) { return date.atStartOfDay(); }
}

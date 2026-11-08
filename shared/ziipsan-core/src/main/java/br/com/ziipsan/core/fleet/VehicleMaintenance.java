package br.com.ziipsan.core.fleet;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Manutencao de veiculos = EXTENSAO da rotina preventiva/corretiva da
 * ED-56 a veiculos (aceite da ED-59: "manutencao pela rotina
 * preventiva e corretiva da ED-56 estendida a veiculos"). NAO e uma
 * rotina paralela: e o mesmo contrato da ED-56 (OPE-20) aplicado ao
 * perfil do veiculo —
 * <ul>
 *   <li>linha de plano com EXATAMENTE UM gatilho amarrado ao
 *       intervalo (como a correcao D2 da ED-56 amarrou gatilho x
 *       intervalo na {@code PmPlanLine}): TEMPO em meses ou USO em
 *       km de odometro (o "uso" do veiculo; horas/volume sao das
 *       bombas da ED-56);</li>
 *   <li>plano de trabalho (tarefas, materiais, ferramentas) que EXIGE
 *       plano de seguranca, como o {@code WorkPlan} da ED-56;</li>
 *   <li>geracao antecipada e tolerancia pelos MESMOS parametros da
 *       ED-56 — {@code maint.pm_generation_lead_days} e
 *       {@code maint.pm_overdue_tolerance_days} — sem parametro novo
 *       e sem fabrica: sem valor do tenant, lead/tolerancia ZERO
 *       declarados, identico a ED-56;</li>
 *   <li>geracao SO pela porta de OS (finalidades
 *       {@code VEHICLE_PREVENTIVE}/{@code VEHICLE_CORRECTIVE}
 *       mapeadas ao catalogo de tipos do tenant na fiacao, como as
 *       finalidades da ED-56); corretiva pela MESMA porta.</li>
 * </ul>
 * O intervalo (meses ou km) e do PLANO DO TENANT para o veiculo —
 * nenhuma periodicidade de veiculo tem fonte nas dependencias, logo
 * nenhuma e semeada.
 */
public final class VehicleMaintenance {

    private VehicleMaintenance() {
    }

    public enum TriggerKind { TIME, ODOMETER_KM }

    /** Plano de trabalho da linha (mesma exigencia da ED-56: seguranca obrigatoria). */
    public record WorkPlan(List<String> tasks, List<String> materials, List<String> tools, String safetyPlan) {
        public WorkPlan {
            tasks = tasks == null ? List.of() : List.copyOf(tasks);
            materials = materials == null ? List.of() : List.copyOf(materials);
            tools = tools == null ? List.of() : List.copyOf(tools);
            if (tasks.isEmpty()) throw new IllegalArgumentException("plano de trabalho sem tarefas");
            if (safetyPlan == null || safetyPlan.isBlank())
                throw new IllegalArgumentException("plano de trabalho sem plano de seguranca (rotina da ED-56/OPE-20)");
        }
    }

    /** Linha do plano preventivo de um veiculo: gatilho e intervalo AMARRADOS. */
    public record VehiclePmLine(UUID vehicleAssetId, TriggerKind trigger, Integer intervalMonths,
                                Long intervalKm, WorkPlan workPlan, String checklistTemplateKey) {
        public VehiclePmLine {
            Objects.requireNonNull(vehicleAssetId, "linha de PM sem veiculo");
            Objects.requireNonNull(trigger, "linha de PM sem gatilho");
            int set = (intervalMonths != null ? 1 : 0) + (intervalKm != null ? 1 : 0);
            if (set != 1)
                throw new IllegalArgumentException("linha de PM de veiculo exige exatamente um intervalo (tempo OU km)");
            boolean matches = switch (trigger) {
                case TIME -> intervalMonths != null;
                case ODOMETER_KM -> intervalKm != null;
            };
            if (!matches)
                throw new IllegalArgumentException("intervalo nao corresponde ao gatilho " + trigger
                        + " (TIME exige meses, ODOMETER_KM exige km)");
            if (intervalMonths != null && intervalMonths <= 0)
                throw new IllegalArgumentException("intervalo em meses deve ser positivo");
            if (intervalKm != null && intervalKm <= 0)
                throw new IllegalArgumentException("intervalo em km deve ser positivo");
            Objects.requireNonNull(workPlan, "linha de PM sem plano de trabalho");
        }
    }

    /** Estado do veiculo frente ao plano (ultima PM e odometro desde a ultima PM). */
    public record VehicleRuntime(UUID vehicleAssetId, LocalDate lastPmOn, long kmSinceLastPm) {
    }

    public enum PmStatus { NOT_DUE, DUE_FOR_GENERATION, OVERDUE }

    public static Optional<LocalDate> dueDateByTime(VehiclePmLine line, VehicleRuntime runtime,
                                                    LocalDate inOperationSince) {
        if (line.trigger() != TriggerKind.TIME) return Optional.empty();
        LocalDate base = runtime.lastPmOn() != null ? runtime.lastPmOn() : inOperationSince;
        if (base == null) return Optional.empty();
        return Optional.of(base.plusMonths(line.intervalMonths()));
    }

    public static boolean dueByUsage(VehiclePmLine line, VehicleRuntime runtime) {
        return line.trigger() == TriggerKind.ODOMETER_KM && runtime.kmSinceLastPm() >= line.intervalKm();
    }

    /**
     * Classificacao identica a da ED-56: DUE_FOR_GENERATION dentro da
     * antecedencia do tenant (0 sem parametro), OVERDUE alem do
     * vencimento + tolerancia do tenant (0 sem parametro); uso devido
     * = geracao imediata.
     */
    public static PmStatus statusAt(VehiclePmLine line, VehicleRuntime runtime, LocalDate inOperationSince,
                                    LocalDate referenceDate, int generationLeadDays, int overdueToleranceDays) {
        if (generationLeadDays < 0 || overdueToleranceDays < 0)
            throw new IllegalArgumentException("lead/tolerancia negativos (parametros maint.* da ED-56)");
        if (line.trigger() != TriggerKind.TIME)
            return dueByUsage(line, runtime) ? PmStatus.DUE_FOR_GENERATION : PmStatus.NOT_DUE;
        Optional<LocalDate> due = dueDateByTime(line, runtime, inOperationSince);
        if (due.isEmpty()) return PmStatus.NOT_DUE;
        if (referenceDate.isAfter(due.get().plusDays(overdueToleranceDays))) return PmStatus.OVERDUE;
        if (!referenceDate.isBefore(due.get().minusDays(generationLeadDays))) return PmStatus.DUE_FOR_GENERATION;
        return PmStatus.NOT_DUE;
    }

    /** Gera a OS preventiva quando devida — so pela porta (rotina da ED-56). */
    public static Optional<FleetPorts.CreatedOrder> generatePreventive(
            FleetPorts.WorkOrderPort port, UUID tenantId, VehiclePmLine line, VehicleRuntime runtime,
            LocalDate inOperationSince, LocalDate referenceDate, int generationLeadDays, int overdueToleranceDays) {
        PmStatus status = statusAt(line, runtime, inOperationSince, referenceDate,
                generationLeadDays, overdueToleranceDays);
        if (status == PmStatus.NOT_DUE) return Optional.empty();
        return Optional.of(port.createOrder(new FleetPorts.VehicleOrderRequest(tenantId,
                FleetPorts.VehicleOrderPurpose.VEHICLE_PREVENTIVE, line.vehicleAssetId(),
                "preventiva de veiculo (rotina da ED-56 estendida, OPE-20)")));
    }

    /** Corretiva de veiculo: MESMA porta, finalidade corretiva (sem nucleo paralelo). */
    public static FleetPorts.CreatedOrder openCorrective(
            FleetPorts.WorkOrderPort port, UUID tenantId, UUID vehicleAssetId, String defectDescription) {
        if (defectDescription == null || defectDescription.isBlank())
            throw new IllegalArgumentException("corretiva de veiculo sem descricao do defeito");
        return port.createOrder(new FleetPorts.VehicleOrderRequest(tenantId,
                FleetPorts.VehicleOrderPurpose.VEHICLE_CORRECTIVE, vehicleAssetId, defectDescription));
    }
}

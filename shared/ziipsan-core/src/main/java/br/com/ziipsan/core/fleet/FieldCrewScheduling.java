package br.com.ziipsan.core.fleet;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Equipes de campo (composicao, veiculo, escala), portao de alocacao
 * e a restricao de equipamento especial no motor de programacao da
 * ED-36 (aceite da ED-59; OPE-28 e OPE-33 consumido da ED-36).
 *
 * <p><b>Testes do aceite, executaveis aqui:</b>
 * <ol>
 *   <li>OS que exige equipamento especial NAO e programada sem o
 *       recurso disponivel — {@link #equipmentAvailable} devolve o
 *       booleano que alimenta o {@code Candidate.equipmentAvailable}
 *       da ED-36, filtrado pela restricao dura
 *       {@code EQUIPMENT_WHEN_ED59} de {@code sched.hard_constraints}
 *       (a ED-36 ja traz o filtro; esta tarefa entrega o FATO); e
 *       {@link #evaluate} recusa a alocacao sem o equipamento;</li>
 *   <li>veiculo com documento VENCIDO nao e alocado — recusa dura em
 *       {@link #evaluate}, sem parametro que a desligue (o
 *       {@code fleet.docs_alert_days} so antecipa o ALERTA, nunca
 *       autoriza alocar vencido). O mesmo vale para a habilitacao
 *       vencida do motorista designado.</li>
 * </ol>
 *
 * <p>Checklist pre-viagem ({@code fleet.checklist_required}): quando
 * o tenant exige, a saida exige um checklist CONCLUIDO da alocacao
 * com TODOS os itens de {@code fleet.checklist_items} marcados OK;
 * item reprovado (NOT_OK) tambem bloqueia. Sem a exigencia do tenant,
 * o checklist nao bloqueia (declarado em {@link FleetParameters}).
 *
 * <p>A habilitacao e a seguranca do trabalho do executor seguem a OS
 * da P1.31 (OPE-14) e o portao de habilitacao da ED-36
 * ({@code CrewAssignment}) — nao sao reimplementados aqui.
 */
public final class FieldCrewScheduling {

    private FieldCrewScheduling() {
    }

    /**
     * Equipe de campo: composicao (membros), motorista designado e
     * veiculo padrao. A escala e a alocacao datada
     * ({@link CrewAssignment}).
     */
    public record FieldCrew(UUID crewId, String name, List<UUID> memberPersonIds, UUID driverPersonId) {
        public FieldCrew {
            Objects.requireNonNull(crewId, "equipe sem id");
            if (name == null || name.isBlank()) throw new IllegalArgumentException("equipe sem nome");
            memberPersonIds = memberPersonIds == null ? List.of() : List.copyOf(memberPersonIds);
            if (memberPersonIds.isEmpty()) throw new IllegalArgumentException("equipe sem composicao (membros)");
            Objects.requireNonNull(driverPersonId, "equipe sem motorista designado");
            if (!memberPersonIds.contains(driverPersonId))
                throw new IllegalArgumentException("motorista designado nao esta na composicao da equipe");
        }
    }

    /** Alocacao datada (escala): equipe + veiculo numa janela para uma OS. */
    public record CrewAllocation(UUID allocationId, UUID crewId, UUID vehicleAssetId, UUID workOrderId,
                                 LocalDateTime windowStart, LocalDateTime windowEnd) {
        public CrewAllocation {
            Objects.requireNonNull(allocationId, "alocacao sem id");
            Objects.requireNonNull(crewId, "alocacao sem equipe");
            Objects.requireNonNull(vehicleAssetId, "alocacao sem veiculo");
            Objects.requireNonNull(workOrderId, "alocacao sem OS");
            Objects.requireNonNull(windowStart, "alocacao sem inicio da janela");
            Objects.requireNonNull(windowEnd, "alocacao sem fim da janela");
            if (!windowEnd.isAfter(windowStart))
                throw new IllegalArgumentException("janela da alocacao invalida (fim deve ser apos o inicio)");
        }

        public boolean overlaps(LocalDateTime start, LocalDateTime end) {
            return windowStart.isBefore(end) && start.isBefore(windowEnd);
        }
    }

    public enum ChecklistItemResult { OK, NOT_OK }

    public record ChecklistEntry(String itemCode, ChecklistItemResult result) {
    }

    /** Checklist pre-viagem concluido de uma alocacao. */
    public record PreTripChecklist(UUID allocationId, List<ChecklistEntry> entries,
                                   UUID completedBy, LocalDateTime completedAt) {
        public PreTripChecklist {
            Objects.requireNonNull(allocationId, "checklist sem alocacao");
            entries = entries == null ? List.of() : List.copyOf(entries);
            Objects.requireNonNull(completedBy, "checklist sem responsavel pela conclusao");
            Objects.requireNonNull(completedAt, "checklist sem instante de conclusao");
        }

        /** Completo = cobre TODOS os itens exigidos pelo tenant e todos OK. */
        public boolean completeAgainst(List<String> requiredItems) {
            for (String item : requiredItems) {
                Optional<ChecklistEntry> e = entries.stream().filter(x -> x.itemCode().equals(item)).findFirst();
                if (e.isEmpty() || e.get().result() != ChecklistItemResult.OK) return false;
            }
            return true;
        }
    }

    public record AllocationDecision(boolean allowed, List<String> refusalReasons) {
        public AllocationDecision {
            refusalReasons = refusalReasons == null ? List.of() : List.copyOf(refusalReasons);
            if (allowed && !refusalReasons.isEmpty())
                throw new IllegalArgumentException("decisao permitida nao pode ter motivos de recusa");
            if (!allowed && refusalReasons.isEmpty())
                throw new IllegalArgumentException("decisao recusada exige ao menos um motivo (decisao explicavel)");
        }

        static AllocationDecision allow() {
            return new AllocationDecision(true, List.of());
        }

        static AllocationDecision refuse(List<String> reasons) {
            return new AllocationDecision(false, reasons);
        }
    }

    /**
     * Portao de alocacao: avalia equipe+veiculo+motorista para uma OS
     * numa janela. TODAS as recusas sao acumuladas e nomeadas
     * (decisao explicavel, como a sugestao da ED-36).
     *
     * @param requiredEquipment equipamentos especiais exigidos pelo
     *                          TIPO DE SERVICO da OS (catalogo do
     *                          tenant na P1.31; vazio = sem exigencia)
     */
    public static AllocationDecision evaluate(FieldCrew crew, FleetRegistry.Vehicle vehicle,
                                              DriverDocuments.DriverLicense driverLicense,
                                              Set<FleetRegistry.SpecialEquipment> requiredEquipment,
                                              LocalDate serviceDate, LocalDateTime windowStart, LocalDateTime windowEnd,
                                              List<CrewAllocation> existingAllocations,
                                              boolean checklistRequired, List<String> checklistItems,
                                              PreTripChecklist checklistOrNull) {
        Objects.requireNonNull(crew, "equipe e obrigatoria");
        Objects.requireNonNull(vehicle, "veiculo e obrigatorio");
        Objects.requireNonNull(serviceDate, "data do servico e obrigatoria");
        List<String> reasons = new ArrayList<>();
        if (vehicle.status() != FleetRegistry.VehicleStatus.ACTIVE)
            reasons.add("veiculo nao esta ATIVO (situacao " + vehicle.status() + ")");
        // TESTE DO ACEITE (negativo obrigatorio): documento vencido NAO aloca.
        if (vehicle.licensingExpiredOn(serviceDate))
            reasons.add("licenciamento do veiculo VENCIDO em " + vehicle.licensingValidUntil()
                    + " — alocacao proibida (aceite da ED-59)");
        if (driverLicense == null) {
            reasons.add("motorista designado sem habilitacao registrada (ED-59)");
        } else {
            if (!driverLicense.personId().equals(crew.driverPersonId()))
                reasons.add("habilitacao apresentada nao e a do motorista designado da equipe");
            else if (driverLicense.expiredOn(serviceDate))
                reasons.add("habilitacao do motorista VENCIDA em " + driverLicense.validUntil()
                        + " — alocacao proibida (aceite da ED-59)");
        }
        // TESTE DO ACEITE: equipamento especial exigido e recurso obrigatorio.
        Set<FleetRegistry.SpecialEquipment> req = requiredEquipment == null ? Set.of() : requiredEquipment;
        if (!vehicle.hasAll(req)) {
            Set<FleetRegistry.SpecialEquipment> missing = new java.util.TreeSet<>(req);
            missing.removeAll(vehicle.specialEquipment());
            reasons.add("veiculo nao porta o equipamento especial exigido pelo tipo de servico: " + missing
                    + " (restricao dura EQUIPMENT_WHEN_ED59 da ED-36/OPE-33)");
        }
        for (CrewAllocation a : existingAllocations == null ? List.<CrewAllocation>of() : existingAllocations) {
            if (a.vehicleAssetId().equals(vehicle.assetId()) && a.overlaps(windowStart, windowEnd))
                reasons.add("veiculo ja alocado na janela (alocacao " + a.allocationId() + ", OS " + a.workOrderId() + ")");
            if (a.crewId().equals(crew.crewId()) && a.overlaps(windowStart, windowEnd))
                reasons.add("equipe ja alocada na janela (alocacao " + a.allocationId() + ", OS " + a.workOrderId() + ")");
        }
        if (checklistRequired) {
            if (checklistItems == null || checklistItems.isEmpty())
                reasons.add("checklist pre-viagem exigido (" + FleetParameters.CHECKLIST_REQUIRED
                        + ") sem itens definidos em " + FleetParameters.CHECKLIST_ITEMS
                        + " — exigencia nao pode ser cumprida (falha fechada declarada)");
            else if (checklistOrNull == null)
                reasons.add("checklist pre-viagem exigido (" + FleetParameters.CHECKLIST_REQUIRED
                        + ") nao concluido para a alocacao");
            else if (!checklistOrNull.completeAgainst(checklistItems))
                reasons.add("checklist pre-viagem incompleto ou com item reprovado frente a "
                        + FleetParameters.CHECKLIST_ITEMS);
        }
        return reasons.isEmpty() ? AllocationDecision.allow() : AllocationDecision.refuse(reasons);
    }

    /**
     * FATO para o motor da ED-36: existe veiculo ATIVO, com
     * licenciamento valido na data e portando TODO o equipamento
     * exigido, livre na janela? Alimenta o
     * {@code Candidate.equipmentAvailable} filtrado pela restricao
     * dura {@code EQUIPMENT_WHEN_ED59}. Sem exigencia de equipamento,
     * devolve {@code true} (a restricao nao se aplica).
     */
    public static boolean equipmentAvailable(Set<FleetRegistry.SpecialEquipment> requiredEquipment,
                                             List<FleetRegistry.Vehicle> vehicles, LocalDate serviceDate,
                                             LocalDateTime windowStart, LocalDateTime windowEnd,
                                             List<CrewAllocation> existingAllocations) {
        Set<FleetRegistry.SpecialEquipment> req = requiredEquipment == null ? Set.of() : requiredEquipment;
        if (req.isEmpty()) return true;
        List<CrewAllocation> allocs = existingAllocations == null ? List.of() : existingAllocations;
        return vehicles.stream()
                .filter(v -> v.status() == FleetRegistry.VehicleStatus.ACTIVE)
                .filter(v -> !v.licensingExpiredOn(serviceDate))
                .filter(v -> v.hasAll(req))
                .anyMatch(v -> allocs.stream().noneMatch(a -> a.vehicleAssetId().equals(v.assetId())
                        && a.overlaps(windowStart, windowEnd)));
    }
}

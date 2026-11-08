package br.com.ziipsan.core.stationops;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Diario de operacao por turno (ED-87/OPE-02) de UMA unidade
 * (ETA, ETE, elevatoria ou reservatorio): equipamentos em operacao e
 * em reserva (com horimetro e partidas), leituras de nivel, vazao,
 * pressao e processo, ocorrencias e a passagem de turno.
 *
 * <p>Maquina de estados: {@code OPEN -> HANDED_OVER}. A passagem de
 * turno e o fechamento do diario: exige o operador que recebe, o
 * instante e as pendencias herdadas (lista pode ser vazia, mas a
 * passagem precisa ser declarada). Diario passado nao aceita mais
 * lancamento — correcao e ato da integracao com trilha, nunca edicao
 * silenciosa do turno anterior.</p>
 */
public final class ShiftLog {

    public enum Status { OPEN, HANDED_OVER }

    /** Estado de um equipamento no turno, com horimetro e partidas quando o equipamento os tem. */
    public record EquipmentState(String equipmentCode, EquipmentSituation situation,
                                 BigDecimal horimeter, Integer starts) {
        public EquipmentState {
            if (equipmentCode == null || equipmentCode.isBlank())
                throw new IllegalArgumentException("equipmentCode e obrigatorio");
            if (situation == null) throw new IllegalArgumentException("situacao do equipamento "
                    + equipmentCode + " e obrigatoria");
            if (horimeter != null && horimeter.signum() < 0)
                throw new IllegalArgumentException("horimetro nao pode ser negativo (" + equipmentCode + ")");
            if (starts != null && starts < 0)
                throw new IllegalArgumentException("partidas nao podem ser negativas (" + equipmentCode + ")");
        }
    }

    /** Em operacao e em reserva sao os estados do aceite; fora de servico e o terceiro estado operacional. */
    public enum EquipmentSituation { OPERATING, STANDBY_RESERVE, OUT_OF_SERVICE }

    /** Ocorrencia registrada no diario; vira OS pelo nucleo da P1.31 (COE-10) via {@link StationOpsService}. */
    public record DiaryOccurrence(UUID id, String description, Instant occurredAt, String reportedBy,
                                  UUID workOrderId) {
        public DiaryOccurrence {
            if (id == null) throw new IllegalArgumentException("id da ocorrencia e obrigatorio");
            if (description == null || description.isBlank())
                throw new IllegalArgumentException("descricao da ocorrencia e obrigatoria");
            if (occurredAt == null) throw new IllegalArgumentException("instante da ocorrencia e obrigatorio");
            if (reportedBy == null || reportedBy.isBlank())
                throw new IllegalArgumentException("quem registrou a ocorrencia e obrigatorio");
        }
        DiaryOccurrence withWorkOrder(UUID orderId) {
            return new DiaryOccurrence(id, description, occurredAt, reportedBy, orderId);
        }
    }

    /** Passagem de turno registrada: quem entrega, quem recebe, quando e o que fica pendente. */
    public record Handover(String outgoingOperatorRef, String incomingOperatorRef, Instant at,
                           List<String> pendingItems, String notes) {
        public Handover {
            if (outgoingOperatorRef == null || outgoingOperatorRef.isBlank())
                throw new IllegalArgumentException("operador que entrega o turno e obrigatorio");
            if (incomingOperatorRef == null || incomingOperatorRef.isBlank())
                throw new IllegalArgumentException("operador que recebe o turno e obrigatorio");
            if (at == null) throw new IllegalArgumentException("instante da passagem e obrigatorio");
            pendingItems = pendingItems == null ? List.of() : List.copyOf(pendingItems);
        }
    }

    private final UUID id;
    private final UUID tenantId;
    private final StationUnit unit;
    private final LocalDate shiftDate;
    private final String shiftCode;
    private final String openedByRef;
    private final Instant openedAt;
    private Status status = Status.OPEN;
    private final List<EquipmentState> equipment = new ArrayList<>();
    private final List<DiaryReading> readings = new ArrayList<>();
    private final List<DiaryOccurrence> occurrences = new ArrayList<>();
    private Handover handover;

    public ShiftLog(UUID id, StationUnit unit, LocalDate shiftDate, String shiftCode,
                    String openedByRef, Instant openedAt) {
        if (id == null) throw new IllegalArgumentException("id do diario e obrigatorio");
        if (unit == null) throw new IllegalArgumentException("unidade do diario e obrigatoria");
        if (shiftDate == null) throw new IllegalArgumentException("data do turno e obrigatoria");
        if (shiftCode == null || shiftCode.isBlank())
            throw new IllegalArgumentException("codigo do turno e obrigatorio");
        if (openedByRef == null || openedByRef.isBlank())
            throw new IllegalArgumentException("operador que abre o diario e obrigatorio");
        if (openedAt == null) throw new IllegalArgumentException("instante de abertura e obrigatorio");
        this.id = id;
        this.tenantId = unit.tenantId();
        this.unit = unit;
        this.shiftDate = shiftDate;
        this.shiftCode = shiftCode;
        this.openedByRef = openedByRef;
        this.openedAt = openedAt;
    }

    private void requireOpen(String action) {
        if (status != Status.OPEN) throw new IllegalStateException(
                "diario " + id + " ja teve a passagem de turno: " + action + " recusado");
    }

    /** Registra o estado de um equipamento; o mesmo equipamento nao se repete no turno. */
    public void setEquipment(EquipmentState state) {
        requireOpen("registro de equipamento");
        if (state == null) throw new IllegalArgumentException("estado do equipamento e obrigatorio");
        if (equipment.stream().anyMatch(e -> e.equipmentCode().equals(state.equipmentCode())))
            throw new IllegalArgumentException("equipamento " + state.equipmentCode()
                    + " ja registrado neste turno (o estado do turno e um retrato, nao um historico)");
        equipment.add(state);
    }

    /** Lanca uma leitura; turbidez so em filtro declarado da unidade. */
    public void addReading(DiaryReading reading) {
        requireOpen("lancamento de leitura");
        if (reading == null) throw new IllegalArgumentException("leitura e obrigatoria");
        if (reading.kind() == DiaryReading.Kind.TURBIDITY
                && !unit.filterUnitCodes().contains(reading.filterUnitCode()))
            throw new IllegalArgumentException("unidade filtrante " + reading.filterUnitCode()
                    + " nao pertence a unidade " + unit.code());
        readings.add(reading);
    }

    /**
     * Remove o lancamento identificado pela chave da serie (correcao
     * ED-87/D1): usado SOMENTE pelo {@link StationOpsService} como
     * rollback quando a porta da serie (ED-17) falha depois do
     * lancamento — o diario nao fica com leitura sem serie e o retry
     * relanca as duas pontas uma unica vez. Nao e edicao de diario:
     * o lancamento removido nunca chegou a existir nas duas pontas.
     */
    void removeReadingBySeriesKey(String stationCode, String seriesSampleKey) {
        readings.removeIf(r -> seriesSampleKey.equals(r.seriesSampleKey(stationCode)));
    }

    /** Registra uma ocorrencia (a abertura da OS e do servico, uma unica vez por ocorrencia). */
    public DiaryOccurrence recordOccurrence(String description, Instant occurredAt, String reportedBy) {
        requireOpen("registro de ocorrencia");
        DiaryOccurrence occurrence = new DiaryOccurrence(UUID.randomUUID(), description, occurredAt,
                reportedBy, null);
        occurrences.add(occurrence);
        return occurrence;
    }

    /** Marca a OS aberta para a ocorrencia (chamado so pelo servico, apos a porta da P1.31 responder). */
    void attachWorkOrder(UUID occurrenceId, UUID workOrderId) {
        if (workOrderId == null) throw new IllegalArgumentException("workOrderId e obrigatorio");
        for (int i = 0; i < occurrences.size(); i++) {
            DiaryOccurrence o = occurrences.get(i);
            if (o.id().equals(occurrenceId)) {
                if (o.workOrderId() != null) throw new IllegalStateException(
                        "ocorrencia " + occurrenceId + " ja tem a OS " + o.workOrderId()
                                + ": ocorrencia do diario vira UMA OS (COE-10)");
                occurrences.set(i, o.withWorkOrder(workOrderId));
                return;
            }
        }
        throw new IllegalArgumentException("ocorrencia inexistente neste diario: " + occurrenceId);
    }

    /** Passagem de turno: fecha o diario com quem recebe e o que fica pendente. */
    public void handover(Handover handover) {
        requireOpen("passagem de turno");
        if (handover == null) throw new IllegalArgumentException("passagem de turno e obrigatoria");
        if (handover.at().isBefore(openedAt))
            throw new IllegalArgumentException("passagem de turno anterior a abertura do diario");
        this.handover = handover;
        this.status = Status.HANDED_OVER;
    }

    /** Leituras de controle de processo (turbidez/cloro/pH) — a fonte do JOR-21. */
    public List<DiaryReading> processReadings() {
        return readings.stream().filter(DiaryReading::isProcessControl).toList();
    }

    /** Leituras de turbidez de um filtro, em ordem cronologica. */
    public List<DiaryReading> turbidityOf(String filterUnitCode) {
        return readings.stream()
                .filter(r -> r.kind() == DiaryReading.Kind.TURBIDITY
                        && r.filterUnitCode().equals(filterUnitCode))
                .sorted(java.util.Comparator.comparing(DiaryReading::measuredAt))
                .toList();
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public StationUnit unit() { return unit; }
    public LocalDate shiftDate() { return shiftDate; }
    public String shiftCode() { return shiftCode; }
    public String openedByRef() { return openedByRef; }
    public Instant openedAt() { return openedAt; }
    public Status status() { return status; }
    public List<EquipmentState> equipment() { return List.copyOf(equipment); }
    public List<DiaryReading> readings() { return List.copyOf(readings); }
    public List<DiaryOccurrence> occurrences() { return List.copyOf(occurrences); }
    public Handover handover() { return handover; }
    public boolean isHandedOver() { return status == Status.HANDED_OVER; }
}

package br.com.ziipsan.commercial.billing.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Item do cronograma mensal de um grupo (V4_3, P2.5/F1): a instancia de uma atividade
 * do catalogo para um grupo numa referencia, com data prevista ({@code plannedOn}),
 * comandada ({@code commandedAt}/{@code commandedBy}) e realizada ({@code doneAt}).
 * {@code status} guarda um valor de {@code ScheduleItemStatus} e acompanha as datas
 * (CHECK no banco). {@code occurrenceNo} comeca em 1: atividade repetivel comandada
 * de novo depois de realizada vira a ocorrencia seguinte, sem apagar a anterior.
 * {@code jobRunId} e a execucao da fundacao de lote da P2.1 despachada pelo comando
 * deste item, quando o comando despacha lote (faturamento); sem FK, como
 * {@code bill_audit_batches.run_id} (V4_6).
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "cycle_schedule_items")
@Getter
@Setter
public class CycleScheduleItemEntity extends TenantAwareEntity {

    @Column(name = "reference_id", nullable = false)
    private UUID referenceId;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "activity_id", nullable = false)
    private UUID activityId;

    @Column(name = "occurrence_no", nullable = false)
    private short occurrenceNo = 1;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "planned_on", nullable = false)
    private LocalDate plannedOn;

    @Column(name = "commanded_at")
    private OffsetDateTime commandedAt;

    @Column(name = "commanded_by")
    private UUID commandedBy;

    @Column(name = "done_at")
    private OffsetDateTime doneAt;

    @Column(name = "job_run_id")
    private UUID jobRunId;
}

package br.com.ziipsan.operations.metering.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import br.com.ziipsan.operations.metering.rules.MeasurementKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Tarefa de leitura terceirizada (P2.7, V5_3): uma unidade a ler, numa rota e
 * numa referencia do ciclo, entregue a um fornecedor. A ligacao com o ciclo
 * da V4_3 (billing) e por identificadores -- {@code groupId},
 * {@code billingReferenceId} e {@code cycleScheduleItemId} (o item do
 * cronograma da atividade de leitura do grupo) -- mais {@code routeCode}, o
 * identificador textual da rota de {@code billing_groups}: os modulos nao se
 * dependem, entao nao ha FK para as tabelas do billing (mesmo formato de
 * {@code job_run_id} na V4_3).
 *
 * <p>{@code referenceMonth} e a competencia, sempre o dia 1; use
 * {@link #setReferenceMonth}. {@code readingId}/{@code readingReference}
 * guardam a leitura que a volta do arquivo gerou, sem FK (a tabela de
 * leituras e particionada pela V5_4; ver o cabecalho da V5_3).
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "reading_tasks")
@Getter
@Setter
public class ReadingTask extends TenantAwareEntity {

    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;

    @Column(name = "route_code", nullable = false, length = 40)
    private String routeCode;

    @Column(name = "reference_month", nullable = false)
    private LocalDate referenceMonth;

    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "billing_reference_id")
    private UUID billingReferenceId;

    @Column(name = "cycle_schedule_item_id")
    private UUID cycleScheduleItemId;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "installation_id", nullable = false)
    private UUID installationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 5)
    private MeasurementKind kind;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(name = "previous_reading")
    private Integer previousReading;

    @Column(name = "planned_on")
    private LocalDate plannedOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private Status status = Status.PENDING;

    @Column(name = "exported_at")
    private OffsetDateTime exportedAt;

    @Column(name = "reading_id")
    private UUID readingId;

    @Column(name = "reading_reference")
    private LocalDate readingReference;

    /** Fluxo da tarefa: criada, exportada ao fornecedor, cumprida com ou sem leitura, ou cancelada. */
    public enum Status { PENDING, EXPORTED, READ, NOT_READ, CANCELLED }

    public void setReferenceMonth(YearMonth month) {
        this.referenceMonth = month.atDay(1);
    }

    public YearMonth referenceMonth() {
        return YearMonth.from(referenceMonth);
    }
}

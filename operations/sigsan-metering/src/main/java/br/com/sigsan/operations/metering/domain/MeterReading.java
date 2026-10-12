package br.com.sigsan.operations.metering.domain;

import br.com.sigsan.core.domain.TenantAwareEntity;
import br.com.sigsan.operations.metering.rules.MeasurementKind;
import br.com.sigsan.operations.metering.rules.ReadingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Leitura aceita (a que passou na validação do retorno). Leitura e consumo faturado são tabelas
 * separadas: o consumo pode diferir da leitura (média, ajuste por dias, anormalidade).
 *
 * <p>{@code reference} é a competência, sempre o dia 1 do mês; use {@link #setReferenceMonth}.
 *
 * <p>Não usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "meter_readings")
@Getter
@Setter
public class MeterReading extends TenantAwareEntity {

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "installation_id", nullable = false)
    private UUID installationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 5)
    private MeasurementKind kind;

    @Column(name = "reference", nullable = false)
    private LocalDate reference;

    @Column(name = "read_on", nullable = false)
    private LocalDate readOn;

    /** Leitura do mostrador; nula quando a linha só registra uma anormalidade. */
    @Column(name = "reading")
    private Integer reading;

    @Enumerated(EnumType.STRING)
    @Column(name = "reading_status", nullable = false, length = 15)
    private ReadingStatus readingStatus;

    @Column(name = "anomaly_id")
    private UUID anomalyId;

    @Column(name = "informed_consumption")
    private Integer informedConsumption;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 15)
    private Source source;

    @Column(name = "reader_id")
    private UUID readerId;

    @Column(name = "captured_at", nullable = false)
    private OffsetDateTime capturedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_warnings", columnDefinition = "jsonb")
    private String validationWarnings;

    public enum Source { MANUAL, MOBILE, COLLECTOR, TELEMETRY }

    public void setReferenceMonth(YearMonth month) {
        this.reference = month.atDay(1);
    }

    public YearMonth referenceMonth() {
        return YearMonth.from(reference);
    }

}

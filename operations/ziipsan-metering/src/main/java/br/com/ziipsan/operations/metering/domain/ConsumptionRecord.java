package br.com.ziipsan.operations.metering.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.operations.metering.rules.AverageResult;
import br.com.ziipsan.operations.metering.rules.ConsumptionResult;
import br.com.ziipsan.operations.metering.rules.ConsumptionType;
import br.com.ziipsan.operations.metering.rules.SewageVolumeCalculator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Consumo faturado de uma unidade, serviço (água ou esgoto) e referência, com o <b>tipo</b> e a
 * <b>anormalidade</b> que explicam como se chegou nele. Uma linha por (unidade, serviço, referência):
 * refaturar atualiza, nunca duplica; a história das versões fica no {@code audit_log}.
 *
 * <p>{@code calculatedConsumption} é a base da média futura: não é zerado quando o faturamento é
 * paralisado ou o tipo não permite faturar ({@code billedConsumption} é).
 *
 * <p>Não usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "consumption_records")
@Getter
@Setter
public class ConsumptionRecord extends TenantAwareEntity {

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_kind", nullable = false, length = 5)
    private ServiceKind serviceKind;

    /** Competência: sempre o dia 1 do mês. */
    @Column(name = "reference", nullable = false)
    private LocalDate reference;

    @Column(name = "billed_consumption", nullable = false)
    private int billedConsumption;

    @Column(name = "calculated_consumption", nullable = false)
    private int calculatedConsumption;

    @Column(name = "billable", nullable = false)
    private boolean billable;

    @Column(name = "billing_paused", nullable = false)
    private boolean billingPaused;

    @Enumerated(EnumType.STRING)
    @Column(name = "consumption_type", nullable = false, length = 30)
    private ConsumptionType consumptionType;

    @Column(name = "anomaly_id")
    private UUID anomalyId;

    @Column(name = "billed_reading_anomaly_id")
    private UUID billedReadingAnomalyId;

    @Column(name = "reading_id")
    private UUID readingId;

    @Column(name = "billed_reading")
    private Integer billedReading;

    @Column(name = "billed_reading_on")
    private LocalDate billedReadingOn;

    @Column(name = "average_consumption")
    private Integer averageConsumption;

    @Column(name = "average_months")
    private Integer averageMonths;

    @Enumerated(EnumType.STRING)
    @Column(name = "average_source", length = 20)
    private AverageResult.Source averageSource;

    @Column(name = "connection_minimum")
    private Integer connectionMinimum;

    @Column(name = "discarded_remainder", nullable = false)
    private int discardedRemainder;

    @Column(name = "applied_action_month")
    private Integer appliedActionMonth;

    /** Percentual de coleta aplicado ao volume; só no esgoto. */
    @Column(name = "collection_percent", precision = 7, scale = 4)
    private BigDecimal collectionPercent;

    @Column(name = "condominium_macro_record_id")
    private UUID condominiumMacroRecordId;

    @Column(name = "apportioned_consumption")
    private Integer apportionedConsumption;

    /** Avisos da cascata (JSON: lista de {code, message}). Nada é aproximado sem deixar rastro aqui. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "warnings", columnDefinition = "jsonb")
    private String warnings;

    /** Insumos do cálculo, para responder "por que cobrou X" (JSON). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "calculation_snapshot", columnDefinition = "jsonb")
    private String calculationSnapshot;

    public enum ServiceKind { WATER, SEWER }

    public void setReferenceMonth(YearMonth month) {
        this.reference = month.atDay(1);
    }

    public YearMonth referenceMonth() {
        return YearMonth.from(reference);
    }

    /**
     * Preenche o registro de água com o resultado da cascata, <b>inclusive os avisos</b>. A
     * identidade (unidade, referência) e os ids do catálogo (que só quem tem acesso ao banco
     * resolve a partir do tipo da anormalidade) vêm de quem chama.
     */
    public void fillFromWater(ConsumptionResult result, UUID anomalyId, UUID billedReadingAnomalyId) {
        this.serviceKind = ServiceKind.WATER;
        this.billedConsumption = result.billedConsumption();
        this.calculatedConsumption = result.calculatedConsumption();
        this.billable = result.billable();
        this.billingPaused = result.billingPaused();
        this.consumptionType = result.type();
        this.anomalyId = anomalyId;
        this.billedReadingAnomalyId = billedReadingAnomalyId;
        this.billedReading = result.billedReading();
        this.billedReadingOn = result.billedReadingDate();
        this.averageConsumption = result.average();
        this.averageMonths = result.averageMonthsConsidered();
        this.averageSource = result.averageSource();
        this.connectionMinimum = result.connectionMinimum();
        this.discardedRemainder = result.discardedRemainder();
        this.appliedActionMonth = result.appliedActionMonth();
        this.collectionPercent = null;
        this.warnings = Json.write(result.warnings());
    }

    /** Preenche o registro de esgoto com o volume calculado, inclusive os avisos. */
    public void fillFromSewage(SewageVolumeCalculator.Result result, BigDecimal collectionPercent,
                               boolean billable, UUID anomalyId) {
        this.serviceKind = ServiceKind.SEWER;
        this.billedConsumption = billable ? result.volume() : 0;
        this.calculatedConsumption = result.volume();
        this.billable = billable;
        this.billingPaused = false;
        this.consumptionType = result.type();
        this.anomalyId = anomalyId;
        this.billedReadingAnomalyId = null;
        this.billedReading = null;
        this.billedReadingOn = null;
        this.averageConsumption = null;
        this.averageMonths = null;
        this.averageSource = null;
        this.connectionMinimum = null;
        this.discardedRemainder = 0;
        this.appliedActionMonth = null;
        this.collectionPercent = collectionPercent;
        this.warnings = Json.write(result.warnings());
    }

}

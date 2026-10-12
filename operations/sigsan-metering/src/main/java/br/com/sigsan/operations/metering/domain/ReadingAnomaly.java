package br.com.sigsan.operations.metering.domain;

import br.com.sigsan.core.domain.TenantAwareEntity;
import br.com.sigsan.operations.metering.rules.ConsumptionEffect;
import br.com.sigsan.operations.metering.rules.ReadingAnomalyRule;
import br.com.sigsan.operations.metering.rules.ReadingEffect;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Anormalidade de leitura (catálogo do tenant, <b>sem semente</b>): cada prestador define as suas e
 * o efeito de cada uma, com e sem leitura atual. Os quatro efeitos não têm valor padrão.
 *
 * <p>{@code systemRole} liga a linha a um papel que a cascata usa (hidrômetro parado). No legado era
 * um id numérico fixo no código; aqui é um papel simbólico, no máximo uma linha por papel.
 *
 * <p>Não usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "reading_anomalies")
@Getter
@Setter
public class ReadingAnomaly extends TenantAwareEntity {

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "description", nullable = false, length = 200)
    private String description;

    @Column(name = "for_unit_without_meter", nullable = false)
    private boolean forUnitWithoutMeter;

    @Column(name = "requires_reading", nullable = false)
    private boolean requiresReading;

    @Column(name = "generates_service_order", nullable = false)
    private boolean generatesServiceOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "reading_effect_without_reading", nullable = false, length = 30)
    private ReadingEffect readingEffectWithoutReading;

    @Enumerated(EnumType.STRING)
    @Column(name = "reading_effect_with_reading", nullable = false, length = 30)
    private ReadingEffect readingEffectWithReading;

    @Enumerated(EnumType.STRING)
    @Column(name = "consumption_effect_without_reading", nullable = false, length = 30)
    private ConsumptionEffect consumptionEffectWithoutReading;

    @Enumerated(EnumType.STRING)
    @Column(name = "consumption_effect_with_reading", nullable = false, length = 30)
    private ConsumptionEffect consumptionEffectWithReading;

    @Column(name = "factor_without_reading", precision = 7, scale = 4)
    private BigDecimal factorWithoutReading;

    @Column(name = "factor_with_reading", precision = 7, scale = 4)
    private BigDecimal factorWithReading;

    @Column(name = "suspend_reading_months")
    private Integer suspendReadingMonths;

    @Column(name = "suspend_after_occurrences")
    private Integer suspendAfterOccurrences;

    @Column(name = "loses_social_tariff", nullable = false)
    private boolean losesSocialTariff;

    @Enumerated(EnumType.STRING)
    @Column(name = "system_role", length = 30)
    private SystemRole systemRole;

    @Column(name = "active", nullable = false)
    private boolean active;

    /** Papéis que a cascata de consumo reconhece. */
    public enum SystemRole { METER_STOPPED, METER_STOPPED_NO_CONSUMPTION }

    /** A parte da anormalidade que a cascata usa (valida os efeitos ao construir). */
    public ReadingAnomalyRule toRule() {
        return new ReadingAnomalyRule(code, readingEffectWithoutReading, readingEffectWithReading,
                consumptionEffectWithoutReading, consumptionEffectWithReading, factorWithoutReading,
                factorWithReading, forUnitWithoutMeter);
    }

}

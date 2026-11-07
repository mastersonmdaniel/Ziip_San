package br.com.ziipsan.operations.metering.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import br.com.ziipsan.operations.metering.rules.ConsumptionEffect;
import br.com.ziipsan.operations.metering.rules.ReadingAnomalyRule;
import br.com.ziipsan.operations.metering.rules.ReadingEffect;
import br.com.ziipsan.operations.metering.rules.ReadingOccurrence;
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

    // V5_7 (P2.5, JOR-10): tipo da OS gerada, por codigo, no catalogo de tipos de
    // OS da P1.31. Obrigatorio se, e somente se, generatesServiceOrder (a regra
    // pura ReadingOccurrence e o CHECK chk_reading_anomalies_service_order_type
    // da V5_7 recusam os dois desencontros); NULL nas ocorrencias sem OS.
    @Column(name = "service_order_type_code", length = 60)
    private String serviceOrderTypeCode;

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

    /**
     * A ocorrência completa do catálogo (JOR-10): a regra da cascata ({@link #toRule()})
     * mais o que só a linha persistida sabe -- o tipo de OS gerado, a perda de
     * benefício (a tarifa social) e a suspensão da leitura. Linha incoerente
     * (gera OS sem tipo, tipo sem gerar OS) é recusada pela própria
     * {@link ReadingOccurrence}, nunca montada por aproximação.
     */
    public ReadingOccurrence toOccurrence() {
        return new ReadingOccurrence(code, description, toRule(), generatesServiceOrder,
                serviceOrderTypeCode, losesSocialTariff, suspendReadingMonths, suspendAfterOccurrences);
    }

}

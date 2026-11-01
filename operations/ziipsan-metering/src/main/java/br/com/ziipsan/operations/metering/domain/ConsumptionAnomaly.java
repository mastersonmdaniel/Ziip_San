package br.com.ziipsan.operations.metering.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import br.com.ziipsan.operations.metering.rules.ConsumptionAnomalyKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Anormalidade de consumo (catálogo do tenant, <b>sem semente</b>). {@code kind} é a condição que
 * a cascata detecta; a linha dá a ela a descrição, a mensagem na conta e se a anormalidade entra
 * na média. Uma linha por {@code kind} e tenant; o onboarding do tenant as cria.
 *
 * <p>Não usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "consumption_anomalies")
@Getter
@Setter
public class ConsumptionAnomaly extends TenantAwareEntity {

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 30)
    private ConsumptionAnomalyKind kind;

    @Column(name = "description", nullable = false, length = 200)
    private String description;

    @Column(name = "bill_message", length = 500)
    private String billMessage;

    @Column(name = "counts_for_average", nullable = false)
    private boolean countsForAverage;

    @Column(name = "active", nullable = false)
    private boolean active;

}

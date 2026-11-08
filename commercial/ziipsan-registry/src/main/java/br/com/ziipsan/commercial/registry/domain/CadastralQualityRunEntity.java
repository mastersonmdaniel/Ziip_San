package br.com.ziipsan.commercial.registry.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Varredura de qualidade cadastral (P3.32): uma execucao do motor sobre a
 * fotografia do cadastro, na data de referencia. As regras nao executadas
 * (desligadas ou sem limiar) ficam contadas e nomeadas em
 * {@code skippedRulesJson} — varredura parcial nunca se apresenta como
 * completa.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de
 * {@code BaseEntity}.
 */
@Entity
@Table(name = "cadastral_quality_runs")
@Getter
@Setter
public class CadastralQualityRunEntity extends TenantAwareEntity {

    @Column(name = "reference_date", nullable = false)
    private LocalDate referenceDate;

    @Column(name = "units_evaluated", nullable = false)
    private int unitsEvaluated;

    @Column(name = "customers_evaluated", nullable = false)
    private int customersEvaluated;

    @Column(name = "findings_count", nullable = false)
    private int findingsCount;

    /** JSON (classe Json do core) com [{code, reason}] das regras nao executadas. */
    @Column(name = "skipped_rules_json", columnDefinition = "text")
    private String skippedRulesJson;
}

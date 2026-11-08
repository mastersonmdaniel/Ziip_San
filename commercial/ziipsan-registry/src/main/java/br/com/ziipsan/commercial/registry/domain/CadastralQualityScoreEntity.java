package br.com.ziipsan.commercial.registry.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Pontuacao de um grupo (TENANT, ROUTE ou SECTOR) numa varredura (P3.32):
 * a serie que alimenta os relatorios de pontuacao/completude e a tendencia
 * sobre o nucleo da P3.22 (views {@code rpt_cadastral_quality_*}).
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de
 * {@code BaseEntity}.
 */
@Entity
@Table(name = "cadastral_quality_scores")
@Getter
@Setter
public class CadastralQualityScoreEntity extends TenantAwareEntity {

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    /** TENANT, ROUTE ou SECTOR (QualityScoreCalculator). */
    @Column(name = "group_type", nullable = false, length = 10)
    private String groupType;

    /** Rota ou setor do grupo; nulo no grupo TENANT. */
    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "total_units", nullable = false)
    private int totalUnits;

    @Column(name = "units_with_findings", nullable = false)
    private int unitsWithFindings;

    @Column(name = "findings_count", nullable = false)
    private int findingsCount;

    @Column(name = "penalty_points", nullable = false)
    private int penaltyPoints;

    @Column(name = "score", nullable = false, precision = 6, scale = 2)
    private BigDecimal score;

    @Column(name = "completeness_percent", nullable = false, precision = 6, scale = 2)
    private BigDecimal completenessPercent;
}

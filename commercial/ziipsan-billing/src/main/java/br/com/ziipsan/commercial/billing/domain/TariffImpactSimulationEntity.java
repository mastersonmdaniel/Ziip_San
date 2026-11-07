package br.com.ziipsan.commercial.billing.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Registro explicito de uma simulacao de impacto ja calculada (P2.29, V4_9).
 * A simulacao em si ({@code ImpactSimulator}) e pura e nao grava nada: esta
 * entidade so e gravada quando o chamador decide registrar o resultado, em
 * tabela propria que nao e base de faturamento (sem FK para bills).
 * {@code inputSnapshot}/{@code resultSnapshot} sao JSON serializado pela
 * classe {@code Json} do core (massa simulada e relatorio de impacto).
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "tariff_impact_simulations")
@Getter
@Setter
public class TariffImpactSimulationEntity extends TenantAwareEntity {

    @Column(name = "base_version_id", nullable = false)
    private UUID baseVersionId;

    @Column(name = "proposed_valid_from", nullable = false)
    private LocalDate proposedValidFrom;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_snapshot", columnDefinition = "jsonb", nullable = false)
    private String inputSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_snapshot", columnDefinition = "jsonb", nullable = false)
    private String resultSnapshot;
}

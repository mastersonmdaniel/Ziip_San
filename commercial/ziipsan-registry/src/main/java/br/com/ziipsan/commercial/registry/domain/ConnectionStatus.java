package br.com.ziipsan.commercial.registry.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Situação da ligação no catálogo ÚNICO {@code connection_statuses}
 * (GIS-05, P2.23, V3_14): funde {@code water_statuses} (kind WATER) e
 * {@code sewer_statuses} (kind SEWER), preservando os ids, e acrescenta
 * {@code systemRole} (papel no sistema, usado pela unicidade "enquanto
 * ativa", pela validação GIS e pelo corte) e {@code daysToCut} (prazo
 * de corte da situação, consumido pela P3.4; nasce nulo porque o legado
 * não tem o dado).
 *
 * <p>Não usar {@code @Builder}: o Lombok descarta os campos herdados de
 * {@code BaseEntity}.
 */
@Entity
@Table(name = "connection_statuses")
@Getter
@Setter
public class ConnectionStatus extends TenantAwareEntity {

    public enum Kind { WATER, SEWER }

    public enum SystemRole { ACTIVE, CUT, SUSPENDED, DISCONNECTED, NO_CONNECTION, INACTIVE }

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 5)
    private Kind kind;

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "abbreviation", length = 5)
    private String abbreviation;

    @Enumerated(EnumType.STRING)
    @Column(name = "system_role", nullable = false, length = 20)
    private SystemRole systemRole;

    @Column(name = "days_to_cut")
    private Integer daysToCut;

    @Column(name = "has_network", nullable = false)
    private boolean hasNetwork;

    @Column(name = "has_connection", nullable = false)
    private boolean hasConnection;

    @Column(name = "bills", nullable = false)
    private boolean bills;

    @Column(name = "requires_network", nullable = false)
    private boolean requiresNetwork;

    @Column(name = "connected", nullable = false)
    private boolean connected;

    /** Só água no legado; esgoto nasce {@code false} na cópia da V3_14. */
    @Column(name = "supplies", nullable = false)
    private boolean supplies;

    @Column(name = "minimum_consumption", nullable = false)
    private boolean minimumConsumption;

    @Column(name = "minimum_billing_consumption")
    private Integer minimumBillingConsumption;

    @Column(name = "minimum_billing_volume", precision = 10, scale = 2)
    private BigDecimal minimumBillingVolume;

    @Column(name = "active", nullable = false)
    private boolean active;
}

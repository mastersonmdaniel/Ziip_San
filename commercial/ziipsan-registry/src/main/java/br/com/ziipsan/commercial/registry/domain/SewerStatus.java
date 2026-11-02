package br.com.ziipsan.commercial.registry.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Situacao da ligacao de esgoto, com flags de comportamento (catalogo do tenant, sem semente).
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "sewer_statuses")
@Getter
@Setter
public class SewerStatus extends TenantAwareEntity {

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** Abreviacao do frontend (catalogDefinitions.ts); opcional no backend. */
    @Column(name = "abbreviation", length = 5)
    private String abbreviation;

    /**
     * Volume minimo de faturamento (m3) do frontend; opcional no backend. Distinto de
     * {@code minimum_consumption} (flag: cobra consumo minimo mesmo sem uso).
     */
    @Column(name = "minimum_billing_volume", precision = 10, scale = 2)
    private BigDecimal minimumBillingVolume;

    /**
     * "Existe rede", do frontend. Campo proprio: nao se confunde com {@code requires_network}
     * (so vale em quadra com a rede) nem o substitui.
     */
    @Column(name = "has_network", nullable = false)
    private boolean hasNetwork;

    /**
     * "Existe ligacao", do frontend. Campo proprio: convive com {@code connected} (ligacao
     * fisica ja modelada); os sentidos nao foram provados identicos, por isso nao ha fusao.
     */
    @Column(name = "has_connection", nullable = false)
    private boolean hasConnection;

    @Column(name = "bills", nullable = false)
    private boolean bills;

    @Column(name = "requires_network", nullable = false)
    private boolean requiresNetwork;

    @Column(name = "connected", nullable = false)
    private boolean connected;

    @Column(name = "minimum_consumption", nullable = false)
    private boolean minimumConsumption;

    @Column(name = "active", nullable = false)
    private boolean active;

}

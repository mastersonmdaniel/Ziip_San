package br.com.ziipsan.commercial.registry.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Unidade consumidora (imovel). A matricula e a chave estavel; a inscricao (quadra, lote, sublote) identifica o lugar e pode mudar. Categoria principal e total de economias sao DERIVADOS de unit_subcategories.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "consumer_units")
@Getter
@Setter
public class ConsumerUnit extends TenantAwareEntity {

    @Column(name = "matricula", nullable = false, length = 50)
    private String matricula;

    @Column(name = "dv", nullable = false, length = 1)
    private String checkDigit;

    @Column(name = "block_id")
    private UUID blockId;

    @Column(name = "lot")
    private Integer lot;

    @Column(name = "sublot")
    private Integer sublot;

    @Column(name = "address_id")
    private UUID addressId;

    @Column(name = "water_status_id")
    private UUID waterStatusId;

    @Column(name = "sewer_status_id")
    private UUID sewerStatusId;

    @Column(name = "built_area_m2", precision = 10, scale = 2)
    private java.math.BigDecimal builtAreaM2;

    @Column(name = "due_day")
    private Short dueDay;

    @Column(name = "notes", length = 750)
    private String notes;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "reading_route_id")
    private UUID readingRouteId;

    @Column(name = "reading_sequence")
    private Integer readingSequence;

    @Column(name = "coordinates", columnDefinition = "geometry(Point, 4674)")
    private org.locationtech.jts.geom.Point coordinates;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "properties", columnDefinition = "jsonb")
    private String properties;

    @Column(name = "deleted_at")
    private java.time.OffsetDateTime deletedAt;

    @Column(name = "deleted_reason", length = 200)
    private String deletedReason;

}

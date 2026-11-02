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

    /** Face da quadra. Sem FK: a tabela block_faces nao existe no backend (P0.16, V3_5). */
    @Column(name = "block_face_id")
    private UUID blockFaceId;

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

    /** Sem FK: o catalogo sewer-discharges nao existe no backend (P0.16, V3_5). */
    @Column(name = "sewer_discharge_id")
    private UUID sewerDischargeId;

    /** Sem FK: o catalogo property-profiles nao existe no backend (P0.16, V3_5). */
    @Column(name = "profile_id")
    private UUID profileId;

    /** Sem FK: o catalogo water-sources nao existe no backend (P0.16, V3_5). */
    @Column(name = "water_source_id")
    private UUID waterSourceId;

    /** Sem FK: o catalogo well-types nao existe no backend (P0.16, V3_5). */
    @Column(name = "well_type_id")
    private UUID wellTypeId;

    /** Sem FK: o catalogo pavements nao existe no backend (P0.16, V3_5). */
    @Column(name = "pavement_street_id")
    private UUID pavementStreetId;

    /** Sem FK: o catalogo pavements nao existe no backend (P0.16, V3_5). */
    @Column(name = "pavement_sidewalk_id")
    private UUID pavementSidewalkId;

    @Column(name = "physical_garden")
    private Boolean physicalGarden;

    @Column(name = "physical_lower_reservoir_m3", precision = 10, scale = 2)
    private java.math.BigDecimal physicalLowerReservoirM3;

    @Column(name = "physical_upper_reservoir_m3", precision = 10, scale = 2)
    private java.math.BigDecimal physicalUpperReservoirM3;

    @Column(name = "physical_pool_m3", precision = 10, scale = 2)
    private java.math.BigDecimal physicalPoolM3;

    @Column(name = "usage_points")
    private Integer usagePoints;

    @Column(name = "residents")
    private Integer residents;

    @Column(name = "external_iptu", length = 30)
    private String externalIptu;

    /** Sem FK: o catalogo bill-delivery-modes nao existe no backend (P0.16, V3_5). */
    @Column(name = "delivery_mode_id")
    private UUID deliveryModeId;

    /** FK para reading_routes (tabela existente). */
    @Column(name = "delivery_route_id")
    private UUID deliveryRouteId;

    @Column(name = "delivery_sequence")
    private Integer deliverySequence;

    /** FK para reading_routes (tabela existente). */
    @Column(name = "alt_route_id")
    private UUID altRouteId;

    /** Imovel principal. Auto-FK para consumer_units. */
    @Column(name = "parent_unit_id")
    private UUID parentUnitId;

    /**
     * Identificador transitorio do wizard. Sem FK: os endpoints de rascunho nao
     * existem no backend (P0.16, V3_5); so a referencia enviada e guardada.
     */
    @Column(name = "draft_id")
    private UUID draftId;

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

    @Column(name = "lot_frontage_m", precision = 10, scale = 2)
    private java.math.BigDecimal lotFrontageM;

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

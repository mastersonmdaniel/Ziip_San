package br.com.ziipsan.intelligence.gis.catalog.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Item de catálogo (V13_1 + V13_4 da P1.27): a peça/montagem comercial de um
 * tipo (ex.: tubo PVC_PBA DN 100, curva 45°). reference_status: STANDARD só
 * para item conferido no texto da norma comprada; REFERENCE_TO_VALIDATE para
 * todo o resto; TENANT para item criado pelo tenant. Nesta frente NENHUM item
 * de fábrica é STANDARD (compra P1.28 pendente) — ver o relatório
 * (GET /report/reference-to-validate). Item desativado continua referenciado
 * pelas feições existentes: desativar nunca apaga nem quebra FK.
 */
@Entity
@Table(name = "gis_catalog_item")
@Getter
@Setter
public class GisCatalogItem extends TenantAwareEntity {

    @Column(name = "feature_type_id", nullable = false)
    private UUID featureTypeId;

    @Column(name = "material_id")
    private UUID materialId;

    @Column(name = "dn_mm")
    private Integer dnMm;

    @Column(name = "dn2_mm")
    private Integer dn2Mm;

    @Column(name = "angle_deg", precision = 6, scale = 2)
    private BigDecimal angleDeg;

    @Column(name = "pressure_class")
    private String pressureClass;

    @Column(name = "stiffness_class")
    private String stiffnessClass;

    @Column(name = "joint_type")
    private String jointType;

    @Column(name = "internal_diameter_mm", precision = 10, scale = 2)
    private BigDecimal internalDiameterMm;

    @Column(name = "external_diameter_mm", precision = 10, scale = 2)
    private BigDecimal externalDiameterMm;

    @Column(name = "sdr", precision = 6, scale = 2)
    private BigDecimal sdr;

    @Column(name = "section_shape")
    private String sectionShape;

    @Column(name = "geom1", precision = 10, scale = 3)
    private BigDecimal geom1;

    @Column(name = "geom2", precision = 10, scale = 3)
    private BigDecimal geom2;

    @Column(name = "geom3", precision = 10, scale = 3)
    private BigDecimal geom3;

    @Column(name = "geom4", precision = 10, scale = 3)
    private BigDecimal geom4;

    @Column(name = "manufacturer")
    private String manufacturer;

    @Column(name = "model")
    private String model;

    @Column(name = "standard_ref")
    private String standardRef;

    @Column(name = "reference_status", nullable = false)
    private String referenceStatus = "REFERENCE_TO_VALIDATE";

    @Column(name = "origin", nullable = false)
    private String origin;

    @Column(name = "factory_code")
    private String factoryCode;

    @Column(name = "factory_retired_on")
    private LocalDate factoryRetiredOn;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "approval_status")
    private String approvalStatus;

    @Column(name = "proposed_by")
    private UUID proposedBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;
}

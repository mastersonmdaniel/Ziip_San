package br.com.ziipsan.intelligence.gis.catalog.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Tipo de feição do GIS (V13_1 + V13_4 da P1.27). O code nunca é opcional
 * (@NotBlank no DTO): sem code o tipo não entra no catálogo nem na simbologia
 * da P1.30. Tipo novo do tenant nasce PENDING + inativo e só vira usável
 * (APPROVED + active) com aprovação por outra pessoa (approved_by diferente
 * de proposed_by, também garantido pelo CHECK da V13_4). Tipo de fábrica
 * (origin FACTORY, factory_code 'gis01:'/'gis02:') nasce APPROVED; o tenant
 * só muda o active dele — nunca origin, factory_code, standard_ref ou
 * reference_status (a R__ também nunca os altera na reaplicação).
 *
 * <p>Elevatórias: attributes_schema guarda {@code suction_pressurized}
 * (NBR 12208:2020 — pressão a montante superior à atmosférica => booster).
 */
@Entity
@Table(name = "gis_feature_type")
@Getter
@Setter
public class GisFeatureType extends TenantAwareEntity {

    @Column(name = "class_id", nullable = false)
    private UUID classId;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "label", nullable = false)
    private String label;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attributes_schema", columnDefinition = "jsonb", nullable = false)
    private String attributesSchema = "{}";

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "terminal_config", columnDefinition = "jsonb")
    private String terminalConfig;

    @Column(name = "min_zoom")
    private Integer minZoom;

    @Column(name = "style_key")
    private String styleKey;

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

    @Column(name = "requires_catalog_item", nullable = false)
    private boolean requiresCatalogItem;
}

package br.com.ziipsan.intelligence.gis.catalog.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** DTOs do catálogo GIS (P1.27). O code do tipo nunca é opcional. */
public final class GisCatalogDtos {

    private GisCatalogDtos() {
    }

    public record ClassView(UUID id, String code, String domain, String topologyRole,
                            String geometryType, String origin, boolean active) {
    }

    public record TypeView(UUID id, UUID classId, String classCode, String code, String label,
                           String attributesSchema, String origin, String factoryCode,
                           LocalDate factoryRetiredOn, boolean active, String approvalStatus,
                           UUID proposedBy, UUID approvedBy, OffsetDateTime approvedAt,
                           boolean requiresCatalogItem) {
    }

    public record TypeInput(
            @NotNull UUID classId,
            @NotBlank String code,
            @NotBlank String label,
            /** JSON Schema montado pelo formulário da tela; '{}' quando ausente. */
            String attributesSchema,
            Boolean requiresCatalogItem) {
    }

    public record ItemView(UUID id, UUID featureTypeId, String featureTypeCode, UUID materialId,
                           String materialCode, Integer dnMm, Integer dn2Mm, BigDecimal angleDeg,
                           String pressureClass, String stiffnessClass, String jointType,
                           BigDecimal internalDiameterMm, BigDecimal externalDiameterMm,
                           BigDecimal sdr, String sectionShape, BigDecimal geom1, BigDecimal geom2,
                           BigDecimal geom3, BigDecimal geom4, String manufacturer, String model,
                           String standardRef, String referenceStatus, String origin,
                           String factoryCode, LocalDate factoryRetiredOn, boolean active,
                           String approvalStatus, UUID proposedBy, UUID approvedBy,
                           OffsetDateTime approvedAt) {
    }

    public record ItemInput(
            @NotNull UUID featureTypeId,
            UUID materialId,
            Integer dnMm,
            Integer dn2Mm,
            BigDecimal angleDeg,
            String pressureClass,
            String stiffnessClass,
            String jointType,
            BigDecimal internalDiameterMm,
            BigDecimal externalDiameterMm,
            BigDecimal sdr,
            String sectionShape,
            BigDecimal geom1,
            BigDecimal geom2,
            BigDecimal geom3,
            BigDecimal geom4,
            String manufacturer,
            String model) {
    }

    public record MaterialView(UUID id, String code, String family, String standardRef,
                               BigDecimal hazenWilliamsC, BigDecimal manningN,
                               boolean legacyOnly, String origin, boolean active) {
    }

    public record ActiveInput(@NotNull Boolean active) {
    }

    /**
     * Relatório dos itens a validar (§4.1): todo item de fábrica com
     * REFERENCE_TO_VALIDATE, e a ressalva de que NENHUM é STANDARD porque a
     * compra P1.28 está pendente de aprovação do dono.
     */
    public record ReferenceReport(long factoryTypes, long factoryItems,
                                  long factoryItemsToValidate, long factoryItemsStandard,
                                  long factoryTypesRetired, long factoryItemsRetired,
                                  long tenantTypesPending, long tenantItemsPending,
                                  boolean normsPurchasePending, String note) {
    }
}

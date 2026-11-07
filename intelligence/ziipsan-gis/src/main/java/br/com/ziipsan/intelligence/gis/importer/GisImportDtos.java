package br.com.ziipsan.intelligence.gis.importer;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Contratos da GIS-07: lote de importação, staging, perfis e mapas de
 * camada, camadas de contexto e relatório ISO 19157. Geometrias trafegam
 * em WKT (SRID 4674), como na GIS-04. JSON só via classe Json do core.
 */
public final class GisImportDtos {

    private GisImportDtos() {
    }

    /** Abertura do lote: hashes e conversão assistida já aconteceram fora. */
    public record CreateBatchInput(
            UUID profileId,
            @NotBlank String sourceFormat,
            @NotBlank String originalFileName,
            @NotBlank String originalSha256,
            String convertedFormat,
            String convertedSha256,
            String conversionTool,
            Integer sourceSrsEpsg,
            String srsSource,
            String datumTransform,
            String ibgeGridRef,
            BigDecimal controlPointResidualM,
            BigDecimal snapToleranceM,
            boolean pilotHomologation) {
    }

    /** Linha crua do staging, já com a geometria transformada p/ 4674. */
    public record StageRowInput(
            @NotBlank String sourceLayer,
            String sourceRef,
            String blockName,
            String labelText,
            Map<String, Object> attributes,
            String geomWkt) {
    }

    public record LayerMapInput(
            @NotBlank String sourceLayer,
            String sourceBlock,
            String textPattern,
            @NotBlank String targetKind,
            String featureTable,
            String featureTypeCode,
            String contextKind,
            boolean personalData,
            String anonymizationMethod) {
    }

    public record ContextFeatureInput(
            @NotBlank String contextKind,
            String code,
            String name,
            boolean personalData,
            String anonymizationMethod,
            Map<String, Object> attributes,
            @NotBlank String geomWkt) {
    }

    /** Relatório ISO 19157-1 (§9.1 etapa 6): elementos como mapa JSON. */
    public record QualityReportInput(
            Map<String, Object> report) {
    }

    public record BatchView(
            UUID id,
            UUID profileId,
            String sourceFormat,
            String originalFileName,
            String originalSha256,
            String convertedFormat,
            String convertedSha256,
            String conversionTool,
            String status,
            Integer sourceSrsEpsg,
            String srsSource,
            String datumTransform,
            String ibgeGridRef,
            BigDecimal snapToleranceM,
            boolean pilotHomologation,
            Map<String, Object> countsBefore,
            Map<String, Object> countsAfter,
            UUID changeSetId,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record RawView(
            UUID id,
            String sourceLayer,
            String sourceRef,
            String blockName,
            String labelText,
            Map<String, Object> attributes,
            String geomWkt,
            String status,
            UUID layerMapId,
            String publishedFeatureTable,
            UUID publishedFeatureId) {
    }

    public record ProfileView(
            UUID id,
            String key,
            String label,
            String origin,
            boolean active) {
    }

    public record LayerMapView(
            UUID id,
            UUID profileId,
            String sourceLayer,
            String sourceBlock,
            String textPattern,
            String targetKind,
            String featureTable,
            String featureTypeCode,
            String contextKind,
            boolean personalData,
            String anonymizationMethod) {
    }

    public record ContextFeatureView(
            UUID id,
            UUID batchId,
            String contextKind,
            String code,
            String name,
            boolean personalData,
            String anonymizationMethod,
            String geomWkt) {
    }

    public record PageView<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }
}

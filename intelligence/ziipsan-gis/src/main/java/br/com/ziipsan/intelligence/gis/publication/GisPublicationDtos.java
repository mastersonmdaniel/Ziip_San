package br.com.ziipsan.intelligence.gis.publication;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Contratos da GIS-06: camadas publicadas, estilo do tenant
 * (override sem código), mapas base, fontes WMS, símbolos da
 * biblioteca, identificação de feição e pesquisas do mapa web.
 * Geometrias de resposta trafegam em lon/lat (4326) ou WKT 4674 —
 * o tile MVT é binário e não passa por estes DTOs.
 */
public final class GisPublicationDtos {

    private GisPublicationDtos() {
    }

    public record LayerView(
            String key, String label, String groupLabel, boolean personal,
            int tileMinZoom, long layerVersion, boolean active) {
    }

    /** Um símbolo da biblioteca (P1.30/ADR-0036): chave e uso. */
    public record SymbolView(
            String key, String typeCode, String typeLabel, String domainKey) {
    }

    public record StyleRowView(
            UUID id, String origin, String domainKey, UUID featureTypeId,
            String featureTypeCode, String lifecycleStatus, String accuracyStatus,
            String colorToken, Double lineWidth, List<Double> lineDash,
            Boolean halo, String iconKey, String labelField, Integer labelMinZoom) {
    }

    /** Override do tenant por tipo e estado — sem código (§10.2). */
    public record StyleOverrideInput(
            UUID featureTypeId,
            String domainKey,
            @NotBlank String lifecycleStatus,
            String accuracyStatus,
            String colorToken,
            Double lineWidth,
            List<Double> lineDash,
            Boolean halo,
            String iconKey,
            String labelField,
            Integer labelMinZoom) {
    }

    public record BasemapView(
            UUID id, String key, String label, String provider, String tilesUrl,
            String styleUrl, String attribution, boolean isDefault, String origin) {
    }

    public record BasemapInput(
            @NotBlank String label, @NotBlank String provider,
            String tilesUrl, String styleUrl, String attribution,
            String licenseRef, boolean makeDefault) {
    }

    public record WmsSourceView(
            UUID id, String label, String url, String layers, String version,
            String format, boolean transparent, boolean active) {
    }

    public record WmsSourceInput(
            @NotBlank String label, @NotBlank String url, @NotBlank String layers,
            String version, String format, Boolean transparent) {
    }

    /** Ficha da feição (§10.3): atributos, catálogo e vínculos mínimos. */
    public record FeatureDetailView(
            String featureTable, UUID id, String code, String name,
            String typeCode, String typeLabel, String domainKey,
            String lifecycleStatus, String accuracyStatus,
            Map<String, Object> attributes, String geomWkt,
            UUID serviceConnectionId, boolean personalDataVisible,
            Instant updatedAt) {
    }

    public record SearchHitView(
            String kind, String featureTable, UUID id, String code, String name,
            String typeCode, Double lon, Double lat) {
    }

    public record DirtyStatusView(
            long pendingTiles, Instant oldestPendingAt) {
    }
}

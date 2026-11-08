package br.com.ziipsan.intelligence.gis.waste;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Contratos da GIS-14 (resíduos sólidos no GIS). */
public final class GisWasteDtos {

    private GisWasteDtos() {
    }

    /**
     * Criação de ponto/unidade de resíduos: a geometria chega como
     * GeoJSON (RFC 7946) já em SIRGAS 2000 (EPSG 4674) — a conversão
     * de SRS é da importação/campo, não deste cadastro (§3.9).
     * {@code attributes} é o JSONB validado pelo attributes_schema do
     * tipo no banco e por {@link GisWasteRules} na api.
     */
    public record WastePointInput(
            @NotBlank String code,
            @NotBlank String typeCode,
            String name,
            @NotBlank String lifecycleStatus,
            @NotBlank String captureMethod,
            @NotNull Map<String, Object> attributes,
            @NotNull Map<String, Object> geometryGeoJson) {
    }

    public record WastePointView(
            UUID id,
            String code,
            String typeCode,
            String featureTable,
            String name,
            String lifecycleStatus,
            Map<String, Object> attributes) {
    }

    /** Foto do ponto (gis_attachment): o documento já passou pelo pipeline de anexos (P1.20). */
    public record PhotoInput(
            @NotNull UUID documentId,
            String description) {
    }

    /** Pedido de OS de limpeza sobre um descarte irregular (foto é pré-condição). */
    public record CleanupRequestView(
            UUID id,
            UUID dumpPoiId,
            UUID serviceOrderId,
            String status,
            LocalDate requestedOn) {
    }

    /** Setor de coleta: polígono GeoJSON + atributos do §4.5. */
    public record SectorInput(
            @NotBlank String code,
            String name,
            @NotNull Map<String, Object> attributes,
            @NotNull Map<String, Object> geometryGeoJson) {
    }

    public record SectorView(
            UUID id,
            String code,
            String name,
            Map<String, Object> attributes) {
    }

    /** Vínculo imóvel ↔ setor (a leitura que a taxa da ED-25 consome). */
    public record PropertySectorLinkView(
            UUID propertyUnitId,
            UUID sectorZoneId,
            String status,
            LocalDate decidedOn) {
    }

    /** Criação de rota: paradas por id de ponto; a ordem pedida é a sequência-base. */
    public record RouteInput(
            @NotBlank String code,
            @NotNull UUID sectorZoneId,
            @NotBlank String vehicleKind,
            String crewLabel,
            @NotNull Long depotVertex,
            @NotNull List<UUID> stopPointIds,
            Double avgSpeedKmh,
            Double minutesPerStop) {
    }

    public record RouteStopView(
            UUID pointId,
            String pointTypeCode,
            int sequence,
            Double legCostM) {
    }

    public record RouteView(
            UUID id,
            String code,
            UUID sectorZoneId,
            String vehicleKind,
            String status,
            Double totalExtensionM,
            Double estimatedMinutes,
            List<RouteStopView> stops) {
    }

    /** Linha do manifesto de símbolos (inventário; arte é da P1.30 — ver GisWasteSymbols). */
    public record SymbolManifestView(
            String typeCode,
            String state,
            String symbolKey,
            boolean presentInLibrary) {
    }
}

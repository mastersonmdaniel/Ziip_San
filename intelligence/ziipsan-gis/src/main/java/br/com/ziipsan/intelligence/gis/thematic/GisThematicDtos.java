package br.com.ziipsan.intelligence.gis.thematic;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Contratos da GIS-08: definição de temático, classes, legenda
 * automática, pontos do backoffice, agregado público, estatísticas
 * da seleção por polígono (TR-1059), OS no mapa (SG-491/TR-534) e
 * linhas dos relatórios de extensão e de precisão.
 *
 * <p>Regra de ouro dos contratos pessoais (§12): nenhum DTO de
 * ponto público existe — o canal público só conhece
 * {@link PublicCellView} (célula, contagem e contagens por
 * categoria). Nome, documento, matrícula e endereço de titular não
 * aparecem em DTO algum desta tarefa.
 */
public final class GisThematicDtos {

    private GisThematicDtos() {
    }

    /** Uma classe do temático (espelho de um item de classes_json). */
    public record ClassDef(
            String key, String label, String colorToken,
            BigDecimal min, BigDecimal max) {
    }

    public record ThematicView(
            UUID id, String key, String name, String description,
            String sourceKind, String categoryField, String measureField,
            boolean personal, String aggregation,
            List<ClassDef> classes, String filterJson,
            String origin, boolean active, boolean requiresConfiguration) {
    }

    /** Criação/edição de temático do tenant — sem código (GIS-08). */
    public record ThematicInput(
            @NotBlank String key,
            @NotBlank String name,
            String description,
            @NotBlank String sourceKind,
            String categoryField,
            String measureField,
            String aggregation,
            List<ClassDef> classes,
            String filterJson) {
    }

    /** Entrada da legenda automática: classe + quantos pontos caíram nela. */
    public record LegendEntry(
            String key, String label, String colorToken, long count) {
    }

    /** Ponto do backoffice (lon/lat 4326; geometria de rede sai como centroide). */
    public record ThematicPoint(
            String id, double lon, double lat, String category,
            BigDecimal measure) {
    }

    public record ThematicDataView(
            String thematicKey, List<ThematicPoint> points,
            List<LegendEntry> legend, boolean truncated) {
    }

    /**
     * Célula do canal público: quadra (BLOCK) ou setor (SECTOR) com
     * pelo menos o mínimo de unidades — célula abaixo do mínimo
     * simplesmente não aparece (supressão por ausência, §12 item 3).
     * {@code sectorGeomGeoJson} só existe no SECTOR (polígono da
     * gis_zone comercial); quadra não tem geometria no cadastro.
     */
    public record PublicCellView(
            String cellKind, String cellId, String cellLabel,
            long unitCount, List<LegendEntry> categories,
            String sectorGeomGeoJson) {
    }

    /**
     * Resposta do canal público. {@code minUnitsApplied} é o mínimo
     * de unidades efetivamente aplicado na supressão de células —
     * só existe no ramo pessoal agregado; no ramo de ativos
     * (rede/precisão, sem supressão) é {@code null}, nunca um zero
     * ou um mínimo que não foi aplicado. {@code truncated} declara
     * que as contagens de ativos pararam no teto de renderização.
     */
    public record PublicThematicView(
            String thematicKey, Integer minUnitsApplied,
            List<PublicCellView> cells, long suppressedCells,
            boolean truncated) {
    }

    /** Estatísticas da seleção por polígono (TR-1059). */
    public record PolygonStatsView(
            long units, long connections, long economies,
            BigDecimal averageConsumption, BigDecimal areaM2) {
    }

    /** Polígono da seleção: GeoJSON Polygon em lon/lat (RFC 7946). */
    public record PolygonInput(String polygonGeoJson) {
    }

    /** Linha da camada de OS (SG-491/TR-534): sem nome de pessoa. */
    public record ServiceOrderPoint(
            UUID orderId, String serviceTypeCode, String serviceTypeName,
            String status, String priority, double lon, double lat,
            String addressText, List<UUID> crewUserIds,
            java.time.OffsetDateTime scheduledFor,
            java.time.OffsetDateTime createdAt) {
    }

    /** Linha do relatório de extensão (função gis_network_extension_at). */
    public record ExtensionRow(
            String domain, String networkFunction, String material,
            Integer dnMm, Integer decadeStart, String sectorCode,
            String sector, BigDecimal extensionM, long arcs) {
    }

    /** Linha do resumo de precisão por classe e método (§3.8/§13.6). */
    public record PrecisionRow(
            String classCode, String captureMethod, String precisionGroup,
            String domain, long features) {
    }

    public record ExtensionReportView(
            LocalDate asOf, List<ExtensionRow> rows, BigDecimal totalExtensionM) {
    }
}

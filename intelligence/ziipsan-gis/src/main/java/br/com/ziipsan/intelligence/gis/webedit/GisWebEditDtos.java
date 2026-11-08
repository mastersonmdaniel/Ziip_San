package br.com.ziipsan.intelligence.gis.webedit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Contratos da GIS-09 (ED-53). Geometrias trafegam em WKT (SRID
 * 4674), como na GIS-04; as operações compostas (dividir, unir,
 * inserir nó) devolvem as geometrias resultantes para a tela
 * conferir antes de gravar o item no pacote.
 *
 * <p>Correção D1 (revisão ED-53): TODA operação de escrita carrega
 * {@code layerId} — é a camada contra a qual o serviço confere a
 * permissão camada×operação (SG-864/SG-865, gis_layer_permission)
 * ANTES de qualquer escrita. Sem layerId não há escrita.
 */
public final class GisWebEditDtos {

    private GisWebEditDtos() {
    }

    public record TransformInput(
            @NotBlank String geomWkt,
            @NotBlank String mode,
            Double dx,
            Double dy,
            Double pivotX,
            Double pivotY,
            Double degrees,
            Double factor,
            Integer vertexIndex,
            Double toX,
            Double toY,
            @NotNull UUID layerId) {
    }

    public record TransformView(String geomWkt) {
    }

    public record SplitInput(
            @NotBlank String geomWkt,
            @NotNull Double atX,
            @NotNull Double atY,
            Double tolerance,
            @NotNull UUID layerId) {
    }

    public record SplitView(String firstWkt, String secondWkt, String nodeWkt) {
    }

    public record MergeInput(
            @NotBlank String firstWkt,
            @NotBlank String secondWkt,
            Double tolerance,
            @NotNull UUID layerId) {
    }

    public record MergeView(String geomWkt) {
    }

    public record LayerPermissionView(
            UUID layerId, String layerKey, List<String> operations) {
    }

    public record UserLayerInput(
            @NotBlank String name,
            @NotBlank String geometryType) {
    }

    public record UserLayerView(
            UUID id, String name, String geometryType, UUID ownerId) {
    }

    /** Feição da camada do usuário (SG-840): escrita e leitura. */
    public record UserFeatureInput(
            @NotBlank String geomWkt,
            Map<String, Object> attributes) {
    }

    public record UserFeatureView(
            UUID id, UUID layerId, String geomWkt,
            Map<String, Object> attributes) {
    }

    public record JoinInput(
            @NotBlank String leftTable,
            @NotBlank String rightTable,
            @NotBlank String joinType,
            @NotBlank String joinKey,
            @NotNull UUID layerId) {
    }

    public record JoinView(
            UUID id, String leftTable, String rightTable,
            String joinType, String joinKey, UUID layerId) {
    }

    /** Linha do resultado de executar a junção (SG-841). */
    public record JoinRowView(UUID leftId, UUID rightId, String keyValue) {
    }

    /** Resultado de EXECUTAR a junção definida (correção D5). */
    public record JoinResultView(
            UUID joinId, String leftTable, String rightTable,
            String joinType, String joinKey, List<JoinRowView> rows) {
    }

    public record PlantStructureInput(
            @NotBlank String structureKind,
            @NotBlank String code,
            String name,
            String geomWkt,
            String geomPointWkt,
            Map<String, Object> attributes,
            @NotNull UUID featureTypeId,
            @NotNull UUID layerId) {
    }

    public record PlantStructureView(
            UUID id, String structureKind, String code, String name,
            String geomWkt, String geomPointWkt) {
    }

    public record ProjectInput(
            @NotBlank String name,
            String description,
            String workRef,
            @NotNull UUID layerId) {
    }

    public record ProjectView(
            UUID id, String name, String status,
            UUID projectChangeSetId, UUID asBuiltChangeSetId,
            String workRef) {
    }

    public record OpenForServiceOrderInput(
            @NotBlank String serviceOrderRef,
            boolean affectsNetwork,
            @NotNull UUID layerId) {
    }

    public record DrawnSyncView(
            UUID gisConnectionId, UUID serviceConnectionId,
            String status, String idempotencyKey) {
    }
}

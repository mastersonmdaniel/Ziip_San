package br.com.ziipsan.intelligence.gis.drain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Contratos da GIS-13 (drenagem no GIS). */
public final class GisDrainDtos {

    private GisDrainDtos() {
    }

    /**
     * Criação de feição de drenagem (nó, arco, estrutura ou zona):
     * geometria GeoJSON (RFC 7946) já em SIRGAS 2000 (EPSG 4674).
     * Em arco, {@code node1Code}/{@code node2Code} são os nós das
     * pontas (o snap da GIS-03 preenche as chaves na gravação em
     * pacote; neste cadastro operacional os códigos são resolvidos
     * para id), e as cotas de fundo de montante/jusante alimentam as
     * regras de declividade e sentido (GisDrainRules).
     */
    public record DrainFeatureInput(
            @NotBlank String code,
            @NotBlank String typeCode,
            String name,
            @NotBlank String lifecycleStatus,
            @NotBlank String captureMethod,
            String conditionRating,
            @NotNull Map<String, Object> attributes,
            @NotNull Map<String, Object> geometryGeoJson,
            String node1Code,
            String node2Code,
            Double upstreamInvertM,
            Double downstreamInvertM,
            String flowDirection) {
    }

    public record DrainFeatureView(
            UUID id,
            String code,
            String typeCode,
            String featureTable,
            String name,
            String lifecycleStatus,
            String conditionRating,
            Map<String, Object> attributes) {
    }

    /** Foto da feição (gis_attachment): documento já passou pelo pipeline de anexos (P1.20). */
    public record PhotoInput(
            @NotNull UUID documentId,
            String description) {
    }

    /** Inspeção de drenagem (§4.4/NR 12: conservação, obstrução, foto, data). */
    public record InspectionInput(
            @NotNull LocalDate inspectedOn,
            @NotBlank String method,
            String conditionRating,
            Double obstructionPct,
            UUID photoAttachmentId,
            String videoRef,
            String notes) {
    }

    public record InspectionView(
            UUID id,
            String featureTable,
            UUID featureId,
            LocalDate inspectedOn,
            String method,
            String conditionRating,
            Double obstructionPct,
            String videoRef) {
    }

    /** Linha do plano de limpeza vinda do tenant (parâmetro + critérios). */
    public record CleaningPlanInput(
            @NotBlank String elementClass,
            Integer intervalMonths,
            Double obstructionThresholdPct,
            boolean includePoorOrCritical,
            @NotNull LocalDate planStart) {
    }

    /** Pedido recorrente de limpeza (a OS é da ED-36; serviceOrderId nasce nulo). */
    public record CleaningRequestView(
            UUID id,
            String featureTable,
            UUID featureId,
            String elementClass,
            LocalDate dueOn,
            String basis,
            UUID serviceOrderId,
            String status) {
    }

    /** Estação/fonte pluviométrica (drain.rain_source). */
    public record RainStationInput(
            @NotBlank String code,
            @NotBlank String sourceKind,
            String name,
            String sourceRef) {
    }

    /**
     * Resultado da importação de chuva. Correção D7 da revisão:
     * {@code readingsStored} conta as leituras EFETIVAMENTE
     * GRAVADAS — a duplicada de um instante já existente é
     * descartada pelo ON CONFLICT DO NOTHING da gravação e não
     * conta; {@code totalMm} é a soma somente das gravadas.
     */
    public record RainImportView(
            UUID stationId,
            int readingsStored,
            double totalMm,
            String sourceRef) {
    }

    /**
     * Ocorrência de alagamento: período obrigatório (é ele que liga a
     * ocorrência à chuva), feição/zona afetada opcional e o marcador
     * da causa ALAGAMENTO para o catálogo da ED-92.
     */
    public record FloodOccurrenceInput(
            @NotNull LocalDateTime periodStart,
            @NotNull LocalDateTime periodEnd,
            String featureTable,
            UUID featureId,
            UUID subcatchmentZoneId,
            String description) {
    }

    public record FloodOccurrenceView(
            UUID id,
            LocalDateTime periodStart,
            LocalDateTime periodEnd,
            Double rainMmInPeriod,
            String networkCauseCode,
            UUID networkOccurrenceId,
            String description) {
    }

    /** Linha do relatório GIS-003 (cadastro: material × estado). */
    public record CadastroReportRow(
            String material,
            String conditionRating,
            String typeCode,
            double extensionM,
            long arcs,
            Double avgObstructionPct) {
    }

    /** Linha do manifesto de símbolos (inventário; arte é da P1.30 — ver GisDrainSymbols). */
    public record SymbolManifestView(
            String typeCode,
            String state,
            String symbolKey,
            boolean presentInLibrary) {
    }

    /** Classe do temático de conservação com a contagem de feições. */
    public record ConservationClassCount(String conditionRating, long features) {
    }

    public record ValidationIssueView(String ruleCode, String severity, String message) {
    }

    public record SubcatchmentInput(
            @NotBlank String code,
            String name,
            @NotNull Map<String, Object> attributes,
            @NotNull Map<String, Object> geometryGeoJson) {
    }

    public record GenerationResult(int created, List<String> skippedReasons) {
    }
}

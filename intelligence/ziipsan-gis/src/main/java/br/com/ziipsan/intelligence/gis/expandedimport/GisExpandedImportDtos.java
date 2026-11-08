package br.com.ziipsan.intelligence.gis.expandedimport;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Contratos da GIS-18: fila de casamento, conciliacao e implantacao. */
public final class GisExpandedImportDtos {

    private GisExpandedImportDtos() {
    }

    /** Unidade candidata vinda do cadastro comercial (porta de leitura). */
    public record UnitInput(
            @NotBlank String unitId,
            String registrationKey,
            String address) {
    }

    /** Pedido de casamento de uma linha do desenho/base (SG-1349). */
    public record MatchRequestInput(
            UUID rawId,
            String registrationKey,
            String address,
            List<UnitInput> candidates) {
    }

    public record MatchQueueView(
            UUID id,
            UUID batchId,
            UUID rawId,
            String registrationKey,
            String normalizedAddress,
            UUID candidateUnitId,
            String matchMethod,
            String matchStatus) {
    }

    public record MatchDecisionInput(
            @NotBlank String decision,
            String note,
            // Candidato escolhido pela pessoa ao decidir MATCHED
            // (correcao D4): obrigatorio na pratica para item
            // AMBIGUOUS, que nasce sem candidato unico; validado
            // contra o conjunto avaliado gravado no item.
            String candidateUnitId) {
    }

    /**
     * Abertura de lote ampliado (GIS-18, correcao D3): o caminho real
     * de aceite que aplica {@code GisExpandedImportRules} e grava em
     * {@code gis_import_batch} (a mesma tabela da GIS-07; o CHECK de
     * formato ampliado esta no SQL pendente da ED-55).
     */
    public record CreateExpandedBatchInput(
            UUID profileId,
            @NotBlank String sourceFormat,
            @NotBlank String originalFileName,
            @NotBlank String originalSha256,
            String convertedFormat,
            String convertedSha256,
            String conversionTool,
            Integer sourceSrsEpsg,
            String srsSource) {
    }

    public record ExpandedBatchView(
            UUID id,
            String sourceFormat,
            String originalFileName,
            String status,
            Integer sourceSrsEpsg,
            String srsSource,
            String readerRoute) {
    }

    /** Ponto GNSS do lote CSV_GNSS: metadado de precisao obrigatorio. */
    public record GnssPointInput(
            @NotBlank String sourceLayer,
            String sourceRef,
            @NotBlank String fixMethod,
            Double accuracyM,
            @NotBlank String geomWkt) {
    }

    public record GnssPointView(
            UUID id,
            UUID batchId,
            String fixMethod,
            Double accuracyM) {
    }

    /** Criacao de etapa do plano de implantacao (SG-1334). */
    public record RolloutStageInput(
            @NotBlank String stage,
            @NotBlank String title,
            boolean sampleNetwork,
            LocalDate startedOn,
            LocalDate sampleDeadlineOn) {
    }

    public record ReconciliationInput(
            boolean inBase,
            boolean inDrawing,
            String baseRef,
            String drawingRef,
            String baseGeomWkt,
            String drawingGeomWkt,
            String baseAttrDigest,
            String drawingAttrDigest) {
    }

    public record ReconciliationView(
            UUID id,
            UUID batchId,
            String outcome,
            String baseRef,
            String drawingRef,
            String resolution) {
    }

    public record RolloutStageView(
            UUID id,
            String stage,
            String title,
            String status,
            boolean sampleNetwork,
            LocalDate startedOn,
            LocalDate sampleDeadlineOn,
            LocalDate finishedOn,
            boolean trainingDelivered) {
    }
}

package br.com.ziipsan.intelligence.gis.changeset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Contratos da GIS-04: pacotes de edição, itens com antes/depois para a
 * tela de revisão, decisões, travas por área e a "rede na data".
 * Geometrias trafegam em WKT (SRID 4674) — o mapa web é da GIS-06; aqui
 * o antes/depois é por atributos e geometria textual, sem inventar uma
 * renderização que ainda não existe no produto.
 */
public final class GisChangeSetDtos {

    private GisChangeSetDtos() {
    }

    public record CreateInput(
            @NotBlank String type,
            @NotBlank String channel,
            String extentWkt,
            String serviceOrderRef,
            String projectRef,
            String technicalReport,
            Map<String, Object> qualityReport) {
    }

    public record ItemInput(
            @NotBlank String operation,
            @NotBlank String featureTable,
            @NotNull UUID featureId,
            Map<String, Object> proposedState,
            String proposedGeomWkt,
            String proposedGeomPointWkt) {
    }

    public record ReviewInput(
            String reason) {
    }

    public record ApproveInput(
            String warningJustification) {
    }

    public record LockInput(
            @NotBlank String geomWkt,
            @NotNull Instant expiresAt) {
    }

    public record ChangeSetView(
            UUID id,
            String type,
            String channel,
            String status,
            UUID authorId,
            String extentWkt,
            String serviceOrderRef,
            String projectRef,
            String technicalReport,
            Map<String, Object> qualityReport,
            Instant submittedAt,
            UUID reviewedBy,
            Instant reviewedAt,
            Instant appliedAt,
            Instant createdAt,
            Instant updatedAt,
            long itemCount) {
    }

    /** Item com o antes (snapshot da base) e o depois (estado proposto). */
    public record ItemView(
            UUID id,
            String operation,
            String featureTable,
            UUID featureId,
            Long baseVersion,
            Map<String, Object> before,
            Map<String, Object> after,
            String proposedGeomWkt,
            String proposedGeomPointWkt,
            Map<String, Object> validationResult) {
    }

    public record ReviewView(
            UUID id,
            String decision,
            UUID reviewerId,
            String reason,
            Instant createdAt) {
    }

    /** Pacote + itens antes/depois + decisões + problemas de validação. */
    public record ReviewDetailView(
            ChangeSetView changeSet,
            List<ItemView> items,
            List<ReviewView> reviews,
            List<Map<String, Object>> validationIssues) {
    }

    public record LockView(
            UUID id,
            UUID changeSetId,
            String geomWkt,
            UUID ownerId,
            Instant expiresAt) {
    }

    /** Linha da view gis_change_set_pending_review (superfície da P2.28). */
    public record PendingReviewView(
            UUID changeSetId,
            String type,
            String channel,
            String status,
            UUID authorId,
            Instant submittedAt,
            Instant createdAt,
            long itemCount) {
    }

    /** Uma feição da "rede na data": snapshot completo como registrado. */
    public record NetworkFeatureView(
            String featureTable,
            UUID featureId,
            String code,
            String lifecycleStatus,
            Map<String, Object> snapshot) {
    }

    public record NetworkAtView(
            String mode,
            LocalDate businessDate,
            Instant recordedAt,
            List<NetworkFeatureView> features) {
    }

    public record PageView<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }
}

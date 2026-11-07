package br.com.ziipsan.intelligence.gis.validation;

import jakarta.validation.constraints.NotBlank;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** DTOs do painel de qualidade GIS (P2.21, §5.5). */
public final class GisQualityDtos {

    private GisQualityDtos() {
    }

    /** Problema de validação como o painel o mostra; envelope em WKT (SRID 4674). */
    public record IssueView(UUID id, String ruleCode, String severity,
                            String featureTable, UUID featureId, UUID feature2Id,
                            UUID changeSetId, String status,
                            String justification, UUID acceptedBy, OffsetDateTime acceptedAt,
                            OffsetDateTime detectedAt, OffsetDateTime resolvedAt,
                            String envelopeWkt) {
    }

    /** Página simples no formato dos demais painéis: conteúdo + totais. */
    public record PageView<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    }

    /** Contagem de problemas abertos de uma regra. */
    public record RuleCount(String ruleCode, long openCount) {
    }

    /** Contagem de problemas abertos de uma severidade. */
    public record SeverityCount(String severity, long openCount) {
    }

    /** Agregados do painel: abertos por severidade e por regra, e totais por estado. */
    public record SummaryView(long open, long accepted, long resolved,
                              List<SeverityCount> openBySeverity, List<RuleCount> openByRule) {
    }

    /** Aceite de problema: justificativa obrigatória (§5.5). */
    public record AcceptInput(@NotBlank String justification) {
    }
}

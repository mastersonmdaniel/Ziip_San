package br.com.ziipsan.operations.metering.selfreading;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * O desfecho completo de uma submissao (II-7): estado, motivos, score
 * (null quando nao houve leitura extraida -- nenhum score e fabricado),
 * leitura OCR (null quando nao extraida), resultado da prova geografica
 * e, so no aceite, o handoff para a critica da II-2.
 */
public record SelfReadingOutcome(UUID submissionId, UUID unitId, YearMonth reference,
                                 SelfReadingStatus status, List<SelfReadingReason> reasons,
                                 ReadingScore score, Integer ocrReading,
                                 GeoProofResult geo, CritiqueHandoff handoff) {

    public SelfReadingOutcome {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}

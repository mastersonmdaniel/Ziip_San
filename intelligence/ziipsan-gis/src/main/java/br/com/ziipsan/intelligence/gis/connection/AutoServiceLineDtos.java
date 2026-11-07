package br.com.ziipsan.intelligence.gis.connection;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Contratos da geração de ramais automáticos (GIS-05, P2.23). O
 * resultado separa gerados de pulados (com motivo): ligação fora da
 * distância, sem ponto ou sem arco compatível nunca some em silêncio.
 */
public final class AutoServiceLineDtos {

    private AutoServiceLineDtos() {
    }

    /** Resultado de uma ligação na rodada de geração. */
    public record GeneratedLineView(
            UUID serviceConnectionId,
            UUID gisConnectionId,
            UUID arcId,
            BigDecimal distanceM,
            boolean generated,
            String skipReason) {
    }

    /** Rodada: o pacote criado (DRAFT já submetido a revisão) e o resultado por ligação. */
    public record GenerationResultView(
            UUID changeSetId,
            BigDecimal maxDistanceM,
            List<GeneratedLineView> lines) {

        public long generatedCount() {
            return lines.stream().filter(GeneratedLineView::generated).count();
        }

        public long skippedCount() {
            return lines.size() - generatedCount();
        }
    }
}

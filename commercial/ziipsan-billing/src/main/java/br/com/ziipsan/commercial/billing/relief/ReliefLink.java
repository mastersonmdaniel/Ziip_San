package br.com.ziipsan.commercial.billing.relief;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Ligacao origem -> desonerada (DESENHO-PA02 secao 3.4): a prova de
 * proveniencia de cada tabela publicada. Cada preco desonerado sabe de que
 * preco nasceu, com que factor e com que arredondamento.
 *
 * <p>Para {@code FIXED_FEE}, {@code fineClassification} regista a
 * classificacao previa das multas exigida pela NT 6.6.2 (ver
 * {@link FineClassification}); para os outros tipos e {@code null}.
 */
public record ReliefLink(UUID id, UUID programId, TargetKind targetKind,
                         UUID sourceVersionId, UUID relievedVersionId,
                         BigDecimal appliedRatePct, BigDecimal factor,
                         String roundingPolicy, FineClassification fineClassification,
                         UUID generationRunId) {

    public enum TargetKind { TARIFF_VERSION, SERVICE_PRICE_TABLE, FIXED_FEE }

    public ReliefLink {
        Objects.requireNonNull(id, "id da ligacao e obrigatorio");
        Objects.requireNonNull(programId, "programa da ligacao e obrigatorio");
        Objects.requireNonNull(targetKind, "tipo de alvo da ligacao e obrigatorio");
        Objects.requireNonNull(sourceVersionId, "versao de origem e obrigatoria");
        Objects.requireNonNull(relievedVersionId, "versao desonerada e obrigatoria");
        Objects.requireNonNull(appliedRatePct, "aliquota aplicada e obrigatoria");
        Objects.requireNonNull(factor, "factor aplicado e obrigatorio");
        if (roundingPolicy == null || roundingPolicy.isBlank()) {
            throw new IllegalArgumentException("politica de arredondamento da ligacao e obrigatoria");
        }
        Objects.requireNonNull(generationRunId, "corrida de geracao e obrigatoria");
    }
}

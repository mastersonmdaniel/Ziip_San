package br.com.ziipsan.core.effluent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Eficiencia de remocao de DBO por ETE e por periodo, sobre os
 * resultados do automonitoramento (afluente x efluente, como o
 * laboratorio os reporta — a chave {@code DBO} e o analito nomeado
 * no aceite; os VALORES sao sempre resultados reais lancados, nunca
 * constantes).
 *
 * <p>Formula declarada: medias aritmeticas dos pares do periodo e
 * {@code remocao % = (1 - mediaEfluente / mediaAfluente) x 100},
 * com 2 casas, HALF_UP. Eficiencia negativa (efluente acima do
 * afluente no periodo) e devolvida como calculada — fato nomeado,
 * nunca escondida nem truncada em zero. Periodo sem par, afluente
 * nao positivo ou efluente negativo sao recusas nomeadas: sem base
 * de calculo nao ha eficiencia presumida.
 */
public final class BodEfficiency {

    /** Um par afluente/efluente do mesmo ciclo de amostragem, como reportado pelo laboratorio. */
    public record BodPair(BigDecimal influentValue, BigDecimal effluentValue) {
        public BodPair {
            if (influentValue == null || effluentValue == null)
                throw new IllegalArgumentException("par de DBO exige afluente e efluente reportados");
            if (influentValue.signum() <= 0)
                throw new IllegalArgumentException("DBO afluente deve ser maior que zero: " + influentValue);
            if (effluentValue.signum() < 0)
                throw new IllegalArgumentException("DBO efluente nao pode ser negativa: " + effluentValue);
        }
    }

    public record Result(
            UUID wastewaterUnitId,
            LocalDate periodStart,
            LocalDate periodEnd,
            int pairCount,
            BigDecimal meanInfluent,
            BigDecimal meanEffluent,
            BigDecimal removalPercent) {}

    private BodEfficiency() {}

    public static Result assess(UUID wastewaterUnitId, LocalDate periodStart, LocalDate periodEnd,
                                List<BodPair> pairs) {
        if (wastewaterUnitId == null) throw new IllegalArgumentException("a ETE e obrigatoria");
        if (periodStart == null || periodEnd == null || periodEnd.isBefore(periodStart))
            throw new IllegalArgumentException("periodo da eficiencia de DBO invalido");
        if (pairs == null || pairs.isEmpty())
            throw new IllegalArgumentException("periodo sem par afluente/efluente de DBO:"
                    + " eficiencia nao se presume sem automonitoramento");
        BigDecimal sumIn = pairs.stream().map(BodPair::influentValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumOut = pairs.stream().map(BodPair::effluentValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal n = BigDecimal.valueOf(pairs.size());
        BigDecimal meanIn = sumIn.divide(n, 6, RoundingMode.HALF_UP);
        BigDecimal meanOut = sumOut.divide(n, 6, RoundingMode.HALF_UP);
        BigDecimal removal = BigDecimal.ONE
                .subtract(meanOut.divide(meanIn, 8, RoundingMode.HALF_UP))
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
        return new Result(wastewaterUnitId, periodStart, periodEnd, pairs.size(),
                meanIn.setScale(2, RoundingMode.HALF_UP),
                meanOut.setScale(2, RoundingMode.HALF_UP), removal);
    }
}

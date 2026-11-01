package br.com.ziipsan.operations.metering.rules;

import java.math.BigDecimal;
import java.util.List;

/**
 * Faixas que definem a leitura esperada a partir do consumo médio (relatório 02, 2.5). Os cortes e
 * os fatores são política do prestador: o legado os tem fixos no código, e aqui não existe
 * conjunto "padrão" no código de produção.
 *
 * <p>Para uma média {@code m} e a leitura anterior {@code LA}, a faixa que contém {@code m} dá
 * <pre>
 *   inicial = LA + lowFactor  * m + lowAddend
 *   final   = LA + highFactor * m + highAddend
 * </pre>
 * Os fatores são {@link BigDecimal} exatos (de texto): {@code new BigDecimal(0.7)} carregaria o erro
 * binário de 0,7 e arredondaria 73,5 para 73 (armadilha 1 do relatório 02).
 */
public record ExpectedRangePolicy(List<Band> bands) {

    /**
     * @param maxAverageInclusive maior média (inclusive) atendida pela faixa; nulo na última faixa (sem teto)
     */
    public record Band(Integer maxAverageInclusive, BigDecimal lowFactor, int lowAddend,
                       BigDecimal highFactor, int highAddend) {
        public Band {
            MeteringInputException.require(maxAverageInclusive == null || maxAverageInclusive >= 0,
                    MeteringErrorCode.INVALID_INPUT, "teto da faixa negativo");
            MeteringInputException.notNull(lowFactor, "lowFactor");
            MeteringInputException.notNull(highFactor, "highFactor");
            MeteringInputException.require(lowFactor.signum() >= 0 && highFactor.signum() >= 0,
                    MeteringErrorCode.INVALID_INPUT, "fatores da faixa não podem ser negativos");
        }
    }

    public ExpectedRangePolicy {
        MeteringInputException.require(bands != null && !bands.isEmpty(), MeteringErrorCode.INVALID_INPUT,
                "a política de faixa esperada precisa de ao menos uma faixa");
        bands = List.copyOf(bands);
        for (int i = 0; i < bands.size(); i++) {
            boolean last = i == bands.size() - 1;
            Integer max = bands.get(i).maxAverageInclusive();
            MeteringInputException.require(last == (max == null), MeteringErrorCode.INVALID_INPUT,
                    "somente a última faixa não tem teto");
            MeteringInputException.require(i == 0 || max == null
                            || max > bands.get(i - 1).maxAverageInclusive(),
                    MeteringErrorCode.INVALID_INPUT, "os tetos das faixas devem ser crescentes");
        }
    }

    /** Faixa que atende a média informada. */
    Band bandFor(int average) {
        for (Band band : bands) {
            if (band.maxAverageInclusive() == null || average <= band.maxAverageInclusive()) {
                return band;
            }
        }
        throw new IllegalStateException("inalcançável: a última faixa não tem teto");
    }
}

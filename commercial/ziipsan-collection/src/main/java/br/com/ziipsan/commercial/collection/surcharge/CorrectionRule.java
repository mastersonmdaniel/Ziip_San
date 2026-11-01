package br.com.ziipsan.commercial.collection.surcharge;

/**
 * Correção monetária. Não há valor padrão: o tenant declara {@link #none()} ou o índice.
 *
 * <p>Com índice, a correção é {@code principal x (fator(mês do pagamento) / fator(mês do
 * vencimento) - 1)}, arredondada uma única vez. Fator ausente <b>não</b> é substituído por outro
 * (o GSAN recorria ao menor índice cadastrado): lança {@link MissingIndexException}, porque
 * calcular com o índice errado em silêncio seria cobrar valor errado do consumidor.
 */
public sealed interface CorrectionRule {

    record None() implements CorrectionRule {
    }

    /**
     * @param series           fatores mensais do índice
     * @param minMonthsToApply só corrige se a diferença entre o mês do pagamento e o do
     *                         vencimento for ao menos esta (0 = corrige sempre que houver
     *                         diferença de mês)
     * @param allowNegative    se {@code false}, índice em queda não reduz a dívida (correção
     *                         limitada a zero)
     */
    record ByIndex(IndexSeries series, int minMonthsToApply, boolean allowNegative) implements CorrectionRule {
        public ByIndex {
            if (series == null) {
                throw new IllegalArgumentException("A série do índice é obrigatória");
            }
            if (minMonthsToApply < 0) {
                throw new IllegalArgumentException("minMonthsToApply não pode ser negativo");
            }
        }
    }

    static CorrectionRule none() {
        return new None();
    }

    static CorrectionRule byIndex(IndexSeries series, int minMonthsToApply, boolean allowNegative) {
        return new ByIndex(series, minMonthsToApply, allowNegative);
    }
}

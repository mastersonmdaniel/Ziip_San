package br.com.ziipsan.core.certification.env14001;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * O criterio de significancia CONFIGURAVEL PELO TENANT (aceite da
 * ED-70): o tenant declara os fatores de avaliacao (nome + escore
 * maximo de cada um) e o limiar a partir do qual um aspecto e
 * significativo. NENHUM fator, escore ou limiar de fabrica existe
 * neste pacote — criterio sem fator, fator duplicado, maximo nao
 * positivo ou limiar fora do intervalo possivel sao recusados.
 *
 * <p>O criterio e registro da norma: a ativacao e COMPONENTE e o
 * construtor canonico exige a norma ligada (padrao ED-73/D1).</p>
 */
public record SignificanceCriteria(Iso14001Activation activation, UUID id, UUID tenantId,
                                   String name, List<Factor> factors, BigDecimal threshold) {

    /** Um fator do criterio: nome declarado pelo tenant e escore maximo dele. */
    public record Factor(String name, BigDecimal maxScore) {
        public Factor {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("nome do fator do criterio e obrigatorio");
            }
            Objects.requireNonNull(maxScore, "maxScore do fator " + name + " e obrigatorio");
            if (maxScore.signum() <= 0) {
                throw new IllegalArgumentException("maxScore do fator " + name
                        + " deve ser maior que zero");
            }
            name = name.trim();
        }
    }

    public SignificanceCriteria {
        Objects.requireNonNull(activation, "activation e obrigatoria (componente do registro)");
        activation.requireEnabled();
        Objects.requireNonNull(id, "id do criterio e obrigatorio");
        Objects.requireNonNull(tenantId, "tenantId do criterio e obrigatorio");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("nome do criterio de significancia e obrigatorio "
                    + "(declarado pelo tenant)");
        }
        if (factors == null || factors.isEmpty()) {
            throw new IllegalArgumentException("o criterio de significancia exige ao menos um "
                    + "fator declarado pelo tenant: nenhum fator de fabrica existe");
        }
        factors = List.copyOf(factors);
        if (factors.stream().map(Factor::name).distinct().count() != factors.size()) {
            throw new IllegalArgumentException("fator duplicado no criterio de significancia");
        }
        Objects.requireNonNull(threshold, "threshold (limiar de significancia) e obrigatorio "
                + "(declarado pelo tenant)");
        BigDecimal maxTotal = factors.stream().map(Factor::maxScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (threshold.signum() < 0 || threshold.compareTo(maxTotal) > 0) {
            throw new IllegalArgumentException("threshold " + threshold + " fora do intervalo "
                    + "possivel do criterio [0, " + maxTotal + "]");
        }
        name = name.trim();
    }

    /** Soma dos escores maximos dos fatores: o teto de qualquer avaliacao neste criterio. */
    public BigDecimal maxTotal() {
        return factors.stream().map(Factor::maxScore).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Fator pelo nome, quando declarado neste criterio. */
    public java.util.Optional<Factor> factor(String factorName) {
        if (factorName == null) return java.util.Optional.empty();
        return factors.stream().filter(f -> f.name().equals(factorName.trim())).findFirst();
    }
}

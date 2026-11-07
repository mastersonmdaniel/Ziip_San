package br.com.ziipsan.commercial.accounting.receivable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * Provisao para devedores duvidosos pelo metodo de acc.doubtful_method (ED-32).
 * Metodos OFICIAIS do catalogo PARAMETROS-DO-TENANT.md (origem 20c §5.2; CPC 48):
 *   TARGET_WRITE_OFF; AGING_MATRIX; EXPECTED_LOSS
 * sem padrao de fabrica — escolha do contador do tenant (classe d).
 * Correcao pos-revisao (G1): a versao anterior inventava PERCENTAGE /
 * AGING_BUCKETS / INDIVIDUAL_ASSESSMENT, que nao existem no parametro oficial;
 * foram removidos.
 *
 * Semantica de cada metodo oficial (da denominacao do parametro e do CPC 48;
 * nenhuma formula alem disso foi inventada):
 * - TARGET_WRITE_OFF: a provisao e o proprio valor-alvo de baixa (write-off)
 *   definido pelo contador do tenant para o periodo; nao pode exceder o saldo.
 * - AGING_MATRIX: matriz de aging — soma, por faixa de atraso, de
 *   (saldo da faixa x taxa da faixa); a matriz faixa->taxa e configuracao do tenant.
 * - EXPECTED_LOSS: perda de credito esperada (CPC 48) — soma, por segmento da
 *   curva de perda, de (exposicao x taxa de perda esperada do segmento).
 *   A CURVA DE PERDA E ENTRADA desta tarefa: quem a calcula a partir da
 *   inadimplencia observada e a ED-100 ("curva de perda disponivel para a
 *   provisao ... na opcao EXPECTED_LOSS de acc.doubtful_method (ED-32)").
 */
public final class DoubtfulProvision {

    public enum Method { TARGET_WRITE_OFF, AGING_MATRIX, EXPECTED_LOSS }

    /** Faixa da matriz de aging: saldo na faixa e taxa de provisao da faixa. */
    public record AgingBucket(BigDecimal balance, BigDecimal rate) {
        public AgingBucket {
            if (balance == null || balance.signum() < 0) throw new IllegalArgumentException("saldo da faixa invalido: " + balance);
            requireRate(rate);
        }
    }

    /**
     * Segmento da curva de perda consumida por EXPECTED_LOSS: faixa de atraso,
     * exposicao (saldo exposto na faixa) e taxa de perda esperada da faixa.
     * A lista de segmentos e a forma de entrada da curva que a ED-100 fornece.
     */
    public record LossCurveSegment(String bracket, BigDecimal exposure, BigDecimal expectedLossRate) {
        public LossCurveSegment {
            if (bracket == null || bracket.isBlank()) throw new IllegalArgumentException("faixa da curva de perda e obrigatoria");
            if (exposure == null || exposure.signum() < 0) throw new IllegalArgumentException("exposicao da curva invalida: " + exposure);
            requireRate(expectedLossRate);
        }
    }

    private DoubtfulProvision() {}

    /**
     * Despachante por metodo oficial. Parametros nao usados pelo metodo
     * escolhido sao ignorados (podem ser nulos).
     */
    public static BigDecimal provision(Method method, BigDecimal targetWriteOffAmount, BigDecimal totalBalance,
                                       List<AgingBucket> agingMatrix, List<LossCurveSegment> lossCurve) {
        Objects.requireNonNull(method, "acc.doubtful_method e obrigatorio");
        return switch (method) {
            case TARGET_WRITE_OFF -> targetWriteOff(targetWriteOffAmount, totalBalance);
            case AGING_MATRIX -> agingMatrix(agingMatrix);
            case EXPECTED_LOSS -> expectedLoss(lossCurve);
        };
    }

    /** TARGET_WRITE_OFF: provisao = valor-alvo de baixa definido pelo contador. */
    public static BigDecimal targetWriteOff(BigDecimal targetWriteOffAmount, BigDecimal totalBalance) {
        requireNonNegative(targetWriteOffAmount, "valor-alvo de baixa");
        requireNonNegative(totalBalance, "saldo total");
        if (targetWriteOffAmount.compareTo(totalBalance) > 0)
            throw new IllegalArgumentException("valor-alvo de baixa excede o saldo total: " + targetWriteOffAmount + " > " + totalBalance);
        return targetWriteOffAmount.setScale(2, RoundingMode.HALF_UP);
    }

    /** AGING_MATRIX: soma de saldo x taxa por faixa de aging. */
    public static BigDecimal agingMatrix(List<AgingBucket> agingMatrix) {
        Objects.requireNonNull(agingMatrix, "matriz de aging e obrigatoria neste metodo");
        if (agingMatrix.isEmpty()) throw new IllegalArgumentException("matriz de aging nao pode ser vazia");
        BigDecimal sum = BigDecimal.ZERO;
        for (AgingBucket b : agingMatrix) sum = sum.add(b.balance().multiply(b.rate()));
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    /** EXPECTED_LOSS (CPC 48): soma de exposicao x taxa de perda esperada por segmento da curva. */
    public static BigDecimal expectedLoss(List<LossCurveSegment> lossCurve) {
        Objects.requireNonNull(lossCurve, "curva de perda e obrigatoria neste metodo (fornecida pela ED-100)");
        if (lossCurve.isEmpty()) throw new IllegalArgumentException("curva de perda nao pode ser vazia");
        BigDecimal sum = BigDecimal.ZERO;
        for (LossCurveSegment s : lossCurve) sum = sum.add(s.exposure().multiply(s.expectedLossRate()));
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    private static void requireNonNegative(BigDecimal v, String name) {
        if (v == null || v.signum() < 0) throw new IllegalArgumentException(name + " invalido: " + v);
    }
    private static void requireRate(BigDecimal r) {
        if (r == null || r.signum() < 0 || r.compareTo(BigDecimal.ONE) > 0) throw new IllegalArgumentException("taxa invalida (0..1): " + r);
    }
}

package br.com.ziipsan.commercial.billing.math;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Repartição de um valor em centavos, sem perder nem criar dinheiro: a soma das partes é sempre o
 * total.
 *
 * <p>Há duas políticas, escolhidas de forma explícita por quem chama:
 * <ul>
 *   <li>{@link #proportional}: maior resto. Cada parte recebe o piso em centavos do seu quinhão
 *       exato; os centavos que sobram vão, um a um, às partes de maior resto. Empate de resto: a de
 *       índice maior recebe primeiro (assim 70,00 em três partes iguais dá 23,33 / 23,33 / 23,34).
 *       Nenhuma parte nasce negativa e cada uma fica a menos de 1 centavo do quinhão exato.</li>
 *   <li>{@link #residueToFirst}: política do legado para repartir um débito por categoria. Quociente
 *       por peso truncado a 2 casas; a diferença inteira vai para a primeira parte.</li>
 * </ul>
 */
public final class Apportionment {

    private Apportionment() {
    }

    /**
     * Reparte {@code total} (em centavos exatos) proporcionalmente aos pesos, por maior resto.
     *
     * @param total   valor com no máximo 2 casas e não negativo
     * @param weights pesos não negativos; ao menos um positivo se o total for diferente de zero
     */
    public static List<BigDecimal> proportional(BigDecimal total, List<Long> weights) {
        if (total.signum() < 0) {
            throw new IllegalArgumentException("Não se reparte valor negativo: " + total);
        }
        if (weights.isEmpty()) {
            throw new IllegalArgumentException("Sem partes para repartir");
        }
        BigInteger cents = total.movePointRight(2).toBigIntegerExact();
        BigInteger weightSum = BigInteger.ZERO;
        for (Long w : weights) {
            if (w < 0) {
                throw new IllegalArgumentException("Peso negativo: " + w);
            }
            weightSum = weightSum.add(BigInteger.valueOf(w));
        }
        List<BigDecimal> result = new ArrayList<>(weights.size());
        if (weightSum.signum() == 0) {
            if (cents.signum() != 0) {
                throw new IllegalArgumentException("Todos os pesos são zero, mas há valor a repartir: " + total);
            }
            for (int i = 0; i < weights.size(); i++) {
                result.add(Money.ZERO);
            }
            return result;
        }

        int n = weights.size();
        BigInteger[] floor = new BigInteger[n];
        BigInteger[] remainder = new BigInteger[n];
        BigInteger distributed = BigInteger.ZERO;
        for (int i = 0; i < n; i++) {
            BigInteger numerator = cents.multiply(BigInteger.valueOf(weights.get(i)));
            BigInteger[] qr = numerator.divideAndRemainder(weightSum);
            floor[i] = qr[0];
            remainder[i] = qr[1];
            distributed = distributed.add(qr[0]);
        }
        int leftover = cents.subtract(distributed).intValueExact();

        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            order.add(i);
        }
        // maior resto primeiro; empate: índice maior primeiro
        order.sort(Comparator.<Integer, BigInteger>comparing(i -> remainder[i]).reversed()
                .thenComparing(Comparator.<Integer>naturalOrder().reversed()));
        for (int k = 0; k < leftover; k++) {
            int idx = order.get(k);
            floor[idx] = floor[idx].add(BigInteger.ONE);
        }
        for (int i = 0; i < n; i++) {
            result.add(new BigDecimal(floor[i]).movePointLeft(2));
        }
        return result;
    }

    /** Atalho para pesos inteiros (ex.: número de economias). */
    public static List<BigDecimal> byIntegerWeights(BigDecimal total, List<Integer> weights) {
        return proportional(total, weights.stream().map(Integer::longValue).toList());
    }

    /**
     * Política do legado (repartição de débito por categoria): {@code fator = total / Σpesos}
     * truncado a 2 casas; cada parte é {@code fator × peso}; a diferença até o total vai para a
     * <b>primeira</b> parte. Arredondamento: {@link RoundingMode#DOWN}.
     */
    public static List<BigDecimal> residueToFirst(BigDecimal total, List<Integer> weights) {
        long sum = weights.stream().mapToLong(Integer::longValue).sum();
        if (sum <= 0) {
            throw new IllegalArgumentException("Soma dos pesos deve ser positiva");
        }
        BigDecimal factor = total.divide(BigDecimal.valueOf(sum), Money.SCALE, RoundingMode.DOWN);
        List<BigDecimal> parts = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO;
        for (Integer w : weights) {
            BigDecimal part = Money.truncate(factor.multiply(BigDecimal.valueOf(w)));
            parts.add(part);
            allocated = allocated.add(part);
        }
        parts.set(0, parts.get(0).add(total.subtract(allocated)));
        return parts;
    }
}

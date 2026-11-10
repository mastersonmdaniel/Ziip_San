package br.com.ziipsan.intelligence.losses.dma;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Ranking de DMC por RETORNO (II-6, E4) — indice DECLARADO, distinto
 * da fila de prioridade da ED-85 ({@code DmcPriorityQueue}, que
 * ordena pelo excesso sobre a meta do tenant):
 *
 * <ul>
 *   <li><b>Com tarifa</b> (parametro do chamador, em moeda/m3):
 *       indice = volume de perdas recuperavel x tarifa — o retorno
 *       potencial exacto ({@link BigDecimal}) de atacar o DMC.
 *       "Recuperavel" = perdas reais quando conhecidas; sem elas,
 *       perdas totais, e a base do indice fica DECLARADA por linha
 *       ({@link LossBasis}).</li>
 *   <li><b>Sem tarifa</b> (tarifa e {@code [A DEFINIR]} — nao ha
 *       fonte nesta entrega): o ranking sai por VOLUME de perdas e
 *       o modo {@link Mode#LOSS_VOLUME_ONLY} fica declarado no
 *       resultado; nenhum preco e presumido.</li>
 * </ul>
 *
 * <p>Ordenacao deterministica: indice desc; desempate 1 pelo volume
 * de perdas desc; desempate 2 (final) pelo codigo do DMC asc —
 * mesma entrada ⇒ mesma saida, sempre.
 */
public final class DmcReturnRanking {

    public enum Mode { TARIFF_WEIGHTED, LOSS_VOLUME_ONLY }
    public enum LossBasis { REAL_LOSSES, TOTAL_LOSSES }

    /** Entrada: um DMC com os volumes do seu balanco de periodo (exactos). */
    public record Entry(String dmcCode, BigDecimal totalLossesM3, Optional<BigDecimal> realLossesM3) {
        public Entry {
            if (dmcCode == null || dmcCode.isBlank())
                throw new IllegalArgumentException("codigo do DMC obrigatorio no ranking");
            Objects.requireNonNull(totalLossesM3, "perdas totais do DMC obrigatorias");
            Objects.requireNonNull(realLossesM3, "perdas reais: Optional obrigatorio (vazio = ausente declarado)");
            realLossesM3.ifPresent(v -> {
                if (v.signum() < 0) throw new IllegalArgumentException("perdas reais negativas: " + v);
            });
            /** Volume que entra no indice: reais se conhecidas, senao totais (declarado por linha). */
        }
        public BigDecimal recoverableLossesM3() { return realLossesM3.orElse(totalLossesM3); }
        public LossBasis basis() { return realLossesM3.isPresent() ? LossBasis.REAL_LOSSES : LossBasis.TOTAL_LOSSES; }
    }

    public record Ranked(String dmcCode, int position, BigDecimal index, LossBasis basis,
                         BigDecimal recoverableLossesM3) {}

    public record Ranking(Mode mode, Optional<BigDecimal> tariffPerM3, List<Ranked> ranked) {
        public Ranking { ranked = List.copyOf(ranked); }
    }

    private DmcReturnRanking() {}

    /**
     * @param tariffPerM3OrEmpty tarifa media (moeda/m3) como PARAMETRO;
     *        vazio = {@code [A DEFINIR]} ⇒ ranking por volume, declarado.
     */
    public static Ranking rank(List<Entry> entries, Optional<BigDecimal> tariffPerM3OrEmpty) {
        Objects.requireNonNull(entries, "entradas do ranking obrigatorias");
        Objects.requireNonNull(tariffPerM3OrEmpty, "tarifa: Optional obrigatorio (vazio = [A DEFINIR])");
        tariffPerM3OrEmpty.ifPresent(t -> {
            if (t.signum() <= 0) throw new IllegalArgumentException("tarifa declarada deve ser positiva: " + t);
        });
        Mode mode = tariffPerM3OrEmpty.isPresent() ? Mode.TARIFF_WEIGHTED : Mode.LOSS_VOLUME_ONLY;
        List<Entry> sorted = entries.stream().sorted(Comparator
                .comparing((Entry e) -> index(e, tariffPerM3OrEmpty), Comparator.reverseOrder())
                .thenComparing(Entry::recoverableLossesM3, Comparator.reverseOrder())
                .thenComparing(Entry::dmcCode))
                .toList();
        List<Ranked> ranked = new java.util.ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            Entry e = sorted.get(i);
            ranked.add(new Ranked(e.dmcCode(), i + 1, index(e, tariffPerM3OrEmpty), e.basis(),
                    e.recoverableLossesM3()));
        }
        return new Ranking(mode, tariffPerM3OrEmpty, ranked);
    }

    private static BigDecimal index(Entry e, Optional<BigDecimal> tariff) {
        return tariff.map(t -> e.recoverableLossesM3().multiply(t)).orElse(e.recoverableLossesM3());
    }
}

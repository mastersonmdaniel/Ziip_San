package br.com.ziipsan.intelligence.losses.pressure;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Fila de suspeitas de vazamento ponderada pela vazao noturna
 * (ED-86, OPE-17): a suspeita de cada DMC e a vazao noturna LIQUIDA
 * medida pela ED-85 ({@code NightFlowAnalyzer}), e o peso declarado
 * desta entrega e a vazao liquida POR LIGACAO (L/h por ligacao) —
 * DMCs comparam-se pelo sinal de perda por ligacao, nao pelo porte.
 * Desempate: vazao liquida total (maior primeiro) e codigo do DMC.
 * DMC sem sinal positivo (liquida &lt;= 0) nao e suspeito: fica fora
 * da fila e e contado, nunca ranqueado com peso presumido.
 */
public final class LeakSuspectQueue {

    public record Suspect(String dmcCode, double netNightFlowLPerH, int connections,
                          Double mainsLengthKmOrNull, String dmcClassOrNull) {
        public Suspect {
            if (dmcCode == null || dmcCode.isBlank()) throw new IllegalArgumentException("DMC da suspeita obrigatorio");
            if (Double.isNaN(netNightFlowLPerH)) throw new IllegalArgumentException("vazao noturna liquida NaN");
            if (connections <= 0) throw new IllegalArgumentException(
                    "fila ponderada por ligacao exige ligacoes > 0: " + connections);
            if (mainsLengthKmOrNull != null && mainsLengthKmOrNull <= 0)
                throw new IllegalArgumentException("extensao de rede deve ser positiva quando conhecida");
        }
        /** Peso da fila: vazao noturna liquida por ligacao (L/h/ligacao). */
        public double netPerConnectionLPerH() { return netNightFlowLPerH / connections; }
        public OptionalDouble netPerKmLPerH() {
            return mainsLengthKmOrNull == null ? OptionalDouble.empty()
                    : OptionalDouble.of(netNightFlowLPerH / mainsLengthKmOrNull);
        }
    }

    public record Ranked(Suspect suspect, double weightLPerHPerConnection) {}

    public record Queue(List<Ranked> ranked, long excludedWithoutPositiveNet) {}

    private LeakSuspectQueue() {}

    public static Queue rank(List<Suspect> suspects) {
        Objects.requireNonNull(suspects, "suspeitas obrigatorias");
        long excluded = suspects.stream().filter(s -> s.netNightFlowLPerH() <= 0).count();
        List<Ranked> ranked = suspects.stream()
                .filter(s -> s.netNightFlowLPerH() > 0)
                .map(s -> new Ranked(s, s.netPerConnectionLPerH()))
                .sorted(Comparator.comparingDouble(Ranked::weightLPerHPerConnection).reversed()
                        .thenComparing(Comparator.comparingDouble(
                                (Ranked r) -> r.suspect().netNightFlowLPerH()).reversed())
                        .thenComparing(r -> r.suspect().dmcCode()))
                .toList();
        return new Queue(ranked, excluded);
    }
}

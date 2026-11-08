package br.com.ziipsan.intelligence.losses.anomaly;

import java.util.Set;

/**
 * As quatro regras do aceite da ED-94 — estatistica simples e
 * auditavel, NENHUM modelo opaco e nenhum ML (fronteira do ADR-0042:
 * a deteccao PROPOE alerta explicavel; quem decide e a revisao
 * humana). Cada regra declara a quais series se aplica; regra ligada
 * pelo tenant mas inaplicavel a serie simplesmente nao produz alerta
 * naquela serie (nao e erro).
 */
public enum AnomalyRule {

    /**
     * Desvio contra a mesma hora e o mesmo dia da semana: o valor de um
     * ponto e comparado a mediana e ao MAD (desvio absoluto mediano) das
     * amostras historicas no mesmo slot (dia da semana + hora do dia);
     * dispara quando o desvio robusto atinge {@code deviationK}. Vale
     * para as tres series.
     */
    SAME_HOUR_WEEKDAY_DEVIATION(Set.of(
            AnomalySeries.SeriesKind.NIGHT_FLOW_DMC,
            AnomalySeries.SeriesKind.PRESSURE_POINT,
            AnomalySeries.SeriesKind.CONSUMPTION_CONNECTION)),

    /**
     * Consumo continuo: sequencia contigua de pelo menos
     * {@code continuousHours} horas com consumo &gt; 0 numa ligacao
     * (possivel vazamento no imovel ou medidor travado aberto).
     */
    CONTINUOUS_CONSUMPTION(Set.of(AnomalySeries.SeriesKind.CONSUMPTION_CONNECTION)),

    /**
     * Consumo zero: sequencia contigua de pelo menos {@code zeroHours}
     * horas com consumo exatamente 0 numa ligacao (possivel medidor
     * parado, ligacao cortada sem registro ou imovel desocupado).
     */
    ZERO_CONSUMPTION(Set.of(AnomalySeries.SeriesKind.CONSUMPTION_CONNECTION)),

    /**
     * Queda subita de pressao: entre duas amostras consecutivas do
     * ponto, a queda relativa atinge {@code pressureDropPercent}
     * (possivel rompimento ou manobra brusca na rede).
     */
    SUDDEN_PRESSURE_DROP(Set.of(AnomalySeries.SeriesKind.PRESSURE_POINT));

    private final Set<AnomalySeries.SeriesKind> applicableKinds;

    AnomalyRule(Set<AnomalySeries.SeriesKind> applicableKinds) {
        this.applicableKinds = applicableKinds;
    }

    public boolean appliesTo(AnomalySeries.SeriesKind kind) {
        return applicableKinds.contains(kind);
    }

    public Set<AnomalySeries.SeriesKind> applicableKinds() { return applicableKinds; }
}

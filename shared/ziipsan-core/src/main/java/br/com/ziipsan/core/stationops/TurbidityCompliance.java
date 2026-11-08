package br.com.ziipsan.core.stationops;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Turbidez da unidade filtrante (ED-87/OPE-02): com filtracao rapida,
 * o efluente de cada unidade filtrante e registrado NO MINIMO a cada
 * 2 h (Portaria GM/MS 888/2021, art. 28, §3o) — o intervalo maximo
 * entre registros consecutivos do mesmo filtro e
 * {@link StationOpsParameters#TURBIDITY_MAX_INTERVAL_MINUTES}, e o
 * mes se apura pelo PERCENTIL das leituras.
 *
 * <p>O nivel do percentil (ex.: 95) NAO vem nomeado no aceite nem nas
 * fontes: ele e ENTRADA explicita de {@link #monthlyPercentile} —
 * nenhum percentil de fabrica e presumido. Os testes usam nivel
 * sintetico declarado como fixture, nunca como norma.</p>
 *
 * <p><b>Correcao ED-87/D2 — conformidade e da JANELA, nao so dos
 * intervalos:</b> a apuracao de conformidade de um dia/periodo e
 * {@link #assessWindow}: as lacunas do inicio da janela a primeira
 * leitura, entre leituras e da ultima leitura ao fim da janela
 * entram na conta como violacoes nomeadas. Uma leitura isolada
 * NUNCA e conforme: alem das lacunas de borda, {@code compliant()}
 * exige ao menos um intervalo verificado (2+ leituras sem lacuna) —
 * leitura unica nao comprova cadencia alguma. A escolha e
 * NAO-CONFORME (e nao "indeterminado"): ha base de julgamento
 * completa (janela + teto de 120 min com fonte) e a ausencia de
 * leituras por mais de 2 h e exatamente a violacao que a regra
 * proibe; "indeterminado" neste pacote fica reservado a ausencia
 * de base (ex.: catalogo sem padrao revisado na ED-50, mes sem
 * leitura no percentil = {@code null}).</p>
 */
public final class TurbidityCompliance {

    /** Um intervalo entre registros consecutivos maior que o teto de 2 h. */
    public record Gap(String filterUnitCode, Instant from, Instant to, long gapMinutes) {}

    public record IntervalAssessment(String filterUnitCode, int readings, List<Gap> gaps) {
        public IntervalAssessment { gaps = List.copyOf(gaps); }
        /**
         * Conforme SOMENTE com cadencia comprovada: nenhuma lacuna
         * (de borda ou entre leituras) E ao menos um intervalo
         * verificado — leitura isolada (0 ou 1 leitura) nunca e
         * conforme (correcao ED-87/D2).
         */
        public boolean compliant() { return gaps.isEmpty() && readings >= 2; }
    }

    private TurbidityCompliance() {}

    /**
     * Confere os intervalos de UM filtro sobre as leituras ja ordenadas
     * do diario (ou de qualquer recorte): cada par consecutivo com
     * distancia maior que 120 min e uma violacao nomeada. ATENCAO:
     * este recorte NAO apura a cobertura de um periodo — para o
     * veredito de conformidade de um dia/periodo use
     * {@link #assessWindow}, que cobra tambem as bordas da janela.
     */
    public static IntervalAssessment assessIntervals(String filterUnitCode, List<DiaryReading> readings) {
        if (filterUnitCode == null || filterUnitCode.isBlank())
            throw new IllegalArgumentException("filterUnitCode e obrigatorio");
        if (readings == null) throw new IllegalArgumentException("leituras sao obrigatorias (podem ser vazias)");
        List<DiaryReading> sorted = turbidityOf(filterUnitCode, readings);
        return new IntervalAssessment(filterUnitCode, sorted.size(), interReadingGaps(filterUnitCode, sorted));
    }

    /**
     * Apuracao de conformidade de UM filtro sobre a JANELA
     * {@code [windowStart, windowEnd]} (o dia ou o periodo —
     * correcao ED-87/D2): so contam leituras dentro da janela, e
     * TODA lacuna maior que 120 min vira violacao nomeada — a do
     * inicio da janela a primeira leitura, as entre leituras e a
     * da ultima leitura ao fim da janela. Janela sem nenhuma
     * leitura e uma unica lacuna do tamanho da janela (quando ela
     * passa de 120 min) e, em qualquer caso, nao conforme: a regra
     * das 2 h exige cobertura da janela inteira, nao apenas
     * intervalos bem comportados entre leituras esparsas.
     */
    public static IntervalAssessment assessWindow(String filterUnitCode, List<DiaryReading> readings,
                                                  Instant windowStart, Instant windowEnd) {
        if (filterUnitCode == null || filterUnitCode.isBlank())
            throw new IllegalArgumentException("filterUnitCode e obrigatorio");
        if (readings == null) throw new IllegalArgumentException("leituras sao obrigatorias (podem ser vazias)");
        if (windowStart == null || windowEnd == null || !windowEnd.isAfter(windowStart))
            throw new IllegalArgumentException("janela da apuracao invalida (inicio e fim obrigatorios, fim apos o inicio)");
        List<DiaryReading> sorted = turbidityOf(filterUnitCode, readings).stream()
                .filter(r -> !r.measuredAt().isBefore(windowStart) && !r.measuredAt().isAfter(windowEnd))
                .toList();
        List<Gap> gaps = new ArrayList<>();
        if (sorted.isEmpty()) {
            long minutes = Duration.between(windowStart, windowEnd).toMinutes();
            if (minutes > StationOpsParameters.TURBIDITY_MAX_INTERVAL_MINUTES) {
                gaps.add(new Gap(filterUnitCode, windowStart, windowEnd, minutes));
            }
        } else {
            long leading = Duration.between(windowStart, sorted.get(0).measuredAt()).toMinutes();
            if (leading > StationOpsParameters.TURBIDITY_MAX_INTERVAL_MINUTES) {
                gaps.add(new Gap(filterUnitCode, windowStart, sorted.get(0).measuredAt(), leading));
            }
            gaps.addAll(interReadingGaps(filterUnitCode, sorted));
            long trailing = Duration.between(sorted.get(sorted.size() - 1).measuredAt(), windowEnd).toMinutes();
            if (trailing > StationOpsParameters.TURBIDITY_MAX_INTERVAL_MINUTES) {
                gaps.add(new Gap(filterUnitCode, sorted.get(sorted.size() - 1).measuredAt(), windowEnd, trailing));
            }
        }
        return new IntervalAssessment(filterUnitCode, sorted.size(), gaps);
    }

    /** Leituras de turbidez do filtro, em ordem cronologica. */
    private static List<DiaryReading> turbidityOf(String filterUnitCode, List<DiaryReading> readings) {
        return readings.stream()
                .filter(r -> r.kind() == DiaryReading.Kind.TURBIDITY
                        && filterUnitCode.equals(r.filterUnitCode()))
                .sorted(java.util.Comparator.comparing(DiaryReading::measuredAt))
                .toList();
    }

    /** Lacunas entre leituras consecutivas (ja filtradas e ordenadas) maiores que o teto de 2 h. */
    private static List<Gap> interReadingGaps(String filterUnitCode, List<DiaryReading> sorted) {
        List<Gap> gaps = new ArrayList<>();
        for (int i = 1; i < sorted.size(); i++) {
            long minutes = Duration.between(sorted.get(i - 1).measuredAt(), sorted.get(i).measuredAt()).toMinutes();
            if (minutes > StationOpsParameters.TURBIDITY_MAX_INTERVAL_MINUTES) {
                gaps.add(new Gap(filterUnitCode, sorted.get(i - 1).measuredAt(),
                        sorted.get(i).measuredAt(), minutes));
            }
        }
        return gaps;
    }

    /**
     * Percentil mensal da turbidez de UM filtro, pelo metodo do posto
     * mais proximo (nearest-rank), sobre as leituras do mes civil no
     * fuso dado. Devolve null quando o mes nao tem leitura do filtro —
     * ausencia declarada, nunca zero presumido.
     *
     * @param percentile nivel em (0, 100], explicito — [SEM FONTE] para
     *                   um nivel unico de fabrica
     */
    public static BigDecimal monthlyPercentile(List<DiaryReading> readings, String filterUnitCode,
                                               YearMonth month, ZoneId zone, double percentile) {
        if (readings == null || filterUnitCode == null || month == null || zone == null)
            throw new IllegalArgumentException("leituras, filtro, mes e fuso sao obrigatorios");
        if (percentile <= 0 || percentile > 100)
            throw new IllegalArgumentException("percentil deve estar em (0, 100]: " + percentile);
        List<BigDecimal> values = readings.stream()
                .filter(r -> r.kind() == DiaryReading.Kind.TURBIDITY
                        && filterUnitCode.equals(r.filterUnitCode()))
                .filter(r -> YearMonth.from(r.measuredAt().atZone(zone)).equals(month))
                .map(DiaryReading::value)
                .sorted()
                .toList();
        if (values.isEmpty()) return null;
        int rank = (int) Math.ceil(percentile / 100.0 * values.size());
        return values.get(Math.max(0, rank - 1));
    }
}

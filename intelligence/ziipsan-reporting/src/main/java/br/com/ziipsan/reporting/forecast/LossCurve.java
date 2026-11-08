package br.com.ziipsan.reporting.forecast;

import br.com.ziipsan.core.json.Json;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Curva de perda da ED-100, no formato que a provisao de devedores
 * duvidosos da ED-32 consome na opcao {@code EXPECTED_LOSS} de
 * {@code acc.doubtful_method}: segmentos (faixa, exposicao, taxa de perda
 * esperada), com a mesma aritmetica da ED-32 —
 * {@code provisao = soma(exposicao x taxa de perda)} (escala 2, HALF_UP).
 * A taxa de cada segmento vem da {@link CollectionCurve} estimada da
 * inadimplencia observada ({@code lossRate = 1 - recoveryRate}); a
 * exposicao e o saldo aberto por faixa na data-base (entrada explicita).
 *
 * <p>O record {@link Segment} espelha campo a campo o
 * {@code DoubtfulProvision.LossCurveSegment} da ED-32
 * ({@code bracket, exposure, expectedLossRate}) e {@link #segmentsJson()}
 * serializa nesse formato pela classe {@code Json} do core, para a fiacao
 * de integracao alimentar a ED-32 sem retraducao.
 */
public record LossCurve(List<Segment> segments, BigDecimal totalExposure, BigDecimal expectedLoss) {

    /** Segmento da curva de perda (espelho do LossCurveSegment da ED-32). */
    public record Segment(String bracket, BigDecimal exposure, BigDecimal expectedLossRate) {
    }

    public LossCurve {
        segments = List.copyOf(segments);
    }

    /** Monta a curva de perda: taxa da curva de recebimento x exposicao por faixa. */
    public static LossCurve fromCurve(CollectionCurve curve, List<BracketExposure> exposures) {
        if (curve == null) {
            throw new ForecastValidationException("CURVE_REQUIRED", "curva de recebimento e obrigatoria");
        }
        if (exposures == null) {
            throw new ForecastValidationException("EXPOSURES_REQUIRED",
                    "exposicoes por faixa sao obrigatorias (lista vazia explicita vale: sem estoque)");
        }
        List<Segment> segments = new ArrayList<>();
        BigDecimal totalExposure = BigDecimal.ZERO;
        BigDecimal sum = BigDecimal.ZERO;
        for (BracketExposure exposure : exposures) {
            CollectionCurve.Entry entry = curve.entryFor(exposure.bracketCode());
            segments.add(new Segment(exposure.bracketCode(), exposure.exposure(), entry.lossRate()));
            totalExposure = totalExposure.add(exposure.exposure());
            sum = sum.add(exposure.exposure().multiply(entry.lossRate()));
        }
        return new LossCurve(segments, totalExposure, ForecastMath.money(sum));
    }

    /** Provisao EXPECTED_LOSS (CPC 48) sobre esta curva: soma(exposicao x taxa), escala 2. */
    public BigDecimal provisionAmount() {
        return expectedLoss;
    }

    /** Segmentos em JSON (classe Json do core), decimais como texto integral, formato da ED-32. */
    public String segmentsJson() {
        List<Map<String, String>> rows = new ArrayList<>();
        for (Segment s : segments) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("bracket", s.bracket());
            row.put("exposure", s.exposure().toPlainString());
            row.put("expectedLossRate", s.expectedLossRate().toPlainString());
            rows.add(row);
        }
        return Json.write(rows);
    }
}

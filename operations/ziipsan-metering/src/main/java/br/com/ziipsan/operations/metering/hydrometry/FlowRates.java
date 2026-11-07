package br.com.ziipsan.operations.metering.hydrometry;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Vazões Q1–Q4 do modelo aprovado (Portaria Inmetro 155/2022), derivadas de Q3 e R —
 * nunca digitadas isoladamente:
 * <ul>
 *   <li>Q1 = Q3 / R (R = Q3/Q1, do catálogo do modelo, faixa 10..800 conforme catálogo);</li>
 *   <li>Q2/Q1 = 1,6;</li>
 *   <li>Q4/Q3 = 1,25.</li>
 * </ul>
 * As razões 1,6 e 1,25 são as da portaria citadas no aceite da ED-14; R vem do modelo.
 */
public record FlowRates(BigDecimal q1, BigDecimal q2, BigDecimal q3, BigDecimal q4) {

    public static final BigDecimal Q2_OVER_Q1 = new BigDecimal("1.6");
    public static final BigDecimal Q4_OVER_Q3 = new BigDecimal("1.25");
    public static final int R_MIN = 10;
    public static final int R_MAX = 800;

    public static FlowRates derive(BigDecimal q3, int r) {
        if (q3 == null || q3.signum() <= 0) throw new IllegalArgumentException("Q3 do modelo é obrigatório e positivo (Portaria 155/2022)");
        if (r < R_MIN || r > R_MAX) throw new IllegalArgumentException("R (Q3/Q1) fora da faixa do catálogo 10..800: " + r);
        BigDecimal q1 = q3.divide(BigDecimal.valueOf(r), 6, RoundingMode.HALF_UP);
        BigDecimal q2 = q1.multiply(Q2_OVER_Q1);
        BigDecimal q4 = q3.multiply(Q4_OVER_Q3);
        return new FlowRates(q1, q2, q3, q4);
    }

    /** Confere vazões declaradas contra as derivadas (tolerância de arredondamento de 0,1%). */
    public boolean matches(BigDecimal dq1, BigDecimal dq2, BigDecimal dq4) {
        return close(q1, dq1) && close(q2, dq2) && close(q4, dq4);
    }
    private static boolean close(BigDecimal expected, BigDecimal actual) {
        if (actual == null) return false;
        BigDecimal tol = expected.abs().multiply(new BigDecimal("0.001")).max(new BigDecimal("0.000001"));
        return expected.subtract(actual).abs().compareTo(tol) <= 0;
    }
}

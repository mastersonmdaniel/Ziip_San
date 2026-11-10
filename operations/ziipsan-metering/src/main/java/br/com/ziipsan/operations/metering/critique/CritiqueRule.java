package br.com.ziipsan.operations.metering.critique;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Uma regra de critica como DADO de configuracao (II-2): codigo do tenant,
 * tipo e limiares. Regra sem o limiar que o seu tipo exige e recusada na
 * construcao -- nenhum limiar e assumido em silencio (principio herdado de
 * {@code MeteringParameters} da P2.5: parametro ausente e erro explicito).
 *
 * <p>Semantica de fronteira (declarada, e a que os testes provam): os
 * disparos sao ESTRITOS -- desvio igual ao limiar NAO dispara; consumo
 * igual a um extremo da faixa NAO dispara.
 *
 * @param code             codigo da regra no tenant (identificacao, sem significado para o motor)
 * @param kind             tipo da regra
 * @param enabled          regra desligada nao e avaliada
 * @param deviationPercent limiar percentual (&gt; 0); obrigatorio nos tipos DEVIATION_*
 * @param bandMin          extremo inferior da faixa (&gt;= 0); obrigatorio em OUTSIDE_ABSOLUTE_BAND
 * @param bandMax          extremo superior da faixa (&gt;= bandMin); obrigatorio em OUTSIDE_ABSOLUTE_BAND
 */
public record CritiqueRule(String code, CritiqueRuleKind kind, boolean enabled,
                           BigDecimal deviationPercent, Integer bandMin, Integer bandMax) {

    public CritiqueRule {
        Objects.requireNonNull(code, "code");
        if (code.isBlank()) {
            throw new IllegalArgumentException("code da regra de critica e obrigatorio");
        }
        Objects.requireNonNull(kind, "kind");
        switch (kind) {
            case DEVIATION_ABOVE_HISTORY, DEVIATION_BELOW_HISTORY -> {
                if (deviationPercent == null || deviationPercent.signum() <= 0) {
                    throw new IllegalArgumentException(code + ": regra de desvio exige deviationPercent > 0 [A DEFINIR pelo tenant]");
                }
            }
            case OUTSIDE_ABSOLUTE_BAND -> {
                if (bandMin == null || bandMax == null) {
                    throw new IllegalArgumentException(code + ": regra de faixa exige bandMin e bandMax [A DEFINIR pelo tenant]");
                }
                if (bandMin < 0 || bandMax < bandMin) {
                    throw new IllegalArgumentException(code + ": faixa invalida [" + bandMin + ", " + bandMax + "]");
                }
            }
            default -> {
                // Tipos sem limiar: nenhum parametro exigido.
            }
        }
    }

    public static CritiqueRule deviationAbove(String code, String deviationPercent) {
        return new CritiqueRule(code, CritiqueRuleKind.DEVIATION_ABOVE_HISTORY, true,
                new BigDecimal(deviationPercent), null, null);
    }

    public static CritiqueRule deviationBelow(String code, String deviationPercent) {
        return new CritiqueRule(code, CritiqueRuleKind.DEVIATION_BELOW_HISTORY, true,
                new BigDecimal(deviationPercent), null, null);
    }

    public static CritiqueRule band(String code, int bandMin, int bandMax) {
        return new CritiqueRule(code, CritiqueRuleKind.OUTSIDE_ABSOLUTE_BAND, true, null, bandMin, bandMax);
    }

    public static CritiqueRule simple(String code, CritiqueRuleKind kind) {
        return new CritiqueRule(code, kind, true, null, null, null);
    }

    public CritiqueRule disabled() {
        return new CritiqueRule(code, kind, false, deviationPercent, bandMin, bandMax);
    }
}

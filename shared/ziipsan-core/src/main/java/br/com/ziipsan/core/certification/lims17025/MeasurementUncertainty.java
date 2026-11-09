package br.com.ziipsan.core.certification.lims17025;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Incerteza de medicao com fator k e regra de decisao (aceite da
 * ED-51; levantamento 18g, E-LIMS17025: "incerteza com fator k e
 * regra de decisao").
 *
 * <p>O orcamento declara a incerteza-padrao combinada {@code u} e o
 * fator de abrangencia {@code k} COMO DADOS do orcamento — nenhum
 * valor de k nasce de fabrica nesta classe (k nao e presumido: quem
 * declara o orcamento declara o k). A incerteza expandida e a
 * aritmetica {@code U = u * k}.
 *
 * <p>A regra de decisao tambem e declarada, nunca presumida:
 * <ul>
 *   <li>{@code SIMPLE_ACCEPTANCE} — compara o valor direto com o
 *       limite (o risco compartilhado nao e aplicado);</li>
 *   <li>{@code GUARD_BAND} — so declara conformidade quando o valor
 *       com a incerteza expandida inteira esta dentro do limite; so
 *       declara nao conformidade quando esta inteiramente fora; na
 *       faixa de guarda, o enunciado e {@code INDETERMINATE}.</li>
 * </ul>
 * Sem orcamento, nao ha enunciado de conformidade com incerteza — o
 * ensaio exige o orcamento do par (metodo, parametro).
 */
public record MeasurementUncertainty(String methodRef, String parameterKey,
                                     BigDecimal standardUncertainty, BigDecimal coverageFactor,
                                     DecisionRule decisionRule, String declaredByRef) {

    /** Regras de decisao declaraveis (o orcamento escolhe; nada e presumido). */
    public enum DecisionRule { SIMPLE_ACCEPTANCE, GUARD_BAND }

    /** Enunciado de conformidade ja considerada a incerteza e a regra declarada. */
    public enum Statement { CONFORMS, DOES_NOT_CONFORM, INDETERMINATE }

    public MeasurementUncertainty {
        if (methodRef == null || methodRef.isBlank())
            throw new IllegalArgumentException("methodRef e obrigatorio");
        if (parameterKey == null || parameterKey.isBlank())
            throw new IllegalArgumentException("parameterKey e obrigatorio");
        Objects.requireNonNull(standardUncertainty, "standardUncertainty (u) e obrigatoria");
        if (standardUncertainty.signum() <= 0)
            throw new IllegalArgumentException("standardUncertainty (u) deve ser maior que zero");
        Objects.requireNonNull(coverageFactor, "coverageFactor (k) e obrigatorio — declarado "
                + "no orcamento, nunca presumido");
        if (coverageFactor.signum() <= 0)
            throw new IllegalArgumentException("coverageFactor (k) deve ser maior que zero");
        Objects.requireNonNull(decisionRule, "decisionRule e obrigatoria — a regra de decisao "
                + "e declarada no orcamento, nunca presumida");
        if (declaredByRef == null || declaredByRef.isBlank())
            throw new IllegalArgumentException("declaredByRef e obrigatorio (quem declarou o orcamento)");
    }

    /** Incerteza expandida U = u * k (aritmetica do orcamento). */
    public BigDecimal expandedUncertainty() {
        return standardUncertainty.multiply(coverageFactor);
    }

    /** Enunciado contra um limite MAXIMO, sob a regra declarada. */
    public Statement statementForMax(BigDecimal value, BigDecimal limitMax) {
        Objects.requireNonNull(value, "value e obrigatorio");
        Objects.requireNonNull(limitMax, "limitMax e obrigatorio");
        return switch (decisionRule) {
            case SIMPLE_ACCEPTANCE -> value.compareTo(limitMax) <= 0
                    ? Statement.CONFORMS : Statement.DOES_NOT_CONFORM;
            case GUARD_BAND -> {
                BigDecimal u = expandedUncertainty();
                if (value.add(u).compareTo(limitMax) <= 0) yield Statement.CONFORMS;
                if (value.subtract(u).compareTo(limitMax) > 0) yield Statement.DOES_NOT_CONFORM;
                yield Statement.INDETERMINATE;
            }
        };
    }

    /** Enunciado contra um limite MINIMO, sob a regra declarada. */
    public Statement statementForMin(BigDecimal value, BigDecimal limitMin) {
        Objects.requireNonNull(value, "value e obrigatorio");
        Objects.requireNonNull(limitMin, "limitMin e obrigatorio");
        return switch (decisionRule) {
            case SIMPLE_ACCEPTANCE -> value.compareTo(limitMin) >= 0
                    ? Statement.CONFORMS : Statement.DOES_NOT_CONFORM;
            case GUARD_BAND -> {
                BigDecimal u = expandedUncertainty();
                if (value.subtract(u).compareTo(limitMin) >= 0) yield Statement.CONFORMS;
                if (value.add(u).compareTo(limitMin) < 0) yield Statement.DOES_NOT_CONFORM;
                yield Statement.INDETERMINATE;
            }
        };
    }
}

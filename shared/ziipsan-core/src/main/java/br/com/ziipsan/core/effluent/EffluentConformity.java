package br.com.ziipsan.core.effluent;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Avaliacao de conformidade de um resultado de efluente contra o
 * catalogo de padrao desta tarefa. A regra de ouro e a da ED-50:
 * SEM padrao revisado vigente da esfera aplicavel, o veredito e
 * INDETERMINATE — nunca conformidade (nem nao conformidade)
 * presumida, nunca limite de memoria ou de fabrica.
 *
 * <p>A esfera aplicavel (federal ou estadual) e escolha declarada do
 * chamador a partir do catalogo de normas do tenant: este avaliador
 * nao decide sozinho se o padrao estadual substitui ou complementa
 * o federal — ele avalia contra a esfera pedida e nomeia a
 * referencia usada no veredito.
 */
public final class EffluentConformity {

    public enum Verdict { CONFORMING, NON_CONFORMING, INDETERMINATE }

    public record Assessment(
            String parameterKey,
            Verdict verdict,
            String reason,
            String normReference) {}

    private EffluentConformity() {}

    public static Assessment evaluate(String parameterKey, BigDecimal numericValue,
                                      String qualitativeValue, EffluentCatalog catalog,
                                      EffluentStandard.Jurisdiction jurisdiction,
                                      String stateCode, LocalDate referenceDate) {
        if (parameterKey == null || parameterKey.isBlank())
            throw new IllegalArgumentException("parameterKey e obrigatorio");
        boolean numeric = numericValue != null;
        boolean qualitative = qualitativeValue != null && !qualitativeValue.isBlank();
        if (numeric == qualitative)
            throw new IllegalArgumentException("resultado de " + parameterKey
                    + " exige exatamente uma forma: numerica OU qualitativa, como reportada pelo laboratorio");
        if (catalog == null) throw new IllegalArgumentException("catalogo de efluente e obrigatorio");
        if (jurisdiction == null) throw new IllegalArgumentException("esfera (federal/estadual) e obrigatoria");
        if (referenceDate == null) throw new IllegalArgumentException("data de referencia e obrigatoria");

        var standardOpt = catalog.standardOn(parameterKey, jurisdiction, stateCode, referenceDate);
        if (standardOpt.isEmpty()) {
            return new Assessment(parameterKey, Verdict.INDETERMINATE,
                    "sem padrao no catalogo " + catalog.version() + " para o parametro na esfera e na data",
                    null);
        }
        EffluentStandard standard = standardOpt.get();
        if (!standard.reviewedByTechnicalResponsible()) {
            return new Assessment(parameterKey, Verdict.INDETERMINATE,
                    "padrao do catalogo " + catalog.version()
                            + " ainda nao revisado pelo responsavel tecnico",
                    standard.normReference());
        }
        boolean conforming = switch (standard.comparator()) {
            case MAX -> {
                if (!numeric) yield mismatch(parameterKey, standard);
                yield numericValue.compareTo(standard.limitMax()) <= 0;
            }
            case MIN -> {
                if (!numeric) yield mismatch(parameterKey, standard);
                yield numericValue.compareTo(standard.limitMin()) >= 0;
            }
            case RANGE -> {
                if (!numeric) yield mismatch(parameterKey, standard);
                yield numericValue.compareTo(standard.limitMin()) >= 0
                        && numericValue.compareTo(standard.limitMax()) <= 0;
            }
            case QUALITATIVE_EQUALS -> {
                if (numeric) yield mismatch(parameterKey, standard);
                yield standard.qualitativeExpected().equals(qualitativeValue);
            }
        };
        return new Assessment(parameterKey,
                conforming ? Verdict.CONFORMING : Verdict.NON_CONFORMING,
                conforming ? "dentro do padrao revisado" : "fora do padrao revisado",
                standard.normReference());
    }

    private static boolean mismatch(String parameterKey, EffluentStandard standard) {
        throw new IllegalArgumentException("forma do resultado de " + parameterKey
                + " (numerica/qualitativa) nao corresponde ao comparador do padrao revisado "
                + standard.comparator() + ": resultado nao avaliavel, nao presumido");
    }
}

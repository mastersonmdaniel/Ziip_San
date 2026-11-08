package br.com.ziipsan.core.quality;

import java.time.LocalDate;

/**
 * Avaliacao de conformidade de um resultado contra o catalogo
 * normativo. A regra de ouro: SEM padrao revisado vigente, o veredito
 * e INDETERMINATE — o sistema nunca declara conformidade (nem nao
 * conformidade) sem a linha revisada pelo responsavel tecnico, e nunca
 * usa limite de memoria ou de fabrica.
 */
public final class ConformityEvaluator {

    public enum Verdict { CONFORMING, NON_CONFORMING, INDETERMINATE }

    public record Assessment(
            String parameterKey,
            Verdict verdict,
            String reason,
            boolean healthRiskRelevant,
            String normReference) {}

    private ConformityEvaluator() {}

    public static Assessment evaluate(AnalysisResult result, NormativeCatalog catalog, LocalDate referenceDate) {
        if (result == null) throw new IllegalArgumentException("resultado e obrigatorio");
        if (catalog == null) throw new IllegalArgumentException("catalogo e obrigatorio");
        if (referenceDate == null) throw new IllegalArgumentException("data de referencia e obrigatoria");
        var standardOpt = catalog.standardOn(result.parameterKey(), referenceDate);
        if (standardOpt.isEmpty()) {
            return new Assessment(result.parameterKey(), Verdict.INDETERMINATE,
                    "sem padrao no catalogo " + catalog.version() + " para o parametro na data", false, null);
        }
        ParameterStandard standard = standardOpt.get();
        if (!standard.reviewedByTechnicalResponsible()) {
            return new Assessment(result.parameterKey(), Verdict.INDETERMINATE,
                    "padrao do catalogo " + catalog.version()
                            + " ainda nao revisado pelo responsavel tecnico", false, standard.normReference());
        }
        boolean conforming = switch (standard.comparator()) {
            case MAX -> {
                if (!result.isNumeric()) yield mismatch(result, standard);
                yield result.numericValue().compareTo(standard.limitMax()) <= 0;
            }
            case MIN -> {
                if (!result.isNumeric()) yield mismatch(result, standard);
                yield result.numericValue().compareTo(standard.limitMin()) >= 0;
            }
            case RANGE -> {
                if (!result.isNumeric()) yield mismatch(result, standard);
                yield result.numericValue().compareTo(standard.limitMin()) >= 0
                        && result.numericValue().compareTo(standard.limitMax()) <= 0;
            }
            case QUALITATIVE_EQUALS -> {
                if (result.isNumeric()) yield mismatch(result, standard);
                yield standard.qualitativeExpected().equals(result.qualitativeValue());
            }
        };
        // mismatch lanca excecao; aqui conforming e sempre um booleano real.
        return new Assessment(result.parameterKey(),
                conforming ? Verdict.CONFORMING : Verdict.NON_CONFORMING,
                conforming ? "dentro do padrao revisado" : "fora do padrao revisado",
                standard.healthRiskRelevant(), standard.normReference());
    }

    private static boolean mismatch(AnalysisResult result, ParameterStandard standard) {
        throw new IllegalArgumentException("forma do resultado de " + result.parameterKey()
                + " (numerica/qualitativa) nao corresponde ao comparador do padrao revisado "
                + standard.comparator() + ": resultado nao avaliavel, nao presumido");
    }
}

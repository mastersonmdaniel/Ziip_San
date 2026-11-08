package br.com.ziipsan.reporting.indicator;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Variavel da formula de um indicador, com fonte declarada (aceite da
 * P3.41). Fonte e exatamente uma das duas formas do aceite:
 * <ul>
 *   <li>{@code REPORT_VIEW}: uma view do nucleo de relatorios da P3.22
 *       (nome {@code rpt_*});</li>
 *   <li>{@code MANUAL}: entrada manual, com responsavel nomeado.</li>
 * </ul>
 * Variavel sem fonte e RECUSADA na construcao, com o codigo
 * {@code VARIABLE_WITHOUT_SOURCE} — e o teste nomeado do aceite. Nenhuma
 * variavel nasce com fonte presumida, view inventada ou responsavel vazio.
 */
public record IndicatorVariable(String name, SourceType sourceType, String reportView,
                                UUID manualResponsibleId) {

    public enum SourceType {
        REPORT_VIEW,
        MANUAL
    }

    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    public IndicatorVariable {
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            throw new IndicatorValidationException("VARIABLE_NAME_INVALID",
                    "nome de variavel invalido: " + name);
        }
        if (sourceType == null) {
            throw new IndicatorValidationException("VARIABLE_WITHOUT_SOURCE",
                    "variavel " + name + " sem fonte declarada "
                            + "(informe REPORT_VIEW com a view da P3.22 ou MANUAL com responsavel)");
        }
        switch (sourceType) {
            case REPORT_VIEW -> {
                if (reportView == null || reportView.isBlank()) {
                    throw new IndicatorValidationException("VARIABLE_WITHOUT_SOURCE",
                            "variavel " + name + " com fonte REPORT_VIEW sem a view declarada");
                }
                if (!reportView.startsWith("rpt_")) {
                    throw new IndicatorValidationException("VARIABLE_VIEW_NOT_FROM_REPORTING_CORE",
                            "variavel " + name + ": a fonte deve ser uma view do nucleo da P3.22 "
                                    + "(rpt_*), recebida: " + reportView);
                }
                if (manualResponsibleId != null) {
                    throw new IndicatorValidationException("VARIABLE_SOURCE_AMBIGUOUS",
                            "variavel " + name + " com duas fontes (view e responsavel manual)");
                }
            }
            case MANUAL -> {
                if (manualResponsibleId == null) {
                    throw new IndicatorValidationException("VARIABLE_WITHOUT_SOURCE",
                            "variavel " + name + " com fonte MANUAL sem responsavel nomeado");
                }
                if (reportView != null && !reportView.isBlank()) {
                    throw new IndicatorValidationException("VARIABLE_SOURCE_AMBIGUOUS",
                            "variavel " + name + " com duas fontes (responsavel manual e view)");
                }
            }
        }
    }

    public static IndicatorVariable fromReportView(String name, String reportView) {
        return new IndicatorVariable(name, SourceType.REPORT_VIEW, reportView, null);
    }

    public static IndicatorVariable manual(String name, UUID responsibleId) {
        return new IndicatorVariable(name, SourceType.MANUAL, null, responsibleId);
    }
}

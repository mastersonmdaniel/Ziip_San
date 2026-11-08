package br.com.ziipsan.reporting.panels;

import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

/**
 * ED-49: definicao declarativa de um painel padrao — o que o Superset da
 * ED-47 monta. {@code supersetSlug} e identificador PROPOSTO por esta
 * entrega para o dashboard (nenhuma fonte nomeia slugs); a integracao
 * pode renomea-lo ao criar os dashboards, sem tocar na regra.
 *
 * <p>{@code realtimeConsole} e {@code false} para TODOS os paineis, por
 * desenho do aceite: o console operacional em tempo quase real fica FORA
 * do BI, na ED-93 (OPE-22). O painel OPERACIONAL da ED-49 e BI periodico
 * como os outros seis — ver {@link RealtimeConsoleBoundary}.
 */
public record PanelDefinition(PanelKind kind, String supersetSlug, boolean realtimeConsole) {

    public PanelDefinition {
        if (kind == null) {
            throw new IndicatorValidationException("PANEL_KIND_REQUIRED",
                    "definicao de painel sem o tipo");
        }
        if (supersetSlug == null || supersetSlug.isBlank()) {
            throw new IndicatorValidationException("PANEL_SLUG_REQUIRED",
                    "painel " + kind + " sem o slug proposto do dashboard");
        }
        if (realtimeConsole) {
            throw new IndicatorValidationException("OPE22_REALTIME_CONSOLE_OUT_OF_BI",
                    "painel " + kind + " declarado como console em tempo quase real: "
                            + "o console em tempo quase real fica fora do BI, na ED-93 (OPE-22)");
        }
    }

    public String title() {
        return kind.title();
    }

    public String pocRef() {
        return kind.pocRef();
    }
}

package br.com.ziipsan.reporting.panels;

import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

/**
 * ED-49: a fronteira do OPE-22, escrita como regra executavel. O aceite
 * remete o console operacional em tempo quase real para FORA do BI, na
 * ED-93: nenhum painel desta entrega — nem o OPERACIONAL — e console em
 * tempo quase real, e um pedido de atualizacao em tempo quase real
 * dirigido a um painel padrao e recusado nomeado, apontando o destino
 * certo. O painel OPERACIONAL da ED-49 e BI periodico sobre a replica
 * (ED-47), como os outros seis.
 */
public final class RealtimeConsoleBoundary {

    /** Achado de origem da remessa, como consta do acceptance/consumed da ED-49. */
    public static final String FINDING = "OPE-22";
    /** Tarefa dona do console em tempo quase real. */
    public static final String TARGET_TASK = "ED-93";

    private RealtimeConsoleBoundary() {
    }

    /** Nenhum painel padrao e console em tempo quase real — para qualquer tipo. */
    public static boolean isRealtimeConsole(PanelKind kind) {
        return false;
    }

    /**
     * Recusa nomeada quando a fiacao pede tempo quase real a um painel
     * padrao; pedido periodico (o unico do BI) passa sem efeito.
     */
    public static void requirePeriodicOnly(PanelKind kind, boolean realtimeRequested) {
        if (realtimeRequested) {
            throw new IndicatorValidationException("OPE22_REALTIME_CONSOLE_OUT_OF_BI",
                    "painel " + kind + " em tempo quase real recusado: o console operacional "
                            + "em tempo quase real fica fora do BI, na " + TARGET_TASK
                            + " (" + FINDING + ")");
        }
    }
}

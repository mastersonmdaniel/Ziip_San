package br.com.ziipsan.reporting.situation;

import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

/**
 * ED-93 (OPE-22): a fronteira de fonte do console, executavel.
 *
 * <p>O aceite manda: o console e alimentado por tabela de estado
 * resumida e NATIVA — NUNCA por consulta livre ao primario nem pelo
 * Superset. Esta guarda e o unico ponto por onde uma fonte e admitida
 * no console: {@link FeedSource#NATIVE_SUMMARY_TABLE} passa; consulta
 * livre ao primario e Superset sao recusados nomeados. E o espelho,
 * do lado do console, da {@code RealtimeConsoleBoundary} da ED-49
 * (que recusa tempo quase real dentro do BI): o painel operacional de
 * GESTAO continua no BI da ED-49; este console e nativo e nao le o BI.
 */
public final class SituationSourceGuard {

    /** Achado de origem, como consta do acceptance da ED-93/ED-49. */
    public static final String FINDING = "OPE-22";

    /** Fontes possiveis de um console; so a primeira e admitida aqui. */
    public enum FeedSource {
        NATIVE_SUMMARY_TABLE,
        PRIMARY_FREE_QUERY,
        SUPERSET
    }

    private SituationSourceGuard() {
    }

    /** Admite somente a tabela de estado resumida e nativa. */
    public static void requireSummaryTable(FeedSource source) {
        if (source == null) {
            throw new IndicatorValidationException("SITUATION_SOURCE_REQUIRED",
                    "fonte do console obrigatoria (" + FINDING + ")");
        }
        if (source != FeedSource.NATIVE_SUMMARY_TABLE) {
            throw new IndicatorValidationException("OPE22_CONSOLE_SOURCE_FORBIDDEN",
                    "fonte " + source + " recusada: o console operacional da ED-93 le "
                            + "SOMENTE a tabela de estado resumida e nativa — nunca consulta "
                            + "livre ao primario nem o Superset (" + FINDING + "); o painel "
                            + "operacional de gestao continua no BI da ED-49");
        }
    }

    /** O console desta entrega e nativo, fora do BI — para qualquer pedido. */
    public static boolean isNativeConsole() {
        return true;
    }
}

package br.com.ziipsan.commercial.billing.cashback;

/**
 * Pontos em aberto da P2.34, <b>isolados</b>: existem como registro
 * explícito para não serem decididos por omissão nem bloquearem o restante
 * da tarefa. Nada neste arquivo altera cálculo, elegibilidade ou gravação.
 */
public final class CashbackOpenPoints {

    /**
     * PONTO A CONFIRMAR POR PARECER TRIBUTÁRIO (pendente): a condição de
     * contribuinte de IBS/CBS de prestador constituído como
     * <b>autarquia</b>. Enquanto o parecer não existir, o sistema <b>não</b>
     * presume a condição em nenhum dos dois sentidos: nenhum ramo de
     * código depende deste ponto, e o relatório por competência o declara.
     */
    public static final String AUTARQUIA_CONTRIBUINTE_PENDENTE =
            "PONTO EM ABERTO (parecer tributário pendente): condição de contribuinte de IBS/CBS "
                    + "de prestador constituído como autarquia — não decidida nesta entrega (P2.34)";

    /**
     * Ativação do {@code tax_event_date}: depende da conferência do texto
     * da regra de fato gerador de operação contínua (LC 227/2026, art. 10,
     * §3º). Ver {@link TaxEventDateResolver.PendingConfirmation}.
     */
    public static final String ATIVACAO_TAX_EVENT_DATE_PENDENTE =
            "ATIVACAO PENDENTE: tax_event_date pela LC 227/2026, art. 10, §3º — texto da regra "
                    + "a conferir antes da ativação (P2.34)";

    private CashbackOpenPoints() {
    }
}

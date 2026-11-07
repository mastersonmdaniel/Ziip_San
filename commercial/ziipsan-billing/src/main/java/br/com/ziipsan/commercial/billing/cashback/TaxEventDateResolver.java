package br.com.ziipsan.commercial.billing.cashback;

import br.com.ziipsan.commercial.billing.parameters.TaxProfile;

import java.time.LocalDate;

/**
 * Porta que fixa o {@code tax_event_date} de uma conta de operação contínua
 * (P2.34): a data é gravada em {@code bills.tax_event_date}, <b>separada</b>
 * de {@code due_date} e de {@code issued_on}, pela regra selecionada em
 * {@code tax.profile.tax_event_date_rule} (LC 227/2026, art. 10, §3º).
 *
 * <p><b>CONDICIONALIDADE EXPRESSA:</b> o aceite manda conferir o <b>texto</b>
 * da regra da LC 227/2026 <b>antes da ativação</b>. Esse texto não foi
 * conferido nesta entrega e <b>não é presumido em código</b>: nenhuma
 * implementação entregue escolhe um dia (início, fim ou outro marco do
 * período) como se fosse a regra legal. A implementação de produção entra
 * na integração, depois da conferência, ligada à mesma regra do perfil.
 */
public interface TaxEventDateResolver {

    /**
     * @param regra       regra selecionada pelo {@code tax.profile} do tenant
     * @param periodoDe   primeiro dia do período de fornecimento contínuo
     * @param periodoAte  último dia do período de fornecimento contínuo
     * @return a data do fato gerador, pela regra conferida
     */
    LocalDate resolver(TaxProfile.TaxEventDateRule regra, LocalDate periodoDe, LocalDate periodoAte);

    /**
     * Implementação entregue nesta tarefa: <b>recusa sempre</b>, com erro
     * explícito, porque o texto da LC 227/2026 (art. 10, §3º) ainda não foi
     * conferido. É o comportamento correto até a ativação: sem conferência,
     * nenhuma devolução é concedida com um {@code tax_event_date} inventado
     * — e nenhuma é concedida sem ele (o serviço resolve a data <b>antes</b>
     * de gravar a devolução).
     */
    final class PendingConfirmation implements TaxEventDateResolver {

        /** Mensagem única da recusa (os testes e o relatório citam esta constante). */
        public static final String MENSAGEM =
                "tax_event_date não resolvido: a ativação da regra de fato gerador de operação contínua "
                        + "(LC 227/2026, art. 10, §3º) depende da conferência do texto da regra, "
                        + "pendente nesta entrega (P2.34)";

        @Override
        public LocalDate resolver(TaxProfile.TaxEventDateRule regra, LocalDate periodoDe, LocalDate periodoAte) {
            throw new IllegalStateException(MENSAGEM);
        }
    }
}

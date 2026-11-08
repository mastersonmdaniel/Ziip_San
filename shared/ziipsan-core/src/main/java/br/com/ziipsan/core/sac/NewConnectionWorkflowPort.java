package br.com.ziipsan.core.sac;

import java.util.List;

/**
 * Porta da solicitacao de LIGACAO NOVA pelo fluxo encadeado da ED-34
 * (ED-35; correcao pos-reprovacao, G2 \u2014 o item estava declarado feito sem
 * nenhum rastro no pacote).
 *
 * <p>O fluxo encadeado em si e da ED-34:
 * {@code br.com.ziipsan.commercial.billing.servicecatalog.NewConnectionWorkflow}
 * (pedido \u2192 documentos \u2192 viabilidade \u2192 orcamento \u2192 guia/NFS-e \u2192
 * vistoria \u2192 execucao \u2192 [esgoto simultaneo, se houver rede e o parametro
 * exigir] \u2192 ativacao \u2192 contrato \u2192 vinculo \u2192 primeira leitura). O core
 * (ziipsan-core) NAO pode depender do modulo comercial \u2014 a dependencia do
 * repo e a inversa (billing importa core, como faz
 * {@code ServiceCatalogParameters} da ED-34) \u2014, por isso o atendimento
 * declara esta porta e a implementacao vive no modulo dono do fluxo:
 * {@code NewConnectionWorkflowSacAdapter} (entrega comercial desta ED-35,
 * pasta {@code commercial/ziipsan-billing}), que delega a
 * {@code NewConnectionWorkflow.definition(...)} da ED-34 sem reimplementar
 * nenhuma etapa.
 */
public interface NewConnectionWorkflowPort {

    /**
     * Codigos das etapas do fluxo encadeado da ED-34, na ordem de execucao.
     *
     * @param sewerAvailable             ha rede de esgoto no local
     * @param requiresSewerWhenAvailable parametro do tenant
     *        {@code connection.new_requires_sewer_when_available} (ED-34)
     * @param inspectionDeadlineHours    prazo da vistoria (NR 11, art. 72,
     *        regra da agencia \u2014 lido pelo chamador, nunca fixado aqui)
     * @param executionDeadlineHours     prazo da ligacao (NR 11, art. 73,
     *        regra da agencia \u2014 idem)
     */
    List<String> chainedStepCodes(boolean sewerAvailable, boolean requiresSewerWhenAvailable,
                                  Integer inspectionDeadlineHours, Integer executionDeadlineHours);
}

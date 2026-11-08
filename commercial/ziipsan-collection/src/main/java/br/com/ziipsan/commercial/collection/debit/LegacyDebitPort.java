package br.com.ziipsan.commercial.collection.debit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Porta da parte FEBRABAN 150 legada - a IMPLEMENTACAO e da P3.24
 * (migration {@code V6_4}: autorizacoes legadas importadas e conferidas por
 * matricula, remessa com registros B, C, E e F, retorno com baixa
 * idempotente, reenvio e cancelamento de optante, so para optante cujo
 * banco do pagador seja o mesmo do recebedor).
 *
 * <p>A ED-27 NAO duplica essa implementacao nem o leiaute do arquivo: ela
 * integra por esta porta. No inicio desta entrega a P3.24 ainda estava em
 * producao paralela (sem pacote local em {@code ziipsan-producao/p3.24/} e
 * sem {@code V6_4} conferida na main no HEAD-espelho), logo nenhum campo,
 * posicao ou codigo de ocorrencia do arquivo FEBRABAN 150 foi presumido
 * aqui - os tipos abaixo sao os do CONTRATO de integracao (identificadores,
 * valores e desfechos), nao os do leiaute fisico.
 *
 * <p>Decisao do dono de 08/11/2026 (DECISOES-DO-DONO.md, item 9): optantes
 * EXISTEM e o FEBRABAN 150 fica MANTIDO como legado para a base atual,
 * junto da campanha de conversao ao Pix Automatico (P4.5). O parecer formal
 * RAS-06/PD-10 NAO existe como documento - a decisao e do dono, nao parecer
 * juridico (declarado no relatorio da ED-27).
 */
public interface LegacyDebitPort {

    /** Autorizacao legada importada (conferida por matricula na P3.24). */
    record LegacyAuthorization(UUID tenantId, UUID unitId, String enrollmentRef,
                               String payerBank, String receiverBank, boolean active) {
        /** Condicao da P3.24: so entra optante com banco do pagador = banco do recebedor. */
        public boolean sameBank() {
            return payerBank != null && payerBank.equals(receiverBank);
        }
    }

    /**
     * Item de debito de um ciclo para uma autorizacao legada.
     *
     * <p>Correcao pos-revisao (D1): o item carrega o {@code unitId} da
     * autorizacao legada a que pertence - sem esse vinculo era impossivel
     * saber a qual optante cada debito pertencia e a remessa nao tinha
     * como excluir os itens de optantes inelegiveis. Item sem vinculo
     * ({@code unitId} nulo) ou de autorizacao nao elegivel e EXCLUIDO da
     * remessa e contado em {@link RemittanceResult#excludedCount()}.
     */
    record LegacyDebitItem(UUID unitId, String chargeIdentifier, UUID documentId, LocalDate collectionMonth,
                           BigDecimal amount, LocalDate debitDate) {
    }

    /**
     * Resultado da geracao da remessa (arquivo fisico e da implementacao
     * da P3.24). {@code excludedCount} e o numero de itens excluidos por
     * inelegibilidade da autorizacao vinculada (correcao D1), preenchido
     * pela orquestracao da ED-27 a partir da filtragem que ela aplica
     * antes de chamar a porta.
     */
    record RemittanceResult(String remittanceId, int itemCount, BigDecimal total, int excludedCount) {

        /** Compatibilidade: resultado sem contagem de excluidos (ex.: dublês antigos) = 0 excluidos declarados pela porta. */
        public RemittanceResult(String remittanceId, int itemCount, BigDecimal total) {
            this(remittanceId, itemCount, total, 0);
        }
    }

    /** Linha de desfecho do retorno, ja traduzida do leiaute pela P3.24. */
    record ReturnLine(String chargeIdentifier, boolean settled, String bankReasonCode) {
    }

    /**
     * Retorno processado pela P3.24. {@code returnKey} e a chave de
     * idempotencia do arquivo de retorno (NSA + hash, no desenho da P3.1).
     */
    record ReturnBatch(String returnKey, List<ReturnLine> lines) {
    }

    /** Importa autorizacoes legadas do sistema anterior (conferencia por matricula e da P3.24). */
    List<LegacyAuthorization> importLegacyAuthorizations(UUID tenantId);

    /** Gera a remessa FEBRABAN 150 (registros B, C, E, F) dos itens dados. */
    RemittanceResult generateRemittance(UUID tenantId, List<LegacyDebitItem> items);

    /** Le e traduz o retorno do banco; a baixa em si e aplicada por quem chama, uma unica vez. */
    ReturnBatch readReturn(UUID tenantId, String returnReference);

    /** Reenvio de um item recusado/devolvido, dentro da janela do legado. */
    void resend(UUID tenantId, String chargeIdentifier);

    /** Cancelamento do optante no legado (ele pode migrar ao Pix Automatico ou sair do debito). */
    void cancelOptant(UUID tenantId, UUID unitId);
}

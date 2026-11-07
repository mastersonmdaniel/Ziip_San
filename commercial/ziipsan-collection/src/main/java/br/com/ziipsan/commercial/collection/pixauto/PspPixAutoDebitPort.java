package br.com.ziipsan.commercial.collection.pixauto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Porta do PSP para o Pix Automatico (P4.5), no mesmo desenho da
 * {@code PspPixPort} da P3.2: o Ziip SAN fala com o PSP do piloto SOMENTE por
 * esta interface. NENHUM endpoint, URL ou credencial e presumido aqui - o
 * adaptador real nasce na homologacao com o PSP do piloto (pendencia nomeada
 * no relatorio da P4.5) e deve seguir os fluxos do Manual de Fluxos do Pix
 * vigentes na homologacao.
 */
public interface PspPixAutoDebitPort {

    /** Identificador do PSP (o mesmo da matriz {@code pix_psp_profiles} da P3.2). */
    String psp();

    /**
     * Registra a autorizacao de debito recorrente no PSP.
     * @return identificador da autorizacao no PSP (guardado como prova complementar)
     */
    String registerAuthorization(String payerDocument, String receiverDocument, int journey,
                                 boolean retryForeseen, BigDecimal maxAmount);

    /**
     * Envia a instrucao de debito de um ciclo para liquidacao na data agendada.
     * @return endToEndId do debito no PSP
     */
    String submitDebit(String chargeIdentifier, BigDecimal amount, LocalDate settlementDate,
                       String payerDocument);

    /** Cancela no PSP uma instrucao ainda nao liquidada. */
    void cancelDebit(String chargeIdentifier);

    /**
     * Solicita ao banco do pagador (via PSP) a devolucao de um debito indevido.
     * A devolucao em si e executada pelo banco do pagador em ate 24 horas
     * (regra registrada na P3.2); esta chamada apenas protocola o pedido.
     * @return protocolo do pedido de devolucao no PSP
     */
    String requestUndueDebitRefund(String chargeIdentifier, String endToEndId, String reason);
}

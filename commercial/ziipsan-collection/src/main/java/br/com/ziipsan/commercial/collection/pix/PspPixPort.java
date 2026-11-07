package br.com.ziipsan.commercial.collection.pix;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Interface do PSP para Pix (P3.2): e aqui que a matriz de comportamento por
 * PSP fica registrada, e e ela que o hub da P3.37 le. Cada PSP ligado declara
 * o seu {@link PspBehavior}; PSP sem comportamento declarado nao opera (erro
 * explicito na fiacao), porque reenvio, SLA de callback e entrega de
 * certificado variam por PSP e presumir qualquer um deles quebra a baixa.
 *
 * <p>Autenticacao do callback: o padrao do Banco Central NAO define assinatura
 * do PSP. A origem e autenticada por mTLS (certificado do PSP) e, onde o PSP
 * oferecer, por segredo adicional - por isso o comportamento declara
 * {@code supportsAdditionalSecret} em vez de existir um campo de assinatura.
 *
 * <p>Confirmacao: sem mecanismo equivalente declarado, a baixa exige a
 * confirmacao por {@link #fetchPixByEndToEndId} (GET /pix/{e2eid}) antes.
 *
 * <p>Pix Automatico: as regras operacionais ficam registradas em
 * {@link PixAutoDebitRules} nesta interface (e nos parametros
 * {@code pay.auto_debit.*}); a EXECUCAO das jornadas e da P4.5/ED-27, nao
 * desta tarefa.
 */
public interface PspPixPort {

    /** Comportamento declarado do PSP. */
    PspBehavior behavior();

    /** GET /pix/{e2eid}: o Pix confirmado pelo PSP, se ele existir la. */
    Optional<PspPix> fetchPixByEndToEndId(String endToEndId);

    /** GET /pix?inicio=&fim=: Pix liquidados no intervalo (varredura). */
    List<PspPix> listPixByInterval(Instant inicio, Instant fim);

    /** Cancela a cobranca Pix (ex.: boleto do documento hibrido foi pago). */
    void cancelCharge(String txid);

    /**
     * @param psp                        identificador do PSP
     * @param callbackResendPolicy       politica de reenvio do callback (do manual/contrato do PSP)
     * @param callbackSlaSeconds         SLA declarado do callback, em segundos (> 0)
     * @param certificateDelivery        como o certificado mTLS do PSP e entregue
     * @param supportsAdditionalSecret   PSP oferece segredo adicional alem do mTLS
     * @param equivalentConfirmation     mecanismo equivalente ao GET /pix/{e2eid}; vazio = nao oferece
     * @param openFinanceJsr             capacidade do PROVEDOR: iniciacao Open Finance com
     *                                   Jornada Sem Redirecionamento (declarada em pay.channels;
     *                                   nao torna Ziip SAN/tenant participantes)
     * @param pixProximity               capacidade do PROVEDOR: Pix por aproximacao (pay.channels)
     */
    record PspBehavior(String psp, String callbackResendPolicy, int callbackSlaSeconds,
                       String certificateDelivery, boolean supportsAdditionalSecret,
                       Optional<String> equivalentConfirmation,
                       boolean openFinanceJsr, boolean pixProximity) {
        public PspBehavior {
            if (psp == null || psp.isBlank()) {
                throw new IllegalArgumentException("PSP do comportamento e obrigatorio");
            }
            if (callbackResendPolicy == null || callbackResendPolicy.isBlank()) {
                throw new IllegalArgumentException("politica de reenvio do callback e obrigatoria (PSP " + psp + ")");
            }
            if (callbackSlaSeconds <= 0) {
                throw new IllegalArgumentException("SLA do callback deve ser > 0: " + callbackSlaSeconds);
            }
            if (certificateDelivery == null || certificateDelivery.isBlank()) {
                throw new IllegalArgumentException("entrega de certificado e obrigatoria (PSP " + psp + ")");
            }
            if (equivalentConfirmation == null) {
                equivalentConfirmation = Optional.empty();
            }
        }

        /** Sem mecanismo equivalente, a baixa exige GET /pix/{e2eid} antes. */
        public boolean requiresGetPixConfirmation() {
            return equivalentConfirmation.isEmpty();
        }
    }

    /** Pix como o PSP o devolve (callback, GET /pix/{e2eid} ou GET /pix). */
    record PspPix(String endToEndId, String txid, BigDecimal amount, Instant paidAt,
                  String payerDocument, String receiverDocument) {
    }
}

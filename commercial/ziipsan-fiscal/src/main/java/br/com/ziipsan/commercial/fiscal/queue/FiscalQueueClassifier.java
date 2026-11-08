package br.com.ziipsan.commercial.fiscal.queue;

import br.com.ziipsan.commercial.fiscal.nfage.NfageStatus;

import java.util.Objects;

/**
 * Classificação de um documento na fila do operador (aceite da P2.33), regra pura
 * sobre o estado do dicionário da P2.6 e as pendências da V4_11:
 *
 * <ul>
 *   <li>REJECTED → {@link FiscalQueueState#REJECTED};</li>
 *   <li>CANCELLED → {@link FiscalQueueState#CANCELLED}; REPLACED →
 *       {@link FiscalQueueState#REPLACED}; VOIDED não é item da fila do aceite
 *       (número inutilizado não é documento pendente) — classificar VOIDED é erro;</li>
 *   <li>AUTHORIZED com cancelamento pendente → {@link FiscalQueueState#CANCELLATION_PENDING};
 *       sem pendência → {@link FiscalQueueState#AUTHORIZED};</li>
 *   <li>em contingência e ainda não transmitido (RESERVED ou SIGNED) →
 *       {@link FiscalQueueState#CONTINGENCY};</li>
 *   <li>demais estados em trânsito (RESERVED, SIGNED, TRANSMITTED,
 *       PENDING_RECONCILIATION sem contingência) não têm estado próprio no aceite:
 *       são classificados como CONTINGENCY somente quando a contingência está
 *       marcada; sem marca, a classificação é {@code null} — a fila do aceite lista
 *       autorizadas, rejeitadas, em contingência, canceladas e substituídas, e um
 *       documento em trânsito normal não é fingido como pendência.</li>
 * </ul>
 *
 * <p>NFS-e avulsa (P3.6): não transmitida → CONTINGENCY (é a pendência de transmissão
 * da avulsa nesta fila); transmitida → {@code null}: transmitida NÃO é autorizada
 * (a P3.6 só conhece GERADA_NAO_TRANSMITIDA e nenhuma autorização da avulsa é
 * presumida nesta fila — correção da revisão cruzada da P2.33).
 */
public final class FiscalQueueClassifier {

    private FiscalQueueClassifier() {
    }

    /** Estado da fila, ou {@code null} quando o documento não é item desta fila. */
    public static FiscalQueueState classify(FiscalDocumentView doc) {
        Objects.requireNonNull(doc, "Documento é obrigatório");
        if (doc.kind() == FiscalDocumentKind.NFSE_AVULSA) {
            return doc.avulsaTransmitted() ? null : FiscalQueueState.CONTINGENCY;
        }
        NfageStatus status = doc.nfageStatus();
        return switch (status) {
            case REJECTED -> FiscalQueueState.REJECTED;
            case CANCELLED -> FiscalQueueState.CANCELLED;
            case REPLACED -> FiscalQueueState.REPLACED;
            case VOIDED -> throw new IllegalStateException(
                    "Documento inutilizado (VOIDED) não é item da fila de pendências do operador");
            case AUTHORIZED -> doc.cancellationPending()
                    ? FiscalQueueState.CANCELLATION_PENDING : FiscalQueueState.AUTHORIZED;
            case RESERVED, SIGNED -> doc.contingency()
                    ? FiscalQueueState.CONTINGENCY : null;
            // Transmitido (mesmo emitido em contingência) já foi transmitido: o item
            // de contingência do aceite é o "ainda não transmitido", com tempo
            // decorrido — transmitido/aguardando reconciliação não é esse item.
            case TRANSMITTED, PENDING_RECONCILIATION -> null;
        };
    }

    /** Pendência que gera item de "minhas tarefas" (aceite: rejeição, contingência e cancelamento pendente). */
    public static boolean generatesWorkItem(FiscalQueueState state) {
        return state == FiscalQueueState.REJECTED
                || state == FiscalQueueState.CONTINGENCY
                || state == FiscalQueueState.CANCELLATION_PENDING;
    }
}

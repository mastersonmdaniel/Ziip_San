package br.com.ziipsan.commercial.collection.identification;

import java.util.Objects;
import java.util.UUID;

/**
 * Fila de pagamentos nao identificados e nao classificados (P3.29).
 *
 * <p><b>Sem cliente ficticio:</b> a entrada da fila carrega SOMENTE o
 * pagamento e o motivo. Nao ha titular, unidade ou documento presumidos -
 * {@code unitId} e {@code document*} nascem nulos e so a OS interna
 * {@code PAYMENT_IDENTIFICATION} os preenche, com vinculo conferido.
 *
 * <p>Entradas: a classificacao da P3.1 devolve {@code UNIDENTIFIED} /
 * {@code UNIDENTIFIED_QUEUE}; a varredura da P3.2 recupera Pix sem
 * {@code txid} ({@code PIX_WITHOUT_TXID}). Um pagamento entra UMA vez
 * (UNIQUE na V6_5 + guarda {@link #enqueue} sobre a entrada existente).
 */
public final class UnidentifiedPaymentQueue {

    public enum QueueReason { UNIDENTIFIED_CLASSIFICATION, PIX_WITHOUT_TXID }

    public enum QueueStatus { NA_FILA, EM_IDENTIFICACAO, IDENTIFICADO, DEVOLVIDO_CLASSIFICACAO }

    /** Entrada da fila (espelha {@code unidentified_payments}). */
    public record QueueEntry(UUID id, UUID paymentId, QueueReason reason, QueueStatus status,
                             UUID unitId, String documentType, UUID documentId,
                             UUID identificationServiceOrderId) {
        /** Entrada nova: nenhum vinculo, nenhum cliente - so o pagamento. */
        public static QueueEntry unidentified(UUID paymentId, QueueReason reason) {
            Objects.requireNonNull(paymentId, "pagamento e obrigatorio");
            Objects.requireNonNull(reason, "motivo da fila e obrigatorio");
            return new QueueEntry(null, paymentId, reason, QueueStatus.NA_FILA, null, null, null, null);
        }

        public boolean hasLink() {
            return unitId != null && documentId != null;
        }
    }

    private UnidentifiedPaymentQueue() {
    }

    /**
     * Enfileirar de novo o mesmo pagamento com o MESMO motivo devolve a
     * entrada existente (sem duplicar). Com motivo DIVERGENTE e conflito
     * explicito (correcao pos-revisao b3): a mesma entrada nao pode ter
     * nascido de duas origens diferentes em silencio - quem reenvia com
     * outro motivo precisa tratar a divergencia, nao empilhar uma segunda
     * leitura sobre a mesma entrada.
     */
    public static QueueEntry enqueue(QueueEntry existing, UUID paymentId, QueueReason reason) {
        Objects.requireNonNull(paymentId, "pagamento e obrigatorio");
        Objects.requireNonNull(reason, "motivo da fila e obrigatorio");
        if (existing != null) {
            if (!existing.paymentId().equals(paymentId)) {
                throw new IllegalStateException("entrada da fila pertence a outro pagamento");
            }
            if (existing.reason() != reason) {
                throw new IllegalStateException("pagamento ja esta na fila com outro motivo ("
                        + existing.reason() + "): reenvio com motivo divergente (" + reason
                        + ") e conflito, nao duplicacao");
            }
            return existing;
        }
        return QueueEntry.unidentified(paymentId, reason);
    }
}

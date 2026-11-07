package br.com.ziipsan.commercial.collection.identification;

import br.com.ziipsan.commercial.collection.identification.UnidentifiedPaymentQueue.QueueEntry;
import br.com.ziipsan.commercial.collection.identification.UnidentifiedPaymentQueue.QueueStatus;

import java.util.Objects;
import java.util.UUID;

/**
 * Tratamento da fila pela OS interna {@code PAYMENT_IDENTIFICATION} (P3.29):
 * a OS vincula o pagamento a uma UNIDADE e a um DOCUMENTO (conta) e o
 * devolve a classificacao da P3.1 - quem classifica continua sendo o
 * {@code PaymentClassifier} da P3.1, agora com a conta identificada.
 *
 * <p>Regras:
 * <ul>
 *   <li>o inicio do trabalho da OS marca a entrada {@code EM_IDENTIFICACAO}
 *       ({@link #beginIdentification}; correcao pos-revisao b3 - o status
 *       existia no enum/CHECK sem nenhuma transicao que o alcancasse);</li>
 *   <li>vinculo exige unidade E documento; vinculo parcial e erro explicito;</li>
 *   <li>o reenvio da MESMA OS com o MESMO vinculo e idempotente
 *       ({@link Outcome#ALREADY_IDENTIFIED}): nao reclassifica nem duplica;</li>
 *   <li>reenvio com vinculo DIFERENTE e conflito explicito - inclusive o
 *       mesmo {@code documentId} com {@code documentType} DIFERENTE
 *       (correcao pos-revisao M3): trocar o vinculo e trabalho de outra OS,
 *       nao efeito colateral de reenvio;</li>
 *   <li>a devolucao a P3.1 carrega pagamento, unidade, {@code documentId} E
 *       {@code documentType} SEM renomear: o documento identificado nao e
 *       necessariamente uma conta ({@code bill}) - quem interpreta o tipo
 *       e a classificacao da P3.1 (correcao M3; a versao original renomeava
 *       {@code documentId} para {@code billId} incondicionalmente).</li>
 * </ul>
 */
public final class PaymentIdentificationService {

    public enum Outcome { IDENTIFIED_AND_RETURNED, ALREADY_IDENTIFIED }

    /**
     * Comando que a fiacao entrega ao classificador da P3.1: documento
     * identificado pelo seu id E pelo seu tipo, sem renomeacao.
     */
    public record ReclassificationHandoff(UUID paymentId, UUID unitId, UUID documentId,
                                          String documentType) {
    }

    /** @param handoff nulo quando o reenvio ja tinha identificado (nada a reclassificar) */
    public record IdentificationResult(Outcome outcome, QueueEntry entry, ReclassificationHandoff handoff) {
    }

    /**
     * Inicio do trabalho da OS sobre a entrada: {@code NA_FILA} passa a
     * {@code EM_IDENTIFICACAO}. Comecar de novo sobre uma entrada ja em
     * identificacao e idempotente (devolve a mesma entrada); sobre entrada
     * ja identificada/devolvida e conflito explicito.
     */
    public QueueEntry beginIdentification(QueueEntry entry) {
        Objects.requireNonNull(entry, "entrada da fila e obrigatoria");
        return switch (entry.status()) {
            case NA_FILA -> new QueueEntry(entry.id(), entry.paymentId(), entry.reason(),
                    QueueStatus.EM_IDENTIFICACAO, entry.unitId(), entry.documentType(),
                    entry.documentId(), entry.identificationServiceOrderId());
            case EM_IDENTIFICACAO -> entry;
            case IDENTIFICADO, DEVOLVIDO_CLASSIFICACAO -> throw new IllegalStateException(
                    "pagamento ja identificado nao volta para identificacao (status="
                            + entry.status() + ")");
        };
    }

    /**
     * @param entry                     entrada atual da fila
     * @param identificationServiceOrderId OS PAYMENT_IDENTIFICATION que trata a entrada
     * @param unitId                    unidade consumidora vinculada
     * @param documentType              tipo do documento vinculado (ex.: BILL)
     * @param documentId                documento/conta vinculada
     */
    public IdentificationResult identify(QueueEntry entry, UUID identificationServiceOrderId,
                                         UUID unitId, String documentType, UUID documentId) {
        Objects.requireNonNull(entry, "entrada da fila e obrigatoria");
        Objects.requireNonNull(identificationServiceOrderId, "OS PAYMENT_IDENTIFICATION e obrigatoria");
        if (unitId == null || documentId == null || documentType == null || documentType.isBlank()) {
            throw new IllegalArgumentException("a identificacao exige unidade e documento vinculados; "
                    + "vinculo parcial nao identifica o pagamento");
        }
        if (entry.status() == QueueStatus.IDENTIFICADO || entry.status() == QueueStatus.DEVOLVIDO_CLASSIFICACAO) {
            boolean sameLink = unitId.equals(entry.unitId()) && documentId.equals(entry.documentId())
                    && Objects.equals(documentType, entry.documentType())
                    && identificationServiceOrderId.equals(entry.identificationServiceOrderId());
            if (!sameLink) {
                throw new IllegalStateException("pagamento ja identificado com outro vinculo: "
                        + "reenvio com vinculo diferente e conflito, nao correcao");
            }
            return new IdentificationResult(Outcome.ALREADY_IDENTIFIED, entry, null);
        }
        QueueEntry identified = new QueueEntry(entry.id(), entry.paymentId(), entry.reason(),
                QueueStatus.DEVOLVIDO_CLASSIFICACAO, unitId, documentType, documentId,
                identificationServiceOrderId);
        return new IdentificationResult(Outcome.IDENTIFIED_AND_RETURNED, identified,
                new ReclassificationHandoff(entry.paymentId(), unitId, documentId, documentType));
    }
}

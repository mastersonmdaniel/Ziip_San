package br.com.ziipsan.commercial.collection.pixauto;

import br.com.ziipsan.commercial.collection.pix.PixAutoDebitRules;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Prazos de cancelamento da instrucao agendada (GES-15, registrados na P3.2 em
 * {@link PixAutoDebitRules} e executados aqui na P4.5):
 *
 * <ul>
 *   <li>pagador: ate 23h59 da vespera da liquidacao;</li>
 *   <li>recebedor: ate 22h do dia da liquidacao.</li>
 * </ul>
 *
 * Os instantes sao hora local do tenant (quem chama injeta o relogio local;
 * instante gravado continua em UTC nas entidades, como no restante do produto).
 */
public final class PixAutoDebitCancellation {

    public enum Actor { PAGADOR, RECEBEDOR }

    private PixAutoDebitCancellation() {
    }

    /** Limite do pagador: 23h59 da vespera da data de liquidacao. */
    public static LocalDateTime payerDeadline(LocalDate settlementDate) {
        return settlementDate.minusDays(1).atTime(PixAutoDebitRules.PAYER_CANCEL_DEADLINE);
    }

    /** Limite do recebedor: 22h do dia da liquidacao. */
    public static LocalDateTime receiverDeadline(LocalDate settlementDate) {
        return settlementDate.atTime(PixAutoDebitRules.RECEIVER_CANCEL_DEADLINE);
    }

    public static boolean canCancel(Actor actor, LocalDate settlementDate, LocalDateTime at) {
        if (actor == null || settlementDate == null || at == null) {
            throw new IllegalArgumentException("ator, data de liquidacao e instante sao obrigatorios");
        }
        LocalDateTime deadline = actor == Actor.PAGADOR
                ? payerDeadline(settlementDate) : receiverDeadline(settlementDate);
        return !at.isAfter(deadline);
    }
}

package br.com.ziipsan.operations.metering.selfreading;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A fila de revisao humana da autoleitura (II-7, E3): ESTADO puro, sem
 * tela (decisao 57 -- a fila visual e a pendencia P-II7-TELA). Guarda os
 * desfechos {@code PENDING_HUMAN_REVIEW} por ordem DETERMINISTICA
 * (competencia, unidade, submissao) e recusa qualquer outro estado --
 * aceite e pendente-de-OCR nao entram na fila de revisao humana -- e
 * recusa duplicados da mesma submissao. A decisao humana em si
 * (aceitar/corrigir/pedir nova foto) e da integracao/operacao; aqui fica
 * a fila ordenada e tipificada.
 */
public final class HumanReviewQueue {

    private final List<SelfReadingOutcome> items = new ArrayList<>();
    private final Set<UUID> seen = new HashSet<>();

    /** Enfileira um desfecho de revisao humana. */
    public void enqueue(SelfReadingOutcome outcome) {
        if (outcome == null) {
            throw new IllegalArgumentException("outcome nao pode ser null");
        }
        if (outcome.status() != SelfReadingStatus.PENDING_HUMAN_REVIEW) {
            throw new IllegalArgumentException(
                    "so desfechos PENDING_HUMAN_REVIEW entram na fila; recebido " + outcome.status());
        }
        if (!seen.add(outcome.submissionId())) {
            throw new IllegalArgumentException(
                    "submissao ja esta na fila: " + outcome.submissionId());
        }
        items.add(outcome);
    }

    /** A fila por ordem deterministica (competencia, unidade, submissao). */
    public List<SelfReadingOutcome> pending() {
        List<SelfReadingOutcome> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparing(SelfReadingOutcome::reference)
                .thenComparing(o -> o.unitId().toString())
                .thenComparing(o -> o.submissionId().toString()));
        return List.copyOf(sorted);
    }

    public int size() {
        return items.size();
    }
}

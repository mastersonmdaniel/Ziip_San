package br.com.ziipsan.operations.metering.selfreading;

import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A fila de revisao humana como ESTADO (§4.2 da estimativa): so entram
 * desfechos PENDING_HUMAN_REVIEW, sem duplicados, ordem deterministica.
 */
class HumanReviewQueueTest {

    private SelfReadingOutcome outcome(UUID submission, UUID unit, YearMonth ref,
                                       SelfReadingStatus status) {
        return new SelfReadingOutcome(submission, unit, ref, status,
                List.of(SelfReadingReason.SCORE_BELOW_THRESHOLD), null, null,
                GeoProofResult.absent(), null);
    }

    @Test
    void ordenacaoDeterministicaPorCompetenciaUnidadeSubmissao() {
        UUID u1 = UUID.fromString("11111111-1111-4111-8111-111111111111");
        UUID u2 = UUID.fromString("22222222-2222-4222-8222-222222222222");
        UUID s1 = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        UUID s2 = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
        UUID s3 = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");
        HumanReviewQueue queue = new HumanReviewQueue();
        queue.enqueue(outcome(s2, u2, YearMonth.of(2026, 11), SelfReadingStatus.PENDING_HUMAN_REVIEW));
        queue.enqueue(outcome(s3, u1, YearMonth.of(2026, 12), SelfReadingStatus.PENDING_HUMAN_REVIEW));
        queue.enqueue(outcome(s1, u1, YearMonth.of(2026, 11), SelfReadingStatus.PENDING_HUMAN_REVIEW));
        List<SelfReadingOutcome> pending = queue.pending();
        assertEquals(3, queue.size());
        assertEquals(List.of(s1, s2, s3),
                pending.stream().map(SelfReadingOutcome::submissionId).toList());
        // determinismo: segunda leitura da fila devolve a mesma ordem
        assertEquals(pending, queue.pending());
    }

    @Test
    void recusaEstadosQueNaoSaoRevisaoHumana() {
        HumanReviewQueue queue = new HumanReviewQueue();
        UUID s = UUID.randomUUID();
        UUID u = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> queue.enqueue(
                outcome(s, u, YearMonth.of(2026, 11), SelfReadingStatus.ACCEPTED_FOR_CRITIQUE)));
        assertThrows(IllegalArgumentException.class, () -> queue.enqueue(
                outcome(s, u, YearMonth.of(2026, 11), SelfReadingStatus.PENDING_OCR)));
        assertEquals(0, queue.size());
    }

    @Test
    void recusaDuplicadoDaMesmaSubmissao() {
        HumanReviewQueue queue = new HumanReviewQueue();
        UUID s = UUID.randomUUID();
        UUID u = UUID.randomUUID();
        queue.enqueue(outcome(s, u, YearMonth.of(2026, 11), SelfReadingStatus.PENDING_HUMAN_REVIEW));
        assertThrows(IllegalArgumentException.class, () -> queue.enqueue(
                outcome(s, u, YearMonth.of(2026, 11), SelfReadingStatus.PENDING_HUMAN_REVIEW)));
        assertEquals(1, queue.size());
    }
}

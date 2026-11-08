package br.com.ziipsan.commercial.billing.field;

import br.com.ziipsan.commercial.billingrules.FieldBillCalculator;
import br.com.ziipsan.commercial.billingrules.FieldBillInput;
import br.com.ziipsan.commercial.billingrules.FieldBillResult;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldBillSubmissionServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-07T12:00:00Z"), ZoneOffset.UTC);
    private static final UUID BILL_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    private record Harness(FieldBillSubmissionService service, FieldTestSupport.InMemoryStore store,
                           FieldTestSupport.RecordingQueue queue, FieldTestSupport.RecordingAlerts alerts) {
    }

    private Harness harness() {
        var store = new FieldTestSupport.InMemoryStore();
        var queue = new FieldTestSupport.RecordingQueue();
        var alerts = new FieldTestSupport.RecordingAlerts();
        var sequence = new AtomicInteger();
        var service = new FieldBillSubmissionService(CLOCK, store, queue, alerts,
                () -> sequence.incrementAndGet() == 1 ? BILL_ID : UUID.randomUUID());
        return new Harness(service, store, queue, alerts);
    }

    private FieldBillSubmissionService.Submission submission(FieldBillInput fieldInput,
                                                             FieldBillInput serverInput,
                                                             String ocr, String requiredLevel,
                                                             boolean certPresent, String signedLevel) {
        FieldBillResult printed = FieldBillCalculator.calculate(fieldInput);
        return new FieldBillSubmissionService.Submission(FieldTestSupport.TENANT, UUID.randomUUID(),
                fieldInput, printed, serverInput, ocr, requiredLevel, certPresent, signedLevel,
                false, null);
    }

    @Test
    void identicalRecalculationConfirms() {
        Harness h = harness();
        var input = FieldTestSupport.input(FieldTestSupport.TARIFF_VERSION, 1000, 1012);
        var outcome = h.service().submit(submission(input, input, "MATCH", null, true, null));
        assertThat(outcome.duplicate()).isFalse();
        assertThat(outcome.record().status()).isEqualTo(FieldBillStatus.CONFIRMED);
        assertThat(outcome.record().billId()).isEqualTo(BILL_ID);
        assertThat(h.queue().explanations).isEmpty();
        assertThat(h.alerts().alerts).isZero();
    }

    @Test
    void resubmissionIsIdempotentNoSecondBillNoSecondQueueNoSecondAlert() {
        Harness h = harness();
        var field = FieldTestSupport.input(FieldTestSupport.TARIFF_VERSION, 1000, 1012);
        var server = FieldTestSupport.input(FieldTestSupport.TARIFF_VERSION, 1000, 1030);
        var first = h.service().submit(submission(field, server, "MATCH", null, true, null));
        assertThat(first.record().status()).isEqualTo(FieldBillStatus.DIVERGENT_QUEUED);

        var second = h.service().submit(submission(field, server, "MATCH", null, true, null));
        assertThat(second.duplicate()).isTrue();
        assertThat(second.record().billId()).isEqualTo(first.record().billId());
        assertThat(h.store().byKey).hasSize(1);
        assertThat(h.queue().explanations).hasSize(1);
        assertThat(h.alerts().alerts).isEqualTo(1);
    }

    @Test
    void valueDivergenceQueuesAndAlertsCentralOnce() {
        Harness h = harness();
        var field = FieldTestSupport.input(FieldTestSupport.TARIFF_VERSION, 1000, 1012);
        var server = FieldTestSupport.input(FieldTestSupport.TARIFF_VERSION, 1000, 1020);
        var outcome = h.service().submit(submission(field, server, "MATCH", null, true, null));
        assertThat(outcome.record().status()).isEqualTo(FieldBillStatus.DIVERGENT_QUEUED);
        assertThat(outcome.divergenceQueueItemId()).isNotNull();
        assertThat(h.queue().explanations).hasSize(1);
        assertThat(h.queue().explanations.get(0)).contains("total");
        assertThat(h.alerts().alerts).isEqualTo(1);
    }

    @Test
    void basisDivergenceQueuesEvenWithSameTotals() {
        Harness h = harness();
        UUID otherVersion = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
        var field = FieldTestSupport.input(otherVersion, 1000, 1012);
        var server = FieldTestSupport.input(FieldTestSupport.TARIFF_VERSION, 1000, 1012);
        var outcome = h.service().submit(submission(field, server, "MATCH", null, true, null));
        assertThat(outcome.record().status()).isEqualTo(FieldBillStatus.DIVERGENT_QUEUED);
        assertThat(h.queue().explanations.get(0)).contains("\"basisDivergent\":true");
    }

    @Test
    void ocrReviewHoldsEmission() {
        Harness h = harness();
        var input = FieldTestSupport.input(FieldTestSupport.TARIFF_VERSION, 1000, 1012);
        var outcome = h.service().submit(submission(input, input, "REVIEW", null, true, null));
        assertThat(outcome.record().status()).isEqualTo(FieldBillStatus.OCR_HELD);
        assertThat(h.queue().explanations).isEmpty();
    }

    @Test
    void missingDeviceCertificateBlocksSignedDocument() {
        Harness h = harness();
        var input = FieldTestSupport.input(FieldTestSupport.TARIFF_VERSION, 1000, 1012);
        var outcome = h.service().submit(submission(input, input, "MATCH", "QUALIFIED", false, null));
        assertThat(outcome.record().status()).isEqualTo(FieldBillStatus.CERTIFICATE_MISSING);
    }

    @Test
    void signatureBelowRequiredLevelBlocks() {
        Harness h = harness();
        var input = FieldTestSupport.input(FieldTestSupport.TARIFF_VERSION, 1000, 1012);
        var outcome = h.service().submit(submission(input, input, "MATCH", "QUALIFIED", true, "SIMPLE"));
        assertThat(outcome.record().status()).isEqualTo(FieldBillStatus.CERTIFICATE_MISSING);
    }

    @Test
    void contingencyWithoutMotiveIsRejected() {
        Harness h = harness();
        var input = FieldTestSupport.input(FieldTestSupport.TARIFF_VERSION, 1000, 1012);
        FieldBillResult printed = FieldBillCalculator.calculate(input);
        var bad = new FieldBillSubmissionService.Submission(FieldTestSupport.TENANT, UUID.randomUUID(),
                input, printed, input, "MATCH", null, true, null, true, null);
        assertThatThrownBy(() -> h.service().submit(bad))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("motivo");
        assertThat(h.store().byKey).isEmpty();
    }

    @Test
    void contingencyWithMotiveIsMarkedOnTheRecord() {
        Harness h = harness();
        var input = FieldTestSupport.input(FieldTestSupport.TARIFF_VERSION, 1000, 1012);
        FieldBillResult printed = FieldBillCalculator.calculate(input);
        var sub = new FieldBillSubmissionService.Submission(FieldTestSupport.TENANT, UUID.randomUUID(),
                input, printed, input, "MATCH", null, true, null, true, "SEFAZ indisponivel");
        var outcome = h.service().submit(sub);
        assertThat(outcome.record().status()).isEqualTo(FieldBillStatus.CONFIRMED);
        assertThat(outcome.record().nfageContingency()).isTrue();
    }
}

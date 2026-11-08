package br.com.ziipsan.commercial.billing.field;

import br.com.ziipsan.commercial.billingrules.FieldBillCalculator;
import br.com.ziipsan.commercial.billingrules.FieldBillInput;
import br.com.ziipsan.commercial.billingrules.FieldBillResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DivergenceApprovalServiceTest {

    private static final UUID SUBMITTER = UUID.fromString("00000000-0000-0000-0000-000000000051");
    private static final UUID APPROVER_1 = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID APPROVER_2 = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final Set<String> WITH_PERMISSION = Set.of(DivergenceApprovalService.APPROVE_PERMISSION);
    private static final Set<String> WITHOUT = Set.of("billing:read");

    private record Harness(DivergenceApprovalService service, FieldTestSupport.InMemoryStore store,
                           FieldTestSupport.RecordingCorrections corrections,
                           FieldBillRecord queued, String printedFingerprint) {
    }

    /** Conta divergente: impresso total 27.90 (leitura 1012), servidor 49.50 (leitura 1020). */
    private Harness harness() {
        var store = new FieldTestSupport.InMemoryStore();
        var corrections = new FieldTestSupport.RecordingCorrections();
        FieldBillInput field = FieldTestSupport.input(FieldTestSupport.TARIFF_VERSION, 1000, 1012);
        FieldBillInput server = FieldTestSupport.input(FieldTestSupport.TARIFF_VERSION, 1000, 1020);
        FieldBillResult printed = FieldBillCalculator.calculate(field);
        FieldBillResult recalc = FieldBillCalculator.calculate(server);
        FieldBillRecord record = new FieldBillRecord(FieldTestSupport.TENANT, FieldTestSupport.TASK,
                UUID.randomUUID(), UUID.fromString("00000000-0000-0000-0000-0000000000b1"),
                printed, recalc, FieldBillStatus.DIVERGENT_QUEUED, 1, false,
                OffsetDateTime.of(2026, 11, 7, 12, 0, 0, 0, ZoneOffset.UTC));
        store.save(record);
        return new Harness(new DivergenceApprovalService(store, corrections), store, corrections,
                record, printed.fingerprint());
    }

    private DivergenceApprovalService.Approval approval(UUID first, Set<String> firstPerms,
                                                        UUID second, Set<String> secondPerms,
                                                        String limit) {
        return new DivergenceApprovalService.Approval(FieldTestSupport.TENANT, FieldTestSupport.TASK,
                SUBMITTER, first, firstPerms, second, secondPerms, new BigDecimal(limit),
                "Divergencia conferida em campo");
    }

    @Test
    void deltaIsTwentyOneSixty() {
        Harness h = harness();
        BigDecimal delta = h.queued().serverRecalc().total()
                .subtract(h.queued().printed().total()).abs();
        assertThat(delta).isEqualByComparingTo("21.60");
    }

    @Test
    void approverWithoutP220PermissionIsRejected() {
        Harness h = harness();
        assertThatThrownBy(() -> h.service().approveAndCorrect(
                approval(APPROVER_1, WITHOUT, null, null, "100.00")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(DivergenceApprovalService.APPROVE_PERMISSION);
        assertThat(h.corrections().calls).isZero();
    }

    @Test
    void submitterCannotApproveOwnDivergence() {
        Harness h = harness();
        assertThatThrownBy(() -> h.service().approveAndCorrect(
                approval(SUBMITTER, WITH_PERMISSION, null, null, "100.00")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("propria divergencia");
    }

    @Test
    void belowLimitSingleApproverCorrectsByNewVersionPrintedUntouched() {
        Harness h = harness();
        FieldBillRecord corrected = h.service().approveAndCorrect(
                approval(APPROVER_1, WITH_PERMISSION, null, null, "100.00"));
        assertThat(corrected.status()).isEqualTo(FieldBillStatus.CORRECTED_BY_NEW_VERSION);
        assertThat(corrected.currentBillVersion()).isEqualTo(2);
        assertThat(corrected.printed().fingerprint()).isEqualTo(h.printedFingerprint());
        assertThat(h.corrections().calls).isEqualTo(1);
        assertThat(h.corrections().lastCorrectedValues.total()).isEqualByComparingTo("49.50");
    }

    @Test
    void aboveLimitWithoutSecondApproverIsRejected() {
        Harness h = harness();
        assertThatThrownBy(() -> h.service().approveAndCorrect(
                approval(APPROVER_1, WITH_PERMISSION, null, null, "10.00")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SEGUNDO aprovador");
        assertThat(h.corrections().calls).isZero();
    }

    @Test
    void aboveLimitWithDistinctSecondApproverCorrects() {
        Harness h = harness();
        FieldBillRecord corrected = h.service().approveAndCorrect(
                approval(APPROVER_1, WITH_PERMISSION, APPROVER_2, WITH_PERMISSION, "10.00"));
        assertThat(corrected.status()).isEqualTo(FieldBillStatus.CORRECTED_BY_NEW_VERSION);
        assertThat(corrected.currentBillVersion()).isEqualTo(2);
    }

    @Test
    void aboveLimitSamePersonTwiceIsNotDoubleApproval() {
        Harness h = harness();
        assertThatThrownBy(() -> h.service().approveAndCorrect(
                approval(APPROVER_1, WITH_PERMISSION, APPROVER_1, WITH_PERMISSION, "10.00")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duas pessoas distintas");
    }

    @Test
    void nonDivergentRecordCannotBeApproved() {
        Harness h = harness();
        h.store().save(h.queued().withStatus(FieldBillStatus.CONFIRMED, 1));
        assertThatThrownBy(() -> h.service().approveAndCorrect(
                approval(APPROVER_1, WITH_PERMISSION, null, null, "100.00")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nao esta em divergencia");
    }
}

package br.com.ziipsan.commercial.billingrules;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtifactAndRouteTest {

    @Test
    void sha256KnownAnswer() {
        // SHA-256("abc"), vetor publico do FIPS 180-4.
        assertThat(ArtifactHash.sha256Hex("abc".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void requireMatchRejectsDifferentArtifact() {
        byte[] artifact = "jar-bytes".getBytes(StandardCharsets.UTF_8);
        String hash = ArtifactHash.sha256Hex(artifact);
        ArtifactHash.requireMatch(hash, artifact, "teste");
        assertThatThrownBy(() -> ArtifactHash.requireMatch(
                "0000000000000000000000000000000000000000000000000000000000000000", artifact, "teste"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("diverge");
    }

    @Test
    void routePackageRequiresCurrentContract() {
        FieldBillInput base = new FieldBillInput(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                100, 100, 5, 1, FieldBillCalculatorTest.snapshotA(), List.of(), List.of(),
                LocalDate.of(2026, 12, 10));
        RoutePackage pkg = new RoutePackage(UUID.randomUUID(), UUID.randomUUID(),
                OffsetDateTime.of(2026, 11, 7, 8, 0, 0, 0, ZoneOffset.UTC),
                RulesVersion.CONTRACT, "a".repeat(64),
                List.of(new RouteTask(base, null)), Map.of(), Map.of(), 20);
        pkg.requireCompatibleRules();
        assertThat(pkg.tasks().get(0).toInput(112).currentReading()).isEqualTo(112);

        RoutePackage foreign = new RoutePackage(UUID.randomUUID(), UUID.randomUUID(),
                OffsetDateTime.of(2026, 11, 7, 8, 0, 0, 0, ZoneOffset.UTC),
                "billing-rules/999", "a".repeat(64),
                List.of(new RouteTask(base, null)), Map.of(), Map.of(), 20);
        assertThatThrownBy(foreign::requireCompatibleRules)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void batchPolicyFlushesOnMultiplesAndAtRouteEnd() {
        BatchSendPolicy policy = new BatchSendPolicy(3);
        assertThat(policy.shouldFlush(0)).isFalse();
        assertThat(policy.shouldFlush(2)).isFalse();
        assertThat(policy.shouldFlush(3)).isTrue();
        assertThat(policy.shouldFlush(6)).isTrue();
        assertThat(policy.shouldFlushAtRouteEnd(1)).isTrue();
        assertThat(policy.shouldFlushAtRouteEnd(0)).isFalse();
        assertThatThrownBy(() -> new BatchSendPolicy(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void retainedBillsAreUniquePerTaskAndRejectChangedPrint() {
        RetainedBills retained = new RetainedBills();
        FieldBillResult printed = FieldBillCalculator.calculate(
                FieldBillCalculatorTest.input(1000, 1012, 1, List.of(), List.of()));
        assertThat(retained.retain(printed)).isTrue();
        assertThat(retained.retain(printed)).isFalse();
        assertThat(retained.size()).isEqualTo(1);

        FieldBillResult changed = FieldBillCalculator.calculate(new FieldBillInput(
                printed.taskId(), UUID.randomUUID(), UUID.randomUUID(), 1000, 1020, 5, 1,
                FieldBillCalculatorTest.snapshotA(), List.of(), List.of(), LocalDate.of(2026, 12, 10)));
        assertThatThrownBy(() -> retained.retain(changed))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nao muda");

        retained.markSent(printed.taskId());
        assertThat(retained.size()).isZero();
        assertThatThrownBy(() -> retained.markSent(printed.taskId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void printerHealthVerdicts() {
        assertThat(PrinterHealthRule.evaluate(true, 80, 30)).isEqualTo(PrinterHealthRule.Verdict.READY);
        assertThat(PrinterHealthRule.evaluate(true, 20, 30)).isEqualTo(PrinterHealthRule.Verdict.LOW_BATTERY);
        assertThat(PrinterHealthRule.evaluate(false, 80, 30)).isEqualTo(PrinterHealthRule.Verdict.HEAD_FAILED);
        assertThat(PrinterHealthRule.evaluate(null, 80, 30)).isEqualTo(PrinterHealthRule.Verdict.NO_READING);
        assertThat(PrinterHealthRule.evaluate(true, null, 30)).isEqualTo(PrinterHealthRule.Verdict.NO_READING);
    }
}

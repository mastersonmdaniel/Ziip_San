package br.com.ziipsan.commercial.collection.dunning.negativation;

import br.com.ziipsan.commercial.collection.dunning.CollectionGates;
import br.com.ziipsan.commercial.collection.dunning.prescription.Prescription;
import br.com.ziipsan.core.parameters.MissingParameterException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static br.com.ziipsan.commercial.collection.dunning.negativation.NegativationTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemittanceReportAndParamsTest {

    static final class FakeFile implements NegativationPorts.BureauFilePort {
        List<FileEntry> lastEntries;
        List<ReturnResult> nextReturn = List.of();
        @Override public GeneratedFile generateRemittance(UUID t, String channel, String purpose, List<FileEntry> entries) {
            lastEntries = entries;
            return new GeneratedFile(channel, "FILE-REF-1", entries.size(), EVAL);
        }
        @Override public List<ReturnResult> importReturn(UUID t, String channel, String fileRef) { return nextReturn; }
    }

    @Test
    void remittanceContainsOnlyEligibleAndRefusalsAreRecorded() {
        UUID deceased = UUID.randomUUID();
        var eligibility = eligibility(holderPort(deceased), noticePort(LocalDate.of(2026, 2, 20)));
        var ledger = new ListingLedger(clockAt(EVAL));
        var fake = new FakeFile();
        var service = new RemittanceAndReturn(params(), eligibility, fake, ledger);
        UUID u1 = UUID.randomUUID(), u2 = UUID.randomUUID(), u3 = UUID.randomUUID();
        var ok = new RemittanceAndReturn.Candidate(u1, UUID.randomUUID(), new BigDecimal("100.00"), LocalDate.of(2025, 1, 10),
                facts(u1, UUID.randomUUID(), "REGULAR", false, false, Set.of(), List.of(debt(LocalDate.of(2025, 1, 10), null))));
        var dead = new RemittanceAndReturn.Candidate(u2, deceased, new BigDecimal("100.00"), LocalDate.of(2025, 1, 10),
                facts(u2, deceased, "REGULAR", false, false, Set.of(), List.of(debt(LocalDate.of(2025, 1, 10), null))));
        var contested = new RemittanceAndReturn.Candidate(u3, UUID.randomUUID(), new BigDecimal("100.00"), LocalDate.of(2025, 1, 10),
                facts(u3, UUID.randomUUID(), "REGULAR", true, false, Set.of(), List.of(debt(LocalDate.of(2025, 1, 10), null))));
        var outcome = service.generate(TENANT, "BUREAU_TENANT_CODE", CollectionGates.ListingPurpose.NEGATIVATION,
                List.of(ok, dead, contested), Prescription.ProviderNature.PRIVATE_LAW,
                Prescription.ClaimType.GENERAL, false, EVAL);
        assertThat(outcome.included()).hasSize(1);
        assertThat(outcome.refused()).hasSize(2);
        assertThat(fake.lastEntries).hasSize(1);
        assertThat(outcome.refused().stream().flatMap(d -> d.reasons().stream()).toList())
                .contains(ListingEligibility.RefusalReason.HOLDER_DECEASED,
                        ListingEligibility.RefusalReason.BILL_CONTESTED_OR_UNDER_REVIEW);
    }

    @Test
    void channelOutsideNegChannelsIsRefused() {
        var service = new RemittanceAndReturn(params(),
                eligibility(holderPort(UUID.randomUUID()), noticePort(LocalDate.of(2026, 2, 20))),
                new FakeFile(), new ListingLedger(clockAt(EVAL)));
        assertThatThrownBy(() -> service.generate(TENANT, "UNKNOWN_CHANNEL", CollectionGates.ListingPurpose.NEGATIVATION,
                List.of(), Prescription.ProviderNature.PRIVATE_LAW, Prescription.ClaimType.GENERAL, false, EVAL))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("neg.channels");
    }

    @Test
    void returnImportKeepsChannelRejectionVerbatim() {
        var ledger = new ListingLedger(clockAt(EVAL));
        var listing = ledger.registerInclusion(TENANT, UUID.randomUUID(), UUID.randomUUID(),
                "BUREAU_TENANT_CODE", "NEGATIVATION", new BigDecimal("10.00"));
        var fake = new FakeFile();
        fake.nextReturn = List.of(
                new NegativationPorts.BureauFilePort.ReturnResult(listing.id(), false, "CH-CODE-AS-RETURNED", "motivo devolvido pelo canal"));
        var service = new RemittanceAndReturn(params(),
                eligibility(holderPort(UUID.randomUUID()), noticePort(LocalDate.of(2026, 2, 20))), fake, ledger);
        var outcome = service.importReturn(TENANT, "BUREAU_TENANT_CODE", "FILE-REF-1");
        assertThat(outcome.accepted()).isZero();
        assertThat(outcome.rejected()).hasSize(1);
        assertThat(outcome.rejected().get(0).channelMessage()).isEqualTo("motivo devolvido pelo canal");
        assertThat(outcome.rejected().get(0).channelCode()).isEqualTo("CH-CODE-AS-RETURNED");
    }

    @Test
    void reportCountsInclusionsAndExclusionsByReason() {
        var ledger = new ListingLedger(clockAt(EVAL));
        var a = ledger.registerInclusion(TENANT, UUID.randomUUID(), UUID.randomUUID(), "BUREAU_TENANT_CODE", "NEGATIVATION", new BigDecimal("10.00"));
        var b = ledger.registerInclusion(TENANT, UUID.randomUUID(), UUID.randomUUID(), "BUREAU_TENANT_CODE", "PROTEST", new BigDecimal("20.00"));
        ledger.registerInclusion(TENANT, UUID.randomUUID(), UUID.randomUUID(), "BUREAU_TENANT_CODE", "NEGATIVATION", new BigDecimal("30.00"));
        ledger.cancelAccount(a.id());
        ledger.excludeByServiceOrder(b.id(), "NEGATIVE_LISTING_EXCLUSION", "OS-7");
        var report = ListingReport.build(ledger, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
        assertThat(report.inclusions()).isEqualTo(3);
        assertThat(report.excluded()).isEqualTo(2);
        assertThat(report.active()).isEqualTo(1);
        assertThat(report.exclusionsByReason())
                .containsEntry(ListingLedger.ExclusionReason.ACCOUNT_CANCELLED, 1)
                .containsEntry(ListingLedger.ExclusionReason.SERVICE_ORDER_REQUEST, 1);
    }

    @Test
    void missingNegParametersAreExplicitErrors() {
        var empty = NegativationParameters.fromValues(Map.of());
        assertThatThrownBy(empty::channels).isInstanceOf(MissingParameterException.class);
        assertThatThrownBy(empty::noticeMinDays).isInstanceOf(MissingParameterException.class);
        assertThatThrownBy(empty::excludedProfiles).isInstanceOf(MissingParameterException.class);
    }

    @Test
    void samae215SliceIsCompleteAndDistinct() {
        assertThat(Samae215NegCoverage.ED30_SLICE).hasSize(11);
        assertThat(Samae215NegCoverage.requirements()).doesNotHaveDuplicates();
        assertThat(Samae215NegCoverage.ED30_SLICE)
                .allSatisfy(e -> { assertThat(e.requirement()).isNotBlank(); assertThat(e.feature()).isNotBlank(); });
    }
}

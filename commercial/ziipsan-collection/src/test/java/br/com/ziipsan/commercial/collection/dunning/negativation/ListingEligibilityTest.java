package br.com.ziipsan.commercial.collection.dunning.negativation;

import br.com.ziipsan.commercial.collection.dunning.CollectionGates;
import br.com.ziipsan.commercial.collection.dunning.prescription.Prescription;
import br.com.ziipsan.commercial.collection.dunning.repactuation.Repactuation;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static br.com.ziipsan.commercial.collection.dunning.negativation.NegativationTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class ListingEligibilityTest {

    private ListingEligibility.Decision eval(ListingEligibility eligibility, ListingEligibility.UnitFacts facts,
                                             CollectionGates.ListingPurpose purpose) {
        return eligibility.evaluate(TENANT, facts, purpose,
                Prescription.ProviderNature.PRIVATE_LAW, Prescription.ClaimType.GENERAL, false, EVAL);
    }

    @Test
    void eligibleWhenNoticeDeliveredExactlyAtMinimumInterval() {
        var e = eligibility(holderPort(UUID.randomUUID()), noticePort(LocalDate.of(2026, 2, 20)));
        var d = eval(e, facts(UUID.randomUUID(), UUID.randomUUID(), "REGULAR", false, false, Set.of(),
                List.of(debt(LocalDate.of(2025, 1, 10), null))), CollectionGates.ListingPurpose.NEGATIVATION);
        assertThat(d.eligible()).isTrue();
        assertThat(d.reasons()).isEmpty();
    }

    @Test
    void refusedWithoutRegisteredDeliveryProof() {
        var e = eligibility(holderPort(UUID.randomUUID()), noticePort(null));
        var d = eval(e, facts(UUID.randomUUID(), UUID.randomUUID(), "REGULAR", false, false, Set.of(),
                List.of(debt(LocalDate.of(2025, 1, 10), null))), CollectionGates.ListingPurpose.NEGATIVATION);
        assertThat(d.eligible()).isFalse();
        assertThat(d.reasons()).containsExactly(ListingEligibility.RefusalReason.PRIOR_NOTICE_WITHOUT_DELIVERY_PROOF);
    }

    @Test
    void refusedWhenMinimumIntervalNotMet() {
        var e = eligibility(holderPort(UUID.randomUUID()), noticePort(LocalDate.of(2026, 2, 25)));
        var d = eval(e, facts(UUID.randomUUID(), UUID.randomUUID(), "REGULAR", false, false, Set.of(),
                List.of(debt(LocalDate.of(2025, 1, 10), null))), CollectionGates.ListingPurpose.NEGATIVATION);
        assertThat(d.reasons()).containsExactly(ListingEligibility.RefusalReason.PRIOR_NOTICE_INTERVAL_NOT_MET);
    }

    @Test
    void deceasedHolderIsNeverListed() {
        UUID deceased = UUID.randomUUID();
        var e = eligibility(holderPort(deceased), noticePort(LocalDate.of(2026, 2, 20)));
        var d = eval(e, facts(UUID.randomUUID(), deceased, "REGULAR", false, false, Set.of(),
                List.of(debt(LocalDate.of(2025, 1, 10), null))), CollectionGates.ListingPurpose.NEGATIVATION);
        assertThat(d.eligible()).isFalse();
        assertThat(d.reasons()).containsExactly(ListingEligibility.RefusalReason.HOLDER_DECEASED);
    }

    @Test
    void excludedProfileContestedAndRepactuationAccumulateReasons() {
        var e = eligibility(holderPort(UUID.randomUUID()), noticePort(LocalDate.of(2026, 2, 20)));
        var d = eval(e, facts(UUID.randomUUID(), UUID.randomUUID(), "SOCIAL_TARIFF", true, true,
                Set.of(Repactuation.Effect.SUSPEND_NEGATIVATION),
                List.of(debt(LocalDate.of(2025, 1, 10), null))), CollectionGates.ListingPurpose.NEGATIVATION);
        assertThat(d.reasons()).containsExactlyInAnyOrder(
                ListingEligibility.RefusalReason.EXCLUDED_PROFILE,
                ListingEligibility.RefusalReason.BILL_CONTESTED_OR_UNDER_REVIEW,
                ListingEligibility.RefusalReason.REPACTUATION_ACTIVE);
        assertThat(d.detail()).isNotBlank();
    }

    @Test
    void prescribedDebtBlockedAndAcknowledgmentOfAnotherItemDoesNotMaskIt() {
        var e = eligibility(holderPort(UUID.randomUUID()), noticePort(LocalDate.of(2026, 2, 20)));
        var d = eval(e, facts(UUID.randomUUID(), UUID.randomUUID(), "REGULAR", false, false, Set.of(),
                List.of(debt(LocalDate.of(2015, 6, 1), null),
                        debt(LocalDate.of(2025, 1, 10), LocalDate.of(2026, 1, 15)))),
                CollectionGates.ListingPurpose.NEGATIVATION);
        assertThat(d.reasons()).contains(ListingEligibility.RefusalReason.DEBT_PRESCRIBED);
    }

    @Test
    void undeterminedPrescriptionBlocksByCaution() {
        var e = eligibility(holderPort(UUID.randomUUID()), noticePort(LocalDate.of(2026, 2, 20)));
        var d = e.evaluate(TENANT, facts(UUID.randomUUID(), UUID.randomUUID(), "REGULAR", false, false, Set.of(),
                        List.of(debt(LocalDate.of(2025, 1, 10), null))),
                CollectionGates.ListingPurpose.PROTEST,
                Prescription.ProviderNature.PRIVATE_LAW, Prescription.ClaimType.TARIFF_EXECUTION, false, EVAL);
        assertThat(d.reasons()).containsExactly(ListingEligibility.RefusalReason.PRESCRIPTION_UNDETERMINED);
    }

    @Test
    void repactuationSuspendingOnlyNegativationDoesNotBlockProtest() {
        var e = eligibility(holderPort(UUID.randomUUID()), noticePort(LocalDate.of(2026, 2, 20)));
        var d = eval(e, facts(UUID.randomUUID(), UUID.randomUUID(), "REGULAR", false, true,
                Set.of(Repactuation.Effect.SUSPEND_NEGATIVATION),
                List.of(debt(LocalDate.of(2025, 1, 10), null))), CollectionGates.ListingPurpose.PROTEST);
        assertThat(d.eligible()).isTrue();
    }
}

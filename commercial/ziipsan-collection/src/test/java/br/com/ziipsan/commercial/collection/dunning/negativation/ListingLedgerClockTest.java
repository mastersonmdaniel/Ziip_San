package br.com.ziipsan.commercial.collection.dunning.negativation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static br.com.ziipsan.commercial.collection.dunning.negativation.NegativationTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListingLedgerClockTest {

    private ListingLedger.Listing include(ListingLedger ledger) {
        return ledger.registerInclusion(TENANT, UUID.randomUUID(), UUID.randomUUID(),
                "BUREAU_TENANT_CODE", "NEGATIVATION", new BigDecimal("250.00"));
    }

    @Test
    void businessDaysSkipWeekend() {
        // sexta 06/03/2026 + 5 dias uteis = sexta 13/03/2026
        assertThat(ListingLedger.plusBusinessDays(LocalDate.of(2026, 3, 6), 5))
                .isEqualTo(LocalDate.of(2026, 3, 13));
    }

    @Test
    void paymentMarksExclusionWithLockedFiveBusinessDayDeadline() {
        var clock = new MutableClock(LocalDate.of(2026, 3, 6));
        var ledger = new ListingLedger(clock);
        var listing = include(ledger);
        var marked = ledger.markPaid(listing.id());
        assertThat(marked.status()).isEqualTo(ListingLedger.Status.EXCLUSION_PENDING_SEND);
        assertThat(marked.exclusionMarkedOn()).isEqualTo(LocalDate.of(2026, 3, 6));
        assertThat(marked.exclusionDueOn()).isEqualTo(LocalDate.of(2026, 3, 13));
        clock.set(LocalDate.of(2026, 3, 13));
        assertThat(ledger.sendPendingExclusion(listing.id()).status()).isEqualTo(ListingLedger.Status.EXCLUDED);
    }

    @Test
    void sendingExclusionAfterLockedDeadlineIsRefusedWithViolationNamed() {
        var clock = new MutableClock(LocalDate.of(2026, 3, 6));
        var ledger = new ListingLedger(clock);
        var listing = include(ledger);
        ledger.markPaid(listing.id());
        clock.set(LocalDate.of(2026, 3, 16));
        assertThatThrownBy(() -> ledger.sendPendingExclusion(listing.id()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fora do prazo travado");
    }

    @Test
    void cancelledAccountIsWrittenOffImmediately() {
        var ledger = new ListingLedger(clockAt(LocalDate.of(2026, 3, 2)));
        var listing = include(ledger);
        var off = ledger.cancelAccount(listing.id());
        assertThat(off.status()).isEqualTo(ListingLedger.Status.EXCLUDED);
        assertThat(off.exclusionReason()).isEqualTo(ListingLedger.ExclusionReason.ACCOUNT_CANCELLED);
        assertThat(off.excludedOn()).isEqualTo(LocalDate.of(2026, 3, 2));
    }

    @Test
    void exclusionByServiceOrderRequiresTheNamedOsType() {
        var ledger = new ListingLedger(clockAt(LocalDate.of(2026, 3, 2)));
        var listing = include(ledger);
        assertThatThrownBy(() -> ledger.excludeByServiceOrder(listing.id(), "SOME_OTHER_OS", "OS-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NEGATIVE_LISTING_EXCLUSION");
        var off = ledger.excludeByServiceOrder(listing.id(), "NEGATIVE_LISTING_EXCLUSION", "OS-42");
        assertThat(off.exclusionReason()).isEqualTo(ListingLedger.ExclusionReason.SERVICE_ORDER_REQUEST);
        assertThat(off.exclusionRef()).isEqualTo("OS-42");
    }

    @Test
    void expiryAtExactlyFiveYearsWithClock() {
        var clock = new MutableClock(LocalDate.of(2021, 3, 2));
        var ledger = new ListingLedger(clock);
        include(ledger);
        clock.set(LocalDate.of(2026, 3, 1));
        assertThat(ledger.expireOverdue()).isEmpty();
        clock.set(LocalDate.of(2026, 3, 2));
        var expired = ledger.expireOverdue();
        assertThat(expired).hasSize(1);
        assertThat(expired.get(0).status()).isEqualTo(ListingLedger.Status.EXPIRED);
    }

    @Test
    void lockedValuesAreFiveBusinessDaysAndFiveYears() {
        assertThat(NegativationParameters.EXCLUSION_SEND_MAX_BUSINESS_DAYS).isEqualTo(5);
        assertThat(NegativationParameters.LISTING_EXPIRY_YEARS).isEqualTo(5);
        assertThat(NegativationParameters.EXCLUSION_SERVICE_ORDER_TYPE).isEqualTo("NEGATIVE_LISTING_EXCLUSION");
    }
}

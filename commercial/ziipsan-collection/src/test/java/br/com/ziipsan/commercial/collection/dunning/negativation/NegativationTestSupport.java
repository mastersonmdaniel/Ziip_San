package br.com.ziipsan.commercial.collection.dunning.negativation;

import br.com.ziipsan.commercial.collection.dunning.prescription.Prescription;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

final class NegativationTestSupport {
    static final LocalDate V1 = LocalDate.of(2026, 1, 1);
    static final LocalDate EVAL = LocalDate.of(2026, 3, 2); // segunda-feira
    static final UUID TENANT = UUID.randomUUID();

    private NegativationTestSupport() {}

    static Prescription.Table table() {
        return new Prescription.Table(List.of(
                new Prescription.RuleRow(Prescription.ProviderNature.PRIVATE_LAW, Prescription.ClaimType.GENERAL,
                        10, "Codigo Civil, art. 205", false, 1, V1),
                new Prescription.RuleRow(Prescription.ProviderNature.PRIVATE_LAW, Prescription.ClaimType.TARIFF_EXECUTION,
                        null, "STJ, Temas 251 a 254 (execucao de tarifa)", false, 1, V1)));
    }

    static NegativationParameters params() {
        return NegativationParameters.fromValues(Map.of(
                "neg.channels", "[\"BUREAU_TENANT_CODE\", \"PROTEST_CENTRAL_TENANT_CODE\"]",
                "neg.prior_notice_by", "\"PRIOR_NOTICE_LETTER\"",
                "neg.notice_min_days", "10",
                "neg.excluded_profiles", "[\"SOCIAL_TARIFF\", \"PUBLIC_BODY\"]"));
    }

    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now;
        MutableClock(LocalDate date) { now = new AtomicReference<>(date.atStartOfDay(ZoneOffset.UTC).toInstant()); }
        void set(LocalDate date) { now.set(date.atStartOfDay(ZoneOffset.UTC).toInstant()); }
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    }

    static Clock clockAt(LocalDate date) { return Clock.fixed(date.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC); }

    /** Porta do titular (P1.41) falsa: falecido so para o customerId marcado. */
    static NegativationPorts.HolderSituationPort holderPort(UUID deceasedCustomerId) {
        return (tenantId, unitId, customerId) -> new NegativationPorts.HolderSituationPort.HolderSituation(
                customerId.equals(deceasedCustomerId), customerId.equals(deceasedCustomerId) ? "DECEASED" : "ACTIVE", "P141-REF");
    }

    /** Porta do aviso previo falsa: entrega registrada na data dada; null = sem prova. */
    static NegativationPorts.PriorNoticePort noticePort(LocalDate deliveredOn) {
        return (tenantId, unitId, noticeBy) -> deliveredOn == null ? Optional.empty()
                : Optional.of(new NegativationPorts.PriorNoticePort.DeliveryProof("NOTICE-1", deliveredOn, "PROOF-1"));
    }

    static ListingEligibility eligibility(NegativationPorts.HolderSituationPort holder,
                                          NegativationPorts.PriorNoticePort notice) {
        return new ListingEligibility(params(), table(), holder, notice);
    }

    static ListingEligibility.UnitFacts facts(UUID unitId, UUID customerId, String profile,
                                              boolean contested, boolean repactuation,
                                              java.util.Set<br.com.ziipsan.commercial.collection.dunning.repactuation.Repactuation.Effect> effects,
                                              List<ListingEligibility.DebtItem> debts) {
        return new ListingEligibility.UnitFacts(unitId, customerId, profile, contested, repactuation, effects, debts);
    }

    static ListingEligibility.DebtItem debt(LocalDate due, LocalDate ack) {
        return new ListingEligibility.DebtItem(UUID.randomUUID(), due, ack);
    }
}

package br.com.ziipsan.commercial.collection.dunning.negativation;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Relatorio de inclusoes e exclusoes (ED-30): sobre o livro, por periodo
 * de inclusao — totais por situacao, exclusoes por motivo e as inclusoes
 * ativas alem da expiracao (que a varredura deveria ter baixado: o
 * relatorio as expoe em vez de esconde-las).
 */
public final class ListingReport {

    public record Report(LocalDate from, LocalDate to, int inclusions, int active, int pendingSend,
                         int excluded, int expired,
                         Map<ListingLedger.ExclusionReason, Integer> exclusionsByReason,
                         List<ListingLedger.Listing> activeBeyondExpiry) {}

    private ListingReport() {}

    public static Report build(ListingLedger ledger, LocalDate from, LocalDate to) {
        if (ledger == null || from == null || to == null || to.isBefore(from))
            throw new IllegalArgumentException("relatorio exige livro e periodo valido");
        int inclusions = 0, active = 0, pending = 0, excluded = 0, expired = 0;
        Map<ListingLedger.ExclusionReason, Integer> byReason = new EnumMap<>(ListingLedger.ExclusionReason.class);
        List<ListingLedger.Listing> beyond = new java.util.ArrayList<>();
        for (ListingLedger.Listing l : ledger.all()) {
            if (l.includedOn().isBefore(from) || l.includedOn().isAfter(to)) continue;
            inclusions++;
            switch (l.status()) {
                case ACTIVE -> { active++; if (!ledger.today().isBefore(l.includedOn().plusYears(NegativationParameters.LISTING_EXPIRY_YEARS))) beyond.add(l); }
                case EXCLUSION_PENDING_SEND -> pending++;
                case EXCLUDED -> { excluded++; if (l.exclusionReason() != null) byReason.merge(l.exclusionReason(), 1, Integer::sum); }
                case EXPIRED -> { expired++; byReason.merge(ListingLedger.ExclusionReason.EXPIRED_5_YEARS, 1, Integer::sum); }
            }
        }
        return new Report(from, to, inclusions, active, pending, excluded, expired,
                Map.copyOf(byReason), List.copyOf(beyond));
    }
}

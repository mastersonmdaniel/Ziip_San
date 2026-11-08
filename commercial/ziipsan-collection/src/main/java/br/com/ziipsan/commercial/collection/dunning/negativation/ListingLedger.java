package br.com.ziipsan.commercial.collection.dunning.negativation;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Ciclo de vida da inclusao (ED-30): inclusao, exclusao e baixa, com os
 * prazos TRAVADOS do aceite — exclusao enviada em no maximo
 * {@link NegativationParameters#EXCLUSION_SEND_MAX_BUSINESS_DAYS} dias
 * uteis apos o pagamento e expiracao em
 * {@link NegativationParameters#LISTING_EXPIRY_YEARS} anos. O relogio e
 * injetado ({@link Clock}) para os testes com relogio do aceite.
 *
 * <p>Baixas/exclusoes:
 * <ul>
 *   <li><b>pagamento (quitacao)</b>: a exclusao e MARCADA no pagamento e
 *       tem prazo de envio; enviar depois do prazo e violacao registrada
 *       (excecao), nunca silenciosa — "baixa no prazo legal apos a
 *       quitacao (teste com relogio)";</li>
 *   <li><b>conta cancelada</b> (20b FAT-43): baixa imediata da inclusao;</li>
 *   <li><b>a pedido, por OS</b> {@code NEGATIVE_LISTING_EXCLUSION}: so com
 *       a referencia da OS desse tipo;</li>
 *   <li><b>expiracao</b>: inclusao com 5 anos e baixada pela varredura.</li>
 * </ul>
 *
 * <p>Dia util: segunda a sexta. O calendario de FERIADOS do ente nao tem
 * fonte nesta entrega e fica como pendencia DECLARADA de integracao no
 * relatorio: sem ele, a contagem considera apenas fins de semana.
 */
public final class ListingLedger {

    public enum Status { ACTIVE, EXCLUSION_PENDING_SEND, EXCLUDED, EXPIRED }

    public enum ExclusionReason { PAID, ACCOUNT_CANCELLED, SERVICE_ORDER_REQUEST, EXPIRED_5_YEARS, RETURN_REJECTED }

    public record Listing(UUID id, UUID tenantId, UUID unitId, UUID customerId, String channel,
                          String purpose, BigDecimal amount, LocalDate includedOn, Status status,
                          LocalDate exclusionMarkedOn, LocalDate exclusionDueOn,
                          LocalDate excludedOn, ExclusionReason exclusionReason, String exclusionRef) {
        Listing withStatus(Status s, LocalDate markedOn, LocalDate dueOn, LocalDate excluded, ExclusionReason reason, String ref) {
            return new Listing(id, tenantId, unitId, customerId, channel, purpose, amount, includedOn,
                    s, markedOn, dueOn, excluded, reason, ref);
        }
    }

    private final Clock clock;
    private final Map<UUID, Listing> byId = new LinkedHashMap<>();

    public ListingLedger(Clock clock) { this.clock = Objects.requireNonNull(clock, "relogio e obrigatorio"); }

    public LocalDate today() { return LocalDate.now(clock); }

    /** Soma {@code days} dias uteis (seg–sex) a partir de {@code from} (o proprio dia nao conta). */
    public static LocalDate plusBusinessDays(LocalDate from, int days) {
        Objects.requireNonNull(from, "data inicial e obrigatoria");
        if (days < 0) throw new IllegalArgumentException("dias uteis nao podem ser negativos");
        LocalDate d = from;
        int remaining = days;
        while (remaining > 0) {
            d = d.plusDays(1);
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) remaining--;
        }
        return d;
    }

    /** Registra uma inclusao ja julgada elegivel e ja remetida/aceita no canal. */
    public Listing registerInclusion(UUID tenantId, UUID unitId, UUID customerId, String channel,
                                     String purpose, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("inclusao exige valor positivo");
        Listing listing = new Listing(UUID.randomUUID(), tenantId, unitId, customerId, channel, purpose,
                amount, today(), Status.ACTIVE, null, null, null, null, null);
        byId.put(listing.id(), listing);
        return listing;
    }

    /** Pagamento: MARCA a exclusao hoje e fixa o prazo travado de envio (5 dias uteis). */
    public Listing markPaid(UUID listingId) {
        Listing l = requireActive(listingId);
        LocalDate due = plusBusinessDays(today(), NegativationParameters.EXCLUSION_SEND_MAX_BUSINESS_DAYS);
        Listing updated = l.withStatus(Status.EXCLUSION_PENDING_SEND, today(), due, null, ExclusionReason.PAID, null);
        byId.put(listingId, updated);
        return updated;
    }

    /** Conta cancelada (20b FAT-43): baixa imediata, sem janela de envio. */
    public Listing cancelAccount(UUID listingId) {
        Listing l = requireActiveOrPending(listingId);
        Listing updated = l.withStatus(Status.EXCLUDED, l.exclusionMarkedOn(), l.exclusionDueOn(),
                today(), ExclusionReason.ACCOUNT_CANCELLED, "FAT-43");
        byId.put(listingId, updated);
        return updated;
    }

    /** Exclusao a pedido: exige a OS do tipo NEGATIVE_LISTING_EXCLUSION. */
    public Listing excludeByServiceOrder(UUID listingId, String serviceOrderType, String serviceOrderRef) {
        if (!NegativationParameters.EXCLUSION_SERVICE_ORDER_TYPE.equals(serviceOrderType))
            throw new IllegalArgumentException("exclusao a pedido exige OS do tipo "
                    + NegativationParameters.EXCLUSION_SERVICE_ORDER_TYPE + ", veio " + serviceOrderType);
        if (serviceOrderRef == null || serviceOrderRef.isBlank())
            throw new IllegalArgumentException("exclusao a pedido exige a referencia da OS");
        Listing l = requireActiveOrPending(listingId);
        Listing updated = l.withStatus(Status.EXCLUDED, l.exclusionMarkedOn(), l.exclusionDueOn(),
                today(), ExclusionReason.SERVICE_ORDER_REQUEST, serviceOrderRef);
        byId.put(listingId, updated);
        return updated;
    }

    /**
     * Envia a exclusao marcada (pagamento) ao canal. Enviar APOS o prazo
     * travado e recusado com a violacao nomeada — o atraso fica provado,
     * nao escondido.
     */
    public Listing sendPendingExclusion(UUID listingId) {
        Listing l = byId.get(listingId);
        if (l == null) throw new IllegalArgumentException("inclusao inexistente: " + listingId);
        if (l.status() != Status.EXCLUSION_PENDING_SEND)
            throw new IllegalStateException("inclusao " + listingId + " nao esta com exclusao pendente de envio (status " + l.status() + ")");
        if (today().isAfter(l.exclusionDueOn()))
            throw new IllegalStateException("exclusao fora do prazo travado de "
                    + NegativationParameters.EXCLUSION_SEND_MAX_BUSINESS_DAYS + " dias uteis: marcada em "
                    + l.exclusionMarkedOn() + ", prazo " + l.exclusionDueOn() + ", hoje " + today());
        Listing updated = l.withStatus(Status.EXCLUDED, l.exclusionMarkedOn(), l.exclusionDueOn(),
                today(), l.exclusionReason(), l.exclusionRef());
        byId.put(listingId, updated);
        return updated;
    }

    /** Varredura de expiracao: baixa toda inclusao ativa com 5 anos (travado) ou mais. */
    public List<Listing> expireOverdue() {
        List<Listing> expired = new ArrayList<>();
        for (Listing l : List.copyOf(byId.values())) {
            if (l.status() == Status.ACTIVE || l.status() == Status.EXCLUSION_PENDING_SEND) {
                LocalDate expiry = l.includedOn().plusYears(NegativationParameters.LISTING_EXPIRY_YEARS);
                if (!today().isBefore(expiry)) {
                    Listing updated = l.withStatus(Status.EXPIRED, l.exclusionMarkedOn(), l.exclusionDueOn(),
                            today(), ExclusionReason.EXPIRED_5_YEARS, null);
                    byId.put(l.id(), updated);
                    expired.add(updated);
                }
            }
        }
        return List.copyOf(expired);
    }

    public Listing get(UUID listingId) { return byId.get(listingId); }
    public List<Listing> all() { return List.copyOf(byId.values()); }

    private Listing requireActive(UUID listingId) {
        Listing l = byId.get(listingId);
        if (l == null) throw new IllegalArgumentException("inclusao inexistente: " + listingId);
        if (l.status() != Status.ACTIVE) throw new IllegalStateException("inclusao " + listingId + " nao esta ativa (status " + l.status() + ")");
        return l;
    }

    private Listing requireActiveOrPending(UUID listingId) {
        Listing l = byId.get(listingId);
        if (l == null) throw new IllegalArgumentException("inclusao inexistente: " + listingId);
        if (l.status() != Status.ACTIVE && l.status() != Status.EXCLUSION_PENDING_SEND)
            throw new IllegalStateException("inclusao " + listingId + " ja baixada (status " + l.status() + ")");
        return l;
    }
}

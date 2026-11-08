package br.com.ziipsan.core.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Lote e validade (ED-58): reagentes, produtos quimicos e EPI sao
 * controlados por lote com data de validade; EPI exige o numero do
 * Certificado de Aprovacao (CA). O alerta dispara para lotes vencendo
 * dentro de {@code inv.lot_expiry_alert_days} (parametro do tenant,
 * nomeado pelo acceptance) ou ja vencidos.
 */
public final class LotControl {
    private LotControl() {}

    public record Lot(UUID tenantId, String itemCode, String locationCode, String lotCode,
                      LocalDate expiryDate, String approvalCertificate, BigDecimal qty) {
        public Lot {
            Objects.requireNonNull(tenantId, "tenant e obrigatorio");
            if (itemCode == null || itemCode.isBlank()) throw new IllegalArgumentException("item do lote e obrigatorio");
            if (lotCode == null || lotCode.isBlank()) throw new IllegalArgumentException("codigo do lote e obrigatorio");
            Objects.requireNonNull(expiryDate, "validade do lote e obrigatoria");
            if (qty == null || qty.signum() <= 0) throw new IllegalArgumentException("quantidade do lote deve ser positiva");
        }
        public boolean expiredOn(LocalDate day) { return expiryDate.isBefore(day); }
        public boolean expiringWithin(LocalDate day, int alertDays) {
            return !expiredOn(day) && !expiryDate.isAfter(day.plusDays(alertDays));
        }
    }

    /** Valida o lote contra o item: EPI sem CA e recusado; item sem controle de lote nao recebe lote. */
    public static Lot requireValid(InventoryItem.Item item, Lot lot) {
        Objects.requireNonNull(item, "item e obrigatorio");
        Objects.requireNonNull(lot, "lote e obrigatorio");
        if (!item.lotTracked()) throw new InventoryExceptions.ValidationException("LOT_NOT_TRACKED",
                "item " + item.code() + " nao e controlado por lote/validade");
        if (item.requiresCa() && (lot.approvalCertificate() == null || lot.approvalCertificate().isBlank()))
            throw new InventoryExceptions.ValidationException("CA_REQUIRED",
                    "EPI " + item.code() + " exige o numero do Certificado de Aprovacao (CA) no lote");
        return lot;
    }

    /** Lotes vencidos ou vencendo dentro da janela do tenant, ordenados pela validade. */
    public static List<Lot> expiryAlerts(List<Lot> lots, LocalDate today, int alertDays) {
        return lots.stream().filter(l -> l.expiredOn(today) || l.expiringWithin(today, alertDays))
                .sorted(java.util.Comparator.comparing(Lot::expiryDate)).toList();
    }
}

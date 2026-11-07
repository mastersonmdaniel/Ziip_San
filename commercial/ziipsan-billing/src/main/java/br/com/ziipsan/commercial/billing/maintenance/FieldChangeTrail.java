package br.com.ziipsan.commercial.billing.maintenance;

import br.com.ziipsan.commercial.billing.domain.BillEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Trilha por campo de uma nova versao da conta (aceite da P2.20, DAD-16):
 * para cada campo faturavel relevante, o valor da versao anterior e o da
 * nova; usuario e momento vao nas linhas persistidas
 * ({@code bill_field_changes}). So entram campos que mudaram -- campo igual
 * nao e mudanca. Valores sao fotografados como texto (monetario com 2
 * casas), o mesmo formato que a auditoria mostra.
 */
public final class FieldChangeTrail {

    public record FieldChange(String field, String previousValue, String newValue) {
    }

    private record Watched(String field, Function<BillEntity, String> read) {
    }

    private static final List<Watched> WATCHED = List.of(
            new Watched("status", b -> b.getStatus()),
            new Watched("water_consumption", b -> integer(b.getWaterConsumption())),
            new Watched("sewer_volume", b -> integer(b.getSewerVolume())),
            new Watched("sewer_pct", b -> decimal(b.getSewerPct())),
            new Watched("collection_pct", b -> decimal(b.getCollectionPct())),
            new Watched("water_value", b -> decimal(b.getWaterValue())),
            new Watched("sewer_value", b -> decimal(b.getSewerValue())),
            new Watched("fixed_part_value", b -> decimal(b.getFixedPartValue())),
            new Watched("debits_value", b -> decimal(b.getDebitsValue())),
            new Watched("credits_value", b -> decimal(b.getCreditsValue())),
            new Watched("taxes_value", b -> decimal(b.getTaxesValue())),
            new Watched("total_value", b -> decimal(b.getTotalValue())),
            new Watched("due_date", b -> date(b.getDueDate())),
            new Watched("period_from", b -> date(b.getPeriodFrom())),
            new Watched("period_to", b -> date(b.getPeriodTo())),
            new Watched("valid_until", b -> date(b.getValidUntil())));

    private FieldChangeTrail() {
    }

    public static List<FieldChange> diff(BillEntity previous, BillEntity next) {
        if (previous == null || next == null) {
            throw new IllegalArgumentException("Versao anterior e nova sao obrigatorias para a trilha");
        }
        List<FieldChange> changes = new ArrayList<>();
        for (Watched w : WATCHED) {
            String before = w.read().apply(previous);
            String after = w.read().apply(next);
            if (!java.util.Objects.equals(before, after)) {
                changes.add(new FieldChange(w.field(), before, after));
            }
        }
        return List.copyOf(changes);
    }

    private static String integer(Integer value) {
        return value == null ? null : value.toString();
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static String date(LocalDate value) {
        return value == null ? null : value.toString();
    }
}

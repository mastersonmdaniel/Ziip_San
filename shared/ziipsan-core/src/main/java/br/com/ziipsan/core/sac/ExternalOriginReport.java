package br.com.ziipsan.core.sac;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Indicador por origem externa (ED-35) — os numeros por tras do relatorio
 * "Reclamacoes por origem externa do protocolo" ja definido no catalogo da
 * ED-46 (RPT-102, view rpt_reclamacoes_origem_externa). Esta classe AGREGA;
 * a view/registro no catalogo e da ED-46, nao e duplicada aqui.
 */
public final class ExternalOriginReport {

    public record CaseFact(ExternalOrigin origin, LocalDate dueDate, LocalDate closedDate) {}

    public record Indicator(ExternalOrigin origin, long total, long closed, long late,
                            BigDecimal latePct) {}

    private ExternalOriginReport() {}

    public static Map<ExternalOrigin, Indicator> indicators(List<CaseFact> facts) {
        Map<ExternalOrigin, Indicator> out = new EnumMap<>(ExternalOrigin.class);
        for (ExternalOrigin origin : ExternalOrigin.values()) {
            List<CaseFact> mine = facts.stream().filter(f -> f.origin() == origin).toList();
            long closed = mine.stream().filter(f -> f.closedDate() != null).count();
            long late = mine.stream().filter(f -> f.closedDate() != null
                    && f.closedDate().isAfter(f.dueDate())).count();
            BigDecimal pct = closed == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(late * 100).divide(BigDecimal.valueOf(closed), 2, RoundingMode.HALF_UP);
            out.put(origin, new Indicator(origin, mine.size(), closed, late, pct));
        }
        return out;
    }
}

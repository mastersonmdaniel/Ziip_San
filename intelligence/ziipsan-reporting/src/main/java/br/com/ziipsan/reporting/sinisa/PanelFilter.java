package br.com.ziipsan.reporting.sinisa;

import java.time.LocalDate;

/**
 * Filtro do painel exportavel (aceite: "painel exportavel por periodo,
 * setor, categoria e tipo de ligacao"). Campo {@code null} = sem filtro
 * naquela dimensao. O periodo filtra pelo INICIO do periodo da celula
 * dentro de [{@code periodFrom}, {@code periodTo}] (limites nulos = abertos).
 */
public record PanelFilter(LocalDate periodFrom, LocalDate periodTo,
                          String sector, String category, String connectionType) {

    public static PanelFilter none() {
        return new PanelFilter(null, null, null, null, null);
    }

    public boolean matches(PanelRow row) {
        if (periodFrom != null && row.periodStart().isBefore(periodFrom)) {
            return false;
        }
        if (periodTo != null && row.periodStart().isAfter(periodTo)) {
            return false;
        }
        if (sector != null && !sector.equals(row.sector())) {
            return false;
        }
        if (category != null && !category.equals(row.category())) {
            return false;
        }
        return connectionType == null || connectionType.equals(row.connectionType());
    }
}

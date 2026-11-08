package br.com.ziipsan.reporting.sinisa;

import br.com.ziipsan.reporting.indicator.Semaphore;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Linha apurada do painel: valor e semaforo vieram do registro da P3.41
 * ({@code frozen == true} quando o periodo esta fechado e o valor e o
 * congelado do fechamento — recalculo nao altera periodo fechado).
 */
public record PanelRow(String indicatorCode, LocalDate periodStart, LocalDate periodEnd,
                       String sector, String category, String connectionType,
                       BigDecimal value, Semaphore semaphore, boolean frozen) {
}

package br.com.ziipsan.reporting.export;

import br.com.ziipsan.reporting.registry.ReportColumnType;
import br.com.ziipsan.reporting.registry.ReportDefinition;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bloco de totais anexado ao fim de PDF/XLSX: soma das colunas numericas e
 * contagem de linhas, calculado sobre as linhas JA mascaradas. E a peca que
 * os testes de conteudo de cada relatorio exercitam sobre a massa sintetica.
 */
public final class ReportSummaryCalculator {

    private ReportSummaryCalculator() {
    }

    public static Map<String, BigDecimal> summarize(ReportDefinition definition,
                                                    List<Map<String, Object>> rows) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        totals.put("__rows", BigDecimal.valueOf(rows.size()));
        for (var column : definition.columns()) {
            if (column.type() == ReportColumnType.LONG
                    || column.type() == ReportColumnType.DECIMAL
                    || column.type() == ReportColumnType.MONEY) {
                BigDecimal sum = BigDecimal.ZERO;
                for (Map<String, Object> row : rows) {
                    Object value = row.get(column.name());
                    if (value instanceof Number number) {
                        sum = sum.add(new BigDecimal(number.toString()));
                    } else if (value instanceof String text && !text.isBlank() && !"***".equals(text)) {
                        sum = sum.add(new BigDecimal(text));
                    }
                }
                totals.put(column.name(), sum);
            }
        }
        return totals;
    }
}

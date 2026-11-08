package br.com.ziipsan.reporting.sinisa;

import java.math.BigDecimal;

/**
 * Comparacao de um valor do tenant com a mediana da base de benchmark:
 * diferenca aritmetica e posicao relativa. NENHUM juizo de bom/ruim e
 * nenhum limiar sao inventados: o aceite pede a comparacao com a mediana,
 * e faixas de avaliacao seriam meta do tenant no registro da P3.41
 * (semaforo), nao constante deste codigo.
 */
public record BenchmarkComparison(String indicatorCode, BigDecimal tenantValue, BigDecimal median,
                                  BigDecimal difference, Position position, String baseVersion) {

    public enum Position {
        ABOVE_MEDIAN,
        AT_MEDIAN,
        BELOW_MEDIAN
    }
}

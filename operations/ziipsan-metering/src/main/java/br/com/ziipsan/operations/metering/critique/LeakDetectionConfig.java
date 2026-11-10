package br.com.ziipsan.operations.metering.critique;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;

/**
 * Parametros da deteccao proactiva de vazamento (II-2, E5). TODOS os
 * limiares sao parametros: nenhum valor operacional esta fixado no codigo.
 *
 * <p>{@link #proofDefaults()} e CONSTRUCAO DE PROVA (janela 02h-03h, limiar
 * 0.1, 3 noites): existe so para os testes fixture. O limiar real de caudal
 * minimo nocturno e as noites exigidas sao do tenant/regulador --
 * pendencia P-II2-LIMIARES, {@code [A DEFINIR]}.
 *
 * @param nightHours          horas consideradas nocturnas (nao vazio)
 * @param minNightFlow        caudal minimo: media nocturna ESTRITAMENTE acima disto conta como fluxo continuo
 * @param consecutiveNights   noites consecutivas com fluxo continuo exigidas para o sinal (&gt;= 1)
 */
public record LeakDetectionConfig(Set<Integer> nightHours, BigDecimal minNightFlow, int consecutiveNights) {

    public LeakDetectionConfig {
        Objects.requireNonNull(nightHours, "nightHours");
        Objects.requireNonNull(minNightFlow, "minNightFlow");
        nightHours = Set.copyOf(nightHours);
        if (nightHours.isEmpty()) {
            throw new IllegalArgumentException("janela nocturna vazia");
        }
        for (int h : nightHours) {
            if (h < 0 || h > 23) {
                throw new IllegalArgumentException("hora nocturna fora de 0..23: " + h);
            }
        }
        if (minNightFlow.signum() < 0) {
            throw new IllegalArgumentException("minNightFlow negativo");
        }
        if (consecutiveNights < 1) {
            throw new IllegalArgumentException("consecutiveNights deve ser >= 1");
        }
    }

    /** Configuracao de prova (ver cabecalho): NAO e omissao operacional. */
    public static LeakDetectionConfig proofDefaults() {
        return new LeakDetectionConfig(Set.of(2, 3), new BigDecimal("0.1"), 3);
    }
}

package br.com.ziipsan.commercial.billing.waterquality;

import java.time.LocalDate;
import java.util.List;

/**
 * O bloco mensal de qualidade da agua de um sistema, pronto para o modelo
 * da conta (P2.24, Decreto 5.440/2005; campo obrigatorio
 * {@code water_quality.monthly_block} do conteudo minimo da P2.32).
 * Resultados ordenados pela chave do parametro (ordem estavel de
 * impressao); bloco sem resultado nao existe — a ausencia do bloco e
 * {@code Optional.empty()} no montador, nunca um bloco vazio impresso.
 */
public record WaterQualityBlock(
        LocalDate referenceMonth,
        String supplySystem,
        List<WaterQualityResult> results) {

    public WaterQualityBlock {
        if (referenceMonth == null || supplySystem == null || supplySystem.isBlank()) {
            throw new IllegalArgumentException("referenceMonth e supplySystem sao obrigatorios no bloco");
        }
        if (results == null || results.isEmpty()) {
            throw new IllegalArgumentException("bloco de qualidade da agua sem nenhum resultado nao e bloco");
        }
        results = List.copyOf(results);
        for (WaterQualityResult result : results) {
            if (!result.referenceMonth().equals(referenceMonth) || !result.supplySystem().equals(supplySystem)) {
                throw new IllegalArgumentException("resultado de " + result.parameterKey()
                        + " nao pertence ao mes/sistema do bloco (" + referenceMonth + ", " + supplySystem + ")");
            }
        }
    }
}

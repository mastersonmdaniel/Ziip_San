package br.com.ziipsan.commercial.billing.bill;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Política de multa e juros por impontualidade de um tenant. Nenhum campo tem padrão: percentuais,
 * limites e carência são do contrato/agência (relatórios 08 e 11; o teto de 2% do CDC art. 52 §1º
 * para tarifa pública é pergunta jurídica aberta e <b>não</b> é imposto aqui).
 *
 * @param fineMode             {@link FineMode#FLAT}: multa de um percentual único; {@link FineMode#PER_DAY}:
 *                             percentual por dia de atraso, limitado a {@code fineLimitPct}
 * @param finePct              percentual da multa (único, ou por dia)
 * @param fineLimitPct         teto da multa por dia; obrigatório em PER_DAY, ignorado em FLAT ({@code null})
 * @param interestPct          percentual de juros por {@code interestPeriodDays}
 * @param interestPeriodDays   período a que {@code interestPct} se refere, em dias (30 para "1% ao mês pro
 *                             rata die"; 1 para "0,033% ao dia")
 * @param interestLimitPct     teto dos juros acumulados; {@code null} = sem teto
 * @param graceBusinessDays    não se cobra nada se o pagamento ocorre em até esta quantidade de dias úteis
 *                             depois do vencimento
 */
public record LateFeePolicy(FineMode fineMode, BigDecimal finePct, BigDecimal fineLimitPct,
                            BigDecimal interestPct, int interestPeriodDays, BigDecimal interestLimitPct,
                            int graceBusinessDays) {

    public enum FineMode { FLAT, PER_DAY }

    public LateFeePolicy {
        Objects.requireNonNull(fineMode, "modo da multa");
        requireNonNegative(finePct, "percentual da multa");
        requireNonNegative(interestPct, "percentual de juros");
        if (fineMode == FineMode.PER_DAY) {
            requireNonNegative(fineLimitPct, "limite da multa por dia");
        }
        if (interestPeriodDays < 1) {
            throw new IllegalArgumentException("Período dos juros deve ser pelo menos 1 dia");
        }
        if (interestLimitPct != null && interestLimitPct.signum() < 0) {
            throw new IllegalArgumentException("Limite de juros negativo");
        }
        if (graceBusinessDays < 0) {
            throw new IllegalArgumentException("Carência negativa");
        }
    }

    private static void requireNonNegative(BigDecimal v, String name) {
        if (v == null || v.signum() < 0) {
            throw new IllegalArgumentException("O " + name + " deve ser informado e não negativo: " + v);
        }
    }
}

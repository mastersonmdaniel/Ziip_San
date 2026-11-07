package br.com.ziipsan.commercial.collection.retorno;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Conciliacao do split payment (P3.1; LC 214/2025 e LC 227/2026): confere se o valor
 * liquido recebido bate com o tributo segregado na liquidacao, pelo modo de
 * recolhimento do perfil tributario do tenant ({@code tax.profile.split_payment_mode},
 * parametro definido na trilha da P2.4/P2.34).
 *
 * <p>Modos tratados (um teste por modo):
 * <ul>
 *   <li>{@link SplitPaymentMode#SEGREGATED_AT_SETTLEMENT}: o tributo exato da conta e
 *       segregado na liquidacao; liquido esperado = bruto - tributo da conta;</li>
 *   <li>{@link SplitPaymentMode#SIMPLIFIED_SEGREGATION}: segregacao simplificada por
 *       percentual do perfil (percentual vem do parametro, nunca de codigo);
 *       segregado esperado = bruto x percentual, HALF_UP;</li>
 *   <li>{@link SplitPaymentMode#NO_SEGREGATION}: sem split; o liquido e o bruto e
 *       qualquer segregacao informada e divergencia.</li>
 * </ul>
 *
 * <p>ATENCAO (registro honesto): os codigos dos modos sao os desta implementacao; o
 * catalogo de valores de {@code tax.profile} e da P2.34 (nao executada nesta base) e a
 * fiacao deve mapear o valor do parametro para este enum na integracao, recusando
 * valor desconhecido - nunca presumindo um modo.
 */
public final class SplitPaymentReconciler {

    public enum SplitPaymentMode { SEGREGATED_AT_SETTLEMENT, SIMPLIFIED_SEGREGATION, NO_SEGREGATION }

    public enum ReconciliationStatus { RECONCILED, DIVERGENT }

    /**
     * @param billTaxAmount      tributo (IBS+CBS) destacado da conta; obrigatorio no modo segregado
     * @param simplifiedRatePct  percentual simplificado do perfil tributario; obrigatorio
     *                           no modo simplificado, ignorado nos demais
     * @param segregatedReceived tributo efetivamente segregado informado pelo liquidante
     * @param netReceived        liquido efetivamente creditado ao tenant
     */
    public record SplitInput(SplitPaymentMode mode, BigDecimal grossAmount, BigDecimal billTaxAmount,
                             BigDecimal simplifiedRatePct, BigDecimal segregatedReceived,
                             BigDecimal netReceived) {
    }

    /** @param difference liquido recebido - liquido esperado (zero quando conciliado) */
    public record SplitResult(ReconciliationStatus status, BigDecimal expectedSegregated,
                              BigDecimal expectedNet, BigDecimal difference) {
    }

    public SplitResult reconcile(SplitInput in) {
        Objects.requireNonNull(in, "entrada da conciliacao de split e obrigatoria");
        Objects.requireNonNull(in.mode(), "modo de recolhimento (split_payment_mode) e obrigatorio: ausencia e erro, nunca presuncao");
        BigDecimal gross = requireNonNegative(in.grossAmount(), "valor bruto");
        BigDecimal segregatedReceived = requireNonNegative(in.segregatedReceived(), "tributo segregado recebido");
        BigDecimal netReceived = requireNonNegative(in.netReceived(), "liquido recebido");
        BigDecimal expectedSegregated = switch (in.mode()) {
            case SEGREGATED_AT_SETTLEMENT -> requireNonNegative(in.billTaxAmount(),
                    "tributo da conta (obrigatorio no modo segregado)");
            case SIMPLIFIED_SEGREGATION -> {
                BigDecimal pct = requireNonNegative(in.simplifiedRatePct(),
                        "percentual simplificado do perfil (obrigatorio no modo simplificado)");
                yield gross.multiply(pct).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            }
            case NO_SEGREGATION -> new BigDecimal("0.00");
        };
        BigDecimal expectedNet = gross.subtract(expectedSegregated);
        BigDecimal difference = netReceived.subtract(expectedNet);
        boolean reconciled = difference.signum() == 0 && segregatedReceived.compareTo(expectedSegregated) == 0;
        return new SplitResult(reconciled ? ReconciliationStatus.RECONCILED : ReconciliationStatus.DIVERGENT,
                expectedSegregated, expectedNet, difference);
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(field + " deve ser >= 0: " + value);
        }
        return value;
    }
}

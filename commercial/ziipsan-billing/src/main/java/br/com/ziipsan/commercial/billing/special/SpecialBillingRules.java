package br.com.ziipsan.commercial.billing.special;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Regras de cobranca ligadas as situacoes especiais (ED-22):
 * isencao, compensacao por nao-conformidade de qualidade
 * ({@code quality.noncompliance_compensation}), faturamento antecipado
 * ({@code bill.advance_billing}) e conta final. Cada regra le <b>uma</b>
 * opcao de parametro e tem teste por opcao - nenhum valor e presumido.
 */
public final class SpecialBillingRules {

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private SpecialBillingRules() {
    }

    /** Resultado da aplicacao da isencao sobre os componentes da conta. */
    public record ExemptionResult(BigDecimal waterValue, BigDecimal sewerValue, boolean exempt) {
    }

    /**
     * Isencao (tipo {@link SpecialSituationType#EXEMPTION} vigente na
     * competencia): agua e esgoto vao a zero. Fora da vigencia, ou com
     * outro tipo, os valores passam intactos.
     */
    public static ExemptionResult applyExemption(SpecialSituationRecord record, YearMonth competence,
                                                 BigDecimal waterValue, BigDecimal sewerValue) {
        requireAmount(waterValue);
        requireAmount(sewerValue);
        boolean exempt = record != null && record.type() == SpecialSituationType.EXEMPTION
                && record.appliesTo(competence);
        return exempt ? new ExemptionResult(ZERO, ZERO, true)
                : new ExemptionResult(waterValue, sewerValue, false);
    }

    /**
     * Compensacao por nao-conformidade de qualidade, pelo modo do parametro:
     * NONE devolve zero; CREDIT_PERCENT aplica sobre o total o percentual da
     * chave nomeada {@code quality.noncompliance_compensation_percent},
     * lida de {@link SpecialSituationParameters} (correcao pos-revisao,
     * D7: o percentual NAO vem mais "do chamador" sem fonte - e parametro
     * do tenant, com ausencia = {@code MissingParameterException});
     * FIXED_CREDIT devolve o valor fixo entregue pelo chamador (valor do
     * registro de nao-conformidade, nao percentual). Sem nao-conformidade
     * registrada, nao ha compensacao em nenhum modo.
     */
    public static BigDecimal qualityCompensation(SpecialSituationParameters parameters,
                                                  boolean noncomplianceRecorded,
                                                  BigDecimal billTotal,
                                                  BigDecimal fixedAmount) {
        requireAmount(billTotal);
        if (!noncomplianceRecorded) {
            return ZERO;
        }
        return switch (parameters.qualityCompensationMode()) {
            case NONE -> ZERO;
            case CREDIT_PERCENT -> {
                BigDecimal percent = parameters.qualityCompensationPercent();
                yield billTotal.multiply(percent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            }
            case FIXED_CREDIT -> {
                requireAmount(fixedAmount);
                yield fixedAmount;
            }
        };
    }

    /**
     * {@code bill.advance_billing}: com a opcao ligada, a conta pode ser
     * emitida antes do fim do periodo (faturamento antecipado); desligada,
     * a emissao so e admitida no/apos o fim do periodo.
     */
    public static boolean canIssue(SpecialSituationParameters parameters, LocalDate issueDate,
                                   LocalDate periodEnd) {
        if (issueDate == null || periodEnd == null) {
            throw new IllegalArgumentException("data de emissao e fim do periodo sao obrigatorios");
        }
        return parameters.advanceBilling() || !issueDate.isBefore(periodEnd);
    }

    /**
     * Conta final (encerramento da unidade): exige situacao/encerramento
     * registrado e aplica a isencao vigente sobre os componentes - a conta
     * final de unidade isenta na competencia do encerramento nasce sem
     * agua/esgoto. O proporcional por dias e o da P2.5
     * ({@code bill.partial_period_rule}); aqui entra so o que a ED-22
     * acrescenta: o portao do encerramento e a isencao.
     */
    public static ExemptionResult finalBill(SpecialSituationRecord exemptionOrNull, YearMonth competence,
                                            BigDecimal waterValue, BigDecimal sewerValue,
                                            boolean closureRecorded) {
        if (!closureRecorded) {
            throw new IllegalArgumentException("conta final exige encerramento registrado da unidade");
        }
        return applyExemption(exemptionOrNull, competence, waterValue, sewerValue);
    }

    private static void requireAmount(BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("valor invalido: " + amount);
        }
    }
}

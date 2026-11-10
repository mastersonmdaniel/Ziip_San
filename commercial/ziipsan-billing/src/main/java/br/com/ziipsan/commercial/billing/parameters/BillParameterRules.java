package br.com.ziipsan.commercial.billing.parameters;

import br.com.ziipsan.commercial.billing.bill.DueDateCalculator;
import br.com.ziipsan.commercial.billingrules.math.Money;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Aplicação das chaves da P2.5 sobre os fatos de uma conta (P2.5): cada método
 * é a leitura de <b>uma</b> chave de {@link BillingParameters} virando decisão
 * do motor. Nada aqui persiste nem orquestra o ciclo (F1); as chaves
 * {@code fee.*} e {@code credit.type_limits} são só <b>lidas</b> — o domínio
 * de débitos/créditos e de encargos cobrados é da F3 e não é redesenhado aqui.
 */
public final class BillParameterRules {

    /** Destino de uma conta depois de conferido o mínimo de emissão. */
    public enum IssueDecision {
        ISSUE,
        CARRY_OVER_TO_NEXT,
        HOLD_FOR_REVIEW
    }

    private BillParameterRules() {
    }

    /** {@code bill.hold_on_anomaly}: com a chave ligada, conta com anomalia é retida (não emitida). */
    public static boolean shouldHoldForAnomaly(BillingParameters parameters, boolean hasAnomaly) {
        return hasAnomaly && parameters.holdOnAnomaly();
    }

    /** {@code bill.auto_review_value_bands}: o total cai numa faixa marcada para revisão? */
    public static boolean requiresAutoReview(BillingParameters parameters, BigDecimal billTotal) {
        requireAmount(billTotal);
        for (BillingParameters.ValueBand band : parameters.autoReviewValueBands()) {
            if (band.review() && band.contains(billTotal)) {
                return true;
            }
        }
        return false;
    }

    /** {@code bill.min_issue_amount} + {@code bill.below_min_rule}: emitir, acumular ou reter a conta. */
    public static IssueDecision issueDecision(BillingParameters parameters, BigDecimal billTotal) {
        requireAmount(billTotal);
        if (billTotal.compareTo(parameters.minIssueAmount()) >= 0) {
            return IssueDecision.ISSUE;
        }
        return switch (parameters.belowMinRule()) {
            case ISSUE_ANYWAY -> IssueDecision.ISSUE;
            case CARRY_OVER_TO_NEXT -> IssueDecision.CARRY_OVER_TO_NEXT;
            case HOLD_FOR_REVIEW -> IssueDecision.HOLD_FOR_REVIEW;
        };
    }

    /** {@code bill.validity_months}: validade da conta, pela regra que o {@link DueDateCalculator} já tem. */
    public static LocalDate validUntil(BillingParameters parameters, LocalDate dueDate) {
        return DueDateCalculator.validUntil(dueDate, parameters.validityMonths());
    }

    /**
     * {@code bill.mail_extra_days}: dias extras que o {@link DueDateCalculator}
     * soma ao vencimento da rota quando a entrega é por correio; {@code null}
     * (sem acréscimo) quando a entrega não é por correio.
     */
    public static Integer mailExtraDays(BillingParameters parameters, boolean deliveryByMail) {
        return deliveryByMail ? parameters.mailExtraDays() : null;
    }

    /** {@code bill.delivery_address_fee}: taxa devida só quando a entrega é em endereço alternativo. */
    public static BigDecimal deliveryFee(BillingParameters parameters, boolean alternativeDeliveryAddress) {
        return alternativeDeliveryAddress ? parameters.deliveryAddressFee() : Money.ZERO;
    }

    /** {@code bill.inactive_economy_rule}: a regra configurada para economia inativa/unidade vaga. */
    public static BillingParameters.InactiveEconomyRule inactiveEconomyRule(BillingParameters parameters) {
        return parameters.inactiveEconomyRule();
    }

    /** {@code bill.carry_over_unpaid}: quanto do saldo não pago entra na conta seguinte. */
    public static BigDecimal carriedOverAmount(BillingParameters parameters, BigDecimal unpaidAmount) {
        requireAmount(unpaidAmount);
        return parameters.carryOverUnpaid() ? unpaidAmount : Money.ZERO;
    }

    /**
     * {@code sewer.no_water_volume_rule}: volume-base do esgoto quando não há
     * volume de água medido — fonte alternativa (poço), mínimo da ligação ou
     * sem cobrança de esgoto.
     */
    public static int sewerBaseVolumeWithoutWater(BillingParameters parameters, int alternativeSourceVolume,
                                                  int minimumVolume) {
        if (alternativeSourceVolume < 0 || minimumVolume < 0) {
            throw new IllegalArgumentException("Volumes não podem ser negativos");
        }
        return switch (parameters.noWaterVolumeRule()) {
            case ALTERNATIVE_SOURCE_VOLUME -> alternativeSourceVolume;
            case MINIMUM_VOLUME -> minimumVolume;
            case NO_SEWER_CHARGE -> 0;
        };
    }

    /**
     * {@code credit.type_limits} (só leitura): recusa crédito acima do teto do
     * seu tipo. Tipo sem teto configurado não tem limite por esta chave.
     */
    public static void checkCreditWithinLimits(BillingParameters parameters, String creditType,
                                               BigDecimal amount, int installments) {
        requireAmount(amount);
        BillingParameters.CreditTypeLimit limit = parameters.creditTypeLimits().get(creditType);
        if (limit == null) {
            return;
        }
        if (amount.compareTo(limit.maxAmount()) > 0) {
            throw new IllegalArgumentException("credit.type_limits: crédito " + creditType + " de " + amount
                    + " acima do teto " + limit.maxAmount());
        }
        if (installments > limit.maxInstallments()) {
            throw new IllegalArgumentException("credit.type_limits: crédito " + creditType + " em " + installments
                    + " parcelas acima do teto de " + limit.maxInstallments());
        }
    }

    /** {@code fee.charge_moment}: os encargos por atraso entram na conta seguinte? */
    public static boolean lateFeeChargedOnNextBill(BillingParameters parameters) {
        return parameters.chargeMoment() == BillingParameters.ChargeMoment.ON_NEXT_BILL;
    }

    /** {@code fee.exemptions}: a categoria está isenta de multa e juros? */
    public static boolean isFeeExempt(BillingParameters parameters, String categoryKey) {
        return categoryKey != null && parameters.feeExemptions().contains(categoryKey);
    }

    private static void requireAmount(BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("Valor inválido: " + amount);
        }
    }
}

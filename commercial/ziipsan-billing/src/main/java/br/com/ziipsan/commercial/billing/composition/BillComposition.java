package br.com.ziipsan.commercial.billing.composition;

import br.com.ziipsan.commercial.billing.bill.CreditLine;
import br.com.ziipsan.commercial.billing.bill.DebitLine;
import br.com.ziipsan.commercial.billing.bill.TaxLine;
import br.com.ziipsan.commercial.billing.math.Money;
import br.com.ziipsan.commercial.billing.tariff.SewerCharge;
import br.com.ziipsan.commercial.billing.tariff.TariffCharge;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Resultado da composicao do maestro (desenho da Camada 0, secao 2.1
 * item 10): a decomposicao proprio x terceiro e SEMPRE explicita.
 *
 * <p>Os componentes do desenho — {@code lines}, {@code ownSubtotal},
 * {@code thirdPartyTotal}, {@code creditLines}, {@code creditsValue},
 * {@code taxBase}, {@code taxLines}, {@code taxesValue}, {@code total}
 * — sao acompanhados dos componentes classicos que o
 * {@link BillCalculatorAdapter} precisa para projectar o
 * {@code BillResult} vigente campo a campo (INV-C2-01): o detalhe e os
 * valores de agua/esgoto, a parcela fixa e as linhas/valor de debitos.
 *
 * @param lines           linhas de cobranca na ordem normativa de
 *                        composicao (secao 2.3): agua, esgoto, fixa,
 *                        demais proprias por codigo e, por ultimo, as
 *                        de terceiro
 * @param water           detalhe da tarifa de agua do motor vigente;
 *                        {@code null} se nao houve linha progressiva
 * @param sewer           detalhe do esgoto do motor vigente;
 *                        {@code null} se nao houve linha derivada
 * @param waterValue      soma das linhas progressivas proprias
 * @param sewerValue      soma das linhas derivadas proprias
 * @param fixedPartValue  valor da parcela fixa classica (a linha
 *                        FIXED_PER_UNIT designada, ou o valor avulso
 *                        resolvido quando nao ha linha designada)
 * @param ownSubtotal     soma de TODAS as linhas proprias (agua +
 *                        esgoto + fixa + demais proprias)
 * @param thirdPartyTotal soma das linhas de terceiro (so entra no total)
 * @param debitLines      parcelas de debito (via {@code Installments} vigente)
 * @param debitsValue     soma dos debitos
 * @param creditLines     creditos aplicados (via {@code CreditApplier} vigente)
 * @param creditsValue    total de creditos aplicados (teto: proprias + debitos)
 * @param taxBase         base da retencao (proprias + debitos - creditos,
 *                        truncada pelo {@code TaxWithholding} vigente;
 *                        ZERO quando nao ha retencao — como no vigente)
 * @param taxLines        linhas de retencao (o ultimo absorve o residuo)
 * @param taxesValue      soma das retencoes
 * @param total           (proprias + debitos - creditos - impostos) +
 *                        terceiro — o parentesis e exactamente a formula
 *                        vigente do CHECK de {@code bills}
 */
public record BillComposition(List<BillChargeLine> lines, TariffCharge water, SewerCharge sewer,
                              BigDecimal waterValue, BigDecimal sewerValue,
                              BigDecimal fixedPartValue, BigDecimal ownSubtotal,
                              BigDecimal thirdPartyTotal,
                              List<DebitLine> debitLines, BigDecimal debitsValue,
                              List<CreditLine> creditLines, BigDecimal creditsValue,
                              BigDecimal taxBase, List<TaxLine> taxLines, BigDecimal taxesValue,
                              BigDecimal total) {

    public BillComposition {
        lines = lines == null ? List.of() : List.copyOf(lines);
        debitLines = debitLines == null ? List.of() : List.copyOf(debitLines);
        creditLines = creditLines == null ? List.of() : List.copyOf(creditLines);
        taxLines = taxLines == null ? List.of() : List.copyOf(taxLines);
        waterValue = requireMoney(waterValue, "waterValue");
        sewerValue = requireMoney(sewerValue, "sewerValue");
        fixedPartValue = fixedPartValue == null ? Money.ZERO : fixedPartValue;
        ownSubtotal = requireMoney(ownSubtotal, "ownSubtotal");
        thirdPartyTotal = requireMoney(thirdPartyTotal, "thirdPartyTotal");
        debitsValue = requireMoney(debitsValue, "debitsValue");
        creditsValue = requireMoney(creditsValue, "creditsValue");
        taxBase = requireMoney(taxBase, "taxBase");
        taxesValue = requireMoney(taxesValue, "taxesValue");
        total = requireMoney(total, "total");
    }

    private static BigDecimal requireMoney(BigDecimal value, String field) {
        return Objects.requireNonNull(value, field + " da composicao e obrigatorio");
    }

    /** Proprias + debitos, antes de creditos e impostos (espelho do {@code charges()} vigente). */
    public BigDecimal charges() {
        return ownSubtotal.add(debitsValue);
    }

    /** Linhas proprias, na ordem de composicao. */
    public List<BillChargeLine> ownLines() {
        return lines.stream().filter(l -> !l.isThirdParty()).toList();
    }

    /** Linhas de terceiro, na ordem de composicao (sempre no fim). */
    public List<BillChargeLine> thirdPartyLines() {
        return lines.stream().filter(BillChargeLine::isThirdParty).toList();
    }
}

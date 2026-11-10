package br.com.ziipsan.commercial.billing.composition;

import br.com.ziipsan.commercial.billing.bill.BillInput;
import br.com.ziipsan.commercial.billing.bill.BillResult;
import br.com.ziipsan.commercial.billingrules.catalog.CalculationModel;
import br.com.ziipsan.commercial.billingrules.composition.BillChargeLine;
import br.com.ziipsan.commercial.billingrules.composition.BillComposition;
import br.com.ziipsan.commercial.billingrules.math.Money;

import java.util.List;
import java.util.Objects;

/**
 * Ponte de compatibilidade entre o maestro e o contrato vigente
 * (desenho da Camada 0, secao 2.1 item 12), nos dois sentidos:
 *
 * <ol>
 *   <li>{@link #toContext}: {@code BillInput} classico -&gt; contexto de
 *       composicao (o chamador acrescenta as resolucoes do catalogo e
 *       as entradas por modelo via builder do contexto);</li>
 *   <li>{@link #toBillResult}: composicao -&gt; {@code BillResult}
 *       vigente, quando a composicao so tem linhas classicas
 *       (agua/esgoto/fixa/debitos) e NENHUM terceiro — e a peca que a
 *       prova diferencial exercita (INV-C2-01);</li>
 *   <li>{@link #fromBillResult}: {@code BillResult} vigente -&gt;
 *       composicao projectada (sem linhas de catalogo: o vigente nao
 *       tem codigos nem versoes de catalogo para dar as linhas; os
 *       valores classicos sao transportados campo a campo e
 *       {@code ownSubtotal} = agua + esgoto + fixa).</li>
 * </ol>
 *
 * O adapter nao calcula nada: transporta e valida fronteiras.
 */
public final class BillCalculatorAdapter {

    private BillCalculatorAdapter() {
    }

    /** Monta o contexto classico a partir do {@code BillInput} vigente. */
    public static ChargeCalculationContext.Builder toContext(BillInput input, String unitRef,
                                                             java.time.LocalDate competence) {
        Objects.requireNonNull(input, "BillInput e obrigatorio");
        return ChargeCalculationContext.builder()
                .unitRef(unitRef)
                .competence(competence)
                .schedule(input.schedule())
                .periodFrom(input.periodFrom())
                .periodTo(input.periodTo())
                .groups(input.groups())
                .waterConsumption(input.waterConsumption())
                .sewer(input.sewer())
                .debits(input.debits())
                .credits(input.credits())
                .taxPolicy(input.taxPolicy())
                .fixedPartValue(input.fixedPartValue());
    }

    /**
     * Projecta a composicao no {@code BillResult} vigente, campo a campo.
     *
     * @throws IllegalStateException se a composicao tiver terceiro ou
     *         linhas de modelos nao classicos (CATALOG_PRICE,
     *         PERCENT_OF_BASE, EXTERNAL_VALUE) — essas composicoes nao
     *         tem representacao no contrato vigente
     */
    public static BillResult toBillResult(BillComposition composition) {
        Objects.requireNonNull(composition, "composicao e obrigatoria");
        if (composition.thirdPartyTotal().signum() != 0 || !composition.thirdPartyLines().isEmpty()) {
            throw new IllegalStateException(
                    "composicao com linhas de terceiro nao projecta em BillResult classico");
        }
        for (BillChargeLine line : composition.lines()) {
            if (line.model() == CalculationModel.CATALOG_PRICE
                    || line.model() == CalculationModel.PERCENT_OF_BASE
                    || line.model() == CalculationModel.EXTERNAL_VALUE) {
                throw new IllegalStateException("composicao com linha nao classica "
                        + line.chargeCode() + " (" + line.model()
                        + ") nao projecta em BillResult classico");
            }
        }
        return new BillResult(composition.water(), composition.sewer(), composition.waterValue(),
                composition.sewerValue(), composition.fixedPartValue(), composition.debitLines(),
                composition.debitsValue(), composition.creditLines(), composition.creditsValue(),
                composition.taxBase(), composition.taxLines(), composition.taxesValue(),
                composition.total());
    }

    /** Projecta o {@code BillResult} vigente numa composicao (sem linhas de catalogo). */
    public static BillComposition fromBillResult(BillResult result) {
        Objects.requireNonNull(result, "BillResult e obrigatorio");
        return new BillComposition(List.of(), result.water(), result.sewer(), result.waterValue(),
                result.sewerValue(), result.fixedPartValue(),
                result.waterValue().add(result.sewerValue()).add(result.fixedPartValue()),
                Money.ZERO, result.debitLines(), result.debitsValue(), result.creditLines(),
                result.creditsValue(), result.taxBase(), result.taxLines(), result.taxesValue(),
                result.total());
    }
}

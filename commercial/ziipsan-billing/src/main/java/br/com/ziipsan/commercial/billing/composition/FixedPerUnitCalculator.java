package br.com.ziipsan.commercial.billing.composition;

import br.com.ziipsan.commercial.billingrules.catalog.CalculationModel;
import br.com.ziipsan.commercial.billingrules.catalog.ChargeResolution;
import br.com.ziipsan.commercial.billingrules.composition.BillChargeLine;
import br.com.ziipsan.commercial.billingrules.math.Money;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Cobranca fixa por economia/unidade (TFDI e taxas fixas).
 *
 * <p><b>Regra de fronteira (iv) / INV-C2-02:</b> quando a cobranca e a
 * parcela fixa classica declarada pelo chamador
 * ({@link ChargeCalculationContext#fixedPartChargeCode()}), a linha
 * TRANSPORTA o valor fixo ja resolvido pelo chamador — a resolucao
 * continua na {@code FixedPartCharge} vigente (Res. ARESC 105/2018) —
 * nem mais nem menos: nem o {@code fixedAmount} do catalogo nem
 * multiplicacao nenhuma entram nesse valor.
 *
 * <p>Para as demais cobrancas fixas do catalogo, o valor e o
 * {@code fixedAmount} da versao vezes o numero de unidades/economias
 * pre-resolvido no contexto ({@code fixedUnits} por codigo; ausencia
 * da entrada = 1 unidade, a unidade da propria definicao "por
 * unidade"), arredondado pelo regime vigente {@link Money#roundHalfUp}.
 */
public final class FixedPerUnitCalculator implements ChargeCalculator {

    @Override
    public CalculationModel model() {
        return CalculationModel.FIXED_PER_UNIT;
    }

    @Override
    public BillChargeLine calculate(ChargeCalculationContext context) {
        Objects.requireNonNull(context, "contexto de calculo e obrigatorio");
        ChargeResolution resolution = context.requireResolution(model());
        String code = resolution.definition().code();
        if (code.equals(context.fixedPartChargeCode())) {
            return BillChargeLine.fromResolution(resolution, context.fixedPartValue(),
                    context.fixedPartValue());
        }
        BigDecimal fixedAmount = resolution.version().parameters().fixedAmount();
        if (fixedAmount == null) {
            throw new IllegalArgumentException(
                    "cobranca fixa " + code + " sem fixedAmount na versao do catalogo");
        }
        int units = context.fixedUnits().getOrDefault(code, 1);
        if (units < 1) {
            throw new IllegalArgumentException(
                    "unidades da cobranca fixa " + code + " devem ser ao menos 1: " + units);
        }
        BigDecimal amount = Money.roundHalfUp(fixedAmount.multiply(BigDecimal.valueOf(units)));
        return BillChargeLine.fromResolution(resolution, amount, fixedAmount);
    }
}

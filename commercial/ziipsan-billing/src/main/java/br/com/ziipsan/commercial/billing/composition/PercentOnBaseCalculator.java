package br.com.ziipsan.commercial.billing.composition;

import br.com.ziipsan.commercial.billingrules.catalog.CalculationModel;
import br.com.ziipsan.commercial.billingrules.catalog.ChargeResolution;
import br.com.ziipsan.commercial.billingrules.composition.BillChargeLine;
import br.com.ziipsan.commercial.billingrules.math.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Cobranca percentual sobre uma base: {@code Money.percentOf(base, pct,
 * modo)} — uma unica divisao e um unico arredondamento, pelo utilitario
 * vigente {@code Money}.
 *
 * <p><b>Regra de fronteira (iii):</b> o {@link RoundingMode} e EXIGIDO
 * do contexto, declarado pelo chamador POR COBRANCA
 * ({@link ChargeCalculationContext#percentRoundingModes()} por codigo).
 * Modo ausente = excecao — nunca se aplica um modo implicito. (Se o
 * modo devesse viver na {@code ChargeVersion} da Camada 1, isso e
 * pendencia nomeada desta camada — ver RELATORIO-CAMADA-2, P-C2-01 —
 * e NAO se altera a Camada 1 aqui.)
 *
 * <p>A base vem de {@code percentBases} por codigo, pre-resolvida pelo
 * chamador; o percentual vem dos parametros da versao do catalogo.
 */
public final class PercentOnBaseCalculator implements ChargeCalculator {

    @Override
    public CalculationModel model() {
        return CalculationModel.PERCENT_OF_BASE;
    }

    @Override
    public BillChargeLine calculate(ChargeCalculationContext context) {
        Objects.requireNonNull(context, "contexto de calculo e obrigatorio");
        ChargeResolution resolution = context.requireResolution(model());
        String code = resolution.definition().code();
        BigDecimal percent = resolution.version().parameters().percent();
        if (percent == null) {
            throw new IllegalArgumentException(
                    "cobranca percentual " + code + " sem percent na versao do catalogo");
        }
        BigDecimal base = context.percentBases().get(code);
        if (base == null) {
            throw new IllegalArgumentException(
                    "cobranca percentual " + code + " sem base pre-resolvida no contexto");
        }
        RoundingMode mode = context.percentRoundingModes().get(code);
        if (mode == null) {
            throw new IllegalStateException("cobranca percentual " + code
                    + " sem modo de arredondamento declarado pelo chamador no contexto: "
                    + "modo ausente = excecao, nunca implicito (regra de fronteira (iii))");
        }
        BigDecimal amount = Money.percentOf(base, percent, mode);
        return BillChargeLine.fromResolution(resolution, amount, base);
    }
}

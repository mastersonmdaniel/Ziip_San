package br.com.ziipsan.commercial.billing.composition;

import br.com.ziipsan.commercial.billing.tariff.TariffEngine;
import br.com.ziipsan.commercial.billingrules.catalog.CalculationModel;
import br.com.ziipsan.commercial.billingrules.catalog.ChargeResolution;
import br.com.ziipsan.commercial.billingrules.composition.BillChargeLine;
import br.com.ziipsan.commercial.billingrules.tariff.TariffCharge;

import java.util.Objects;

/**
 * Cobranca progressiva por consumo (ex.: tarifa de agua).
 *
 * <p><b>INV-C2-09:</b> esta calculadora NAO tem aritmetica propria —
 * delega integralmente em {@link TariffEngine#calculate}, o motor
 * vigente provado da P2.5, e transporta o valor liquido e o detalhe
 * que ele devolve. A coerencia tarifa x cobranca (D-03) e verificada
 * antes da delegacao, sobre os escopos pre-resolvidos do contexto.
 * O schedule que o contexto carrega ja foi seleccionado por municipio
 * pelo chamador (seleccao previa D-03.3); {@code TariffEngine} e
 * {@code TariffSchedule} permanecem INTOCADOS.
 */
public final class ProgressiveConsumptionCalculator implements ChargeCalculator {

    @Override
    public CalculationModel model() {
        return CalculationModel.PROGRESSIVE_BY_CONSUMPTION;
    }

    @Override
    public BillChargeLine calculate(ChargeCalculationContext context) {
        Objects.requireNonNull(context, "contexto de calculo e obrigatorio");
        ChargeResolution resolution = context.requireResolution(model());
        context.validateTariffCoherence(resolution);
        if (context.waterConsumption() == null) {
            throw new IllegalArgumentException("cobranca progressiva "
                    + resolution.definition().code() + " sem consumo de agua no contexto");
        }
        TariffCharge charge = TariffEngine.calculate(context.schedule(), context.periodFrom(),
                context.periodTo(), context.groups(), context.waterConsumption());
        return BillChargeLine.fromResolution(resolution, charge.net(), charge);
    }
}

package br.com.ziipsan.commercial.billing.composition;

import br.com.ziipsan.commercial.billing.catalog.CalculationModel;
import br.com.ziipsan.commercial.billing.catalog.ChargeResolution;
import br.com.ziipsan.commercial.billing.tariff.SewerCharge;
import br.com.ziipsan.commercial.billing.tariff.TariffEngine;

import java.util.Objects;

/**
 * Cobranca derivada de volume/percentual (ex.: esgoto derivado da agua).
 *
 * <p><b>INV-C2-09:</b> esta calculadora NAO tem aritmetica propria —
 * delega integralmente em {@link TariffEngine#calculateSewer} e confere
 * o volume com {@link TariffEngine#sewerVolume}, os dois do motor
 * vigente provado da P2.5. A coerencia tarifa x cobranca (D-03) e
 * verificada antes da delegacao, sobre os escopos pre-resolvidos do
 * contexto; o schedule ja foi seleccionado por municipio pelo chamador
 * (seleccao previa D-03.3).
 */
public final class DerivedVolumePercentCalculator implements ChargeCalculator {

    @Override
    public CalculationModel model() {
        return CalculationModel.DERIVED_FROM_VOLUME;
    }

    @Override
    public BillChargeLine calculate(ChargeCalculationContext context) {
        Objects.requireNonNull(context, "contexto de calculo e obrigatorio");
        ChargeResolution resolution = context.requireResolution(model());
        context.validateTariffCoherence(resolution);
        if (context.sewer() == null) {
            throw new IllegalArgumentException("cobranca derivada "
                    + resolution.definition().code() + " sem esgoto no contexto");
        }
        int volume = TariffEngine.sewerVolume(context.sewer().baseVolume(),
                context.sewer().parameters().collectionPct());
        SewerCharge sewer = TariffEngine.calculateSewer(context.schedule(), context.periodFrom(),
                context.periodTo(), context.groups(), context.sewer().baseVolume(),
                context.sewer().parameters());
        if (sewer.volume() != volume) {
            throw new IllegalStateException("volume de esgoto divergente entre sewerVolume e "
                    + "calculateSewer para a cobranca " + resolution.definition().code());
        }
        return BillChargeLine.fromResolution(resolution, sewer.net(), sewer);
    }
}

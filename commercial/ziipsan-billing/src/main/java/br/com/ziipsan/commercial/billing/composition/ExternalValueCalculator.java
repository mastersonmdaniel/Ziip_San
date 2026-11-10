package br.com.ziipsan.commercial.billing.composition;

import br.com.ziipsan.commercial.billing.catalog.CalculationModel;
import br.com.ziipsan.commercial.billing.catalog.ChargeOwner;
import br.com.ziipsan.commercial.billing.catalog.ChargeResolution;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Cobranca de valor externo informado (convenio/cofaturamento).
 *
 * <p>Esta calculadora <b>NAO calcula</b>: transporta o valor resolvido
 * pela porta {@link ChargeCalculationContext.ExternalValueProvider} —
 * sem arredondamento, sem factor, sem ajuste (o valor e do parceiro;
 * a autarquia nao o versiona nem o reajusta). A implementacao da porta
 * sobre os contratos da ED-25 e da Camada 3; nesta camada vive so o
 * contrato e este transporte. Porta ausente ou valor ausente/negativo
 * = falha fechada.
 *
 * <p>A linha nasce {@link ChargeOwner#THIRD_PARTY} (desenho, secao 2.1
 * item 8), qualquer que seja o dono declarado — e por isso entra por
 * ultimo na composicao, fora do teto de creditos e da base de retencao
 * (INV-C2-03/04, tratados pelo {@link BillComposer}).
 */
public final class ExternalValueCalculator implements ChargeCalculator {

    @Override
    public CalculationModel model() {
        return CalculationModel.EXTERNAL_VALUE;
    }

    @Override
    public BillChargeLine calculate(ChargeCalculationContext context) {
        Objects.requireNonNull(context, "contexto de calculo e obrigatorio");
        ChargeResolution resolution = context.requireResolution(model());
        String code = resolution.definition().code();
        ChargeCalculationContext.ExternalValueProvider provider = context.externalValueProvider();
        if (provider == null) {
            throw new IllegalStateException("cobranca externa " + code
                    + " sem porta de valor externo no contexto (a implementacao e da Camada 3)");
        }
        BigDecimal value = provider.resolveValue(resolution, context);
        if (value == null) {
            throw new IllegalStateException(
                    "porta de valor externo devolveu null para a cobranca " + code);
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException(
                    "valor externo negativo para a cobranca " + code + ": " + value);
        }
        return BillChargeLine.fromResolution(resolution, ChargeOwner.THIRD_PARTY, value,
                resolution.version().externalContractId());
    }
}

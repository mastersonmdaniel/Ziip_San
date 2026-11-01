package br.com.ziipsan.operations.metering.rules;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Volume de esgoto faturado (relatório 02, 3.10), escrito do zero.
 *
 * <p>O que este cálculo produz é o <b>volume</b> (m³) de esgoto. O <i>valor</i> em reais do esgoto
 * (percentual da tarifa de água, com a política do fator de 2 casas) é do módulo de faturamento.
 * São dois percentuais distintos: o de <b>coleta</b> reduz o volume (aqui), o de esgoto
 * multiplica o valor (billing).
 *
 * <ol>
 *   <li>Sem poço medido: com ligação de água, o volume é o consumo faturado de água; sem ela, é o
 *       volume de referência informado por quem chama (mínimo da ligação ou não medido).</li>
 *   <li>Com poço medido: o volume é o consumo faturado do poço; se o tipo do poço não é de média
 *       ({@link ConsumptionType#isAverage()}), soma o consumo faturado de água.</li>
 *   <li>Volume mínimo de esgoto: se o volume ficou abaixo, sobe para o mínimo, com aviso.</li>
 *   <li>Consumo informado no poço sobrepõe o volume (tipo INFORMED).</li>
 *   <li>Percentual de coleta: volume &times; coleta / 100, <b>HALF_UP</b> para inteiro (27 &times; 80% =
 *       21,6 vira 22), exceto quando o tipo é de média.</li>
 * </ol>
 */
public final class SewageVolumeCalculator {

    private SewageVolumeCalculator() {
    }

    /**
     * @param water                 resultado da água da unidade; nulo se não há ligação de água
     * @param well                  resultado da medição do poço (a cascata aplicada ao poço); nulo se não há
     * @param referenceVolume       volume de referência quando não há água nem poço medido
     * @param referenceType         tipo do volume de referência (mínimo fixado ou não medido)
     * @param sewerMinimumVolume    volume mínimo de esgoto da ligação; nulo = não há
     * @param collectionPercent     percentual da água consumida que é coletado (0 a 100); obrigatório
     */
    public record Input(ConsumptionResult water, ConsumptionResult well, Integer referenceVolume,
                        ConsumptionType referenceType, Integer sewerMinimumVolume, BigDecimal collectionPercent) {
        public Input {
            MeteringInputException.require(collectionPercent != null && collectionPercent.signum() >= 0
                            && collectionPercent.compareTo(BigDecimal.valueOf(100)) <= 0,
                    MeteringErrorCode.INVALID_INPUT, "percentual de coleta deve estar entre 0 e 100");
            MeteringInputException.require(sewerMinimumVolume == null || sewerMinimumVolume >= 0,
                    MeteringErrorCode.INVALID_INPUT, "volume mínimo de esgoto negativo");
            MeteringInputException.require(referenceVolume == null || (referenceVolume >= 0 && referenceType != null),
                    MeteringErrorCode.INVALID_INPUT, "volume de referência negativo ou sem tipo");
        }
    }

    /**
     * @param volume   volume de esgoto faturado (m³)
     * @param type     tipo do volume
     * @param anomaly  {@link ConsumptionAnomalyKind#MINIMUM_FIXED} se o mínimo foi aplicado; senão nula
     * @param warnings avisos
     */
    public record Result(int volume, ConsumptionType type, ConsumptionAnomalyKind anomaly,
                         List<CalculationWarning> warnings) {
        public Result {
            warnings = List.copyOf(warnings);
        }
    }

    public static Result calculate(Input input) {
        MeteringInputException.notNull(input, "input");
        List<CalculationWarning> warnings = new ArrayList<>();
        ConsumptionAnomalyKind anomaly = null;

        long volume;
        ConsumptionType type;
        if (input.well() != null) {
            type = input.well().type();
            volume = input.well().billedConsumption();
            if (!type.isAverage() && input.water() != null) {
                volume += input.water().billedConsumption();
            }
        } else if (input.water() != null) {
            type = input.water().type();
            volume = input.water().billedConsumption();
        } else {
            MeteringInputException.require(input.referenceVolume() != null, MeteringErrorCode.SEWER_VOLUME_UNAVAILABLE,
                    "esgoto sem água nem poço medido exige o volume de referência");
            type = input.referenceType();
            volume = input.referenceVolume();
        }

        if (input.sewerMinimumVolume() != null && volume < input.sewerMinimumVolume()) {
            warnings.add(new CalculationWarning(WarningCode.SEWER_MINIMUM_APPLIED, "Volume de " + volume
                    + " m³ abaixo do mínimo de esgoto (" + input.sewerMinimumVolume() + " m³): mínimo aplicado"));
            volume = input.sewerMinimumVolume();
            anomaly = ConsumptionAnomalyKind.MINIMUM_FIXED;
        }

        if (input.well() != null && input.well().type() == ConsumptionType.INFORMED) {
            volume = input.well().billedConsumption();
            type = ConsumptionType.INFORMED;
        }

        if (!type.isAverage()) {
            volume = Rounding.halfUp(BigDecimal.valueOf(volume).multiply(input.collectionPercent())
                    .divide(BigDecimal.valueOf(100)));
        }
        return new Result(Math.toIntExact(volume), type, anomaly, warnings);
    }
}

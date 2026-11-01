package br.com.ziipsan.operations.metering.rules;

import java.time.YearMonth;

/**
 * Situação especial de faturamento vigente na unidade (relatório 02, 3.7): paralisa o faturamento
 * e/ou fixa o consumo. A vigência é por competência, de {@code start} até {@code end} inclusive.
 *
 * <p>Precedência do consumo: valor fixo (medido ou não medido, conforme a unidade) &gt; efeito.
 * Se a situação vale na referência e não traz nem um nem outro, o consumo calculado é mantido e a
 * cascata avisa.
 *
 * @param start                      primeira referência em que vale
 * @param end                        última referência em que vale; nula = ainda vigente (sem retirada)
 * @param pausesBilling              paralisa o faturamento: o consumo é calculado e gravado, mas não é faturado
 * @param fixedMeteredConsumption    consumo fixo para ligação com hidrômetro; nulo = não fixa
 * @param fixedNotMeteredConsumption consumo fixo para ligação sem hidrômetro; nulo = não fixa
 * @param effect                     efeito alternativo ao valor fixo, com a semântica "sem leitura"
 *                                   (média, mínimo ou não medido); nulo = nenhum
 */
public record SpecialSituation(
        YearMonth start,
        YearMonth end,
        boolean pausesBilling,
        Integer fixedMeteredConsumption,
        Integer fixedNotMeteredConsumption,
        ConsumptionEffect effect) {

    public SpecialSituation {
        MeteringInputException.notNull(start, "start");
        MeteringInputException.require(end == null || !end.isBefore(start), MeteringErrorCode.INVALID_INPUT,
                "end anterior a start");
        MeteringInputException.require(fixedMeteredConsumption == null || fixedMeteredConsumption >= 0,
                MeteringErrorCode.INVALID_INPUT, "consumo fixo negativo");
        MeteringInputException.require(fixedNotMeteredConsumption == null || fixedNotMeteredConsumption >= 0,
                MeteringErrorCode.INVALID_INPUT, "consumo fixo negativo");
        MeteringInputException.require(effect == null || effect.validWithoutReading(),
                MeteringErrorCode.EFFECT_NOT_APPLICABLE,
                "situação especial só aceita média, mínimo ou não medido; recebeu " + effect);
    }

    boolean appliesTo(YearMonth reference) {
        return !reference.isBefore(start) && (end == null || !reference.isAfter(end));
    }
}

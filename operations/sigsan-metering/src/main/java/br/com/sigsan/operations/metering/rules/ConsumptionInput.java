package br.com.sigsan.operations.metering.rules;

import java.time.YearMonth;
import java.util.List;

/**
 * Tudo que o cálculo do consumo de água de <b>uma unidade em um mês</b> precisa. Montado por quem
 * tem acesso a dados (o job por rota); a cascata em si não faz I/O.
 *
 * @param reference            referência de faturamento
 * @param economyGroups        economias por categoria (pelo menos uma)
 * @param meter                dados de leitura; <b>nulo = ligação sem hidrômetro</b>
 * @param connection           condição da ligação de água
 * @param history              histórico de consumo (apenas meses anteriores à referência)
 * @param fixedMinimumConsumption consumo mínimo fixado na ligação; nulo = não há
 * @param specialSituation     situação especial vigente; nula = nenhuma
 * @param nonMeteredFacts      dados cadastrais do cálculo de não medido
 */
public record ConsumptionInput(
        YearMonth reference,
        List<EconomyGroup> economyGroups,
        MeterReadingData meter,
        ConnectionCondition connection,
        List<HistoryEntry> history,
        Integer fixedMinimumConsumption,
        SpecialSituation specialSituation,
        NonMeteredFacts nonMeteredFacts) {

    public ConsumptionInput {
        MeteringInputException.notNull(reference, "reference");
        MeteringInputException.require(economyGroups != null && !economyGroups.isEmpty(),
                MeteringErrorCode.NO_ECONOMIES, "unidade sem economias");
        economyGroups = List.copyOf(economyGroups);
        MeteringInputException.notNull(connection, "connection");
        history = history == null ? List.of() : List.copyOf(history);
        MeteringInputException.require(fixedMinimumConsumption == null || fixedMinimumConsumption >= 0,
                MeteringErrorCode.INVALID_INPUT, "consumo mínimo fixado negativo");
        nonMeteredFacts = nonMeteredFacts == null ? NonMeteredFacts.NONE : nonMeteredFacts;
    }
}

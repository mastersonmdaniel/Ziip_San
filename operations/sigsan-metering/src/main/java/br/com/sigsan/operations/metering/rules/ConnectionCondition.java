package br.com.sigsan.operations.metering.rules;

import java.util.Set;

/**
 * Condição da ligação de água no mês, derivada da situação de ligação do cadastro
 * ({@code water_statuses}, módulo registry) e do que o prestador definiu para ela.
 *
 * @param suppliedWithWater  a ligação tem abastecimento (a "água com abastecimento" do hidrômetro parado)
 * @param cut                ligação cortada
 * @param hasWell            há poço ativo
 * @param seasonal           imóvel sazonal (nunca gera "hidrômetro parado")
 * @param sewerMinimumFixed  o esgoto tem consumo mínimo fixado
 * @param billableTypes      tipos de consumo que permitem faturar nesta situação de ligação; um tipo
 *                           fora do conjunto zera o faturado (o calculado é mantido para a média)
 */
public record ConnectionCondition(
        boolean suppliedWithWater,
        boolean cut,
        boolean hasWell,
        boolean seasonal,
        boolean sewerMinimumFixed,
        Set<ConsumptionType> billableTypes) {

    public ConnectionCondition {
        billableTypes = Set.copyOf(MeteringInputException.notNull(billableTypes, "billableTypes"));
    }
}

package br.com.sigsan.operations.metering.rules;

import java.math.BigDecimal;

/**
 * Ação do prestador diante de uma anormalidade de consumo (estouro, alto ou baixo) numa categoria,
 * com escalonamento por reincidência: o mês 1 vale para a primeira ocorrência, o 2 quando a
 * mesma anormalidade ocorreu no mês anterior, o 3 quando ocorreu nos dois meses anteriores.
 *
 * <p>Cada passo tem um efeito sobre o consumo (como as anormalidades de leitura com leitura) e um
 * fator opcional que multiplica o resultado, arredondado {@link java.math.RoundingMode#HALF_UP}.
 * Mensagem na conta, carta e ordem de serviço ficam fora desta regra pura (README).
 *
 * @param kind         {@link ConsumptionAnomalyKind#BURST}, {@link ConsumptionAnomalyKind#HIGH_CONSUMPTION}
 *                     ou {@link ConsumptionAnomalyKind#LOW_CONSUMPTION}
 * @param categoryCode categoria principal da unidade à qual a ação se aplica
 */
public record ConsumptionAnomalyAction(
        ConsumptionAnomalyKind kind,
        String categoryCode,
        Step month1,
        Step month2,
        Step month3) {

    /**
     * @param effect efeito sobre o consumo medido
     * @param factor multiplicador do resultado; nulo = sem fator
     */
    public record Step(ConsumptionEffect effect, BigDecimal factor) {
        public Step {
            MeteringInputException.notNull(effect, "effect");
            MeteringInputException.require(factor == null || factor.signum() >= 0, MeteringErrorCode.INVALID_INPUT,
                    "fator da ação negativo");
        }
    }

    public ConsumptionAnomalyAction {
        MeteringInputException.require(kind == ConsumptionAnomalyKind.BURST
                        || kind == ConsumptionAnomalyKind.HIGH_CONSUMPTION
                        || kind == ConsumptionAnomalyKind.LOW_CONSUMPTION,
                MeteringErrorCode.INVALID_INPUT, "ação só existe para estouro, alto ou baixo consumo");
        MeteringInputException.require(categoryCode != null && !categoryCode.isBlank(),
                MeteringErrorCode.INVALID_INPUT, "categoryCode é obrigatório");
        MeteringInputException.notNull(month1, "month1");
        MeteringInputException.notNull(month2, "month2");
        MeteringInputException.notNull(month3, "month3");
    }

    Step stepFor(int month) {
        return switch (month) {
            case 1 -> month1;
            case 2 -> month2;
            case 3 -> month3;
            default -> throw new IllegalArgumentException("mês de escalonamento inválido: " + month);
        };
    }
}

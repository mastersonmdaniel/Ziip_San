package br.com.ziipsan.operations.metering.rules;

import java.util.List;

/**
 * Consumo mínimo da ligação (UC0105 do legado, escrito do zero): soma, por categoria, do mínimo
 * por economia da tarifa vezes as economias (as equivalentes, se a categoria as define).
 *
 * <p>Sem arredondamento: tudo é inteiro. Ex.: RES 2 economias a 10 m³ e COM 1 economia a 10 m³
 * dão 20 + 10 = 30 m³.
 */
public final class ConnectionMinimum {

    private ConnectionMinimum() {
    }

    public static int of(List<EconomyGroup> groups) {
        MeteringInputException.require(groups != null && !groups.isEmpty(), MeteringErrorCode.NO_ECONOMIES,
                "unidade sem nenhuma categoria de economia");
        long total = 0;
        for (EconomyGroup group : groups) {
            total += (long) group.minimumConsumptionPerEconomy() * group.effectiveEconomies();
        }
        return Math.toIntExact(total);
    }

    /** Total de economias da unidade (reais, não equivalentes). */
    public static int totalEconomies(List<EconomyGroup> groups) {
        return groups.stream().mapToInt(EconomyGroup::economies).sum();
    }
}

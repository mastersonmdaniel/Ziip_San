package br.com.ziipsan.commercial.registry.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Economias atendidas por UMA ligação (ED-13; POC-10 do roteiro VISAN,
 * alínea c: ligações com economias mistas demonstráveis). Uma ligação
 * 1:1 com o imóvel atende várias unidades consumidoras (condomínio,
 * P2.23) e cada unidade tem as suas economias por categoria — a ligação
 * é MISTA quando as unidades atendidas somam mais de uma categoria.
 * Agregação pura sobre os fatos resolvidos pelo chamador.
 */
public final class ConnectionEconomies {

    private ConnectionEconomies() {
    }

    /**
     * Economias de uma unidade atendida: código de categoria DO
     * CATÁLOGO ({@code categories.code}, via subcategoria) ->
     * quantidade, derivadas de {@code unit_subcategories} vigentes —
     * a montagem a partir dos dados reais é
     * {@link ConnectionEconomiesService}.
     */
    public record UnitEconomies(java.util.UUID unitId, Map<String, Integer> economiesByCategory) {
        public UnitEconomies {
            economiesByCategory = economiesByCategory == null ? Map.of() : Map.copyOf(economiesByCategory);
            for (int count : economiesByCategory.values()) {
                if (count < 0) {
                    throw new IllegalArgumentException("Economias negativas na unidade " + unitId);
                }
            }
        }

        public int total() {
            return economiesByCategory.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    /** Agrega as economias de todas as unidades atendidas pela ligação, por categoria. */
    public static Map<String, Integer> aggregate(List<UnitEconomies> servedUnits) {
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (UnitEconomies unit : servedUnits == null ? List.<UnitEconomies>of() : servedUnits) {
            unit.economiesByCategory().forEach((category, count) ->
                    totals.merge(category, count, Integer::sum));
        }
        totals.values().removeIf(count -> count == 0);
        return totals;
    }

    /** Ligação com economias mistas: mais de uma categoria com economia nas unidades atendidas. */
    public static boolean isMixed(List<UnitEconomies> servedUnits) {
        return aggregate(servedUnits).size() > 1;
    }

    /** Total de economias da ligação (soma das unidades atendidas). */
    public static int totalEconomies(List<UnitEconomies> servedUnits) {
        return aggregate(servedUnits).values().stream().mapToInt(Integer::intValue).sum();
    }
}

package br.com.ziipsan.intelligence.gis.asset;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Manutencao por condicao e pecas sobressalentes criticas — OPE-38.
 *
 * <p>OS gerada por LEITURA: horimetro, corrente, vibracao ou
 * temperatura acima/abaixo do limiar do gatilho configurado no
 * parametro {@code maint.condition_triggers} (mapa por equipamento/
 * grandeza, do tenant). <b>Nenhum limiar de fabrica</b>: nenhuma
 * fonte fixa vibracao/temperatura/corrente de alarme para os
 * equipamentos do tenant — sem gatilho configurado, leitura nenhuma
 * gera OS.
 *
 * <p>Peca sobressalente critica por equipamento no parametro
 * {@code inv.critical_spares}: minimo e ponto de reposicao por
 * peca/equipamento — politica do tenant; a avaliacao acusa abaixo
 * do minimo e no ponto de reposicao, sem numeros de fabrica.
 */
public final class ConditionMaintenance {

    private ConditionMaintenance() {
    }

    public enum Measurement { RUNNING_HOURS, CURRENT, VIBRATION, TEMPERATURE }

    public enum Comparison { ABOVE, BELOW }

    /** Gatilho de condicao (uma linha de {@code maint.condition_triggers}). */
    public record ConditionTrigger(UUID assetId, Measurement measurement, Comparison comparison,
                                   double threshold, String unit) {
        public ConditionTrigger {
            Objects.requireNonNull(assetId, "gatilho sem equipamento");
            Objects.requireNonNull(measurement, "gatilho sem grandeza");
            Objects.requireNonNull(comparison, "gatilho sem comparacao");
            if (unit == null || unit.isBlank())
                throw new IllegalArgumentException("gatilho sem unidade da grandeza (nenhuma unidade presumida)");
        }

        public boolean fires(double reading) {
            return comparison == Comparison.ABOVE ? reading > threshold : reading < threshold;
        }
    }

    /** Leitura de condicao de um equipamento. */
    public record ConditionReading(UUID assetId, Measurement measurement, double value, String unit) {
        public ConditionReading {
            Objects.requireNonNull(assetId, "leitura sem equipamento");
            Objects.requireNonNull(measurement, "leitura sem grandeza");
            if (unit == null || unit.isBlank()) throw new IllegalArgumentException("leitura sem unidade");
        }
    }

    /** Gatilhos disparados por um conjunto de leituras (unidade divergente = erro, nunca conversao presumida). */
    public static List<ConditionTrigger> evaluate(List<ConditionTrigger> triggers, List<ConditionReading> readings) {
        Map<String, ConditionReading> byKey = new java.util.HashMap<>();
        for (ConditionReading r : readings) byKey.put(r.assetId() + ":" + r.measurement(), r);
        java.util.List<ConditionTrigger> fired = new java.util.ArrayList<>();
        for (ConditionTrigger t : triggers) {
            ConditionReading r = byKey.get(t.assetId() + ":" + t.measurement());
            if (r == null) continue;
            if (!r.unit().equals(t.unit()))
                throw new IllegalArgumentException("unidade da leitura (" + r.unit() + ") diverge do gatilho (" + t.unit()
                        + ") para " + t.measurement() + " — conversao nao presumida");
            if (t.fires(r.value())) fired.add(t);
        }
        return List.copyOf(fired);
    }

    /** Gera OS de verificacao por condicao para cada gatilho disparado (porta ED-36). */
    public static List<AssetPorts.CreatedOrder> generateConditionOrders(
            AssetPorts.WorkOrderPort orders, UUID tenantId, List<ConditionTrigger> fired,
            java.util.Map<UUID, String> regionByAsset, java.time.LocalDate referenceDate, UUID requestedBy) {
        Objects.requireNonNull(orders, "porta de OS da ED-36 e obrigatoria");
        return fired.stream().map(t -> orders.createOrder(new AssetPorts.OrderRequest(tenantId,
                AssetPorts.OrderPurpose.CONDITION_CHECK, "EQUIPMENT", t.assetId(),
                regionByAsset.get(t.assetId()), referenceDate.atStartOfDay(),
                "COND:" + t.measurement().name(), requestedBy))).toList();
    }

    /** Politica de peca critica de um equipamento (linha de {@code inv.critical_spares}). */
    public record CriticalSparePolicy(UUID equipmentAssetId, String partCode,
                                      int minimumStock, int reorderPoint) {
        public CriticalSparePolicy {
            Objects.requireNonNull(equipmentAssetId, "politica sem equipamento");
            if (partCode == null || partCode.isBlank()) throw new IllegalArgumentException("politica sem peca");
            if (minimumStock < 0) throw new IllegalArgumentException("minimo negativo");
            if (reorderPoint < minimumStock)
                throw new IllegalArgumentException("ponto de reposicao abaixo do minimo");
        }
    }

    public enum SpareStatus { OK, AT_REORDER_POINT, BELOW_MINIMUM }

    public static SpareStatus spareStatus(CriticalSparePolicy policy, int currentStock) {
        if (currentStock < 0) throw new IllegalArgumentException("estoque negativo");
        if (currentStock < policy.minimumStock()) return SpareStatus.BELOW_MINIMUM;
        if (currentStock <= policy.reorderPoint()) return SpareStatus.AT_REORDER_POINT;
        return SpareStatus.OK;
    }
}

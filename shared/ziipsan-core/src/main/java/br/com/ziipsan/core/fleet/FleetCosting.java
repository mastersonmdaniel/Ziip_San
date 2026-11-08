package br.com.ziipsan.core.fleet;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Custo por veiculo e custo de frota da OS (aceite da ED-59):
 * <ul>
 *   <li><b>custo por veiculo</b> no periodo = abastecimentos +
 *       manutencao (OS de veiculo da rotina ED-56, valores entregues
 *       pelo chamador a partir das OS) + multas do veiculo. Nenhum
 *       componente e presumido: quem nao tem registro soma zero
 *       PORQUE nao ha registro, nao por padrao;</li>
 *   <li><b>custo de frota da OS</b> = combustivel apropriado pelo
 *       consumo ({@link FuelAndConsumption#fuelCostForOrder}) — e o
 *       valor que preenche o campo de frota do
 *       {@code WorkOrderCost.total} da ED-36, que era nulo "enquanto
 *       a ED-59 nao existir". A mao de obra da equipe entra pela
 *       regra da ED-32/ED-36 ({@code cost.hourly_rates}); a equipe
 *       (composicao/escala) e o veiculo sao os desta tarefa.</li>
 * </ul>
 */
public final class FleetCosting {

    private FleetCosting() {
    }

    public record VehicleCostBreakdown(BigDecimal fuel, BigDecimal maintenance, BigDecimal fines,
                                       BigDecimal total) {
    }

    /** Custo conhecido de um veiculo a partir dos registros (sintetico nos testes, real na fiacao). */
    public static VehicleCostBreakdown costPerVehicle(List<FuelAndConsumption.FuelRecord> fuelRecords,
                                                      UUID vehicleAssetId, BigDecimal maintenanceCost,
                                                      List<DriverDocuments.TrafficFine> fines) {
        Objects.requireNonNull(fuelRecords, "registros de abastecimento sao obrigatorios");
        BigDecimal fuel = FuelAndConsumption.totalFuelCost(fuelRecords, vehicleAssetId);
        BigDecimal maint = maintenanceCost == null ? BigDecimal.ZERO : maintenanceCost;
        if (maint.signum() < 0) throw new IllegalArgumentException("custo de manutencao negativo");
        BigDecimal fineTotal = DriverDocuments.totalByVehicle(fines == null ? List.of() : fines, vehicleAssetId);
        BigDecimal total = fuel.add(maint).add(fineTotal).setScale(2, RoundingMode.HALF_UP);
        return new VehicleCostBreakdown(fuel.setScale(2, RoundingMode.HALF_UP),
                maint.setScale(2, RoundingMode.HALF_UP), fineTotal.setScale(2, RoundingMode.HALF_UP), total);
    }
}

package br.com.ziipsan.core.fleet;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Abastecimento, consumo medio por veiculo com alerta de desvio e
 * custo de combustivel apropriado a OS (aceite da ED-59, OPE-28).
 *
 * <p>Metodo do consumo medio (declarado): sobre os abastecimentos do
 * veiculo ordenados por odometro, media = (ultimo odometro − primeiro
 * odometro) / soma dos litros dos abastecimentos POSTERIORES ao
 * primeiro (o primeiro marca o tanque de partida). Com menos de dois
 * abastecimentos ou sem avanco de odometro, nao ha media (vazio) —
 * nunca numero presumido.
 *
 * <p>Desvio: o consumo do ULTIMO trecho (entre os dois ultimos
 * abastecimentos) e comparado a media dos trechos anteriores; o
 * desvio percentual so vira alerta com o limite do tenant
 * ({@code fleet.consumption_deviation_percent}). Sem limite, nenhum
 * alerta (declarado em {@link FleetParameters}). Desvio de consumo
 * PIOR (km/L menor) e o sinal do alerta; melhora nao alerta.
 *
 * <p>Apropriacao a OS: o custo do combustivel de uma OS e
 * (km rodados para a OS / consumo medio) x preco do litro — o preco
 * vem do abastecimento (dado registrado), nunca de tabela inventada.
 * Esse valor e o {@code fleetCostOrNull} do
 * {@code WorkOrderCost.total} da ED-36 (campo de frota que aguardava
 * a ED-59).
 */
public final class FuelAndConsumption {

    private FuelAndConsumption() {
    }

    /** Abastecimento registrado de um veiculo. */
    public record FuelRecord(UUID vehicleAssetId, long odometerKm, BigDecimal liters,
                             BigDecimal costPerLiter, LocalDateTime fueledAt, UUID workOrderId) {
        public FuelRecord {
            Objects.requireNonNull(vehicleAssetId, "abastecimento sem veiculo");
            if (odometerKm < 0) throw new IllegalArgumentException("odometro negativo: " + odometerKm);
            if (liters == null || liters.signum() <= 0)
                throw new IllegalArgumentException("abastecimento exige litros positivos");
            if (costPerLiter == null || costPerLiter.signum() < 0)
                throw new IllegalArgumentException("preco do litro nao pode ser nulo/negativo");
            Objects.requireNonNull(fueledAt, "abastecimento sem data/hora");
        }

        public BigDecimal totalCost() {
            return liters.multiply(costPerLiter).setScale(2, RoundingMode.HALF_UP);
        }
    }

    private static List<FuelRecord> sorted(List<FuelRecord> records, UUID vehicleAssetId) {
        return records.stream().filter(r -> r.vehicleAssetId().equals(vehicleAssetId))
                .sorted(Comparator.comparingLong(FuelRecord::odometerKm)).toList();
    }

    /** Consumo medio (km/L) do veiculo; vazio sem dados suficientes. */
    public static Optional<BigDecimal> averageKmPerLiter(List<FuelRecord> records, UUID vehicleAssetId) {
        List<FuelRecord> rs = sorted(records, vehicleAssetId);
        if (rs.size() < 2) return Optional.empty();
        long km = rs.get(rs.size() - 1).odometerKm() - rs.get(0).odometerKm();
        if (km <= 0) return Optional.empty();
        BigDecimal liters = rs.subList(1, rs.size()).stream().map(FuelRecord::liters)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (liters.signum() <= 0) return Optional.empty();
        return Optional.of(BigDecimal.valueOf(km).divide(liters, 4, RoundingMode.HALF_UP));
    }

    /** Alerta de desvio do ultimo trecho frente a media anterior. */
    public record DeviationAlert(UUID vehicleAssetId, BigDecimal baselineKmPerLiter,
                                 BigDecimal lastSegmentKmPerLiter, BigDecimal deviationPercent) {
    }

    public static Optional<DeviationAlert> deviationAlert(List<FuelRecord> records, UUID vehicleAssetId,
                                                          Optional<BigDecimal> deviationLimitPercent) {
        if (deviationLimitPercent == null || deviationLimitPercent.isEmpty()) return Optional.empty();
        List<FuelRecord> rs = sorted(records, vehicleAssetId);
        if (rs.size() < 3) return Optional.empty();
        Optional<BigDecimal> baseline = averageKmPerLiter(rs.subList(0, rs.size() - 1), vehicleAssetId);
        if (baseline.isEmpty()) return Optional.empty();
        FuelRecord prev = rs.get(rs.size() - 2);
        FuelRecord last = rs.get(rs.size() - 1);
        long km = last.odometerKm() - prev.odometerKm();
        if (km <= 0) return Optional.empty();
        BigDecimal segment = BigDecimal.valueOf(km).divide(last.liters(), 4, RoundingMode.HALF_UP);
        // desvio de PIORA: (media - trecho) / media * 100
        BigDecimal deviation = baseline.get().subtract(segment)
                .divide(baseline.get(), 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
        if (deviation.compareTo(deviationLimitPercent.get()) > 0)
            return Optional.of(new DeviationAlert(vehicleAssetId, baseline.get(), segment,
                    deviation.setScale(2, RoundingMode.HALF_UP)));
        return Optional.empty();
    }

    /** Custo total de abastecimento de um veiculo no conjunto de registros. */
    public static BigDecimal totalFuelCost(List<FuelRecord> records, UUID vehicleAssetId) {
        return records.stream().filter(r -> r.vehicleAssetId().equals(vehicleAssetId))
                .map(FuelRecord::totalCost).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Custo de combustivel apropriado a uma OS: km da OS / consumo
     * medio x preco do litro. Sem consumo medio conhecido, recusa —
     * a apropriacao nao inventa media.
     */
    public static BigDecimal fuelCostForOrder(long kmForOrder, BigDecimal averageKmPerLiter,
                                              BigDecimal costPerLiter) {
        if (kmForOrder < 0) throw new IllegalArgumentException("km da OS negativo: " + kmForOrder);
        Objects.requireNonNull(averageKmPerLiter, "consumo medio e obrigatorio para apropriar a OS");
        if (averageKmPerLiter.signum() <= 0) throw new IllegalArgumentException("consumo medio deve ser positivo");
        Objects.requireNonNull(costPerLiter, "preco do litro e obrigatorio");
        BigDecimal liters = BigDecimal.valueOf(kmForOrder).divide(averageKmPerLiter, 6, RoundingMode.HALF_UP);
        return liters.multiply(costPerLiter).setScale(2, RoundingMode.HALF_UP);
    }
}

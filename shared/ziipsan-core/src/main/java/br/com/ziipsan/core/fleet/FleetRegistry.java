package br.com.ziipsan.core.fleet;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Veiculo como ATIVO DE FROTA sobre o cadastro de ativos da P1.35
 * (aceite da ED-59): o veiculo NAO e uma segunda entidade patrimonial
 * — e o registro de frota do ativo ({@code assetId} da P1.35), com
 * placa, licenciamento e os equipamentos especiais montados. A
 * classe de alvo da OS e {@code FLEET_ASSET} (catalogo de alvos da
 * P1.31), e o verificador de existencia dessa classe e DESTA tarefa
 * (a spec da P1.31 atribui: "frota, ED-59").
 *
 * <p>Equipamentos especiais: EXATAMENTE os quatro nomeados no aceite
 * (hidrojato, vacuo, limpa-fossa, retroescavadeira) — nenhum outro
 * codigo e aceito sem fonte.
 */
public final class FleetRegistry {

    /** Equipamentos especiais nomeados no aceite da ED-59. */
    public enum SpecialEquipment { HIDROJATO, VACUO, LIMPA_FOSSA, RETROESCAVADEIRA }

    public enum VehicleStatus { ACTIVE, IN_MAINTENANCE, INACTIVE }

    /**
     * Registro de frota de um veiculo. {@code licensingValidUntil} e a
     * validade do licenciamento do veiculo (documento do veiculo);
     * a habilitacao e do condutor (ver {@link DriverDocuments}).
     */
    public record Vehicle(UUID assetId, String plate, LocalDate licensingValidUntil,
                          VehicleStatus status, Set<SpecialEquipment> specialEquipment,
                          long odometerKm) {
        public Vehicle {
            Objects.requireNonNull(assetId, "veiculo sem o ativo da P1.35");
            if (plate == null || plate.isBlank()) throw new IllegalArgumentException("veiculo sem placa");
            Objects.requireNonNull(licensingValidUntil, "veiculo sem validade do licenciamento");
            Objects.requireNonNull(status, "veiculo sem situacao");
            specialEquipment = specialEquipment == null ? Set.of() : Set.copyOf(specialEquipment);
            if (odometerKm < 0) throw new IllegalArgumentException("odometro negativo: " + odometerKm);
        }

        /** Licenciamento vencido NA data (validade anterior a data = vencido; no dia, vale). */
        public boolean licensingExpiredOn(LocalDate date) {
            return licensingValidUntil.isBefore(date);
        }

        public boolean hasAll(Set<SpecialEquipment> required) {
            return specialEquipment.containsAll(required);
        }
    }

    private final FleetPorts.AssetPort assetPort;
    private final Map<UUID, Map<UUID, Vehicle>> byTenant = new HashMap<>();

    public FleetRegistry(FleetPorts.AssetPort assetPort) {
        this.assetPort = Objects.requireNonNull(assetPort, "porta de ativos da P1.35 e obrigatoria");
    }

    /**
     * Cadastra o veiculo como ativo de frota: exige o ativo existente
     * na P1.35 (mesmo tenant) e recusa duplicidade de frota do mesmo
     * ativo. Nenhum ativo e criado aqui.
     */
    public Vehicle register(UUID tenantId, Vehicle vehicle) {
        Objects.requireNonNull(tenantId, "tenant e obrigatorio");
        Objects.requireNonNull(vehicle, "veiculo e obrigatorio");
        if (!assetPort.assetExists(tenantId, vehicle.assetId()))
            throw new IllegalArgumentException("ativo " + vehicle.assetId()
                    + " nao existe no cadastro da P1.35 para o tenant (veiculo e FLEET_ASSET sobre o ativo)");
        Map<UUID, Vehicle> fleet = byTenant.computeIfAbsent(tenantId, t -> new HashMap<>());
        if (fleet.containsKey(vehicle.assetId()))
            throw new IllegalArgumentException("ativo " + vehicle.assetId() + " ja cadastrado como veiculo de frota");
        fleet.put(vehicle.assetId(), vehicle);
        return vehicle;
    }

    public Optional<Vehicle> find(UUID tenantId, UUID assetId) {
        return Optional.ofNullable(byTenant.getOrDefault(tenantId, Map.of()).get(assetId));
    }

    /**
     * Verificador de existencia do alvo {@code FLEET_ASSET} da P1.31,
     * registrado por esta tarefa: o alvo so e declaravel se o veiculo
     * de frota existe no MESMO tenant (alvo de outro tenant = falso).
     */
    public boolean existsFleetTarget(UUID tenantId, UUID targetId) {
        return find(tenantId, targetId).isPresent();
    }

    /** Veiculos ATIVOS do tenant portando TODOS os equipamentos exigidos. */
    public java.util.List<Vehicle> vehiclesWith(UUID tenantId, Set<SpecialEquipment> required) {
        Set<SpecialEquipment> req = required == null ? Set.of() : required;
        return byTenant.getOrDefault(tenantId, Map.of()).values().stream()
                .filter(v -> v.status() == VehicleStatus.ACTIVE)
                .filter(v -> v.hasAll(req))
                .toList();
    }

    /** Conjunto vazio tipado para composicao de requisitos. */
    public static Set<SpecialEquipment> none() {
        return EnumSet.noneOf(SpecialEquipment.class);
    }
}

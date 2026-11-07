package br.com.ziipsan.operations.metering.hydrometry;

import br.com.ziipsan.operations.metering.domain.Meter;
import br.com.ziipsan.operations.metering.hydrometry.HydrometryEnums.MovementType;
import br.com.ziipsan.operations.metering.hydrometry.HydrometryEnums.RemovalDestination;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ciclo de vida do hidrômetro (ED-14): o estado muda SÓ por instalação, retirada ou
 * movimentação — cada uma referenciando a OS que a executou. Não há setter livre de
 * status no serviço: toda transição passa por {@link #requireTransition}.
 *
 * <ul>
 *   <li>local de armazenagem obrigatório em AVAILABLE e IN_MAINTENANCE;</li>
 *   <li>motivo de baixa obrigatório em WRITTEN_OFF;</li>
 *   <li>retirada tem destino AVAILABLE (volta ao estoque) ou SCRAP (sucata → baixa);</li>
 *   <li>STOLEN só por movimentação com OS (furto é fato registrado, não edição).</li>
 * </ul>
 */
public final class MeterLifecycleRule {
    private MeterLifecycleRule() {}

    private static final Map<Meter.Status, Set<MovementType>> ALLOWED = new EnumMap<>(Meter.Status.class);
    static {
        // de AVAILABLE: instala, vai à manutenção, baixa ou é furtado (via movimentação)
        ALLOWED.put(Meter.Status.AVAILABLE, EnumSet.of(MovementType.INSTALL, MovementType.MOVE_TO_MAINTENANCE,
                MovementType.WRITE_OFF, MovementType.STOLEN));
        ALLOWED.put(Meter.Status.INSTALLED, EnumSet.of(MovementType.REMOVE));
        ALLOWED.put(Meter.Status.IN_MAINTENANCE, EnumSet.of(MovementType.RETURN_FROM_MAINTENANCE,
                MovementType.WRITE_OFF, MovementType.STOLEN));
        ALLOWED.put(Meter.Status.STOLEN, EnumSet.of(MovementType.RETURN_FROM_MAINTENANCE, MovementType.WRITE_OFF));
        ALLOWED.put(Meter.Status.WRITTEN_OFF, EnumSet.noneOf(MovementType.class));
    }

    public static Meter.Status targetStatus(MovementType type, RemovalDestination destination) {
        return switch (type) {
            case RECEIPT -> Meter.Status.AVAILABLE;
            case INSTALL, REINSTALL -> Meter.Status.INSTALLED;
            case REMOVE -> {
                if (destination == null) throw new IllegalArgumentException("Destino da retirada é obrigatório (disponível ou sucata)");
                yield destination == RemovalDestination.SCRAP ? Meter.Status.WRITTEN_OFF : Meter.Status.AVAILABLE;
            }
            case MOVE_TO_MAINTENANCE -> Meter.Status.IN_MAINTENANCE;
            case RETURN_FROM_MAINTENANCE -> Meter.Status.AVAILABLE;
            case WRITE_OFF -> Meter.Status.WRITTEN_OFF;
            case STOLEN -> Meter.Status.STOLEN;
        };
    }

    /**
     * @param current estado atual (null = hidrômetro ainda não recebido)
     * @param storageLocationId local de armazenagem resultante (obrigatório p/ AVAILABLE/IN_MAINTENANCE)
     * @param writeOffReason motivo de baixa resultante (obrigatório p/ WRITTEN_OFF)
     */
    public static Meter.Status requireTransition(Meter.Status current, MovementType type, RemovalDestination destination,
                                                 UUID serviceOrderId, UUID storageLocationId, String writeOffReason) {
        if (type == null) throw new IllegalArgumentException("Tipo de movimentação é obrigatório");
        if (type != MovementType.RECEIPT && serviceOrderId == null)
            throw new IllegalArgumentException("Estado do hidrômetro muda só por instalação, retirada ou movimentação com OS referenciada");
        if (current == null) {
            if (type != MovementType.RECEIPT) throw new IllegalArgumentException("Primeiro movimento do hidrômetro é o recebimento de lote");
        } else if (!ALLOWED.getOrDefault(current, Set.of()).contains(type)
                && !(current == Meter.Status.AVAILABLE && type == MovementType.REINSTALL)) {
            // REINSTALL é um INSTALL sobre hidrômetro disponível com histórico — tratado como INSTALL.
            throw new IllegalArgumentException("Transição não permitida: " + current + " por " + type);
        }
        Meter.Status target = targetStatus(type == MovementType.REINSTALL ? MovementType.INSTALL : type, destination);
        if ((target == Meter.Status.AVAILABLE || target == Meter.Status.IN_MAINTENANCE) && storageLocationWhenNotInstalled(target, type) && storageLocationId == null)
            throw new IllegalArgumentException("Local de armazenagem é obrigatório para hidrômetro disponível ou em manutenção");
        if (target == Meter.Status.WRITTEN_OFF && (writeOffReason == null || writeOffReason.isBlank()))
            throw new IllegalArgumentException("Motivo de baixa é obrigatório na baixa do hidrômetro");
        return target;
    }

    private static boolean storageLocationWhenNotInstalled(Meter.Status target, MovementType type) {
        return type != MovementType.INSTALL && type != MovementType.REINSTALL;
    }
}

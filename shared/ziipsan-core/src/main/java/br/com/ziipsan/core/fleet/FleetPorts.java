package br.com.ziipsan.core.fleet;

import java.util.List;
import java.util.UUID;

/**
 * Portas da frota (ED-59) — mesmo padrao das dependencias (ED-56
 * {@code AssetPorts}, ED-36): o nucleo nao importa servicos de outros
 * modulos; a fiacao liga cada porta a dona real na integracao.
 * <ul>
 *   <li><b>P1.35</b>: {@link AssetPort} — o veiculo so existe SOBRE o
 *       cadastro de ativos: a existencia do ativo e conferida aqui e
 *       a tabela {@code asset} NAO e recriada por esta tarefa;</li>
 *   <li><b>P1.31</b>: o verificador de existencia do alvo
 *       {@code FLEET_ASSET} e registrado por ESTA tarefa (a spec da
 *       P1.31 atribui o verificador da frota a ED-59) — ver
 *       {@link FleetRegistry#existsFleetTarget};</li>
 *   <li><b>ED-36</b>: {@link WorkOrderPort} — unico caminho de OS da
 *       manutencao de veiculos (rotina da ED-56); a finalidade e
 *       mapeada ao tipo de servico do catalogo do tenant na fiacao.</li>
 * </ul>
 */
public final class FleetPorts {

    private FleetPorts() {
    }

    /** Existencia do ativo no cadastro da P1.35 (mesmo tenant). */
    public interface AssetPort {
        boolean assetExists(UUID tenantId, UUID assetId);
    }

    /** Finalidade de OS de veiculo (extensao da rotina da ED-56 a veiculos). */
    public enum VehicleOrderPurpose { VEHICLE_PREVENTIVE, VEHICLE_CORRECTIVE }

    public record VehicleOrderRequest(UUID tenantId, VehicleOrderPurpose purpose, UUID vehicleAssetId,
                                      String description) {
    }

    public record CreatedOrder(UUID orderId) {
    }

    /** Unico caminho de OS da manutencao de veiculos. */
    public interface WorkOrderPort {
        CreatedOrder createOrder(VehicleOrderRequest request);

        default List<CreatedOrder> createGrouped(List<VehicleOrderRequest> requests) {
            return requests.stream().map(this::createOrder).toList();
        }
    }
}

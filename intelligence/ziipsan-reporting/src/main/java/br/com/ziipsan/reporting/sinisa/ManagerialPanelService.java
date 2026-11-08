package br.com.ziipsan.reporting.sinisa;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Monta a secao gerencial do painel lendo a porta da ED-32. Nenhum calculo
 * aqui: o servico so transporta o valor da fonte e nomeia o que faltou.
 */
public final class ManagerialPanelService {

    private final ManagerialCostPort port;

    public ManagerialPanelService(ManagerialCostPort port) {
        this.port = port;
    }

    /**
     * @param serviceOrderCode codigo da OS cujo custo total entra na secao;
     *                         {@code null} = a secao nao pede custo de OS
     *                         (e o campo nao vira pendencia)
     */
    public ManagerialPanelSection read(UUID tenantId, LocalDate periodStart, LocalDate periodEnd,
                                       String serviceOrderCode) {
        List<String> pending = new ArrayList<>();
        var costM3 = port.costPerCubicMeter(tenantId, periodStart, periodEnd);
        if (costM3.isEmpty()) {
            pending.add("custo_por_m3");
        }
        var costConn = port.costPerConnection(tenantId, periodStart, periodEnd);
        if (costConn.isEmpty()) {
            pending.add("custo_por_ligacao");
        }
        List<ManagerialCostPort.CategoryMargin> margins =
                port.marginsByCategory(tenantId, periodStart, periodEnd);
        if (margins == null || margins.isEmpty()) {
            pending.add("margem_por_categoria");
            margins = List.of();
        }
        java.math.BigDecimal osCost = null;
        if (serviceOrderCode != null) {
            var os = port.serviceOrderTotalCost(tenantId, serviceOrderCode);
            if (os.isEmpty()) {
                pending.add("custo_total_os");
            } else {
                osCost = os.get();
            }
        }
        return new ManagerialPanelSection(costM3.orElse(null), costConn.orElse(null),
                margins, osCost, pending);
    }
}

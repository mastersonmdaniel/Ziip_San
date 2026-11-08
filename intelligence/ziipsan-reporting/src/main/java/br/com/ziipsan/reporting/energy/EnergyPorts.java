package br.com.ziipsan.reporting.energy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

/**
 * Portas da ED-90 — o nucleo nao importa servico de outro modulo; a
 * fiacao liga cada porta a dona real na integracao (mesmo padrao de
 * FleetPorts da ED-59 e ManagerialCostPort da ED-48):
 * <ul>
 *   <li>{@link EnergyBillFilePort}: o LEIAUTE de fatura de cada
 *       distribuidora. FRONTEIRA DURA: nenhum leiaute e implementado
 *       nesta entrega — o aceite manda conferir o leiaute de cada
 *       distribuidora NA IMPLANTACAO. A porta e o ponto onde a
 *       implementacao conferida entra; sem ela e sem o registro
 *       verificado ({@link DistributorLayout}), a importacao recusa;</li>
 *   <li>{@link TelemetryPort}: leitura automatica (telemetria) do
 *       consumo de uma instalacao — a fonte que substitui a entrada
 *       manual da ED-77 com grau HIGH;</li>
 *   <li>{@link FleetFuelPort}: combustivel da frota lido da ED-59
 *       quando ela existir (aceite GES-21). A ED-59 existe na main
 *       (camada C4): o adaptador sobre os registros reais dela esta em
 *       {@link AutomaticSourceUpgrade#fleetLitersFromEd59}; o ramo
 *       manual permanece para tenant/periodo sem dado da frota.</li>
 * </ul>
 */
public final class EnergyPorts {

    private EnergyPorts() {
    }

    /**
     * Parser da fatura de UMA distribuidora, implementado na
     * implantacao a partir do leiaute conferido. Recebe o conteudo do
     * arquivo e devolve a fatura ja no modelo da ED-90; a validacao do
     * modelo (faixas, versao de leiaute) e daqui, nao do parser.
     */
    public interface EnergyBillFilePort {
        EnergyBill parse(String distributorKey, String fileName, byte[] content,
                         EnergyInstallation installation);
    }

    /** Consumo (kWh) por telemetria de uma instalacao em um mes; vazio sem leitura. */
    public interface TelemetryPort {
        Optional<BigDecimal> kwh(UUID tenantId, UUID installationId, YearMonth month);
    }

    /** Litros de combustivel da frota no periodo, lidos da ED-59; vazio sem dado da frota. */
    public interface FleetFuelPort {
        Optional<BigDecimal> fuelLiters(UUID tenantId, LocalDate periodStart, LocalDate periodEnd);
    }
}

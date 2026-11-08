package br.com.ziipsan.intelligence.gis.asset;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import br.com.ziipsan.intelligence.gis.asset.AssetInventoryModel.AssetRecord;
import br.com.ziipsan.intelligence.gis.asset.AssetInventoryModel.HierarchyLevel;
import br.com.ziipsan.intelligence.gis.asset.AssetInventoryModel.LifecycleStatus;
import br.com.ziipsan.intelligence.gis.asset.AssetInventoryModel.TechnicalSheet;

/**
 * Massa SINTETICA declarada do inventario (nenhum dado real):
 * municipio MUN > sistema SIS > etapa ETA-AGUA > instalacao ETA-01
 * (espaco confinado) > bomba B-01 (critica) > selo (componente);
 * valvula V-01 e hidrante H-01 sob a etapa de distribuicao.
 */
final class AssetFixtures {

    static UUID id(int n) {
        return UUID.fromString(String.format("00000000-0000-0000-0000-%012d", n));
    }

    static final UUID MUN = id(1), SIS = id(2), ETAPA = id(3), ETA = id(4),
            BOMBA = id(5), SELO = id(6), VALVULA = id(7), HIDRANTE = id(8), MOTOR = id(9),
            REDE = id(10);

    static AssetRecord asset(UUID id, UUID parent, HierarchyLevel level, String code, String name) {
        return new AssetRecord(id, parent, level, code, name, null, null,
                McpsbCodes.empty(), TechnicalSheet.empty(), null, null,
                false, false, null, null, LocalDate.of(2020, 1, 1),
                LifecycleStatus.IN_OPERATION, null, "R1", null, false);
    }

    static AssetRecord bomba() {
        return new AssetRecord(BOMBA, ETA, HierarchyLevel.EQUIPAMENTO, "PAT-B01", "Bomba de recalque B-01",
                "PUMP", "PUMP-STD",
                new McpsbCodes(null, "4205407", null, null, "12", "1234567",
                        List.of("00", "00"), AssetInventoryModel.ControlMode.MASS, "un", null, null),
                new TechnicalSheet("Fabrica Sintetica", "Modelo S", "SN-1", 2019,
                        new BigDecimal("15"), "kW", new BigDecimal("120"), "m3/h"),
                null, new BigDecimal("45000.00"), false, true,
                LocalDate.of(2019, 6, 1), "Investimento do titular (sintetico)",
                LocalDate.of(2020, 1, 1), LifecycleStatus.IN_OPERATION, "CRITICAL", "R1", null, false);
    }

    static AssetInventoryModel.InventorySnapshot base() {
        AssetRecord eta = new AssetRecord(ETA, ETAPA, HierarchyLevel.INSTALACAO, "PAT-ETA01", "ETA Central",
                null, null, McpsbCodes.empty(), TechnicalSheet.empty(), null, null,
                true, true, LocalDate.of(2005, 3, 1), "Rede do titular (sintetico)",
                LocalDate.of(2005, 4, 1), LifecycleStatus.IN_OPERATION, null, "R1", id(500), true);
        AssetRecord valvula = new AssetRecord(VALVULA, REDE, HierarchyLevel.EQUIPAMENTO, "PAT-V01", "Valvula V-01",
                "VALVE", null, McpsbCodes.empty(), TechnicalSheet.empty(), null,
                new BigDecimal("3000"), false, false, null, null,
                LocalDate.of(2018, 5, 1), LifecycleStatus.IN_OPERATION, "CRITICAL", "R1", id(501), false);
        AssetRecord hidrante = new AssetRecord(HIDRANTE, REDE, HierarchyLevel.EQUIPAMENTO, "PAT-H01", "Hidrante H-01",
                "HYDRANT", null, McpsbCodes.empty(), TechnicalSheet.empty(), null, null,
                false, false, null, null, LocalDate.of(2018, 5, 1),
                LifecycleStatus.IN_OPERATION, "STANDARD", "R2", id(502), false);
        AssetRecord motor = new AssetRecord(MOTOR, ETA, HierarchyLevel.EQUIPAMENTO, "PAT-M01", "Motor M-01",
                "MOTOR", null, McpsbCodes.empty(), TechnicalSheet.empty(), null,
                new BigDecimal("12000"), false, false, null, null,
                LocalDate.of(2020, 1, 1), LifecycleStatus.IN_OPERATION, "STANDARD", "R1", null, false);
        return new AssetInventoryModel.InventorySnapshot(List.of(
                asset(MUN, null, HierarchyLevel.MUNICIPIO, "PAT-MUN", "Municipio sintetico"),
                asset(SIS, MUN, HierarchyLevel.SISTEMA, "PAT-SIS", "Sistema de agua"),
                asset(ETAPA, SIS, HierarchyLevel.ETAPA_SERVICO, "PAT-ETAPA", "Etapa tratamento"),
                eta, bomba(),
                asset(REDE, ETAPA, HierarchyLevel.INSTALACAO, "PAT-REDE", "Rede de distribuicao"),
                asset(SELO, BOMBA, HierarchyLevel.COMPONENTE, "PAT-SELO", "Selo mecanico"),
                valvula, hidrante, motor));
    }

    private AssetFixtures() {
    }
}

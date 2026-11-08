package br.com.ziipsan.intelligence.gis.asset;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import br.com.ziipsan.intelligence.gis.asset.AssetInventoryModel.AssetRecord;
import br.com.ziipsan.intelligence.gis.asset.AssetInventoryModel.HierarchyLevel;
import br.com.ziipsan.intelligence.gis.asset.AssetInventoryModel.LifecycleStatus;
import br.com.ziipsan.intelligence.gis.asset.AssetInventoryModel.TechnicalSheet;

import static br.com.ziipsan.intelligence.gis.asset.AssetFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class AssetInventoryModelTest {

    @Test
    void sixLevelHierarchyIsAcceptedAndDescendantsResolve() {
        var snap = base();
        assertEquals(10, snap.assets().size());
        // ETA -> bomba -> selo: descendentes transitivos da etapa
        var desc = snap.descendantsOf(ETAPA);
        assertTrue(desc.stream().anyMatch(a -> a.id().equals(BOMBA)));
        assertTrue(desc.stream().anyMatch(a -> a.id().equals(SELO)));
        assertEquals(2, snap.childrenOf(ETA).size()); // bomba + motor
    }

    @Test
    void levelIncoherentWithParentIsRefused() {
        // componente direto sob instalacao (pula equipamento)
        AssetRecord bad = asset(id(20), ETA, HierarchyLevel.COMPONENTE, "PAT-X", "Componente orfao de nivel");
        var list = new java.util.ArrayList<>(base().assets());
        list.add(bad);
        assertThrows(IllegalArgumentException.class,
                () -> new AssetInventoryModel.InventorySnapshot(list));
    }

    @Test
    void cycleInHierarchyIsRefused() {
        AssetRecord a = asset(id(30), id(31), HierarchyLevel.SISTEMA, "PAT-A", "A");
        AssetRecord b = new AssetRecord(id(31), id(30), HierarchyLevel.ETAPA_SERVICO, "PAT-B", "B",
                null, null, McpsbCodes.empty(), TechnicalSheet.empty(), null, null, false, false,
                null, null, LocalDate.of(2020, 1, 1), LifecycleStatus.IN_OPERATION, null, null, null, false);
        // A(2) sob B(3) ja e incoerente de nivel; o ciclo tambem deve ser acusado
        assertThrows(IllegalArgumentException.class,
                () -> new AssetInventoryModel.InventorySnapshot(List.of(
                        asset(MUN, null, HierarchyLevel.MUNICIPIO, "PAT-M", "M"), a, b)));
    }

    @Test
    void duplicatedPatrimonialCodeIsRefused() {
        var list = new java.util.ArrayList<>(base().assets());
        list.add(asset(id(40), REDE, HierarchyLevel.EQUIPAMENTO, "PAT-V01", "Clone da valvula"));
        assertThrows(IllegalArgumentException.class,
                () -> new AssetInventoryModel.InventorySnapshot(list));
    }

    @Test
    void reversibleAssetRequiresInvestmentOrigin() {
        assertThrows(IllegalArgumentException.class, () -> new AssetRecord(id(50), ETAPA,
                HierarchyLevel.EQUIPAMENTO, "PAT-R", "Reversivel sem origem", null, null,
                McpsbCodes.empty(), TechnicalSheet.empty(), null, null, false, true,
                LocalDate.of(2020, 1, 1), null, LocalDate.of(2020, 1, 1),
                LifecycleStatus.IN_OPERATION, null, null, null, false));
    }

    @Test
    void usefulLifeAbsentIsValidNeverInvented() {
        // bomba da massa nao tem vida util (sem perfil conferido): retrato valido com null
        assertNull(base().byId(BOMBA).orElseThrow().usefulLifeYears());
        assertEquals("CRITICAL", base().byId(BOMBA).orElseThrow().criticalityClass());
        assertTrue(base().byId(ETA).orElseThrow().confinedSpace());
    }

    @Test
    void technicalSheetRequiresUnitForPowerAndCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new TechnicalSheet(
                "F", "M", "S", 2020, new java.math.BigDecimal("10"), null, null, null));
    }
}

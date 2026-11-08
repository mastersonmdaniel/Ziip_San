package br.com.ziipsan.core.watersystems;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SystemRegistryServiceTest {

    private InMemorySystemsStore storeWithSystem() {
        InMemorySystemsStore store = new InMemorySystemsStore();
        SystemRegistryService.registerSystem(store, SystemsTestSupport.catalogPort(Map.of()), SystemsTestSupport.supplySystem("SAA-01"));
        return store;
    }

    private WaterSystem.SystemComponent component(String typeKey, UUID featureId) {
        return new WaterSystem.SystemComponent(UUID.randomUUID(), SystemsTestSupport.TENANT,
                "SAA-01", typeKey, featureId, "Componente de teste", true);
    }

    @Test void duplicateSystemCodeRejected() {
        InMemorySystemsStore store = storeWithSystem();
        SystemsExceptions.ValidationException ex = assertThrows(SystemsExceptions.ValidationException.class,
                () -> SystemRegistryService.registerSystem(store, SystemsTestSupport.catalogPort(Map.of()),
                        SystemsTestSupport.supplySystem("SAA-01")));
        assertEquals("SYSTEM_CODE_DUPLICATE", ex.code());
    }

    @Test void componentWithKnownActiveTypeRegisters() {
        InMemorySystemsStore store = storeWithSystem();
        var reg = SystemRegistryService.registerComponent(store,
                SystemsTestSupport.catalogPort(Map.of("TYPE_ETA", true)),
                (t, f) -> true, component("TYPE_ETA", null));
        assertTrue(reg.catalogTypeActive());
        assertEquals(1, store.components(SystemsTestSupport.TENANT, "SAA-01").size());
    }

    @Test void unknownCatalogTypeRejected() {
        InMemorySystemsStore store = storeWithSystem();
        SystemsExceptions.ValidationException ex = assertThrows(SystemsExceptions.ValidationException.class,
                () -> SystemRegistryService.registerComponent(store,
                        SystemsTestSupport.catalogPort(Map.of("TYPE_ETA", true)),
                        (t, f) -> true, component("TYPE_INEXISTENTE", null)));
        assertEquals("CATALOG_TYPE_UNKNOWN", ex.code());
        assertTrue(store.components(SystemsTestSupport.TENANT, "SAA-01").isEmpty());
    }

    @Test void deactivatedCatalogTypeStillRegistersFlagged() {
        // Regra da P1.27: item desativado continua valido nas feicoes existentes.
        InMemorySystemsStore store = storeWithSystem();
        var reg = SystemRegistryService.registerComponent(store,
                SystemsTestSupport.catalogPort(Map.of("TYPE_ANTIGO", false)),
                (t, f) -> true, component("TYPE_ANTIGO", null));
        assertFalse(reg.catalogTypeActive());
        assertEquals(1, store.components(SystemsTestSupport.TENANT, "SAA-01").size());
    }

    @Test void unknownGisFeatureRejected() {
        InMemorySystemsStore store = storeWithSystem();
        SystemsExceptions.ValidationException ex = assertThrows(SystemsExceptions.ValidationException.class,
                () -> SystemRegistryService.registerComponent(store,
                        SystemsTestSupport.catalogPort(Map.of("TYPE_ETA", true)),
                        (t, f) -> false, component("TYPE_ETA", UUID.randomUUID())));
        assertEquals("GIS_FEATURE_UNKNOWN", ex.code());
    }

    @Test void knownGisFeatureRegisters() {
        InMemorySystemsStore store = storeWithSystem();
        UUID feature = UUID.randomUUID();
        var reg = SystemRegistryService.registerComponent(store,
                SystemsTestSupport.catalogPort(Map.of("TYPE_ETA", true)),
                (t, f) -> f.equals(feature), component("TYPE_ETA", feature));
        assertEquals(feature, reg.component().gisFeatureId());
    }

    @Test void componentForMissingSystemRejected() {
        InMemorySystemsStore store = new InMemorySystemsStore();
        SystemsExceptions.NotFoundException ex = assertThrows(SystemsExceptions.NotFoundException.class,
                () -> SystemRegistryService.registerComponent(store,
                        SystemsTestSupport.catalogPort(Map.of("TYPE_ETA", true)),
                        (t, f) -> true, component("TYPE_ETA", null)));
        assertEquals("SYSTEM_NOT_FOUND", ex.code());
    }

    // Correcao D4: chaves de tratamento declaradas por tipo sao conferidas contra a P1.27.
    @Test void unknownTreatmentTypeKeyRejected() {
        InMemorySystemsStore store = new InMemorySystemsStore();
        WaterSystem sys = new WaterSystem(UUID.randomUUID(), SystemsTestSupport.TENANT, "SAA-T", "T",
                WaterSystem.Kind.SUPPLY, 100, java.time.LocalDate.of(2026, 1, 1),
                new WaterSystem.SourceInfo(WaterSystem.SourceKind.SURFACE, "Manancial", null),
                new WaterSystem.TreatmentInfo(List.of(), List.of("TIPO_QUE_NAO_EXISTE_NA_P1_27"), null), true);
        SystemsExceptions.ValidationException ex = assertThrows(SystemsExceptions.ValidationException.class,
                () -> SystemRegistryService.registerSystem(store,
                        SystemsTestSupport.catalogPort(Map.of("TYPE_ETA", true)), sys));
        assertEquals("TREATMENT_TYPE_UNKNOWN", ex.code());
        assertTrue(store.systems(SystemsTestSupport.TENANT).isEmpty());
    }

    @Test void knownTreatmentTypeKeyRegistersEvenIfDeactivated() {
        InMemorySystemsStore store = new InMemorySystemsStore();
        WaterSystem sys = new WaterSystem(UUID.randomUUID(), SystemsTestSupport.TENANT, "SAA-T", "T",
                WaterSystem.Kind.SUPPLY, 100, java.time.LocalDate.of(2026, 1, 1),
                new WaterSystem.SourceInfo(WaterSystem.SourceKind.SURFACE, "Manancial", null),
                new WaterSystem.TreatmentInfo(List.of(), List.of("TYPE_ANTIGO"), null), true);
        SystemRegistryService.registerSystem(store,
                SystemsTestSupport.catalogPort(Map.of("TYPE_ANTIGO", false)), sys);
        assertEquals(1, store.systems(SystemsTestSupport.TENANT).size());
    }
}

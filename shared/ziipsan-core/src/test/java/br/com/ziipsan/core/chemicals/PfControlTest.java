package br.com.ziipsan.core.chemicals;

import br.com.ziipsan.core.parameters.MissingParameterException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static br.com.ziipsan.core.chemicals.ChemTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class PfControlTest {
    static final Instant AT = Instant.parse("2026-03-02T12:00:00Z");
    static final LocalDate TODAY = LocalDate.of(2026, 3, 2);

    private ControlledCatalog.ListVersion listV1() {
        return new ControlledCatalog.ListVersion("2026-01", ControlledCatalog.NORM_SOURCE,
                LocalDate.of(2026, 1, 1), null,
                List.of(new ControlledCatalog.ListEntry("ACIDO", "Acido de teste")));
    }

    private static TestParams paramsPfOn() {
        var p = new TestParams(); p.pfEnabled = true; return p;
    }

    private InMemoryChemStore storeWithAcid() {
        var store = new InMemoryChemStore();
        store.putProduct(product(TENANT, "ACIDO", "Acido de teste"));
        store.putProduct(product(TENANT, "CLORO", "Cloro granulado"));
        return store;
    }

    @Test void catalogWithoutVersionInForceRefuses() {
        var old = new ControlledCatalog.ListVersion("2025-01", ControlledCatalog.NORM_SOURCE,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), List.of());
        assertThrows(ChemExceptions.ValidationException.class, () -> ControlledCatalog.currentOn(List.of(old), TODAY));
    }

    @Test void checkMatchesOnlyListedProducts() {
        var store = storeWithAcid();
        var check = PfControl.runPreEnableCheck(TENANT, store.products(TENANT), listV1(), AT);
        assertEquals(List.of("ACIDO"), check.matchedItemCodes());
        assertEquals("2026-01", check.listVersion());
    }

    @Test void enableWithoutCheckRefused() {
        var store = storeWithAcid();
        assertThrows(ChemExceptions.ValidationException.class, () ->
                PfControl.enable(store, paramsPfOn(), TENANT, "2026-01", TODAY, AT));
    }

    @Test void enableWithUnregisteredMatchRefusedThenSucceeds() {
        var store = storeWithAcid();
        store.savePfCheck(PfControl.runPreEnableCheck(TENANT, store.products(TENANT), listV1(), AT));
        assertThrows(ChemExceptions.ValidationException.class, () ->
                PfControl.enable(store, paramsPfOn(), TENANT, "2026-01", TODAY, AT));
        store.savePfRegistration(new PfControl.PfRegistration(TENANT, "ACIDO", "REG-PF-001", LocalDate.of(2026, 2, 1)));
        // ainda sem licenca vigente
        assertThrows(ChemExceptions.ValidationException.class, () ->
                PfControl.enable(store, paramsPfOn(), TENANT, "2026-01", TODAY, AT));
        store.savePfLicence(new PfControl.PfLicence(TENANT, "LIC-2026", LocalDate.of(2026, 1, 15), LocalDate.of(2027, 1, 14)));
        var state = PfControl.enable(store, paramsPfOn(), TENANT, "2026-01", TODAY, AT);
        assertTrue(state.enabled());
        assertTrue(PfControl.isControlled(store, TENANT, "ACIDO"));
        assertFalse(PfControl.isControlled(store, TENANT, "CLORO"));
    }

    @Test void staleCheckRefused() {
        var store = storeWithAcid();
        store.savePfCheck(new PfControl.PreEnableCheck(TENANT, "2025-01", LocalDate.of(2025, 6, 1), List.of(), AT));
        assertThrows(ChemExceptions.ValidationException.class, () ->
                PfControl.enable(store, paramsPfOn(), TENANT, "2026-01", TODAY, AT));
    }

    @Test void expiredLicenceBlocksControlledReceiptButNotCommonProduct() {
        var store = new InMemoryChemStore(); var stock = new RecordingStock();
        var svc = new ChemService(store, new TestParams(), stock);
        svc.registerProduct(product(TENANT, "ACIDO", "Acido de teste"), chemItem("ACIDO", "Acido de teste"), ACTOR, AT);
        svc.registerProduct(product(TENANT, "CLORO", "Cloro granulado"), chemItem("CLORO", "Cloro granulado"), ACTOR, AT);
        store.savePfCheck(PfControl.runPreEnableCheck(TENANT, store.products(TENANT), listV1(), AT));
        store.savePfRegistration(new PfControl.PfRegistration(TENANT, "ACIDO", "REG-PF-001", LocalDate.of(2026, 2, 1)));
        store.savePfLicence(new PfControl.PfLicence(TENANT, "LIC-2026", LocalDate.of(2026, 1, 15), LocalDate.of(2027, 1, 14)));
        PfControl.enable(store, paramsPfOn(), TENANT, "2026-01", TODAY, AT);
        // licenca "vencida" na data do recebimento futuro (2028): controlado bloqueia
        Instant future = Instant.parse("2028-02-01T12:00:00Z");
        var lotAcid = Map.of("ACIDO", new ChemReceipt.LotReceipt(TENANT, "ACIDO", "ALM-01", "LA1",
                LocalDate.of(2029, 1, 1), "LAUDO-A", new java.math.BigDecimal("10"), future));
        assertThrows(ChemExceptions.ValidationException.class, () ->
                svc.receiveFromNfe(TENANT, nfeXml("NFeA1", "12345678000199", "ACIDO", "10", "9.90"),
                        Map.of("ACIDO", new java.math.BigDecimal("10")), lotAcid, ACTOR, future));
        assertEquals(0, stock.entries.size());
        // produto comum recebe normalmente na mesma data
        var lotCloro = Map.of("CLORO", new ChemReceipt.LotReceipt(TENANT, "CLORO", "ALM-01", "LC1",
                LocalDate.of(2029, 1, 1), "LAUDO-C", new java.math.BigDecimal("10"), future));
        var ok = svc.receiveFromNfe(TENANT, nfeXml("NFeC1", "12345678000199", "CLORO", "10", "5.50"),
                Map.of("CLORO", new java.math.BigDecimal("10")), lotCloro, ACTOR, future);
        assertFalse(ok.duplicate());
    }

    /** Loja com conferencia, cadastro e licenca validos: so falta a chave (R1). */
    private InMemoryChemStore storeReadyToEnable() {
        var store = storeWithAcid();
        store.savePfCheck(PfControl.runPreEnableCheck(TENANT, store.products(TENANT), listV1(), AT));
        store.savePfRegistration(new PfControl.PfRegistration(TENANT, "ACIDO", "REG-PF-001", LocalDate.of(2026, 2, 1)));
        store.savePfLicence(new PfControl.PfLicence(TENANT, "LIC-2026", LocalDate.of(2026, 1, 15), LocalDate.of(2027, 1, 14)));
        return store;
    }

    @Test void enableWithoutPfKeyRefusedByName() {
        // R1: tudo o mais valido, mas chem.pf_control_enabled AUSENTE
        var store = storeReadyToEnable();
        var e = assertThrows(MissingParameterException.class, () ->
                PfControl.enable(store, new TestParams(), TENANT, "2026-01", TODAY, AT));
        assertEquals(ChemParameters.PF_CONTROL_ENABLED, e.definitionKey());
        assertTrue(store.pfState(TENANT).isEmpty() || !store.pfState(TENANT).orElseThrow().enabled());
    }

    @Test void enableWithPfKeyFalseRefusedByName() {
        // R1: chave presente mas FALSE tambem recusa, com codigo nomeado
        var store = storeReadyToEnable();
        var params = new TestParams(); params.pfEnabled = false;
        var e = assertThrows(ChemExceptions.ValidationException.class, () ->
                PfControl.enable(store, params, TENANT, "2026-01", TODAY, AT));
        assertEquals("PF_CONTROL_DISABLED", e.code());
        assertTrue(store.pfState(TENANT).isEmpty() || !store.pfState(TENANT).orElseThrow().enabled());
    }

    @Test void isControlledStableAfterNewerCheckGateDoesNotOpen() {
        // R2: habilitar na lista 2026-01; conferencia NOVA (2027-01)
        // gravada depois NAO pode zerar o portao (fail-open).
        var store = storeReadyToEnable();
        var state = PfControl.enable(store, paramsPfOn(), TENANT, "2026-01", TODAY, AT);
        assertTrue(state.enabled());
        assertEquals(List.of("ACIDO"), state.enabledMatchedItemCodes());
        assertTrue(PfControl.isControlled(store, TENANT, "ACIDO"));
        assertFalse(PfControl.hasPendingNewerCheck(store, TENANT));
        // pre-checagem contra versao mais nova, ainda nao habilitada
        store.savePfCheck(new PfControl.PreEnableCheck(TENANT, "2027-01", LocalDate.of(2026, 6, 1), List.of(), AT));
        // portao INALTERADO: segue pela conferencia da versao habilitada
        assertTrue(PfControl.isControlled(store, TENANT, "ACIDO"));
        assertFalse(PfControl.isControlled(store, TENANT, "CLORO"));
        // e a pendencia e EXPLICITA, nunca silenciosa
        assertTrue(PfControl.hasPendingNewerCheck(store, TENANT));
        assertEquals("2026-01", PfControl.enabledCheck(store, TENANT).orElseThrow().listVersion());
        // com a licenca vencida na data, o recebimento do controlado
        // continua BLOQUEADO (o portao nao abriu)
        var e = assertThrows(ChemExceptions.ValidationException.class, () ->
                PfControl.requireReceiptAllowed(store, TENANT, "ACIDO", LocalDate.of(2028, 2, 1)));
        assertEquals("PF_LICENCE_EXPIRED", e.code());
    }

    @Test void licenceRenewalRestoresValidity() {
        var store = storeWithAcid();
        store.savePfLicence(new PfControl.PfLicence(TENANT, "LIC-2025", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)));
        assertTrue(PfControl.licenceValidOn(store, TENANT, TODAY).isEmpty());
        store.savePfLicence(new PfControl.PfLicence(TENANT, "LIC-2026", LocalDate.of(2026, 1, 15), LocalDate.of(2027, 1, 14)));
        assertEquals("LIC-2026", PfControl.licenceValidOn(store, TENANT, TODAY).orElseThrow().licenceRef());
    }

    @Test void periodicReportOverdueOnlyWhenDeclaredDuePassed() {
        var store = storeWithAcid();
        store.savePeriodicReport(new PfControl.PeriodicReport(TENANT, "2026-Q1", LocalDate.of(2026, 4, 30), null, null));
        assertEquals(0, PfControl.overdueReports(store, TENANT, LocalDate.of(2026, 4, 30)).size());
        assertEquals(1, PfControl.overdueReports(store, TENANT, LocalDate.of(2026, 5, 1)).size());
        store.savePeriodicReport(new PfControl.PeriodicReport(TENANT, "2026-Q1", LocalDate.of(2026, 4, 30),
                LocalDate.of(2026, 4, 28), "PROT-123"));
        assertEquals(0, PfControl.overdueReports(store, TENANT, LocalDate.of(2026, 5, 1)).size());
        assertThrows(IllegalArgumentException.class, () ->
                new PfControl.PeriodicReport(TENANT, "X", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), null));
    }
}

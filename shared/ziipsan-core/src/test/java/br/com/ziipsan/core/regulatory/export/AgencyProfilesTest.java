package br.com.ziipsan.core.regulatory.export;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;

/** Teste por agencia com massa sintetica e comparacao com o leiaute (aceite ED-97). */
class AgencyProfilesTest {
    static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final Instant NOW = Instant.parse("2026-11-09T00:00:00Z");

    @Test void factoryProfilesExistForArisAgirAresc() {
        assertEquals(3, FactoryAgencyProfiles.factoryProfiles().size());
        assertTrue(FactoryAgencyProfiles.factoryFor("ARIS").isPresent());
        assertTrue(FactoryAgencyProfiles.factoryFor("AGIR").isPresent());
        assertTrue(FactoryAgencyProfiles.factoryFor("ARESC").isPresent());
    }
    @Test void arisProfileIsSisarisMonthlyAssistedWithDeclaredActs() {
        var p = FactoryAgencyProfiles.arisSisaris();
        assertEquals("SISARIS", p.layoutId());
        assertEquals(AgencyExportProfile.Periodicity.MONTHLY, p.periodicity());
        assertEquals(AgencyExportProfile.Channel.ASSISTED_FILE, p.channel());
        assertTrue(p.sourceAct().contains("IN DG 1/2025"));
        assertTrue(p.sourceAct().contains("RN 11/2017"));
        assertFalse(p.layoutComplete()); // leiaute oficial nao localizado: declarado, nao fingido
        assertTrue(p.hasSemFonteFields());
    }
    @Test void agirProfileIsQuarterlySacOuvidoria() {
        var p = FactoryAgencyProfiles.agirSacOuvidoria();
        assertEquals(AgencyExportProfile.Periodicity.QUARTERLY, p.periodicity());
        assertTrue(p.sourceAct().contains("RN 013/2022"));
    }
    @Test void nonScAgencyHasNoFactoryProfile() {
        assertTrue(FactoryAgencyProfiles.factoryFor("ADASA").isEmpty());
        assertTrue(FactoryAgencyProfiles.factoryFor("ARSESP").isEmpty());
    }
    @Test void tenantDefinedProfileRequiresAcceptance() {
        assertThrows(IllegalArgumentException.class, () -> FactoryAgencyProfiles.tenantDefined(
            "ADASA", "LAY", "1", AgencyExportProfile.Periodicity.MONTHLY,
            List.of(LayoutField.confirmed("periodo", true)), false));
        var p = FactoryAgencyProfiles.tenantDefined("ADASA", "LAY", "1",
            AgencyExportProfile.Periodicity.MONTHLY,
            List.of(LayoutField.confirmed("periodo", true)), true);
        assertFalse(p.factory());
        assertTrue(p.layoutComplete());
    }
    @Test void arisSyntheticPackageValidatesWithTenantAcceptanceAndRegistersHash() {
        var svc = new ExportSubmissionService();
        var pkg = ExportPackage.build(FactoryAgencyProfiles.arisSisaris(), "2026-10",
            ExportPackage.Kind.AGGREGATE_PUBLIC,
            List.of("categoria=RESIDENCIAL;faixa=0-10;consumo_medio=8.5", "categoria=COMERCIAL;faixa=10-20;consumo_medio=15.0"));
        var rec = svc.validateAndRegister(TENANT, pkg, true, NOW);
        assertEquals(pkg.sha256Hex(), rec.packageSha256Hex());
        assertEquals(SubmissionRecord.Status.GENERATED_VALIDATED, rec.status());
        assertTrue(rec.evidenceRef().startsWith("ED97-EVIDENCE:ARIS:2026-10:"));
        assertEquals(List.of(rec.evidenceRef()), svc.evidenceRefsFor("2026-10"));
    }
    @Test void agirSyntheticPackageWithoutTenantAcceptanceIsRefused() {
        var svc = new ExportSubmissionService();
        var pkg = ExportPackage.build(FactoryAgencyProfiles.agirSacOuvidoria(), "2026-Q3",
            ExportPackage.Kind.AGGREGATE_PUBLIC, List.of("sac_total=10;ouvidoria_total=2"));
        assertThrows(IllegalArgumentException.class, () -> svc.validateAndRegister(TENANT, pkg, false, NOW));
        assertTrue(svc.records().isEmpty());
    }
    @Test void arescSyntheticPackageRegisters() {
        var svc = new ExportSubmissionService();
        var pkg = ExportPackage.build(FactoryAgencyProfiles.arescGeneric(), "2026-10",
            ExportPackage.Kind.AGGREGATE_PUBLIC, List.of("indicador=ITS;valor=0.1200"));
        assertNotNull(svc.validateAndRegister(TENANT, pkg, true, NOW));
    }
    @Test void completeLayoutMissingRequiredFieldIsRefused() {
        var p = FactoryAgencyProfiles.tenantDefined("ADASA", "LAY", "1",
            AgencyExportProfile.Periodicity.MONTHLY,
            List.of(LayoutField.confirmed("periodo", true), LayoutField.confirmed("municipio", true)), true);
        var pkg = ExportPackage.build(p, "2026-10", ExportPackage.Kind.AGGREGATE_PUBLIC,
            List.of("periodo=2026-10"));
        var svc = new ExportSubmissionService();
        var ex = assertThrows(IllegalArgumentException.class, () -> svc.validateAndRegister(TENANT, pkg, true, NOW));
        assertTrue(ex.getMessage().contains("municipio"));
    }
    @Test void emptyPackageRefused() {
        var svc = new ExportSubmissionService();
        var pkg = ExportPackage.build(FactoryAgencyProfiles.arisSisaris(), "2026-10",
            ExportPackage.Kind.AGGREGATE_PUBLIC, List.of());
        assertThrows(IllegalArgumentException.class, () -> svc.validateAndRegister(TENANT, pkg, true, NOW));
    }
    @Test void submittedStatusRequiresAgencyProtocol() {
        var svc = new ExportSubmissionService();
        var pkg = ExportPackage.build(FactoryAgencyProfiles.arisSisaris(), "2026-10",
            ExportPackage.Kind.AGGREGATE_PUBLIC, List.of("categoria=RESIDENCIAL;consumo_medio=8.5"));
        var rec = svc.validateAndRegister(TENANT, pkg, true, NOW);
        assertThrows(IllegalArgumentException.class, () -> rec.markSubmittedByTenant(" ", NOW));
        var done = rec.markSubmittedByTenant("SYN-PROTOCOLO-1", NOW);
        assertEquals(SubmissionRecord.Status.SUBMITTED_BY_TENANT, done.status());
    }
}

package br.com.ziipsan.core.regulatory.export;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import br.com.ziipsan.core.certification.external.ExternalProfile;
import br.com.ziipsan.core.certification.external.ExternalResource;
import br.com.ziipsan.core.certification.external.ExternalScope;

class SeparationAndScopeTest {
    @Test void nominalLineNeverEntersAggregatePublicPackage() {
        assertThrows(IllegalArgumentException.class, () -> ExportPackage.build(
            FactoryAgencyProfiles.arisSisaris(), "2026-10", ExportPackage.Kind.AGGREGATE_PUBLIC,
            List.of("categoria=RESIDENCIAL;consumo_medio=8.5", "NOMINAL:cpf=000;nome=SYN")));
    }
    @Test void nominalRestrictedPackageRequiresNominalLine() {
        assertThrows(IllegalArgumentException.class, () -> ExportPackage.build(
            FactoryAgencyProfiles.arisSisaris(), "2026-10", ExportPackage.Kind.NOMINAL_RESTRICTED,
            List.of("categoria=RESIDENCIAL")));
        var pkg = ExportPackage.build(FactoryAgencyProfiles.arisSisaris(), "2026-10",
            ExportPackage.Kind.NOMINAL_RESTRICTED, List.of("NOMINAL:economia=SYN-E1"));
        assertFalse(pkg.isPublic());
        assertEquals(64, pkg.sha256Hex().length());
    }
    @Test void hashIsDeterministicAndContentSensitive() {
        var a = ExportPackage.build(FactoryAgencyProfiles.agirSacOuvidoria(), "2026-Q3",
            ExportPackage.Kind.AGGREGATE_PUBLIC, List.of("sac_total=10"));
        var b = ExportPackage.build(FactoryAgencyProfiles.agirSacOuvidoria(), "2026-Q3",
            ExportPackage.Kind.AGGREGATE_PUBLIC, List.of("sac_total=10"));
        var c = ExportPackage.build(FactoryAgencyProfiles.agirSacOuvidoria(), "2026-Q3",
            ExportPackage.Kind.AGGREGATE_PUBLIC, List.of("sac_total=11"));
        assertEquals(a.sha256Hex(), b.sha256Hex());
        assertNotEquals(a.sha256Hex(), c.sha256Hex());
    }
    @Test void regulatorScopeHasExactlyTheFiveItemsOfTheAcceptance() {
        assertEquals(5, RegulatorExternalScope.items().size());
        assertTrue(RegulatorExternalScope.items().contains(RegulatorExternalScope.ScopeItem.ED97_REGULATORY_PACKAGE));
        assertTrue(RegulatorExternalScope.items().contains(RegulatorExternalScope.ScopeItem.ED48_INDICATORS));
        assertTrue(RegulatorExternalScope.items().contains(RegulatorExternalScope.ScopeItem.NR11_ART54_REPORT));
        assertTrue(RegulatorExternalScope.items().contains(RegulatorExternalScope.ScopeItem.OUVIDORIA));
        assertTrue(RegulatorExternalScope.items().contains(RegulatorExternalScope.ScopeItem.INTERRUPTIONS));
    }
    @Test void regulatorScopeExcludesIsoEvidenceAndPersonalData() {
        assertTrue(RegulatorExternalScope.isCoherentWithEd75Matrix());
        assertFalse(ExternalScope.canRead(ExternalProfile.REGULATOR_SERVICE_OWNER, ExternalResource.EVIDENCE_PACK));
        assertFalse(ExternalScope.canRead(ExternalProfile.REGULATOR_SERVICE_OWNER, ExternalResource.PERSONAL_DATA));
        assertTrue(RegulatorExternalScope.mapping().values().stream()
            .noneMatch(r -> r == ExternalResource.EVIDENCE_PACK || r == ExternalResource.PERSONAL_DATA));
    }
}

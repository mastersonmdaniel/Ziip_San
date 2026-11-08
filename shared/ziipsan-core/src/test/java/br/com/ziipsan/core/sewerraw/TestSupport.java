package br.com.ziipsan.core.sewerraw;

import br.com.ziipsan.core.certification.governance.LegalRequirement;
import br.com.ziipsan.core.effluent.EffluentCatalog;
import br.com.ziipsan.core.effluent.EffluentStandard;
import br.com.ziipsan.core.watersystems.WaterSystem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Fixtures SINTETICAS declaradas [FIXTURE] — nenhum valor daqui e norma, limite ou dado real. */
final class TestSupport {
    static final UUID TENANT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    static final UUID TENANT2 = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    static WaterGrant grant(LocalDate from, LocalDate to) {
        return new WaterGrant(UUID.randomUUID(), TENANT, "OUT-FIXTURE-1", "ORGAO-FIXTURE",
                "Lei 9.433/1997; Resolucao ANA 236/2024", WaterGrant.WaterDomain.UNION_FEDERAL,
                from, to, new BigDecimal("120.5"), "L/s", List.of("COND-A", "COND-B"));
    }
    static EnvironmentalLicense license(LocalDate from, LocalDate to) {
        return new EnvironmentalLicense(UUID.randomUUID(), TENANT, "LO-FIXTURE-1", "LO",
                "ORGAO-FIXTURE", from, to, List.of("COND-L1"));
    }
    static RawWaterIntake intake(UUID grantId, UUID licenseId) {
        return new RawWaterIntake(UUID.randomUUID(), TENANT, "SAA-1", "CAPT-1", "Captacao Fixture",
                "Rio Fixture", WaterSystem.SourceKind.SURFACE, grantId, licenseId, true);
    }
    static WaterSystem supplySystem() {
        return new WaterSystem(UUID.randomUUID(), TENANT, "SAA-1", "Sistema Fixture", WaterSystem.Kind.SUPPLY,
                1000, LocalDate.of(2026, 1, 1),
                new WaterSystem.SourceInfo(WaterSystem.SourceKind.SURFACE, "Rio Fixture", "OUT-FIXTURE-1"),
                new WaterSystem.TreatmentInfo(List.of("ETA-1"), List.of(), "descricao fixture"), true);
    }
    static WaterSystem sewageSystem() {
        return new WaterSystem(UUID.randomUUID(), TENANT, "SES-1", "Esgoto Fixture", WaterSystem.Kind.SEWAGE,
                1000, LocalDate.of(2026, 1, 1), null,
                new WaterSystem.TreatmentInfo(List.of("ETE-1"), List.of(), "descricao fixture"), true);
    }
    static LegalRequirement legalReq() {
        return new LegalRequirement(UUID.randomUUID(), TENANT, "Outorga da captacao [FIXTURE]",
                "Lei 9.433/1997", LegalRequirement.Origin.TENANT_DECLARED, LocalDate.of(2027, 1, 1));
    }
    /** Catalogo da ED-105 com UM padrao sintetico revisado e publicado [FIXTURE]. */
    static EffluentCatalog catalog() {
        EffluentStandard s = new EffluentStandard("PARAM_A", "Parametro A [FIXTURE]", "mg/L",
                EffluentStandard.Comparator.MAX, null, new BigDecimal("999"), null,
                "NORMA-SINTETICA [FIXTURE]", EffluentStandard.Jurisdiction.FEDERAL, null,
                EffluentStandard.PublicationStatus.PUBLISHED, true, "RT-FIXTURE",
                LocalDate.of(2026, 1, 1), null);
        return new EffluentCatalog("v-fixture-1", List.of(s));
    }
}

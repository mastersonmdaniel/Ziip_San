package br.com.ziipsan.core.regulatory.export;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Testes negativos permanentes das correcoes D1–D5 da REVISAO-ED-97
 * (frente de correcao ED-97, 09/11/2026): cada sonda adversaria do
 * revisor vira teste — a garantia passa a valer POR CONSTRUCAO, no
 * construtor canonico, nao so pela fabrica.
 */
class CorrectionsD1D5Test {
    static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final Instant NOW = Instant.parse("2026-11-09T00:00:00Z");
    static final String FORGED_HASH = "a".repeat(64);

    // ---------- D1: ExportPackage por construcao ----------

    @Test void d1CanonicalConstructorRefusesNominalLineInAggregatePackage() {
        // Sonda literal do revisor: agregado com linha nominal e hash forjado.
        var profile = FactoryAgencyProfiles.arisSisaris();
        assertThrows(IllegalArgumentException.class, () -> new ExportPackage(
            profile, "2026-10", ExportPackage.Kind.AGGREGATE_PUBLIC,
            List.of("NOMINAL:cpf=000;nome=SYN"), FORGED_HASH));
    }

    @Test void d1CanonicalConstructorRefusesForgedHashEvenWithoutNominalLine() {
        var profile = FactoryAgencyProfiles.arisSisaris();
        assertThrows(IllegalArgumentException.class, () -> new ExportPackage(
            profile, "2026-10", ExportPackage.Kind.AGGREGATE_PUBLIC,
            List.of("categoria=RESIDENCIAL;consumo_medio=8.5"), FORGED_HASH));
    }

    @Test void d1CanonicalConstructorRefusesTamperedHashOfBuiltPackage() {
        var built = ExportPackage.build(FactoryAgencyProfiles.arisSisaris(), "2026-10",
            ExportPackage.Kind.AGGREGATE_PUBLIC, List.of("categoria=RESIDENCIAL;consumo_medio=8.5"));
        String tampered = (built.sha256Hex().startsWith("0") ? "1" : "0") + built.sha256Hex().substring(1);
        assertThrows(IllegalArgumentException.class, () -> new ExportPackage(
            built.profile(), built.periodRef(), built.kind(), built.lines(), tampered));
    }

    @Test void d1CanonicalConstructorAcceptsBuiltPackageReconstruction() {
        var built = ExportPackage.build(FactoryAgencyProfiles.arisSisaris(), "2026-10",
            ExportPackage.Kind.AGGREGATE_PUBLIC, List.of("categoria=RESIDENCIAL;consumo_medio=8.5"));
        var rebuilt = new ExportPackage(built.profile(), built.periodRef(), built.kind(),
            built.lines(), built.sha256Hex());
        assertEquals(built, rebuilt);
        assertEquals(built.sha256Hex(), rebuilt.expectedSha256());
    }

    @Test void d1CanonicalConstructorRefusesNominalRestrictedWithoutNominalLine() {
        var profile = FactoryAgencyProfiles.arisSisaris();
        var lines = List.of("categoria=RESIDENCIAL");
        String hash = ExportPackage.computeSha256(profile, "2026-10",
            ExportPackage.Kind.NOMINAL_RESTRICTED, lines);
        assertThrows(IllegalArgumentException.class, () -> new ExportPackage(
            profile, "2026-10", ExportPackage.Kind.NOMINAL_RESTRICTED, lines, hash));
    }

    @Test void d1NominalDetectionIsCaseInsensitive() {
        assertTrue(ExportPackage.isNominalLine("CPF=123;nome=SYN"));
        assertTrue(ExportPackage.isNominalLine("Nome=SYN;economia=E1"));
        assertTrue(ExportPackage.isNominalLine("nominal:economia=SYN-E1"));
        assertTrue(ExportPackage.isNominalLine("NOMINAL:economia=SYN-E1"));
        assertTrue(ExportPackage.isNominalLine("linha;CpF=000"));
        assertFalse(ExportPackage.isNominalLine("categoria=RESIDENCIAL;consumo_medio=8.5"));
        assertFalse(ExportPackage.isNominalLine(null));
    }

    @Test void d1FactoryRefusesUppercaseNominalMarkersInAggregate() {
        assertThrows(IllegalArgumentException.class, () -> ExportPackage.build(
            FactoryAgencyProfiles.arisSisaris(), "2026-10", ExportPackage.Kind.AGGREGATE_PUBLIC,
            List.of("CPF=123;NOME=SYN")));
    }

    @Test void d1ServiceRegistersOnlyHashCoherentPackage() {
        // O pacote valido (hash coerente por construcao) registra; um
        // pacote invalido nem chega ao servico — o construtor o recusa.
        var svc = new ExportSubmissionService();
        var pkg = ExportPackage.build(FactoryAgencyProfiles.arisSisaris(), "2026-10",
            ExportPackage.Kind.AGGREGATE_PUBLIC, List.of("categoria=RESIDENCIAL;consumo_medio=8.5"));
        var rec = svc.validateAndRegister(TENANT, pkg, true, NOW);
        assertEquals(pkg.expectedSha256(), rec.packageSha256Hex());
        assertEquals(1, svc.records().size());
    }

    // ---------- D2: SocialTariffIndex por construcao ----------

    @Test void d2CanonicalConstructorRefusesBenefitedAboveActive() {
        // Sonda literal do revisor.
        assertThrows(IllegalArgumentException.class, () -> new SocialTariffIndex(
            YearMonth.of(2026, 10), 2000, 1000, new BigDecimal("9.9999")));
    }

    @Test void d2CanonicalConstructorRefusesIncoherentValue() {
        assertThrows(IllegalArgumentException.class, () -> new SocialTariffIndex(
            YearMonth.of(2026, 10), 120, 1000, new BigDecimal("0.9999")));
        assertThrows(IllegalArgumentException.class, () -> new SocialTariffIndex(
            YearMonth.of(2026, 10), 120, 1000, new BigDecimal("0.12")));
    }

    @Test void d2CanonicalConstructorRefusesZeroActiveAndNegativeBenefited() {
        assertThrows(IllegalArgumentException.class, () -> new SocialTariffIndex(
            YearMonth.of(2026, 10), 0, 0, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new SocialTariffIndex(
            YearMonth.of(2026, 10), -1, 1000, new BigDecimal("-0.0010")));
    }

    @Test void d2CanonicalConstructorAcceptsCoherentIndex() {
        var its = new SocialTariffIndex(YearMonth.of(2026, 10), 120, 1000, new BigDecimal("0.1200"));
        assertEquals(SocialTariffIndex.of(YearMonth.of(2026, 10), 120, 1000), its);
    }

    // ---------- D3: AnaReconciliation por construcao ----------

    @Test void d3CanonicalConstructorRefusesNegativeAndInconsistentCounts() {
        // Sonda literal do revisor.
        assertThrows(IllegalArgumentException.class, () -> new AnaReconciliation(
            YearMonth.of(2026, 10), "4203000", "ARIS", -5, 3, 99, 0, 0));
    }

    @Test void d3CanonicalConstructorRefusesSumsThatDoNotClose() {
        assertThrows(IllegalArgumentException.class, () -> new AnaReconciliation(
            YearMonth.of(2026, 10), "4203000", "ARIS", 3, 3, 2, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new AnaReconciliation(
            YearMonth.of(2026, 10), "4203000", "ARIS", 3, 4, 2, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new AnaReconciliation(
            YearMonth.of(2026, 10), "4203000", "ARIS", 3, 3, 4, 0, 0));
    }

    @Test void d3CanonicalConstructorAcceptsConsistentReconciliation() {
        var r = new AnaReconciliation(YearMonth.of(2026, 10), "4203000", "ARIS", 3, 3, 2, 1, 1);
        assertTrue(r.diverged());
        assertEquals(AnaReconciliation.reconcile(YearMonth.of(2026, 10), "4203000", "ARIS",
            java.util.Set.of("SYN-E1", "SYN-E2", "SYN-E3"),
            java.util.Set.of("SYN-E2", "SYN-E3", "SYN-E4")), r);
    }

    // ---------- D4: ContentPack.covers() da secao anual ----------

    @Test void d4EmptyPackDoesNotCoverAnnualSection() {
        var empty = new RegulatoryContent.ContentPack(List.of(), List.of(), List.of(), List.of(), 0);
        assertFalse(empty.covers(RegulatoryContent.Section.RELATORIO_ANUAL_BENEFICIARIOS_TARIFA_SOCIAL));
        for (var s : RegulatoryContent.Section.values()) assertFalse(empty.covers(s), s.name());
    }

    @Test void d4PackWithBeneficiariesCoversAnnualSection() {
        var pack = new RegulatoryContent.ContentPack(List.of(), List.of(), List.of(), List.of(), 1);
        assertTrue(pack.covers(RegulatoryContent.Section.RELATORIO_ANUAL_BENEFICIARIOS_TARIFA_SOCIAL));
    }

    // ---------- D5: perfil completo vazio ----------

    @Test void d5CanonicalProfileRefusesCompleteLayoutWithNoFields() {
        assertThrows(IllegalArgumentException.class, () -> new AgencyExportProfile(
            "ADASA", "TENANT_ADASA", "LAY", "1", AgencyExportProfile.Periodicity.MONTHLY,
            AgencyExportProfile.Channel.ASSISTED_FILE, false,
            "valores proprios aceitos pelo tenant (RAS-31)", List.of(), true));
    }

    @Test void d5TenantDefinedWithEmptyFieldsIsNotLayoutComplete() {
        // Sonda literal do revisor: nascia layoutComplete=true.
        var p = FactoryAgencyProfiles.tenantDefined("ADASA", "LAY", "1",
            AgencyExportProfile.Periodicity.MONTHLY, List.of(), true);
        assertFalse(p.layoutComplete());
    }

    @Test void d5TenantDefinedWithConfirmedFieldRemainsComplete() {
        var p = FactoryAgencyProfiles.tenantDefined("ADASA", "LAY", "1",
            AgencyExportProfile.Periodicity.MONTHLY,
            List.of(LayoutField.confirmed("periodo", true)), true);
        assertTrue(p.layoutComplete());
    }
}

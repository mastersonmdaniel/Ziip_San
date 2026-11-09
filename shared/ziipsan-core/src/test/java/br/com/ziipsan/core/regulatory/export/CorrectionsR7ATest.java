package br.com.ziipsan.core.regulatory.export;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import br.com.ziipsan.core.certification.external.ExternalResource;

/**
 * Testes das correcoes R7-B1..R7-B3 do repasse da Rodada 7 da auditoria
 * continua (lote R7-A, 09/11/2026): cada achado do revisor 1 vira teste
 * negativo permanente. Nenhum arquivo de teste existente foi reescrito —
 * o teste de consistencia Java x SQL ganhou 2 testes novos no proprio
 * arquivo (Ed97PendenteSqlConsistencyTest).
 */
class CorrectionsR7ATest {
    static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final Instant NOW = Instant.parse("2026-11-09T00:00:00Z");

    // ---------- R7-B1 (MODERADO): kind distinguido no escopo/registro/evidencia ----------

    @Test void r7b1NominalPackageHasNoExternalResource() {
        assertTrue(RegulatorExternalScope.resourceForPackage(
            ExportPackage.Kind.NOMINAL_RESTRICTED).isEmpty());
        assertFalse(RegulatorExternalScope.canRegulatorReadPackage(
            ExportPackage.Kind.NOMINAL_RESTRICTED));
        assertEquals(ExternalResource.REGULATORY_DATA,
            RegulatorExternalScope.resourceForPackage(
                ExportPackage.Kind.AGGREGATE_PUBLIC).orElseThrow());
        assertTrue(RegulatorExternalScope.canRegulatorReadPackage(
            ExportPackage.Kind.AGGREGATE_PUBLIC));
    }

    @Test void r7b1ScopeItemsStillExactlyFiveAndCoherentWithEd75Matrix() {
        // A distincao por kind nao altera os 5 itens nem a matriz da
        // ED-75: a P-ED75-01 segue resolvida no nivel da definicao.
        assertEquals(5, RegulatorExternalScope.items().size());
        assertTrue(RegulatorExternalScope.isCoherentWithEd75Matrix());
        assertEquals(ExternalResource.REGULATORY_DATA,
            RegulatorExternalScope.ScopeItem.ED97_REGULATORY_PACKAGE.resource());
    }

    @Test void r7b1EvidenceRefsExcludeNominalByDefault() {
        var svc = new ExportSubmissionService();
        var agg = ExportPackage.build(FactoryAgencyProfiles.arisSisaris(), "2026-10",
            ExportPackage.Kind.AGGREGATE_PUBLIC,
            List.of("categoria=RESIDENCIAL;consumo_medio=8.5"));
        var nom = ExportPackage.build(FactoryAgencyProfiles.arisSisaris(), "2026-10",
            ExportPackage.Kind.NOMINAL_RESTRICTED, List.of("NOMINAL:economia=SYN-E1"));
        var aggRec = svc.validateAndRegister(TENANT, agg, true, NOW);
        var nomRec = svc.validateAndRegister(TENANT, nom, true, NOW);
        // O canal padrao de evidencia so devolve refs agregadas.
        assertEquals(List.of(aggRec.evidenceRef()), svc.evidenceRefsFor("2026-10"));
        // Por kind explicito (uso local), cada kind devolve so as suas.
        assertEquals(List.of(aggRec.evidenceRef()),
            svc.evidenceRefsFor("2026-10", ExportPackage.Kind.AGGREGATE_PUBLIC));
        assertEquals(List.of(nomRec.evidenceRef()),
            svc.evidenceRefsFor("2026-10", ExportPackage.Kind.NOMINAL_RESTRICTED));
    }

    @Test void r7b1RecordsDistinguishKind() {
        var svc = new ExportSubmissionService();
        var agg = ExportPackage.build(FactoryAgencyProfiles.arisSisaris(), "2026-10",
            ExportPackage.Kind.AGGREGATE_PUBLIC,
            List.of("categoria=RESIDENCIAL;consumo_medio=8.5"));
        var nom = ExportPackage.build(FactoryAgencyProfiles.arisSisaris(), "2026-10",
            ExportPackage.Kind.NOMINAL_RESTRICTED, List.of("NOMINAL:economia=SYN-E1"));
        var aggRec = svc.validateAndRegister(TENANT, agg, true, NOW);
        var nomRec = svc.validateAndRegister(TENANT, nom, true, NOW);
        assertFalse(aggRec.isRestricted());
        assertTrue(nomRec.isRestricted());
        assertEquals(List.of(aggRec),
            svc.recordsOfKind(ExportPackage.Kind.AGGREGATE_PUBLIC));
        assertEquals(List.of(nomRec),
            svc.recordsOfKind(ExportPackage.Kind.NOMINAL_RESTRICTED));
    }

    // ---------- R7-B2 (MENOR): evidence_ref do Java passa no CHECK do SQL ----------

    @Test void r7b2JavaEvidenceRefMatchesSqlFormatCheck() {
        // O padrao e o mesmo do CHECK ck_reg_export_evidence_ref_format
        // no SQL pendente: ED97-EVIDENCE:<agencia>:<periodo>:<16 hex>.
        var pattern = Pattern.compile(
            "^ED97-EVIDENCE:[A-Za-z0-9_-]+:[A-Za-z0-9_-]+:[0-9a-f]{16}$");
        var svc = new ExportSubmissionService();
        var pkg = ExportPackage.build(FactoryAgencyProfiles.arisSisaris(), "2026-10",
            ExportPackage.Kind.AGGREGATE_PUBLIC,
            List.of("categoria=RESIDENCIAL;consumo_medio=8.5"));
        var rec = svc.validateAndRegister(TENANT, pkg, true, NOW);
        assertTrue(pattern.matcher(rec.evidenceRef()).matches(), rec.evidenceRef());
        var pkgQ = ExportPackage.build(FactoryAgencyProfiles.agirSacOuvidoria(), "2026-Q3",
            ExportPackage.Kind.AGGREGATE_PUBLIC, List.of("sac_total=10"));
        var recQ = svc.validateAndRegister(TENANT, pkgQ, true, NOW);
        assertTrue(pattern.matcher(recQ.evidenceRef()).matches(), recQ.evidenceRef());
    }

    // ---------- R7-B3 (MENOR): campo obrigatorio por chave, nao por substring ----------

    static AgencyExportProfile completeProfileRequiringCampo() {
        return FactoryAgencyProfiles.tenantDefined("ADASA", "LAY", "1",
            AgencyExportProfile.Periodicity.MONTHLY,
            List.of(LayoutField.confirmed("campo", true)), true);
    }

    @Test void r7b3FieldNameInsideAnotherFieldsValueDoesNotSatisfy() {
        // Sonda do revisor: `nota=campo=5` NAO satisfaz o campo `campo`
        // (a chave do segmento e `nota`, nao `campo`).
        var svc = new ExportSubmissionService();
        var pkg = ExportPackage.build(completeProfileRequiringCampo(), "2026-10",
            ExportPackage.Kind.AGGREGATE_PUBLIC, List.of("nota=campo=5"));
        var ex = assertThrows(IllegalArgumentException.class,
            () -> svc.validateAndRegister(TENANT, pkg, true, NOW));
        assertTrue(ex.getMessage().contains("campo"));
    }

    @Test void r7b3FieldNameIsCaseInsensitive() {
        // Sonda do revisor: `CAMPO=...` satisfaz o campo `campo`.
        var svc = new ExportSubmissionService();
        var pkg = ExportPackage.build(completeProfileRequiringCampo(), "2026-10",
            ExportPackage.Kind.AGGREGATE_PUBLIC, List.of("CAMPO=7"));
        assertNotNull(svc.validateAndRegister(TENANT, pkg, true, NOW));
    }

    @Test void r7b3FieldNameInLaterSegmentSatisfies() {
        // O campo pode estar em qualquer segmento `chave=valor;...`.
        var svc = new ExportSubmissionService();
        var pkg = ExportPackage.build(completeProfileRequiringCampo(), "2026-10",
            ExportPackage.Kind.AGGREGATE_PUBLIC, List.of("nota=1;campo=5"));
        assertNotNull(svc.validateAndRegister(TENANT, pkg, true, NOW));
    }

    @Test void r7b3BareKeyWithoutEqualsDoesNotSatisfy() {
        // Segmento sem `=` nao conta: a linha segue o formato
        // `chave=valor;...` (mesma severidade do `contains` anterior).
        var svc = new ExportSubmissionService();
        var pkg = ExportPackage.build(completeProfileRequiringCampo(), "2026-10",
            ExportPackage.Kind.AGGREGATE_PUBLIC, List.of("campo"));
        assertThrows(IllegalArgumentException.class,
            () -> svc.validateAndRegister(TENANT, pkg, true, NOW));
    }
}

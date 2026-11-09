package br.com.ziipsan.core.regulatory.agenda;

import br.com.ziipsan.core.regulatory.export.ExportPackage;
import br.com.ziipsan.core.regulatory.export.SubmissionRecord;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;

/** RAS-08 (evidencia da ED-97) e RAS-17 (item manual MTR/CDF/DMR). */
class EvidenceAndManualTest {

    private AgendaItem pendingItem() {
        return new ObligationCalendarService(AgendaFixtures.fullPort(), new AgendaFixtures.RecordingReminders())
                .generate(AgendaFixtures.profile(AgendaFixtures.TENANT_A, TenantLegalNature.AUTARQUIA, false), 2027)
                .itemsOf(ObligationKind.SISAGUA).get(0);
    }

    private SubmissionRecord submission(UUID tenant) {
        // Correcao D1 (fixture, sem tocar em nenhuma assercao): o perfil
        // do envio corresponde ao tipo do item usado neste arquivo
        // (SISAGUA) — "ARIS_SISARIS" era o perfil de OUTRO relatorio e,
        // com a correspondencia de tipo da correcao, nao quita o SISAGUA.
        return new SubmissionRecord(UUID.randomUUID(), tenant, "ARIS", "ARIS_SISAGUA", "[A DEFINIR]",
                "2027-ANNUAL", ExportPackage.Kind.AGGREGATE_PUBLIC, "a".repeat(64),
                SubmissionRecord.Status.SUBMITTED_BY_TENANT, Instant.now(), "SYN-PROTO-1");
    }

    @Test void ed97SubmissionEvidenceLinksToItem() {
        SubmissionRecord rec = submission(AgendaFixtures.TENANT_A);
        AgendaItem linked = pendingItem().linkSubmission(rec);
        assertEquals(rec.evidenceRef(), linked.evidenceRef(), "evidenceRef e o da ED-97, sem reformatar");
        assertTrue(linked.evidenceRef().startsWith("ED97-EVIDENCE:"));
        assertEquals(AgendaItem.Status.EVIDENCE_LINKED, linked.status());
        assertEquals(AgendaItem.Status.DONE, linked.complete().status());
    }

    @Test void evidenceFromAnotherTenantIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> pendingItem().linkSubmission(submission(AgendaFixtures.TENANT_B)));
    }

    @Test void completionWithoutEvidenceIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> pendingItem().complete());
        assertThrows(IllegalArgumentException.class, () -> new AgendaItem(UUID.randomUUID(),
                AgendaFixtures.TENANT_A, ObligationKind.SISAGUA, "t", "k", "2027-ANNUAL", null,
                "[A DEFINIR]", AgendaFixtures.RESP, null, AgendaItem.Status.DONE, false,
                null, null, null, null));
    }

    @Test void manualWasteItemIsManualAndCompletesOnlyWithNote() {
        AgendaItem manual = new ObligationCalendarService(AgendaFixtures.fullPort(),
                new AgendaFixtures.RecordingReminders())
                .generate(AgendaFixtures.profile(AgendaFixtures.TENANT_A, TenantLegalNature.AUTARQUIA, false), 2027)
                .itemsOf(ObligationKind.WASTE_MTR_CDF_DMR_MANUAL).get(0);
        assertTrue(manual.manual(), "MTR/CDF/DMR e item manual enquanto a ED-104 nao for liberada (RAS-17)");
        assertThrows(IllegalArgumentException.class, () -> manual.linkSubmission(submission(AgendaFixtures.TENANT_A)),
                "item manual nao aceita evidencia de envio da ED-97");
        assertThrows(IllegalArgumentException.class, () -> manual.completeManually(" "));
        AgendaItem done = manual.completeManually("MTR/CDF conferidos no documento fisico do transportador");
        assertEquals(AgendaItem.Status.DONE, done.status());
        assertNotNull(done.completionNote());
    }
}

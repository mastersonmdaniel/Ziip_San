package br.com.ziipsan.core.regulatory.agenda;

import br.com.ziipsan.core.regulatory.export.ExportPackage;
import br.com.ziipsan.core.regulatory.export.SubmissionRecord;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Correcao R8-ED98-1 (Rodada 8): {@link AgendaItem#linkSubmission} exige
 * pacote AGGREGATE_PUBLIC — um registro NOMINAL_RESTRICTED, mesmo com
 * tenant, periodo, estado e perfil correspondentes, nao quita a
 * obrigacao. Produtor do lote R8-B, sessao
 * 8b71ddfb-41b3-40ac-9ecb-88a13c7d22a3.
 */
class R8Ed98KindGuardTest {

    private AgendaItem pendingItem() {
        return new ObligationCalendarService(AgendaFixtures.fullPort(), new AgendaFixtures.RecordingReminders())
                .generate(AgendaFixtures.profile(AgendaFixtures.TENANT_A, TenantLegalNature.AUTARQUIA, false), 2027)
                .itemsOf(ObligationKind.SISAGUA).get(0);
    }

    private SubmissionRecord submission(UUID tenant, ExportPackage.Kind kind) {
        return new SubmissionRecord(UUID.randomUUID(), tenant, "ARIS", "ARIS_SISAGUA", "[A DEFINIR]",
                "2027-ANNUAL", kind, "a".repeat(64),
                SubmissionRecord.Status.SUBMITTED_BY_TENANT, Instant.now(), "SYN-PROTO-1");
    }

    @Test void nominalRestrictedSubmissionIsRefusedAndItemStaysPending() {
        AgendaItem item = pendingItem();
        SubmissionRecord nominal = submission(AgendaFixtures.TENANT_A, ExportPackage.Kind.NOMINAL_RESTRICTED);
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> item.linkSubmission(nominal),
                "registro NOMINAL_RESTRICTED nao quita a obrigacao (R8-ED98-1)");
        assertTrue(refused.getMessage().contains("NOMINAL_RESTRICTED"),
                "a mensagem nomeia o kind recusado: " + refused.getMessage());
        assertEquals(AgendaItem.Status.PENDING, item.status(), "o item fica PENDING");
        assertNull(item.evidenceRef(), "nenhuma evidencia ligada");
        assertThrows(IllegalArgumentException.class, () -> item.complete(),
                "complete() continua recusado sem evidencia ligada");
    }

    @Test void aggregatePublicSubmissionStillLinksAndCompletes() {
        SubmissionRecord aggregate = submission(AgendaFixtures.TENANT_A, ExportPackage.Kind.AGGREGATE_PUBLIC);
        AgendaItem linked = pendingItem().linkSubmission(aggregate);
        assertEquals(aggregate.evidenceRef(), linked.evidenceRef(), "evidenceRef e o da ED-97, sem reformatar");
        assertEquals(AgendaItem.Status.EVIDENCE_LINKED, linked.status());
        assertEquals(AgendaItem.Status.DONE, linked.complete().status());
    }
}

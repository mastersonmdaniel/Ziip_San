package br.com.ziipsan.core.regulatory.agenda;

import br.com.ziipsan.platform.central.complete.RegulatoryLibrary;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** JOR-24 (fiscalizacao -> ocorrencia P2.25) e alerta antecipado da P1.34. */
class InspectionAndEarlyWarningTest {

    static final class RecordingOccurrences implements AgendaPorts.OccurrencePort {
        final List<AgendaPorts.OccurrenceRequest> requests = new ArrayList<>();
        String nextRef = "P225-OCCURRENCE:1";
        @Override public String registerOccurrence(AgendaPorts.OccurrenceRequest request) {
            requests.add(request);
            return nextRef;
        }
    }

    @Test void inspectionFromEachSourceBecomesOccurrenceWithActionPlanAndReminder() {
        for (AgendaPorts.InspectionSource source : AgendaPorts.InspectionSource.values()) {
            var occ = new RecordingOccurrences();
            var rem = new AgendaFixtures.RecordingReminders();
            var svc = new InspectionAgendaService(occ, rem);
            AgendaItem item = svc.registerInspection(
                    AgendaFixtures.profile(AgendaFixtures.TENANT_A, TenantLegalNature.AUTARQUIA, false),
                    source, "Auto de fiscalizacao SYN-" + source, LocalDate.of(2026, 11, 2),
                    LocalDate.of(2026, 12, 2), "Plano de acao: corrigir e comprovar");
            assertEquals(1, occ.requests.size());
            assertEquals(source, occ.requests.get(0).source());
            assertEquals("P225-OCCURRENCE:1", item.occurrenceRef());
            assertEquals("Plano de acao: corrigir e comprovar", item.actionPlan());
            assertNotNull(item.reminderRef(), "fiscalizacao tem lembrete nesta agenda (JOR-24)");
            assertEquals(1, rem.requests.size());
            assertEquals(LocalDate.of(2026, 12, 2), item.dueDate());
        }
    }

    @Test void inspectionWithoutActionPlanIsRefusedBeforeOccurrence() {
        var occ = new RecordingOccurrences();
        var svc = new InspectionAgendaService(occ, new AgendaFixtures.RecordingReminders());
        assertThrows(IllegalArgumentException.class, () -> svc.registerInspection(
                AgendaFixtures.profile(AgendaFixtures.TENANT_A, TenantLegalNature.AUTARQUIA, false),
                AgendaPorts.InspectionSource.COURT_OF_ACCOUNTS, "Oficio SYN-1",
                LocalDate.of(2026, 11, 2), LocalDate.of(2026, 12, 2), " "));
        assertTrue(occ.requests.isEmpty(), "sem plano de acao, nenhuma ocorrencia e registrada");
    }

    @Test void blankOccurrenceRefFromPortCreatesNothing() {
        var occ = new RecordingOccurrences();
        occ.nextRef = " ";
        var svc = new InspectionAgendaService(occ, new AgendaFixtures.RecordingReminders());
        assertThrows(IllegalStateException.class, () -> svc.registerInspection(
                AgendaFixtures.profile(AgendaFixtures.TENANT_A, TenantLegalNature.AUTARQUIA, false),
                AgendaPorts.InspectionSource.AGENCY, "Visita SYN-2",
                LocalDate.of(2026, 11, 2), LocalDate.of(2026, 12, 2), "Plano de acao SYN"));
    }

    @Test void earlyWarningForFutureEffectNormWithinWindowOnly() {
        LocalDate today = LocalDate.of(2026, 11, 9);
        var futureSoon = AgendaFixtures.norm("norma-futura-perto", "Norma futura proxima",
                "VIGENCIA_FUTURA", today.plusDays(20));
        var futureFar = AgendaFixtures.norm("norma-futura-longe", "Norma futura longe",
                "VIGENCIA_FUTURA", today.plusDays(200));
        var futureNoDate = AgendaFixtures.norm("norma-futura-sem-data", "Norma futura sem data",
                "VIGENCIA_FUTURA", null);
        var library = new RegulatoryLibrary(List.of(futureSoon, futureFar, futureNoDate));
        // Coerencia com a P3.15/P1.34 REAL: withFutureEffect ve as edicoes
        // futuras; statusAt mantem VIGENCIA_FUTURA antes da data de efeito.
        assertEquals("VIGENCIA_FUTURA", RegulatoryLibrary.statusAt(futureSoon, today));
        var rem = new AgendaFixtures.RecordingReminders();
        var svc = new ObligationCalendarService(AgendaFixtures.portOf(library), rem);
        var warnings = svc.earlyNormWarnings(
                AgendaFixtures.profile(AgendaFixtures.TENANT_A, TenantLegalNature.AUTARQUIA, false), today, 90);
        assertEquals(1, warnings.size(), "so a norma com data de efeito na janela alerta (P1.34)");
        assertEquals("norma-futura-perto", warnings.get(0).catalogKey());
        assertEquals(today.plusDays(20), warnings.get(0).effectiveFrom());
        assertNotNull(warnings.get(0).reminderRef());
        assertEquals(1, rem.requests.size());
    }
}

package br.com.ziipsan.reporting.exportprofile;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CadastreChecklistTest {

    @Test void has16NamedItemsAllWithoutFabricatedRequirement() {
        List<CadastreChecklistItem> items = CadastreChecklist.items();
        assertEquals(16, items.size());
        for (String norm : ExportReferences.CADASTRE_NORMS) {
            assertEquals(4, items.stream().filter(i -> i.normReference().equals(norm)).count(), norm);
        }
        for (CadastreChecklistItem item : items) {
            assertTrue(item.requirementSource().startsWith("[SEM FONTE]"), item.itemCode());
        }
        assertEquals(4, items.stream().filter(CadastreChecklistItem::requiresReferenceNetworkLink).count());
    }

    @Test void refusesNormOutsideAcceptanceList() {
        ExportValidationException ex = assertThrows(ExportValidationException.class, () ->
                new CadastreChecklistItem("X-1", "ABNT NBR 99999", "topico", "[SEM FONTE] x", false));
        assertEquals("CHECKLIST_NORM_NOT_IN_ACCEPTANCE", ex.code());
    }

    @Test void notCompleteWhileAnyItemPendingOrMissing() {
        assertFalse(CadastreChecklist.isComplete(Map.of()));
        Map<String, CadastreChecklist.Evaluation> ev = new HashMap<>();
        for (CadastreChecklistItem item : CadastreChecklist.items()) {
            ev.put(item.itemCode(), new CadastreChecklist.Evaluation(item.itemCode(),
                    CadastreChecklist.ItemStatus.VERIFIED, true, "avaliador", null));
        }
        ev.put("NBR12586-COMPLETUDE", new CadastreChecklist.Evaluation("NBR12586-COMPLETUDE",
                CadastreChecklist.ItemStatus.PENDING_VERIFICATION, false, null, null));
        assertFalse(CadastreChecklist.isComplete(ev));
    }

    @Test void notCompleteWhenReferenceNetworkUnlinked() {
        Map<String, CadastreChecklist.Evaluation> ev = new HashMap<>();
        for (CadastreChecklistItem item : CadastreChecklist.items()) {
            ev.put(item.itemCode(), new CadastreChecklist.Evaluation(item.itemCode(),
                    CadastreChecklist.ItemStatus.VERIFIED, false, "avaliador", null));
        }
        assertFalse(CadastreChecklist.isComplete(ev));
    }

    // Correcao D2 (sonda J3 do revisor): NOT_APPLICABLE sem justificativa e recusado
    // no construtor - a avaliacao nem existe, logo nao pode completar o checklist.
    @Test void refusesNotApplicableWithoutJustification() {
        ExportValidationException ex = assertThrows(ExportValidationException.class, () ->
                new CadastreChecklist.Evaluation("NBR12586-COMPLETUDE",
                        CadastreChecklist.ItemStatus.NOT_APPLICABLE, true, "avaliador", null));
        assertEquals("CHECKLIST_EVAL_JUSTIFICATION_REQUIRED", ex.code());
        ExportValidationException blank = assertThrows(ExportValidationException.class, () ->
                new CadastreChecklist.Evaluation("NBR12586-COMPLETUDE",
                        CadastreChecklist.ItemStatus.NOT_APPLICABLE, true, "avaliador", "   "));
        assertEquals("CHECKLIST_EVAL_JUSTIFICATION_REQUIRED", blank.code());
    }

    // Caso J3 completo: montar as 16 avaliacoes NOT_APPLICABLE sem justificativa
    // falha na primeira construcao - o cenario que antes dava isComplete=true
    // nao e mais construtivel.
    @Test void sixteenNotApplicableWithoutJustificationDoNotComplete() {
        Map<String, CadastreChecklist.Evaluation> ev = new HashMap<>();
        ExportValidationException ex = assertThrows(ExportValidationException.class, () -> {
            for (CadastreChecklistItem item : CadastreChecklist.items()) {
                ev.put(item.itemCode(), new CadastreChecklist.Evaluation(item.itemCode(),
                        CadastreChecklist.ItemStatus.NOT_APPLICABLE, true, "avaliador", null));
            }
        });
        assertEquals("CHECKLIST_EVAL_JUSTIFICATION_REQUIRED", ex.code());
        assertFalse(CadastreChecklist.isComplete(ev));
    }

    // Controle positivo do D2: NOT_APPLICABLE COM justificativa, avaliador nomeado
    // e rede ligada segue sendo caminho legitimo de completude.
    @Test void completeWithAllNotApplicableJustifiedAndNetworkLinked() {
        Map<String, CadastreChecklist.Evaluation> ev = new HashMap<>();
        for (CadastreChecklistItem item : CadastreChecklist.items()) {
            ev.put(item.itemCode(), new CadastreChecklist.Evaluation(item.itemCode(),
                    CadastreChecklist.ItemStatus.NOT_APPLICABLE, true, "avaliador",
                    "justificativa registrada por quem avaliou"));
        }
        assertTrue(CadastreChecklist.isComplete(ev));
    }

    @Test void completeOnlyWhenAllVerifiedAndNetworkLinked() {
        Map<String, CadastreChecklist.Evaluation> ev = new HashMap<>();
        for (CadastreChecklistItem item : CadastreChecklist.items()) {
            ev.put(item.itemCode(), new CadastreChecklist.Evaluation(item.itemCode(),
                    CadastreChecklist.ItemStatus.VERIFIED, true, "avaliador", null));
        }
        assertTrue(CadastreChecklist.isComplete(ev));
    }
}
